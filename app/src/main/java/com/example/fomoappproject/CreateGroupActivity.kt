package com.example.fomoappproject

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CreateGroupActivity : AppCompatActivity() {

    private lateinit var editTextGroupName: EditText
    private lateinit var buttonCreateGroup: Button
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_group)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        editTextGroupName = findViewById(R.id.editTextGroupName)
        buttonCreateGroup = findViewById(R.id.buttonCreateGroup)

        buttonCreateGroup.setOnClickListener {
            val groupName = editTextGroupName.text.toString().trim()
            val userId = auth.currentUser?.uid

            if (groupName.isBlank() || userId == null) {
                Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val groupData = hashMapOf(
                "name" to groupName,
                "members" to listOf(userId)
            )

            db.collection("groups")
                .add(groupData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Group created!", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to create group", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
