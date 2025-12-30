package com.studiokei.walkaround.data.model

data class SectionSummary(
    val sectionId: Long,
    val startTimeMillis: Long,
    val steps: Int,
    val trackPointCount: Int,
    val startAddressLine: String? = null,
    val startAdminArea: String? = null,
    val destinationAddressLine: String? = null,
    val destinationAdminArea: String? = null
) {
    /**
     * 出発地の市表示
     */
    fun startCityDisplay(): String? = getCityDisplay(startAddressLine, startAdminArea)

    /**
     * 目的地の市表示
     */
    fun destinationCityDisplay(): String? = getCityDisplay(destinationAddressLine, destinationAdminArea)

    private fun getCityDisplay(address: String?, admin: String?): String? {
        if (address == null) return null
        val area = admin ?: return address
        val index = address.indexOf(area)
        return if (index != -1) {
            address.substring(index + area.length).trim().removePrefix("、").removePrefix(",").trim()
        } else {
            address
        }
    }
}
