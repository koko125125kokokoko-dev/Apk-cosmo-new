package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StoreApp(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "dev_name") val devName: String? = null,
    @Json(name = "version") val version: String? = null,
    @Json(name = "size") val size: String? = null,
    @Json(name = "main_category_id") val mainCategoryId: Int? = null,
    @Json(name = "sub_category_id") val subCategoryId: Int? = null,
    @Json(name = "icon_url") val iconUrl: String? = null,
    @Json(name = "screenshots") val screenshots: List<String>? = emptyList(),
    @Json(name = "android_download_url") val downloadUrl: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "download_count") val downloadCount: String? = "0",
    @Json(name = "real_download_count") val realDownloadCount: Int? = 0,
    @Json(name = "rating_avg") val ratingAvg: Double? = 0.0,
    @Json(name = "rating_count") val ratingCount: Int? = 0,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class Category(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "main_category_id") val mainCategoryId: Int
)

data class InstalledAppInfo(
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean
)
