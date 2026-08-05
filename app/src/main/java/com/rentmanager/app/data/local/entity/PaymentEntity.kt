package com.rentmanager.app.data.local.entity
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payments",
    foreignKeys = [ForeignKey(entity = TenantEntity::class, parentColumns = ["id"], childColumns = ["tenantId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["tenantId"]), Index(value = ["shopId"]), Index(value = ["propertyId"])]
)
data class PaymentEntity(
    @PrimaryKey val id: String,
    val receiptNumber: String,
    val tenantId: String,
    val shopId: String,
    val propertyId: String,
    val amount: Double,
    val forMonthYear: String,
    val paymentDate: String,
    val paymentMode: String,
    val referenceNo: String?,
    val notes: String?,
    val isPartial: Boolean = false
)
