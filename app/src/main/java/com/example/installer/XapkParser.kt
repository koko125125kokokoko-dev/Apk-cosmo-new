package com.example.installer

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object XapkParser {
    private const val TAG = "XapkParser"

    fun parse(context: Context, xapkFile: File): PackageModel? {
        if (!xapkFile.exists()) return null

        var zipFile: ZipFile? = null
        return try {
            zipFile = ZipFile(xapkFile)
            var manifestJson: String? = null
            var extractedBitmap: Bitmap? = null
            var apkCount = 0
            var hasObb = false
            var baseApkEntryName: String? = null

            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                if (name.equals("manifest.json", ignoreCase = true)) {
                    manifestJson = readZipEntryString(zipFile, entry)
                } else if (name.equals("icon.png", ignoreCase = true)) {
                    extractedBitmap = readZipEntryBitmap(zipFile, entry)
                } else if (name.endsWith(".apk", ignoreCase = true)) {
                    apkCount++
                    if (baseApkEntryName == null || name.contains("base", ignoreCase = true) || !name.contains("config.")) {
                        baseApkEntryName = name
                    }
                } else if (name.endsWith(".obb", ignoreCase = true) || name.contains("/obb/", ignoreCase = true)) {
                    hasObb = true
                }
            }

            var packageName = ""
            var title = ""
            var versionName = ""
            var versionCode = 1L

            // 1. Try reading manifest.json
            if (manifestJson != null) {
                try {
                    val json = JSONObject(manifestJson)
                    packageName = json.optString("package_name", "")
                    title = json.optString("name", "")
                    versionName = json.optString("version_name", "")
                    versionCode = json.optLong("version_code", 1L)
                    if (json.has("expansions") && json.getJSONArray("expansions").length() > 0) {
                        hasObb = true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing manifest.json in XAPK", e)
                }
            }

            // 2. Inspect base APK inside XAPK if needed
            if (packageName.isEmpty() && baseApkEntryName != null) {
                val tempApk = extractEntryToTemp(context, zipFile, baseApkEntryName)
                if (tempApk != null) {
                    try {
                        val pm = context.packageManager
                        val info: PackageInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getPackageArchiveInfo(tempApk.absolutePath, PackageManager.PackageInfoFlags.of(0))
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getPackageArchiveInfo(tempApk.absolutePath, 0)
                        }
                        if (info != null) {
                            packageName = info.packageName ?: ""
                            versionName = info.versionName ?: ""
                            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                info.longVersionCode
                            } else {
                                @Suppress("DEPRECATION")
                                info.versionCode.toLong()
                            }
                            info.applicationInfo?.sourceDir = tempApk.absolutePath
                            info.applicationInfo?.publicSourceDir = tempApk.absolutePath
                            if (title.isEmpty()) {
                                title = info.applicationInfo?.loadLabel(pm)?.toString() ?: ""
                            }
                            if (extractedBitmap == null) {
                                val d = info.applicationInfo?.loadIcon(pm)
                                if (d != null) {
                                    extractedBitmap = ApkParser.drawableToBitmap(d)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error inspecting internal APK", e)
                    } finally {
                        tempApk.delete()
                    }
                }
            }

            if (title.isEmpty()) {
                title = xapkFile.nameWithoutExtension.replace("_", " ")
            }

            var isInstalled = false
            var installedVersionName: String? = null
            var installedVersionCode = 0L

            if (packageName.isNotEmpty()) {
                try {
                    val pm = context.packageManager
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

            val iconDrawable: Drawable? = extractedBitmap?.let { BitmapDrawable(context.resources, it) }

            PackageModel(
                file = xapkFile,
                type = PackageType.XAPK,
                title = title,
                packageName = packageName,
                versionName = if (versionName.isNotEmpty()) versionName else "1.0",
                versionCode = versionCode,
                sizeBytes = xapkFile.length(),
                splitCount = apkCount,
                hasObb = hasObb,
                isInstalled = isInstalled,
                installedVersionName = installedVersionName,
                installedVersionCode = installedVersionCode,
                iconBitmap = extractedBitmap,
                iconDrawable = iconDrawable
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error opening XAPK file", e)
            null
        } finally {
            try {
                zipFile?.close()
            } catch (_: Exception) {}
        }
    }

    private fun readZipEntryString(zipFile: ZipFile, entry: ZipEntry): String? {
        return try {
            zipFile.getInputStream(entry).use { input ->
                ByteArrayOutputStream().use { baos ->
                    val buffer = byteBufferPool()
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        baos.write(buffer, 0, read)
                    }
                    baos.toString(StandardCharsets.UTF_8.name())
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun readZipEntryBitmap(zipFile: ZipFile, entry: ZipEntry): Bitmap? {
        return try {
            zipFile.getInputStream(entry).use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractEntryToTemp(context: Context, zipFile: ZipFile, entryName: String): File? {
        val entry = zipFile.getEntry(entryName) ?: return null
        val temp = File(context.cacheDir, "temp_inspect_${System.currentTimeMillis()}.apk")
        return try {
            zipFile.getInputStream(entry).use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = byteBufferPool()
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                }
            }
            temp
        } catch (e: Exception) {
            if (temp.exists()) temp.delete()
            null
        }
    }

    private fun byteBufferPool(): ByteArray = ByteArray(8192)
}
