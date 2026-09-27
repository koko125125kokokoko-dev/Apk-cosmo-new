package com.example.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

interface InstallCallback {
    fun onProgress(message: String, progressPercent: Int)
    fun onSuccess()
    fun onError(errorMessage: String)
}

object PackageInstallerHelper {
    private const val TAG = "PackageInstallerHelper"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                val fallbackIntent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            }
        }
    }

    fun installStandardApk(
        context: Context,
        apkFile: File,
        callback: InstallCallback? = null
    ) {
        try {
            callback?.onProgress("Launching Package Installer...", 50)
            val authority = "${context.applicationContext.packageName}.fileprovider"
            val apkUri: Uri = FileProvider.getUriForFile(context.applicationContext, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            callback?.onSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Error installing standard APK", e)
            callback?.onError("Failed to launch installer: ${e.message}")
        }
    }

    suspend fun installXapk(
        context: Context,
        xapkFile: File,
        model: PackageModel,
        callback: InstallCallback? = null
    ) = withContext(Dispatchers.IO) {
        var session: PackageInstaller.Session? = null
        var zipFile: ZipFile? = null

        try {
            notifyProgress(callback, "Preparing XAPK installation session...", 10)
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

            if (model.packageName.isNotEmpty()) {
                params.setAppPackageName(model.packageName)
            }
            if (xapkFile.length() > 0) {
                params.setSize(xapkFile.length())
            }

            val sessionId = packageInstaller.createSession(params)
            session = packageInstaller.openSession(sessionId)
            zipFile = ZipFile(xapkFile)

            val totalEntries = zipFile.size()
            var processed = 0
            notifyProgress(callback, "Streaming split APKs to installer...", 30)

            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                processed++

                if (name.endsWith(".apk", ignoreCase = true)) {
                    val cleanName = sanitizeZipEntryName(name)
                    val size = if (entry.size <= 0) 0L else entry.size

                    session.openWrite(cleanName, 0, size).use { out ->
                        zipFile.getInputStream(entry).use { input ->
                            val buffer = ByteArray(65536)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                out.write(buffer, 0, read)
                            }
                            session.fsync(out)
                        }
                    }
                } else if (name.endsWith(".obb", ignoreCase = true) || name.contains("/obb/", ignoreCase = true)) {
                    notifyProgress(callback, "Extracting expansion game data (OBB)...", 70)
                    extractObb(context, zipFile, entry, model.packageName)
                }

                val pct = 30 + (processed.toFloat() / totalEntries.coerceAtLeast(1) * 50).toInt()
                notifyProgress(callback, "Writing: ${File(name).name}", pct.coerceAtMost(85))
            }

            notifyProgress(callback, "Committing installation to system...", 90)

            val statusIntent = Intent(context, InstallStatusReceiver::class.java).apply {
                action = InstallStatusReceiver.ACTION_INSTALL_STATUS
                putExtra(InstallStatusReceiver.EXTRA_PACKAGE_NAME, model.packageName)
            }

            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or PendingIntent.FLAG_MUTABLE
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                statusIntent,
                flags
            )

            session.commit(pendingIntent.intentSender)
            session.close()
            session = null

            notifyProgress(callback, "Installer prompt dispatched. Confirm on screen.", 100)
            notifySuccess(callback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed XAPK installation: ${xapkFile.absolutePath}", e)
            try {
                session?.abandon()
            } catch (_: Exception) {}
            notifyError(callback, "XAPK install error: ${e.message}")
        } finally {
            try {
                zipFile?.close()
            } catch (_: Exception) {}
        }
    }

    private fun extractObb(context: Context, zipFile: ZipFile, entry: ZipEntry, packageName: String?) {
        try {
            val obbBaseDir = if (!packageName.isNullOrEmpty()) {
                val externalStorage = Environment.getExternalStorageDirectory()
                File(externalStorage, "Android/obb/$packageName")
            } else {
                File(context.getExternalFilesDir(null), "obb")
            }

            if (!obbBaseDir.exists()) {
                obbBaseDir.mkdirs()
            }

            val fileName = File(entry.name).name
            val destObb = File(obbBaseDir, fileName)

            zipFile.getInputStream(entry).use { input ->
                FileOutputStream(destObb).use { output ->
                    val buffer = ByteArray(65536)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed extracting OBB: ${entry.name}", e)
        }
    }

    private fun sanitizeZipEntryName(name: String): String {
        val clean = File(name).name
        return clean.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    private fun notifyProgress(callback: InstallCallback?, msg: String, pct: Int) {
        callback?.let { cb -> mainHandler.post { cb.onProgress(msg, pct) } }
    }

    private fun notifySuccess(callback: InstallCallback?) {
        callback?.let { cb -> mainHandler.post { cb.onSuccess() } }
    }

    private fun notifyError(callback: InstallCallback?, error: String) {
        callback?.let { cb -> mainHandler.post { cb.onError(error) } }
    }
}
