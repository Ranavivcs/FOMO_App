package com.example.fomoappproject

data class Group(
    val id: String = "",
    val name: String = "",
    val ownerId: String = "",
    val members: List<String> = emptyList(),
    val startDate: String = "",  // yyyy-MM-dd
    val endDate: String = ""     // yyyy-MM-dd
)
