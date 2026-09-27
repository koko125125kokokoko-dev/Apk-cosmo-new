package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM downloaded_packages ORDER BY timestamp DESC")
    fun getAllPackages(): Flow<List<DownloadedGameEntity>>

    @Query("SELECT * FROM downloaded_packages WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackageName(packageName: String): DownloadedGameEntity?

    @Query("SELECT * FROM downloaded_packages WHERE downloadId = :downloadId LIMIT 1")
    suspend fun getByDownloadId(downloadId: Long): DownloadedGameEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadedGameEntity): Long

    @Update
    suspend fun update(entity: DownloadedGameEntity)

    @Query("DELETE FROM downloaded_packages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloaded_packages WHERE downloadId = :downloadId")
    suspend fun deleteByDownloadId(downloadId: Long)

    @Query("DELETE FROM downloaded_packages")
    suspend fun deleteAll()
}
