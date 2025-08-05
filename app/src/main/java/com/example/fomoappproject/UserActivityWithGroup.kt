package com.example.fomoappproject
import com.google.firebase.Timestamp


data class UserActivityWithGroup(
    val description: String = "",
    val category: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val groupId: String = "",
    val groupName: String = ""
)

