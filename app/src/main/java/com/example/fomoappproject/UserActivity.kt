package com.example.fomoappproject

enum class ActivityCategory { SPORT, HEALTH, STUDY }

data class UserActivity(
    val id: String = "",
    val userId: String = "",
    val username: String = "",      // קאש לשם לתצוגה מהירה
    val description: String = "",
    val category: ActivityCategory = ActivityCategory.SPORT,
    val timestamp: Long = System.currentTimeMillis(),
    val groupId: String = "",
    val groupName: String = "",
    val subCompetitionId: String = ""
)
