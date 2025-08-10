package com.example.fomoappproject

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import java.text.SimpleDateFormat
import java.util.*

class CreateGroupActivity : AppCompatActivity() {

    private lateinit var editTextGroupName: EditText
    private lateinit var textViewStartDate: TextView
    private lateinit var textViewEndDate: TextView
    private lateinit var buttonPickEndDate: Button
    private lateinit var buttonCreateGroup: Button

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var selectedEndDate: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_group)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        editTextGroupName = findViewById(R.id.editTextGroupName)
        textViewStartDate = findViewById(R.id.textViewStartDate)
        textViewEndDate = findViewById(R.id.textViewEndDate)
        buttonPickEndDate = findViewById(R.id.buttonPickEndDate)
        buttonCreateGroup = findViewById(R.id.buttonCreateGroup)

        // Start Date is always today
        val today = Calendar.getInstance().time
        textViewStartDate.text = "Start Date: ${dateFormat.format(today)}"

        buttonPickEndDate.setOnClickListener {
            showEndDatePicker()
        }

        buttonCreateGroup.setOnClickListener {
            createGroup()
        }
    }

    private fun showEndDatePicker() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 1) // Minimum end date is tomorrow

        val datePicker = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                selectedEndDate = dateFormat.format(calendar.time)
                textViewEndDate.text = "End Date: $selectedEndDate"
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // Disable dates before tomorrow
        datePicker.datePicker.minDate = calendar.timeInMillis
        datePicker.show()
    }

    private fun createGroup() {
        val groupName = editTextGroupName.text.toString().trim()
        val userId = auth.currentUser?.uid

        if (groupName.isBlank() || userId == null || selectedEndDate == null) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val groupData = hashMapOf(
            "name" to groupName,
            "members" to listOf(userId),
            "groupOwner" to userId,
            "pendingInvites" to listOf<String>(),
            "startDate" to dateFormat.format(Calendar.getInstance().time),
            "endDate" to selectedEndDate!!
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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
