package com.rentmanager.app.data.local.entity
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shops",
    foreignKeys = [ForeignKey(entity = PropertyEntity::class, parentColumns = ["id"], childColumns = ["propertyId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["propertyId"])]
)
data class ShopEntity(
    @PrimaryKey val id: String,
    val propertyId: String,
    val shopNumber: String,
    val floor: String,
    val areaSqFt: Double,
    val monthlyRent: Double,
    val securityDeposit: Double,
    val status: String,
    val imageUri: String? = null,
    val notes: String? = null
)
