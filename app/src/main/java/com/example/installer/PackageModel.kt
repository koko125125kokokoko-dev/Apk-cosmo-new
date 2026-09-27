package com.example.installer

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import java.io.File

enum class PackageType {
    APK,
    XAPK
}

data class PackageModel(
    val file: File,
    val type: PackageType,
    val title: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val sizeBytes: Long,
    val splitCount: Int = 1,
    val hasObb: Boolean = false,
    val isInstalled: Boolean = false,
    val installedVersionName: String? = null,
    val installedVersionCode: Long = 0L,
    val iconBitmap: Bitmap? = null,
    val iconDrawable: Drawable? = null
)
