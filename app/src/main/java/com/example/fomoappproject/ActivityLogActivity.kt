package com.example.fomoappproject

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QueryDocumentSnapshot

class ActivityLogActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ActivityAdapter
    private val activityList = mutableListOf<UserActivityWithGroup>()
    private lateinit var emptyStateTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_activity)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView = findViewById(R.id.recyclerViewActivities)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ActivityAdapter(activityList)
        recyclerView.adapter = adapter

        emptyStateTextView = findViewById(R.id.textViewEmptyState)

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
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collectionGroup("activities")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                activityList.clear()

                if (documents.isEmpty) {
                    emptyStateTextView.visibility = View.VISIBLE
                    adapter.notifyDataSetChanged()
                    return@addOnSuccessListener
                } else {
                    emptyStateTextView.visibility = View.GONE
                }

                var pendingFetches = documents.size()

                for (document in documents) {
                    processActivityDocument(document) {
                        pendingFetches--
                        if (pendingFetches == 0) {
                            // Sort activities by timestamp descending before displaying
                            activityList.sortByDescending { it.timestamp }
                            adapter.notifyDataSetChanged()
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load activities", Toast.LENGTH_SHORT).show()
            }
    }

    private fun processActivityDocument(doc: QueryDocumentSnapshot, onComplete: () -> Unit) {
        val description = doc.getString("description") ?: ""
        val category = doc.getString("category") ?: "No Category"
        val timestamp = doc.getTimestamp("timestamp") ?: Timestamp.now()

        val groupId = extractGroupIdFromPath(doc.reference.path)
        if (groupId == null) {
            onComplete()
            return
        }

        FirebaseFirestore.getInstance()
            .collection("groups").document(groupId)
            .get()
            .addOnSuccessListener { groupDoc ->
                val groupName = groupDoc.getString("name") ?: "Unknown Group"
                val activity = UserActivityWithGroup(description, category, timestamp, groupId, groupName)
                activityList.add(activity)
            }
            .addOnCompleteListener { onComplete() }
    }

    private fun extractGroupIdFromPath(path: String): String? {
        val parts = path.split("/")
        val groupIndex = parts.indexOf("groups")
        return if (groupIndex != -1 && parts.size > groupIndex + 1) {
            parts[groupIndex + 1]
        } else null
    }
}
