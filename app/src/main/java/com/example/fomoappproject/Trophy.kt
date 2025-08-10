package com.example.fomoappproject

data class Trophy(
    val type: String = "sub",          // "sub" או "group" (לעתיד)
    val groupId: String = "",
    val groupName: String = "",
    val subCompetitionId: String? = null,
    val subName: String? = null,       // שם התת־תחרות (אם יש)
    val winnerId: String = "",
    val winnerName: String = "",       // שם המנצח
    val endDate: String = "",          // yyyy-MM-dd (כמו שאנחנו שומרים)
    val activityCount: Int = 0         // ניקוד/פעילויות מנצחות
)
