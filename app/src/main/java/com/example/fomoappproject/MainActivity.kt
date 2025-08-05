package com.example.fomoappproject

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var welcomeTextView: TextView
    private lateinit var activitiesCountTextView: TextView
    private lateinit var groupsCountTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val logoutButton = findViewById<Button>(R.id.buttonLogout)
        logoutButton.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        welcomeTextView = findViewById(R.id.textViewWelcome)
        activitiesCountTextView = findViewById(R.id.textViewActivitiesCount)
        groupsCountTextView = findViewById(R.id.textViewGroupsCount)

        findViewById<CardView>(R.id.cardMyActivities).setOnClickListener {
            startActivity(Intent(this, ActivityLogActivity::class.java))
        }

        findViewById<CardView>(R.id.cardMyGroups).setOnClickListener {
            startActivity(Intent(this, MyGroupsActivity::class.java))
        }

        findViewById<CardView>(R.id.cardMyTrophies).setOnClickListener {
            startActivity(Intent(this, TrophiesActivity::class.java))
        }

        loadUsername()
        saveFcmToken()
        loadCounts()
    }

    private fun saveFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                return@addOnCompleteListener
            }

            val token = task.result
            val userId = auth.currentUser?.uid

            if (userId != null && token != null) {
                db.collection("users").document(userId)
                    .update("fcmToken", token)
            }
        }
    }

    private fun loadUsername() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                val username = document.getString("username") ?: "User"
                welcomeTextView.text = "Hey $username 👋"
            }
    }

    private fun loadCounts() {
        val uid = auth.currentUser?.uid ?: return

        // Count Activities using collectionGroup query
        db.collectionGroup("activities")
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { documents ->
                val count = documents.size()
                activitiesCountTextView.text = "Activities: $count"
            }

        // Count Groups
        db.collection("groups")
            .whereArrayContains("members", uid)
            .get()
            .addOnSuccessListener { documents ->
                val count = documents.size()
                groupsCountTextView.text = "Groups: $count"
            }
    }

    override fun onResume() {
        super.onResume()
        loadCounts()  // Refresh counts every time we come back to MainActivity
    }
}
