package com.hoopoe.zyfuntv.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteDao {
    @Query("SELECT * FROM tbl_site ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SiteEntity>>

    @Query("SELECT COUNT(*) FROM tbl_site")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM tbl_site WHERE type = :type")
    suspend fun countByType(type: Int): Int

    @Query("SELECT * FROM tbl_site WHERE id = :id")
    suspend fun getById(id: String): SiteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SiteEntity)

    @Delete
    suspend fun delete(item: SiteEntity)

    @Query("DELETE FROM tbl_site")
    suspend fun clear()

    @Query("UPDATE tbl_site SET isActive = :active, updatedAt = :ts WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean, ts: Long = System.currentTimeMillis())
}

@Dao
interface IptvDao {
    @Query("SELECT * FROM tbl_iptv ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<IptvEntity>>

    @Query("SELECT COUNT(*) FROM tbl_iptv")
    suspend fun count(): Int

    @Query("SELECT * FROM tbl_iptv WHERE id = :id")
    suspend fun getById(id: String): IptvEntity?

    @Query("SELECT * FROM tbl_iptv ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getFirst(): IptvEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: IptvEntity)

    @Delete
    suspend fun delete(item: IptvEntity)

    @Query("DELETE FROM tbl_iptv")
    suspend fun clear()

    @Query("UPDATE tbl_iptv SET isActive = :active, updatedAt = :ts WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean, ts: Long = System.currentTimeMillis())
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM tbl_channel ORDER BY group_name ASC, name ASC")
    fun observeAll(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM tbl_channel WHERE group_name = :groupName")
    fun observeByGroup(groupName: String): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ChannelEntity)

    @Delete
    suspend fun delete(item: ChannelEntity)

    @Query("DELETE FROM tbl_channel")
    suspend fun clear()
}

@Dao
interface AnalyzeDao {
    @Query("SELECT * FROM tbl_analyze ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AnalyzeEntity>>

    @Query("SELECT * FROM tbl_analyze WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AnalyzeEntity?

    @Query("SELECT * FROM tbl_analyze WHERE isActive = 1 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getFirstActive(): AnalyzeEntity?

    @Query("SELECT * FROM tbl_analyze WHERE isActive = 1 ORDER BY updatedAt DESC")
    suspend fun getActiveAll(): List<AnalyzeEntity>

    @Query("SELECT COUNT(*) FROM tbl_analyze")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: AnalyzeEntity)

    @Delete
    suspend fun delete(item: AnalyzeEntity)

    @Query("DELETE FROM tbl_analyze")
    suspend fun clear()

    @Query("UPDATE tbl_analyze SET isActive = :active, updatedAt = :ts WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean, ts: Long = System.currentTimeMillis())
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM tbl_history ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM tbl_history WHERE videoId = :videoId AND relateId = :relateId LIMIT 1")
    suspend fun findByVideo(videoId: String, relateId: String?): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: HistoryEntity)

    @Delete
    suspend fun delete(item: HistoryEntity)

    @Query("DELETE FROM tbl_history")
    suspend fun clear()
}

@Dao
interface StarDao {
    @Query("SELECT * FROM tbl_star ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<StarEntity>>

    @Query("SELECT COUNT(*) FROM tbl_star WHERE videoId = :videoId AND relateId = :relateId")
    suspend fun countByVideo(videoId: String, relateId: String?): Int

    @Query("SELECT * FROM tbl_star WHERE videoId = :videoId AND relateId = :relateId LIMIT 1")
    suspend fun findByVideo(videoId: String, relateId: String?): StarEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: StarEntity)

    @Delete
    suspend fun delete(item: StarEntity)

    @Query("DELETE FROM tbl_star")
    suspend fun clear()
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM tbl_search_history ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM tbl_search_history WHERE keyword LIKE :kw ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun suggest(kw: String, limit: Int = 8): List<SearchHistoryEntity>

    @Query("SELECT * FROM tbl_search_history WHERE keyword = :keyword LIMIT 1")
    suspend fun getByKeyword(keyword: String): SearchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SearchHistoryEntity)

    @Query("DELETE FROM tbl_search_history WHERE keyword = :keyword")
    suspend fun delete(keyword: String)

    @Query("DELETE FROM tbl_search_history")
    suspend fun clear()
}
