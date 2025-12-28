package com.studiokei.walkaround.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.studiokei.walkaround.data.model.AddressRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface AddressDao {
    @Insert
    suspend fun insert(addressRecord: AddressRecord): Long

    @Query("SELECT * FROM address_records ORDER BY time DESC")
    fun getAllAddressRecords(): Flow<List<AddressRecord>>

    @Query("DELETE FROM address_records")
    suspend fun deleteAll()
}
