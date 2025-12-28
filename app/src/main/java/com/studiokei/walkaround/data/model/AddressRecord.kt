package com.studiokei.walkaround.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "address_records")
data class AddressRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val time: Long,
    val sectionId: Long?,
    val trackId: Long?,
    val lat: Double?,
    val lng: Double?,
    val name: String?,
    val addressLine: String?
)
