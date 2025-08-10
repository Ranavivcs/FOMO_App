package com.example.fomoappproject

import java.util.Date

data class SubCompetition(
    val id: String = "",
    val name: String = "",
    val theme: String = "",
    val isActive: Boolean = false,
    val startDate: Date? = null,
    val endDate: Date? = null,
    val points: Map<String, Int> = emptyMap(),
    val closed: Boolean = false
)
