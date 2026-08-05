package com.rentmanager.app.data.local.dao
import androidx.room.*
import com.rentmanager.app.data.local.entity.PropertyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PropertyDao {
    @Query("SELECT * FROM properties ORDER BY createdAt DESC")
    fun getAllProperties(): Flow<List<PropertyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProperty(property: PropertyEntity)

    @Delete
    suspend fun deleteProperty(property: PropertyEntity)
}
