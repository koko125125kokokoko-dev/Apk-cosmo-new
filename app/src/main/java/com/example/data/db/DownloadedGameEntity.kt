package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_packages")
data class DownloadedGameEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val downloadId: Long = -1L,
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val filePath: String,
    val fileSize: Long,
    val isXapk: Boolean,
    val iconUrl: String? = null,
    val status: String = "COMPLETED", // DOWNLOADING, COMPLETED, FAILED, INSTALLED
    val progress: Int = 100,
    val timestamp: Long = System.currentTimeMillis()
)
