package com.studiokei.walkaround.data.model

data class SectionSummary(
    val sectionId: Long,
    val startTimeMillis: Long,
    val steps: Int,
    val trackPointCount: Int
)
