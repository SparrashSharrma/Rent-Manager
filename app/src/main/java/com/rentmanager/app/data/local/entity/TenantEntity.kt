package com.rentmanager.app.data.local.entity
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tenants",
    foreignKeys = [ForeignKey(entity = ShopEntity::class, parentColumns = ["id"], childColumns = ["shopId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["shopId"])]
)
data class TenantEntity(
    @PrimaryKey val id: String,
    val shopId: String,
    val name: String,
    val mobile: String,
    val alternateMobile: String?,
    val email: String?,
    val permanentAddress: String,
    val idProofType: String,
    val idProofNumber: String?,
    val agreementStartDate: String,
    val agreementEndDate: String,
    val dueDayOfMonth: Int,
    val monthlyRent: Double,
    val securityDepositPaid: Double,
    val isActive: Boolean = true,
    val vacatedAt: String? = null,
    val refundAmount: Double? = null,
    val pendingDuesAtVacate: Double? = null,
    val finalSettlementNotes: String? = null,
    val notes: String? = null
)
