package com.rentmanager.app.data.local.entity
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "properties")
data class PropertyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String,
    val city: String,
    val floors: Int,
    val type: String,
    val imageUri: String?,
    val notes: String?,
    val createdAt: Long = System.currentTimeMillis()
)
