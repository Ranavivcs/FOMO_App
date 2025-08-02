package com.example.fomoappproject


import com.google.firebase.Timestamp


data class UserActivity(
    val description: String = "",
    val category: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val userId: String = ""
)

