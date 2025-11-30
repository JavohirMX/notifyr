package com.javohirmx.notifyr.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomTagDao {
    
    @Query("SELECT * FROM custom_tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<CustomTagEntity>>
    
    @Query("SELECT * FROM custom_tags ORDER BY name ASC")
    suspend fun getAllTagsSync(): List<CustomTagEntity>
    
    @Query("SELECT * FROM custom_tags WHERE id = :id")
    suspend fun getTagById(id: Long): CustomTagEntity?
    
    @Query("SELECT * FROM custom_tags WHERE id IN (:ids)")
    suspend fun getTagsByIds(ids: List<Long>): List<CustomTagEntity>
    
    @Query("SELECT * FROM custom_tags WHERE name = :name")
    suspend fun getTagByName(name: String): CustomTagEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: CustomTagEntity): Long
    
    @Update
    suspend fun updateTag(tag: CustomTagEntity)
    
    @Delete
    suspend fun deleteTag(tag: CustomTagEntity)
    
    @Query("DELETE FROM custom_tags WHERE id = :id")
    suspend fun deleteTagById(id: Long)
    
    @Query("DELETE FROM custom_tags")
    suspend fun deleteAllTags()
}

