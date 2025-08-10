package com.example.fomoappproject

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class TrophiesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TrophyAdapter
    private lateinit var emptyStateTextView: TextView
    private val trophyList = mutableListOf<Trophy>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trophies)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recyclerView = findViewById(R.id.recyclerViewTrophies)
        emptyStateTextView = findViewById(R.id.textViewEmptyTrophies)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = TrophyAdapter(trophyList)
        recyclerView.adapter = adapter

        fetchTrophiesFromFirestore()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun fetchTrophiesFromFirestore() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("trophies")
            .whereEqualTo("winnerId", userId)
            .get()
            .addOnSuccessListener { documents ->
                trophyList.clear()

                for (doc in documents) {
                    val type = (doc.getString("type") ?: "sub").lowercase()
                    val wins = (doc.getLong("wins") ?: 0L).toInt()
                    val activities = (doc.getLong("activityCount") ?: 0L).toInt()

                    val trophy = Trophy(
                        type = type,
                        groupId = doc.getString("groupId") ?: "",
                        groupName = doc.getString("groupName") ?: "Unknown Group",
                        subCompetitionId = doc.getString("subCompetitionId"),
                        subName = doc.getString("subName"),
                        winnerId = doc.getString("winnerId") ?: "",
                        winnerName = doc.getString("winnerName") ?: "",
                        endDate = doc.getString("endDate") ?: "Unknown Date",
                        // לשדה המאוחד באובייקט נכניס wins אם זה גביע קבוצה, אחרת activities
                        activityCount = if (type == "group") wins else activities
                    )
                    trophyList.add(trophy)
                }

                // מיון לפי תאריך סיום יורד (yyyy-MM-dd עובד לקסיקוגרפית)
                trophyList.sortByDescending { it.endDate }

                adapter.notifyDataSetChanged()
                emptyStateTextView.visibility = if (trophyList.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load trophies", Toast.LENGTH_SHORT).show()
            }
    }
}
