package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.CosmoApiService
import com.example.data.db.AppDatabase
import com.example.data.db.DownloadedGameEntity
import com.example.data.model.Category
import com.example.data.model.StoreApp
import com.example.downloader.ActiveDownload
import com.example.downloader.CosmoDownloadManager
import com.example.installer.ApkParser
import com.example.installer.InstallCallback
import com.example.installer.PackageInstallerHelper
import com.example.installer.PackageModel
import com.example.installer.PackageType
import com.example.installer.XapkParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class CosmoViewModel(application: Application) : AndroidViewModel(application) {

    private val apiService = CosmoApiService.create()
    private val database = AppDatabase.getDatabase(application)
    private val prefs = application.getSharedPreferences("cosmo_prefs", Context.MODE_PRIVATE)

    // Store Data State
    private val _allApps = MutableStateFlow<List<StoreApp>>(emptyList())
    val allApps: StateFlow<List<StoreApp>> = _allApps.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Room DB Packages
    val downloadedEntities: StateFlow<List<DownloadedGameEntity>> = database.gameDao().getAllPackages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Live Download
    val activeDownload: StateFlow<ActiveDownload?> = CosmoDownloadManager.currentActiveDownload

    // App Detail Sheet Selection
    private val _selectedAppForDetail = MutableStateFlow<StoreApp?>(null)
    val selectedAppForDetail: StateFlow<StoreApp?> = _selectedAppForDetail.asStateFlow()

    // Package to Install Dialog
    private val _pendingInstallModel = MutableStateFlow<PackageModel?>(null)
    val pendingInstallModel: StateFlow<PackageModel?> = _pendingInstallModel.asStateFlow()

    private val _isInstalling = MutableStateFlow(false)
    val isInstalling: StateFlow<Boolean> = _isInstalling.asStateFlow()

    private val _installProgress = MutableStateFlow(0)
    val installProgress: StateFlow<Int> = _installProgress.asStateFlow()

    private val _installMessage = MutableStateFlow("")
    val installMessage: StateFlow<String> = _installMessage.asStateFlow()

    // Preferences
    private val _themePreference = MutableStateFlow(prefs.getString("theme_preference", "SYSTEM") ?: "SYSTEM")
    val themePreference: StateFlow<String> = _themePreference.asStateFlow()

    private val _autoLaunch = MutableStateFlow(prefs.getBoolean("auto_launch_after_install", true))
    val autoLaunch: StateFlow<Boolean> = _autoLaunch.asStateFlow()

    init {
        loadCatalog()
    }

    fun loadCatalog() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val apps = apiService.getApps()
                val cats = apiService.getCategories()
                _allApps.value = apps
                _categories.value = cats
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Unable to connect to worker API"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectAppForDetail(app: StoreApp?) {
        _selectedAppForDetail.value = app
    }

    fun showInstallDialog(model: PackageModel?) {
        _pendingInstallModel.value = model
    }

    fun dismissInstallDialog() {
        if (!_isInstalling.value) {
            _pendingInstallModel.value = null
        }
    }

    fun startDownload(app: StoreApp) {
        val context = getApplication<Application>()
        CosmoDownloadManager.startDownload(context, app) { downloadedFile, isXapk ->
            // Download completed callback
            val model = if (isXapk) {
                XapkParser.parse(context, downloadedFile)
            } else {
                ApkParser.parse(context, downloadedFile)
            }
            if (model != null) {
                _pendingInstallModel.value = model
            }
        }
    }

    fun cancelActiveDownload(downloadId: Long) {
        val context = getApplication<Application>()
        CosmoDownloadManager.cancelActiveDownload(context, downloadId)
    }

    fun executeInstall(model: PackageModel) {
        val context = getApplication<Application>()

        if (!PackageInstallerHelper.canRequestPackageInstalls(context)) {
            PackageInstallerHelper.openInstallPermissionSettings(context)
            Toast.makeText(context, "Please allow Unknown Apps installation permission", Toast.LENGTH_LONG).show()
            return
        }

        if (model.type == PackageType.XAPK) {
            viewModelScope.launch {
                _isInstalling.value = true
                _installProgress.value = 10
                _installMessage.value = "Starting multi-split installer..."

                PackageInstallerHelper.installXapk(
                    context = context,
                    xapkFile = model.file,
                    model = model,
                    callback = object : InstallCallback {
                        override fun onProgress(message: String, progressPercent: Int) {
                            _installMessage.value = message
                            _installProgress.value = progressPercent
                        }

                        override fun onSuccess() {
                            _isInstalling.value = false
                            _pendingInstallModel.value = null
                            Toast.makeText(context, "XAPK installation session committed", Toast.LENGTH_SHORT).show()
                        }

                        override fun onError(errorMessage: String) {
                            _isInstalling.value = false
                            Toast.makeText(context, "Install failed: $errorMessage", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
        } else {
            PackageInstallerHelper.installStandardApk(
                context = context,
                apkFile = model.file,
                callback = object : InstallCallback {
                    override fun onProgress(message: String, progressPercent: Int) {}

                    override fun onSuccess() {
                        _pendingInstallModel.value = null
                    }

                    override fun onError(errorMessage: String) {
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }

    fun setTheme(theme: String) {
        _themePreference.value = theme
        prefs.edit().putString("theme_preference", theme).apply()
    }

    fun setAutoLaunch(enabled: Boolean) {
        _autoLaunch.value = enabled
        prefs.edit().putBoolean("auto_launch_after_install", enabled).apply()
    }

    fun launchInstalledPackage(packageName: String) {
        val context = getApplication<Application>()
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "Unable to launch application", Toast.LENGTH_SHORT).show()
        }
    }

    fun isAppInstalled(packageName: String?): Boolean {
        if (packageName.isNullOrEmpty()) return false
        val context = getApplication<Application>()
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }
}
