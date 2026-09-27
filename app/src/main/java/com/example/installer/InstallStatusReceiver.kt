package com.example.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

class InstallStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_INSTALL_STATUS) return

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmIntent)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Log.d(TAG, "Package installed successfully: $packageName")
                Toast.makeText(context, "Installation succeeded: $packageName", Toast.LENGTH_SHORT).show()

                // Check Auto-Launch preference
                val prefs = context.getSharedPreferences("cosmo_prefs", Context.MODE_PRIVATE)
                val autoLaunch = prefs.getBoolean("auto_launch_after_install", true)

                if (autoLaunch && packageName.isNotEmpty()) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                            if (launchIntent != null) {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(launchIntent)
                                Toast.makeText(context, "Auto-launching app", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed auto-launching installed app", e)
                        }
                    }, 500)
                }
            }

            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_ABORTED,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Status code $status"
                Log.e(TAG, "Installation failed: $message ($packageName)")
                Toast.makeText(context, "Installation error: $message", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val TAG = "InstallStatusReceiver"
        const val ACTION_INSTALL_STATUS = "com.cosmogamestore.app.installer.ACTION_INSTALL_STATUS"
        const val EXTRA_PACKAGE_NAME = "com.cosmogamestore.app.installer.EXTRA_PACKAGE_NAME"
    }
}
