package com.example.notification

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val downloadId = intent.getLongExtra(DownloadNotificationHelper.EXTRA_DOWNLOAD_ID, -1L)
        val gameName = intent.getStringExtra(DownloadNotificationHelper.EXTRA_GAME_NAME) ?: "Package"

        when (action) {
            DownloadNotificationHelper.ACTION_CANCEL_DOWNLOAD -> {
                Log.d("DownloadReceiver", "Cancelling download: $downloadId")
                if (downloadId != -1L) {
                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                    dm?.remove(downloadId)

                    DownloadNotificationHelper.cancelNotification(context, downloadId)

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            AppDatabase.getDatabase(context).gameDao().deleteByDownloadId(downloadId)
                        } catch (e: Exception) {
                            Log.e("DownloadReceiver", "Error cleaning DB for download $downloadId", e)
                        }
                    }

                    Toast.makeText(context, "Download cancelled: $gameName", Toast.LENGTH_SHORT).show()
                }
            }

            DownloadNotificationHelper.ACTION_INSTALL_APK -> {
                val path = intent.getStringExtra(DownloadNotificationHelper.EXTRA_FILE_PATH)
                if (path != null) {
                    val file = File(path)
                    if (file.exists()) {
                        val pending = DownloadNotificationHelper.createInstallPendingIntent(context, file, System.currentTimeMillis())
                        try {
                            pending.send()
                        } catch (e: Exception) {
                            Log.e("DownloadReceiver", "Error sending install intent", e)
                        }
                    }
                }
            }
        }
    }
}
