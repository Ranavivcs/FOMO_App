package com.example.fomoappproject

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SubCompetitionDetailsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var leaderboardRecyclerView: RecyclerView
    private lateinit var feedRecyclerView: RecyclerView
    private lateinit var addActivityButton: Button

    private val leaderboardList = mutableListOf<Pair<String, Int>>()  // username to count
    private val feedList = mutableListOf<String>()  // simple string feed for now

    private lateinit var leaderboardAdapter: SimpleStringAdapter
    private lateinit var feedAdapter: SimpleStringAdapter

    private var groupId: String = ""
    private var subCompetitionId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sub_competition_details)

        groupId = intent.getStringExtra("groupId") ?: ""
        subCompetitionId = intent.getStringExtra("subCompetitionId") ?: ""

        if (groupId.isEmpty() || subCompetitionId.isEmpty()) {
            Toast.makeText(this, "Missing Data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        leaderboardRecyclerView = findViewById(R.id.recyclerViewLeaderboard)
        feedRecyclerView = findViewById(R.id.recyclerViewFeed)
        addActivityButton = findViewById(R.id.buttonAddActivity)

        leaderboardAdapter = SimpleStringAdapter(leaderboardList.map { "${it.first}: ${it.second}" })
        feedAdapter = SimpleStringAdapter(feedList)

        leaderboardRecyclerView.layoutManager = LinearLayoutManager(this)
        leaderboardRecyclerView.adapter = leaderboardAdapter

        feedRecyclerView.layoutManager = LinearLayoutManager(this)
        feedRecyclerView.adapter = feedAdapter

        addActivityButton.setOnClickListener { addActivity() }

        loadLeaderboard()
        loadFeed()
    }

    private fun loadLeaderboard() {
        db.collection("groups").document(groupId)
            .collection("subCompetitions").document(subCompetitionId)
            .collection("activities")
            .get()
            .addOnSuccessListener { result ->
                val userCounts = mutableMapOf<String, Int>()

                if (result.isEmpty) {
                    leaderboardList.clear()
                    leaderboardAdapter.updateData(listOf("No activities yet"))
                    return@addOnSuccessListener
                }

                for (doc in result) {
                    val username = doc.getString("username") ?: "Unknown"
                    userCounts[username] = userCounts.getOrDefault(username, 0) + 1
                }

                leaderboardList.clear()
                leaderboardList.addAll(userCounts.entries
                    .sortedByDescending { it.value }
                    .map { it.toPair() })

                leaderboardAdapter.updateData(leaderboardList.map { "${it.first}: ${it.second}" })
            }
    }

    private fun loadFeed() {
        db.collection("groups").document(groupId)
            .collection("subCompetitions").document(subCompetitionId)
            .collection("activities")
            .orderBy("timestamp")
            .get()
            .addOnSuccessListener { result ->
                feedList.clear()
                for (doc in result) {
                    val desc = doc.getString("description") ?: "Activity"
                    val username = doc.getString("username") ?: "Unknown"
                    val timestamp = doc.getTimestamp("timestamp")?.toDate()?.toString() ?: ""

                    feedList.add("$username did \"$desc\" on $timestamp")
                }

                if (feedList.isEmpty()) {
                    feedList.add("No activities yet")
                }

                feedAdapter.updateData(feedList)
            }
    }

    private fun addActivity() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users").document(userId).get()
            .addOnSuccessListener { userDoc ->
                val username = userDoc.getString("username") ?: "Unknown"

                val input = EditText(this)
                input.hint = "Describe your activity"

                AlertDialog.Builder(this)
                    .setTitle("Add Activity")
                    .setView(input)
                    .setPositiveButton("Add") { _, _ ->
                        val description = input.text.toString().trim()
                        if (description.isBlank()) return@setPositiveButton

                        val activityData = hashMapOf(
                            "userId" to userId,
                            "username" to username,  // <--- שמירה של שם המשתמש
                            "description" to description,
                            "timestamp" to Timestamp.now()
                        )

                        db.collection("groups").document(groupId)
                            .collection("subCompetitions").document(subCompetitionId)
                            .collection("activities")
                            .add(activityData)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Activity Added", Toast.LENGTH_SHORT).show()
                                loadLeaderboard()
                                loadFeed()
                            }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
    }
}
