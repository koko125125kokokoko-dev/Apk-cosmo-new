package com.example.downloader

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.example.data.db.AppDatabase
import com.example.data.db.DownloadedGameEntity
import com.example.data.model.StoreApp
import com.example.installer.ApkParser
import com.example.installer.XapkParser
import com.example.notification.DownloadNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ActiveDownload(
    val downloadId: Long,
    val name: String,
    val progress: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val isXapk: Boolean
)

object CosmoDownloadManager {
    private const val TAG = "CosmoDownloader"
    private val scope = CoroutineScope(Dispatchers.IO)
    private var trackingJob: Job? = null

    private val _currentActiveDownload = MutableStateFlow<ActiveDownload?>(null)
    val currentActiveDownload = _currentActiveDownload.asStateFlow()

    fun startDownload(context: Context, app: StoreApp, onCompleted: ((File, Boolean) -> Unit)? = null): Long {
        val rawUrl = app.downloadUrl
        if (rawUrl.isNullOrEmpty()) {
            Toast.makeText(context, "No download URL available for ${app.name}", Toast.LENGTH_SHORT).show()
            return -1L
        }

        val isXapk = rawUrl.contains("XAPK", ignoreCase = true) || rawUrl.endsWith(".xapk", ignoreCase = true)
        val extension = if (isXapk) ".xapk" else ".apk"
        val cleanName = app.name.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val fileName = "${cleanName}_${app.version ?: "v1"}$extension"

        return try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(rawUrl)).apply {
                setTitle(app.name)
                setDescription("Downloading ${if (isXapk) "XAPK" else "APK"} package")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                try {
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                } catch (e: Exception) {
                    setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                }
            }

            val downloadId = downloadManager.enqueue(request)
            Toast.makeText(context, "Download started: ${app.name}", Toast.LENGTH_SHORT).show()

            DownloadNotificationHelper.createNotificationChannel(context)

            // Save initial record to Room database
            scope.launch {
                val db = AppDatabase.getDatabase(context)
                val targetFile = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    fileName
                )
                val entity = DownloadedGameEntity(
                    downloadId = downloadId,
                    name = app.name,
                    packageName = "",
                    versionName = app.version ?: "1.0",
                    versionCode = 1L,
                    filePath = targetFile.absolutePath,
                    fileSize = 0L,
                    isXapk = isXapk,
                    iconUrl = app.iconUrl,
                    status = "DOWNLOADING",
                    progress = 0
                )
                db.gameDao().insert(entity)
            }

            startTrackingDownload(context, downloadId, app.name, isXapk, fileName, onCompleted)
            downloadId
        } catch (e: Exception) {
            Log.e(TAG, "Failed initiating download for ${app.name}", e)
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
            -1L
        }
    }

    private fun startTrackingDownload(
        context: Context,
        downloadId: Long,
        name: String,
        isXapk: Boolean,
        fileName: String,
        onCompleted: ((File, Boolean) -> Unit)?
    ) {
        trackingJob?.cancel()
        trackingJob = scope.launch {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            var isFinished = false

            while (isActive && !isFinished) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                var cursor: Cursor? = null
                try {
                    cursor = downloadManager.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val bytesSoFarIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val totalBytesIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)

                        val status = if (statusIndex != -1) cursor.getInt(statusIndex) else -1
                        val bytesSoFar = if (bytesSoFarIndex != -1) cursor.getLong(bytesSoFarIndex) else 0L
                        val totalBytes = if (totalBytesIndex != -1) cursor.getLong(totalBytesIndex) else 0L
                        val localUri = if (localUriIndex != -1) cursor.getString(localUriIndex) else null

                        val progress = if (totalBytes > 0) ((bytesSoFar * 100) / totalBytes).toInt() else 0

                        when (status) {
                            DownloadManager.STATUS_RUNNING -> {
                                _currentActiveDownload.value = ActiveDownload(
                                    downloadId = downloadId,
                                    name = name,
                                    progress = progress,
                                    bytesDownloaded = bytesSoFar,
                                    totalBytes = totalBytes,
                                    isXapk = isXapk
                                )
                                DownloadNotificationHelper.showProgressNotification(
                                    context,
                                    downloadId,
                                    name,
                                    progress,
                                    bytesSoFar,
                                    totalBytes
                                )
                            }

                            DownloadManager.STATUS_SUCCESSFUL -> {
                                isFinished = true
                                _currentActiveDownload.value = null

                                val resolvedFile = resolveFile(localUri, fileName)
                                if (resolvedFile != null && resolvedFile.exists()) {
                                    handleDownloadSuccess(context, downloadId, name, resolvedFile, isXapk)
                                    withContext(Dispatchers.Main) {
                                        onCompleted?.invoke(resolvedFile, isXapk)
                                    }
                                }
                            }

                            DownloadManager.STATUS_FAILED -> {
                                isFinished = true
                                _currentActiveDownload.value = null
                                DownloadNotificationHelper.showFailedNotification(
                                    context,
                                    downloadId,
                                    name,
                                    "Download error occurred"
                                )
                            }
                        }
                    } else {
                        isFinished = true
                        _currentActiveDownload.value = null
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error polling download progress", e)
                } finally {
                    cursor?.close()
                }

                if (!isFinished) {
                    delay(800)
                }
            }
        }
    }

    private suspend fun handleDownloadSuccess(
        context: Context,
        downloadId: Long,
        name: String,
        file: File,
        isXapk: Boolean
    ) {
        val model = if (isXapk) {
            XapkParser.parse(context, file)
        } else {
            ApkParser.parse(context, file)
        }

        val packageName = model?.packageName ?: ""
        val versionName = model?.versionName ?: "1.0"
        val versionCode = model?.versionCode ?: 1L

        val db = AppDatabase.getDatabase(context)
        val entity = DownloadedGameEntity(
            downloadId = downloadId,
            name = model?.title ?: name,
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            filePath = file.absolutePath,
            fileSize = file.length(),
            isXapk = isXapk,
            status = "COMPLETED",
            progress = 100,
            timestamp = System.currentTimeMillis()
        )
        db.gameDao().insert(entity)

        DownloadNotificationHelper.showCompleteNotification(
            context,
            downloadId,
            model?.title ?: name,
            file
        )
    }

    fun cancelActiveDownload(context: Context, downloadId: Long) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        dm?.remove(downloadId)
        DownloadNotificationHelper.cancelNotification(context, downloadId)
        if (_currentActiveDownload.value?.downloadId == downloadId) {
            _currentActiveDownload.value = null
        }
        scope.launch {
            try {
                AppDatabase.getDatabase(context).gameDao().deleteByDownloadId(downloadId)
            } catch (_: Exception) {}
        }
    }

    private fun resolveFile(localUriString: String?, fallbackFileName: String): File? {
        if (!localUriString.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(localUriString)
                if (uri.scheme == "file") {
                    val path = uri.path
                    if (path != null) {
                        val file = File(path)
                        if (file.exists()) return file
                    }
                }
            } catch (_: Exception) {}
        }

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val directFile = File(downloadsDir, fallbackFileName)
        if (directFile.exists()) return directFile

        return null
    }
}
