package com.example.fomoappproject

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MyGroupsActivity : AppCompatActivity() {

    private lateinit var createGroupLauncher: ActivityResultLauncher<Intent>
    private lateinit var recyclerView: RecyclerView
    private lateinit var groupAdapter: GroupAdapter
    private val groupList = mutableListOf<Group>()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_groups)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // כפתור חזרה
        findViewById<Button>(R.id.buttonBack).setOnClickListener {
            finish()
        }

        // איתחול ל־ActivityResult שיריץ fetchGroupsForUser כשחוזרים
        createGroupLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                fetchGroupsForUser()
            }
        }

        val createGroupButton = findViewById<Button>(R.id.buttonCreateGroup)
        createGroupButton.setOnClickListener {
            val intent = Intent(this, CreateGroupActivity::class.java)
            createGroupLauncher.launch(intent)
        }

        recyclerView = findViewById(R.id.recyclerViewGroups)
        groupAdapter = GroupAdapter(groupList) { group ->
            val intent = Intent(this, GroupDetailsActivity::class.java)
            intent.putExtra("groupId", group.id)
            intent.putExtra("groupName", group.name)
            startActivity(intent)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = groupAdapter

        fetchGroupsForUser()
    }

    private fun fetchGroupsForUser() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("groups")
            .whereArrayContains("members", currentUserId)
            .get()
            .addOnSuccessListener { result ->
                groupList.clear()
                for (document in result) {
                    val group = Group(
                        id = document.id,
                        name = document.getString("name") ?: "Unnamed Group",
                        members = document.get("members") as? List<String> ?: listOf()
                    )
                    groupList.add(group)
                }
                groupAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load groups", Toast.LENGTH_SHORT).show()
            }
    }
}
