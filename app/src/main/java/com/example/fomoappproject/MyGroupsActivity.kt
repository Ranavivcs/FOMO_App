package com.example.fomoappproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class MyGroupsActivity : AppCompatActivity() {

    private lateinit var createGroupLauncher: ActivityResultLauncher<Intent>
    private lateinit var groupDetailsLauncher: ActivityResultLauncher<Intent>

    private lateinit var recyclerViewActive: RecyclerView
    private lateinit var recyclerViewPast: RecyclerView
    private lateinit var activeAdapter: GroupAdapter
    private lateinit var pastAdapter: GroupAdapter

    private val activeGroupList = mutableListOf<Group>()
    private val pastGroupList = mutableListOf<Group>()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_groups)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<Button>(R.id.buttonBack).setOnClickListener { finish() }

        createGroupLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result -> if (result.resultCode == RESULT_OK) fetchGroupsForUser() }

        groupDetailsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result -> if (result.resultCode == RESULT_OK) fetchGroupsForUser() }

        findViewById<Button>(R.id.buttonCreateGroup).setOnClickListener {
            val intent = Intent(this, CreateGroupActivity::class.java)
            createGroupLauncher.launch(intent)
        }
        findViewById<Button>(R.id.buttonViewInvitations).setOnClickListener {
            startActivity(Intent(this, InvitationsActivity::class.java))
        }

        recyclerViewActive = findViewById(R.id.recyclerViewActiveGroups)
        activeAdapter = GroupAdapter(activeGroupList) { openGroup(it) }
        recyclerViewActive.layoutManager = LinearLayoutManager(this)
        recyclerViewActive.adapter = activeAdapter

        recyclerViewPast = findViewById(R.id.recyclerViewPastGroups)
        pastAdapter = GroupAdapter(pastGroupList) { openGroup(it) }
        recyclerViewPast.layoutManager = LinearLayoutManager(this)
        recyclerViewPast.adapter = pastAdapter

        fetchGroupsForUser()
    }

    override fun onResume() {
        super.onResume()
        fetchGroupsForUser()
    }

    private fun openGroup(group: Group) {
        val intent = Intent(this, GroupDetailsActivity::class.java)
        intent.putExtra("groupId", group.id)
        intent.putExtra("groupName", group.name)
        groupDetailsLauncher.launch(intent)
    }

    private fun todayMidnight(): Date {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    private fun parseDateOrNull(s: String?): Date? = try {
        if (s.isNullOrBlank()) null else sdf.parse(s)
    } catch (_: Exception) { null }

    private fun fetchGroupsForUser() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("groups")
            .whereArrayContains("members", currentUserId)
            .get()
            .addOnSuccessListener { result ->
                activeGroupList.clear()
                pastGroupList.clear()

                val today = todayMidnight()

                for (document in result) {
                    val endStr = document.getString("endDate")
                    val endDate = parseDateOrNull(endStr)
                    val group = Group(
                        id = document.id,
                        name = document.getString("name") ?: "Unnamed Group",
                        ownerId = document.getString("groupOwner") ?: "",
                        members = document.get("members") as? List<String> ?: listOf(),
                        startDate = document.getString("startDate") ?: "",
                        endDate = endStr ?: ""
                    )
                    val ended = endDate?.before(today) == true
                    if (ended) pastGroupList.add(group) else activeGroupList.add(group)
                }

                activeAdapter.notifyDataSetChanged()
                pastAdapter.notifyDataSetChanged()

                findViewById<TextView>(R.id.textViewPastGroupsTitle).visibility =
                    if (pastGroupList.isEmpty()) View.GONE else View.VISIBLE
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load groups", Toast.LENGTH_SHORT).show()
            }
    }
}
