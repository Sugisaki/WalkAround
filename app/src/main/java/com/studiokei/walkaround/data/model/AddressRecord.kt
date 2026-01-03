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
    val addressLine: String?,
    val adminArea: String? = null,
    val countryName: String? = null,
    val locality: String? = null,
    val subLocality: String? = null,
    val thoroughfare: String? = null,
    val subThoroughfare: String? = null,
    val postalCode: String? = null
) {
    /**
     * adminで表示される「県」とそれより大きな枠を表示しない
     */
    fun cityDisplay(): String? {
        if (addressLine == null) return null
        val admin = adminArea ?: return addressLine
        val index = addressLine.indexOf(admin)
        return if (index != -1) {
            addressLine.substring(index + admin.length).trim().removePrefix("、").removePrefix(",").trim()
        } else {
            addressLine
        }
    }

    /**
     * adminで表示される「県」より大きな枠を表示しない
     */
    fun prefectureDisplay(): String? {
        if (addressLine == null) return null
        val admin = adminArea ?: return addressLine
        val index = addressLine.indexOf(admin)
        return if (index != -1) {
            addressLine.substring(index).trim()
        } else {
            addressLine
        }
    }
}
