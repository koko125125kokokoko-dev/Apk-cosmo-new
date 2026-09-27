package com.example.installer

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import java.io.File

object ApkParser {
    private const val TAG = "ApkParser"

    fun parse(context: Context, apkFile: File): PackageModel? {
        if (!apkFile.exists()) return null
        return try {
            val pm = context.packageManager
            val packageInfo: PackageInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            }

            if (packageInfo != null) {
                packageInfo.applicationInfo?.let { appInfo ->
                    appInfo.sourceDir = apkFile.absolutePath
                    appInfo.publicSourceDir = apkFile.absolutePath
                }

                val packageName = packageInfo.packageName ?: ""
                val versionName = packageInfo.versionName ?: "1.0"
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }

                val title = packageInfo.applicationInfo?.loadLabel(pm)?.toString()
                    ?: apkFile.nameWithoutExtension.replace("_", " ")

                val iconDrawable = packageInfo.applicationInfo?.loadIcon(pm)
                val iconBitmap = iconDrawable?.let { drawableToBitmap(it) }

                var isInstalled = false
                var installedVersionName: String? = null
                var installedVersionCode = 0L

                if (packageName.isNotEmpty()) {
                    try {
                        val installedInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getPackageInfo(packageName, 0)
                        }
                        isInstalled = true
                        installedVersionName = installedInfo.versionName
                        installedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            installedInfo.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            installedInfo.versionCode.toLong()
                        }
                    } catch (_: PackageManager.NameNotFoundException) {
                        isInstalled = false
                    }
                }

                PackageModel(
                    file = apkFile,
                    type = PackageType.APK,
                    title = title,
                    packageName = packageName,
                    versionName = versionName,
                    versionCode = versionCode,
                    sizeBytes = apkFile.length(),
                    splitCount = 1,
                    hasObb = false,
                    isInstalled = isInstalled,
                    installedVersionName = installedVersionName,
                    installedVersionCode = installedVersionCode,
                    iconBitmap = iconBitmap,
                    iconDrawable = iconDrawable
                )
            } else {
                // Fallback basic model if archive info fails
                PackageModel(
                    file = apkFile,
                    type = PackageType.APK,
                    title = apkFile.nameWithoutExtension.replace("_", " "),
                    packageName = "",
                    versionName = "1.0",
                    versionCode = 1L,
                    sizeBytes = apkFile.length()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing APK: ${apkFile.absolutePath}", e)
            null
        }
    }

    fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
