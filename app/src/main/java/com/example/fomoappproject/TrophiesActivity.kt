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
                    val groupName = doc.getString("groupName") ?: "Unknown Group"
                    val endDate = doc.getString("endDate") ?: "Unknown Date"
                    val activityCount = doc.getLong("activityCount")?.toInt() ?: 0
                    val trophy = Trophy(groupName, endDate, activityCount)
                    trophyList.add(trophy)
                }
                adapter.notifyDataSetChanged()

                if (trophyList.isEmpty()) {
                    emptyStateTextView.visibility = View.VISIBLE
                } else {
                    emptyStateTextView.visibility = View.GONE
                }

            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load trophies", Toast.LENGTH_SHORT).show()
            }
    }
}