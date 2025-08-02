package com.example.fomoappproject

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class GroupDetailsActivity : AppCompatActivity() {

    private lateinit var buttonStartChallenge: Button
    private lateinit var layoutSubCompetitions: LinearLayout
    private lateinit var db: FirebaseFirestore
    private lateinit var layoutMembersList: LinearLayout
    private lateinit var layoutLeaderboard: LinearLayout
    private lateinit var textViewTitle: TextView
    private var groupId: String? = null
    private var groupName: String? = null
    private val userIdToUsername = mutableMapOf<String, String>()

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_details)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val buttonBack = findViewById<Button>(R.id.buttonBack)
        buttonBack.setOnClickListener {
            finish() // פשוט סוגר את המסך הנוכחי וחוזר אחורה
        }

        val buttonAddSubCompetition = findViewById<Button>(R.id.buttonAddSubCompetition)
        buttonAddSubCompetition.setOnClickListener {
            val subCompetitionData = hashMapOf(
                "title" to "New SubCompetition",
                "category" to "Sport",
                "createdAt" to System.currentTimeMillis()
            )

            db.collection("groups").document(groupId!!)
                .collection("subCompetitions")
                .add(subCompetitionData)
                .addOnSuccessListener {
                    Toast.makeText(this, "SubCompetition created!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to create SubCompetition", Toast.LENGTH_SHORT).show()
                }
        }



        buttonStartChallenge = findViewById(R.id.buttonStartChallenge)
        layoutSubCompetitions = findViewById(R.id.layoutSubCompetitions)

        buttonStartChallenge.setOnClickListener {
            showCreateSubCompetitionDialog()
        }

        loadSubCompetitions()


        db = FirebaseFirestore.getInstance()
        layoutMembersList = findViewById(R.id.layoutMembersList)
        layoutLeaderboard = findViewById(R.id.layoutLeaderboard)
        textViewTitle = findViewById(R.id.textViewGroupTitle)

        groupId = intent.getStringExtra("groupId")
        groupName = intent.getStringExtra("groupName")

        if (groupId == null) {
            Toast.makeText(this, "Error: missing group ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        textViewTitle.text = groupName ?: "Group Details"

        loadGroupMembersAndLeaderboard()
    }

    private fun loadGroupMembersAndLeaderboard() {
        db.collection("groups").document(groupId!!)
            .get()
            .addOnSuccessListener { groupDoc ->
                val members = groupDoc.get("members") as? List<String> ?: emptyList()

                if (members.isEmpty()) {
                    Toast.makeText(this, "No members in this group", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                layoutMembersList.removeAllViews()
                userIdToUsername.clear()

                for (uid in members) {
                    db.collection("users").document(uid)
                        .get()
                        .addOnSuccessListener { userDoc ->
                            val username = userDoc.getString("username") ?: "Unknown"
                            userIdToUsername[uid] = username

                            val textView = TextView(this)
                            textView.text = username
                            textView.textSize = 16f
                            layoutMembersList.addView(textView)

                            if (userIdToUsername.size == members.size) {
                                loadActivitiesForLeaderboard(members)
                            }
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load group", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadActivitiesForLeaderboard(members: List<String>) {
        db.collection("activities")
            .get()
            .addOnSuccessListener { result ->
                val activityCount = mutableMapOf<String, Int>()

                for (doc in result) {
                    val uid = doc.getString("userId") ?: continue
                    if (uid in members) {
                        activityCount[uid] = activityCount.getOrDefault(uid, 0) + 1
                    }
                }

                val sorted = activityCount.entries.sortedByDescending { it.value }

                layoutLeaderboard.removeAllViews()
                for ((uid, count) in sorted) {
                    val username = userIdToUsername[uid] ?: "Unknown"
                    val tv = TextView(this)
                    tv.text = "$username - $count activities"
                    tv.textSize = 16f
                    layoutLeaderboard.addView(tv)
                }

                if (sorted.isEmpty()) {
                    val tv = TextView(this)
                    tv.text = "No activities yet"
                    layoutLeaderboard.addView(tv)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load leaderboard", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadSubCompetitions() {
        db.collection("groups").document(groupId!!)
            .collection("subCompetitions")
            .get()
            .addOnSuccessListener { result ->
                layoutSubCompetitions.removeAllViews()
                for (doc in result) {
                    val name = doc.getString("name") ?: "Unnamed"
                    val theme = doc.getString("theme") ?: "Unknown"
                    val isActive = doc.getBoolean("isActive") ?: false

                    val textView = TextView(this)
                    textView.text = "$name ($theme) ${if (isActive) "[Active]" else ""}"
                    textView.textSize = 16f
                    layoutSubCompetitions.addView(textView)
                }

                if (result.isEmpty) {
                    val tv = TextView(this)
                    tv.text = "No challenges yet"
                    layoutSubCompetitions.addView(tv)
                }
            }
    }

    private fun showCreateSubCompetitionDialog() {
        val editText = EditText(this)
        editText.hint = "Challenge Name"

        val themes = arrayOf("Sport", "Health", "Study")
        val themeSpinner = Spinner(this)
        themeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, themes)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.addView(editText)
        layout.addView(themeSpinner)

        AlertDialog.Builder(this)
            .setTitle("New Challenge")
            .setView(layout)
            .setPositiveButton("Create") { _, _ ->
                val challengeName = editText.text.toString().trim()
                val theme = themeSpinner.selectedItem.toString()

                if (challengeName.isNotBlank()) {
                    val subComp = hashMapOf(
                        "name" to challengeName,
                        "theme" to theme,
                        "isActive" to true
                    )
                    db.collection("groups").document(groupId!!)
                        .collection("subCompetitions")
                        .add(subComp)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Challenge Created!", Toast.LENGTH_SHORT).show()
                            loadSubCompetitions()
                        }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

}
