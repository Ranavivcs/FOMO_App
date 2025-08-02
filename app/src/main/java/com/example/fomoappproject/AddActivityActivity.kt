package com.example.fomoappproject

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AddActivityActivity : AppCompatActivity() {

    private lateinit var categorySpinner: Spinner
    private lateinit var descriptionEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var backButton: Button
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_activity)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Init views
        categorySpinner = findViewById(R.id.spinnerCategory)
        descriptionEditText = findViewById(R.id.editTextDescription)
        saveButton = findViewById(R.id.buttonSave)
        backButton = findViewById(R.id.buttonBack)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setupCategorySpinner()
        setupListeners()
    }

    private fun setupCategorySpinner() {
        val categories = listOf("Sport", "Study", "Health", "Other")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = adapter
    }

    private fun setupListeners() {
        saveButton.setOnClickListener {
            val category = categorySpinner.selectedItem.toString()
            val description = descriptionEditText.text.toString()
            val userId = auth.currentUser?.uid

            if (description.isBlank() || userId == null) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val activityData = hashMapOf(
                "category" to category,
                "description" to description,
                "userId" to userId,
                "timestamp" to Timestamp.now()
            )

            db.collection("activities")
                .add(activityData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Activity saved", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        backButton.setOnClickListener {
            finish()
        }
    }
}
