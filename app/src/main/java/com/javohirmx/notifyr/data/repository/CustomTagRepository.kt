package com.javohirmx.notifyr.data.repository

import com.javohirmx.notifyr.data.database.CustomTagDao
import com.javohirmx.notifyr.data.database.CustomTagEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomTagRepository @Inject constructor(
    private val customTagDao: CustomTagDao
) {
    
    fun getAllTags(): Flow<List<CustomTagEntity>> {
        return customTagDao.getAllTags()
    }
    
    suspend fun getAllTagsSync(): List<CustomTagEntity> {
        return customTagDao.getAllTagsSync()
    }
    
    suspend fun getTagById(id: Long): CustomTagEntity? {
        return customTagDao.getTagById(id)
    }
    
    suspend fun getTagsByIds(ids: List<String>): List<CustomTagEntity> {
        val longIds = ids.mapNotNull { it.toLongOrNull() }
        return if (longIds.isNotEmpty()) {
            customTagDao.getTagsByIds(longIds)
        } else {
            emptyList()
        }
    }
    
    suspend fun getTagByName(name: String): CustomTagEntity? {
        return customTagDao.getTagByName(name)
    }
    
    suspend fun insertTag(name: String, color: String? = null): Long {
        // Check if tag with same name already exists
        val existing = customTagDao.getTagByName(name)
        return if (existing != null) {
            existing.id
        } else {
            customTagDao.insertTag(
                CustomTagEntity(
                    name = name,
                    color = color,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }
    
    suspend fun updateTag(tag: CustomTagEntity) {
        customTagDao.updateTag(tag)
    }
    
    suspend fun deleteTag(tag: CustomTagEntity) {
        customTagDao.deleteTag(tag)
    }
    
    suspend fun deleteTagById(id: Long) {
        customTagDao.deleteTagById(id)
    }
    
    suspend fun deleteAllTags() {
        customTagDao.deleteAllTags()
    }
}

