package com.example.fomoappproject

import com.google.firebase.Timestamp
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ActivityLogActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ActivityAdapter
    private val activityList = mutableListOf<UserActivity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_activity)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView = findViewById(R.id.recyclerViewActivities)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ActivityAdapter(activityList)
        recyclerView.adapter = adapter

        findViewById<Button>(R.id.buttonBackToMain).setOnClickListener {
            finish()
        }

        fetchActivitiesFromFirestore()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun fetchActivitiesFromFirestore() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            Toast.makeText(this, "User ID is null", Toast.LENGTH_SHORT).show()
            println("❌ User ID is null")
            return
        }

        println("📤 Fetching activities for userId = $userId")

        FirebaseFirestore.getInstance()
            .collection("activities")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                println("✅ Fetched ${documents.size()} documents")
                Toast.makeText(this, "Found ${documents.size()} activities", Toast.LENGTH_SHORT).show()

                activityList.clear()

                for (document in documents) {
                    println("📄 Document: ${document.data}")

                    try {
                        val raw = document.data

                        val activity = UserActivity(
                            description = raw["description"] as? String ?: "",
                            category = raw["category"] as? String ?: "",
                            timestamp = raw["timestamp"] as? Timestamp ?: Timestamp.now(),
                            userId = raw["userId"] as? String ?: ""
                        )

                        println("✅ Built activity manually: $activity")
                        activityList.add(activity)
                    } catch (e: Exception) {
                        println("❌ Still failed manually: ${e.message}")
                        Toast.makeText(this, "Manual parsing failed", Toast.LENGTH_SHORT).show()
                    }
                }

                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                println("❌ Firestore error: ${e.message}")
                Toast.makeText(this, "Firestore failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

}
