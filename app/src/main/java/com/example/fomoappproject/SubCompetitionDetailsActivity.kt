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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions

class SubCompetitionDetailsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var leaderboardRecyclerView: RecyclerView
    private lateinit var feedRecyclerView: RecyclerView
    private lateinit var addActivityButton: Button

    private val leaderboardList = mutableListOf<Pair<String, Int>>()  // username -> count
    private val feedList = mutableListOf<String>()                    // simple string feed

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
                leaderboardList.addAll(
                    userCounts.entries
                        .sortedByDescending { it.value }
                        .map { it.toPair() }
                )

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

        val groupRef = db.collection("groups").document(groupId)
        val subRef = groupRef.collection("subCompetitions").document(subCompetitionId)

        // parser לגמישוּת: תומך Firestore Timestamp / Date / String("yyyy-MM-dd")
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        fun parseDateAny(v: Any?): java.util.Date? = when (v) {
            is com.google.firebase.Timestamp -> v.toDate()
            is java.util.Date -> v
            is String -> try { sdf.parse(v) } catch (_: Exception) { null }
            else -> null
        }

        // טען קבוצה ותת־תחרות, ואז בדיקות חלון לפי *יום UTC* בלבד
        groupRef.get().addOnSuccessListener { g ->
            subRef.get().addOnSuccessListener { s ->
                val groupStart = parseDateAny(g.get("startDate"))
                val groupEnd   = parseDateAny(g.get("endDate"))
                val subStart   = parseDateAny(s.get("startDate"))
                val subEnd     = parseDateAny(s.get("endDate"))

                if (groupStart == null || groupEnd == null || subStart == null || subEnd == null) {
                    Toast.makeText(this, "Dates unavailable for this challenge", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                // השוואת יום-בלבד ב-UTC (מונע בעיות אזור־זמן בין מכשירים)
                val todayDay = todayUtcDayInt()
                val gStartDay = toUtcDayInt(groupStart)
                val gEndDay   = toUtcDayInt(groupEnd)
                val sStartDay = toUtcDayInt(subStart)
                val sEndDay   = toUtcDayInt(subEnd)

                val inGroup = todayDay in gStartDay..gEndDay
                val inSub   = todayDay in sStartDay..sEndDay

                if (!inGroup) {
                    Toast.makeText(this, "Activities are allowed only within the group's dates.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                if (!inSub) {
                    Toast.makeText(this, "This challenge is not active today.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                // קטגוריה מתוך ה-theme של התת־תחרות
                val categoryFromTheme = s.getString("theme") ?: "Other"

                // נביא שם משתמש ונפתח דיאלוג תיאור בלבד
                db.collection("users").document(userId).get()
                    .addOnSuccessListener { userDoc ->
                        val username = userDoc.getString("username") ?: "Unknown"

                        val input = EditText(this).apply { hint = "Describe your activity" }

                        AlertDialog.Builder(this)
                            .setTitle("Add Activity")
                            .setView(input)
                            .setPositiveButton("Add") { _, _ ->
                                val description = input.text.toString().trim()
                                if (description.isBlank()) return@setPositiveButton

                                val activityData = hashMapOf(
                                    "userId" to userId,
                                    "username" to username,
                                    "description" to description,
                                    "category" to categoryFromTheme,
                                    "timestamp" to Timestamp.now(),
                                    "groupId" to groupId
                                )

                                subRef.collection("activities")
                                    .add(activityData)
                                    .addOnSuccessListener {
                                        // עדכון נקודות
                                        subRef.update("points.$userId", FieldValue.increment(1))
                                            .addOnFailureListener {
                                                subRef.set(mapOf("points" to mapOf(userId to 1)), SetOptions.merge())
                                            }

                                        Toast.makeText(this, "Activity Added", Toast.LENGTH_SHORT).show()
                                        loadLeaderboard()
                                        loadFeed()
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(this, "Failed to add activity", Toast.LENGTH_SHORT).show()
                                    }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
            }
        }
    }

    // ------- Helpers: day-only comparison in UTC -------
    private fun toUtcDayInt(date: java.util.Date): Int {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.time = date
        val year = cal.get(java.util.Calendar.YEAR)
        val dayOfYear = cal.get(java.util.Calendar.DAY_OF_YEAR)
        return year * 1000 + dayOfYear
    }

    private fun todayUtcDayInt(): Int {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val year = cal.get(java.util.Calendar.YEAR)
        val dayOfYear = cal.get(java.util.Calendar.DAY_OF_YEAR)
        return year * 1000 + dayOfYear
    }
}
