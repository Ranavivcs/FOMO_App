package com.example.fomoappproject

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ChooseUsernameActivity : AppCompatActivity() {

    private lateinit var editTextUsername: EditText
    private lateinit var buttonContinue: Button
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choose_username)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        editTextUsername = findViewById(R.id.editTextUsername)
        buttonContinue = findViewById(R.id.buttonContinue)

        buttonContinue.setOnClickListener { handleContinue() }
    }

    private fun handleContinue() {
        val username = editTextUsername.text.toString().trim()
        if (username.isBlank()) {
            Toast.makeText(this, "Username cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUser = auth.currentUser
        val userId = currentUser?.uid ?: return
        val email = currentUser.email ?: ""

        // בדיקה אם השם תפוס
        db.collection("users")
            .whereEqualTo("username", username)
            .get()
            .addOnSuccessListener { docs ->
                if (!docs.isEmpty) {
                    Toast.makeText(this, "Username already taken", Toast.LENGTH_SHORT).show()
                } else {
                    saveUserData(userId, username, email)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error checking username", Toast.LENGTH_LONG).show()
            }
    }

    private fun saveUserData(userId: String, username: String, email: String) {
        val userData = mapOf(
            "username" to username,
            "email" to email
        )

        db.collection("users").document(userId)
            .set(userData)
            .addOnSuccessListener {
                Toast.makeText(this, "Username saved", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }
}
