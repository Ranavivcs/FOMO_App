package com.example.fomoappproject

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var welcomeTextView: TextView
    private lateinit var logoutButton: Button
    private lateinit var addActivityButton: Button
    private lateinit var myActivityLogButton: Button
    private lateinit var viewGroupsButton: Button



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        initViews()
        loadUsername()
        setupListeners()
    }

    private fun initViews() {

        welcomeTextView = findViewById(R.id.textViewWelcome)
        logoutButton = findViewById(R.id.buttonLogout)
        addActivityButton = findViewById(R.id.buttonAddActivity)
        myActivityLogButton = findViewById(R.id.buttonViewLog)
        viewGroupsButton = findViewById(R.id.buttonViewGroups)

    }

    private fun loadUsername() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            welcomeTextView.text = "Welcome!"
            return
        }

        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                val username = document.getString("username") ?: "User"
                welcomeTextView.text = "Welcome, $username"
            }
            .addOnFailureListener {
                welcomeTextView.text = "Welcome!"
                Toast.makeText(this, "Failed to load username", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupListeners() {
        logoutButton.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        addActivityButton.setOnClickListener {
            startActivity(Intent(this, AddActivityActivity::class.java))
        }

        myActivityLogButton.setOnClickListener {
            startActivity(Intent(this, ActivityLogActivity::class.java))
        }

        viewGroupsButton.setOnClickListener {
            startActivity(Intent(this, MyGroupsActivity::class.java))
        }
    }
}
