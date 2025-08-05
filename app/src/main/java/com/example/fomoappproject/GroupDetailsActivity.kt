package com.example.fomoappproject

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class GroupDetailsActivity : AppCompatActivity() {

    private lateinit var recyclerViewSubCompetitions: RecyclerView
    private lateinit var subCompetitionAdapter: SubCompetitionAdapter
    private val subCompetitionList = mutableListOf<SubCompetition>()
    private lateinit var db: FirebaseFirestore
    private lateinit var layoutMembersList: LinearLayout
    private lateinit var layoutLeaderboard: LinearLayout
    private lateinit var textViewTitle: TextView
    private var groupId: String? = null
    private var groupName: String? = null
    private var groupEndDate: Date? = null
    private var groupOwnerId: String? = null
    private val userIdToUsername = mutableMapOf<String, String>()

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        groupId = intent.getStringExtra("groupId")
        groupName = intent.getStringExtra("groupName")

        if (groupId == null) {
            Toast.makeText(this, "Error: missing group ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContentView(R.layout.activity_group_details)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = FirebaseFirestore.getInstance()

        val buttonDeleteGroup = findViewById<Button>(R.id.buttonDeleteGroup)
        buttonDeleteGroup.setOnClickListener { confirmAndDeleteGroup() }

        val buttonBack = findViewById<Button>(R.id.buttonBack)
        buttonBack.setOnClickListener { finish() }

        layoutMembersList = findViewById(R.id.layoutMembersList)
        layoutLeaderboard = findViewById(R.id.layoutLeaderboard)
        textViewTitle = findViewById(R.id.textViewGroupTitle)

        recyclerViewSubCompetitions = findViewById(R.id.recyclerViewSubCompetitions)
        subCompetitionAdapter = SubCompetitionAdapter(
            subCompetitionList,
            onClick = { competition ->
                val intent = Intent(this, SubCompetitionDetailsActivity::class.java)
                intent.putExtra("groupId", groupId)
                intent.putExtra("subCompetitionId", competition.id)
                startActivity(intent)
            },
            onLongClick = { competition ->
                showSubCompetitionOptions(competition)
            }
        )
        recyclerViewSubCompetitions.layoutManager = LinearLayoutManager(this)
        recyclerViewSubCompetitions.adapter = subCompetitionAdapter

        val buttonAddSubCompetition = findViewById<Button>(R.id.buttonAddSubCompetition)
        buttonAddSubCompetition.setOnClickListener { showCreateSubCompetitionDialog() }

        val buttonInviteMember = findViewById<Button>(R.id.buttonInviteMember)
        buttonInviteMember.setOnClickListener { showInviteMemberDialog() }

        textViewTitle.text = groupName ?: "Group Details"

        loadPendingInvites()
        loadSubCompetitions()
        loadGroupMembersAndLeaderboard()
    }

    private fun confirmAndDeleteGroup() {
        AlertDialog.Builder(this)
            .setTitle("Delete Group")
            .setMessage("Are you sure you want to delete this group?")
            .setPositiveButton("Delete") { _, _ -> deleteGroup() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteGroup() {
        db.collection("groups").document(groupId!!)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Group deleted", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete group", Toast.LENGTH_SHORT).show()
            }
    }
    private fun showInviteMemberDialog() {
        val dialogView = layoutInflater.inflate(R.layout.invite_member_dialog, null)
        val autoCompleteTextView = dialogView.findViewById<AutoCompleteTextView>(R.id.autoCompleteInvite)

        db.collection("users").get()
            .addOnSuccessListener { documents ->
                val userMap = mutableMapOf<String, String>()  // key: display name (username or email), value: uid
                val displayNames = mutableListOf<String>()

                for (doc in documents) {
                    val uid = doc.id
                    val username = doc.getString("username") ?: ""
                    val email = doc.getString("email") ?: ""

                    if (username.isNotEmpty()) {
                        userMap[username] = uid
                        displayNames.add(username)
                    }
                    if (email.isNotEmpty()) {
                        userMap[email] = uid
                        displayNames.add(email)
                    }
                }

                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, displayNames)
                autoCompleteTextView.setAdapter(adapter)

                autoCompleteTextView.setOnItemClickListener { parent, _, position, _ ->
                    val selectedText = parent.getItemAtPosition(position).toString()
                    autoCompleteTextView.setText(selectedText)
                }

                AlertDialog.Builder(this)
                    .setTitle("Invite Member")
                    .setView(dialogView)
                    .setPositiveButton("Invite") { _, _ ->
                        val inputText = autoCompleteTextView.text.toString().trim()

                        val uidFromMap = userMap[inputText]
                        if (uidFromMap != null) {
                            inviteMemberToGroup(uidFromMap)
                        } else {
                            // Try Firestore search by email first
                            db.collection("users")
                                .whereEqualTo("email", inputText)
                                .get()
                                .addOnSuccessListener { emailDocs ->
                                    if (!emailDocs.isEmpty) {
                                        val uid = emailDocs.documents[0].id
                                        inviteMemberToGroup(uid)
                                    } else {
                                        // If not found by email, try by username
                                        db.collection("users")
                                            .whereEqualTo("username", inputText)
                                            .get()
                                            .addOnSuccessListener { usernameDocs ->
                                                if (!usernameDocs.isEmpty) {
                                                    val uid = usernameDocs.documents[0].id
                                                    inviteMemberToGroup(uid)
                                                } else {
                                                    Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            .addOnFailureListener {
                                                Toast.makeText(this, "Failed to search by username", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                }
                                .addOnFailureListener {
                                    Toast.makeText(this, "Failed to search by email", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load users", Toast.LENGTH_SHORT).show()
            }
    }




    private fun fetchUidByEmailOrUsernameAndInvite(input: String) {
        val usersRef = db.collection("users")

        // Try to find by email first
        usersRef.whereEqualTo("email", input)
            .get()
            .addOnSuccessListener { emailDocs ->
                if (!emailDocs.isEmpty) {
                    val uid = emailDocs.documents[0].id
                    inviteMemberToGroup(uid)
                } else {
                    // If not found by email, try by username
                    usersRef.whereEqualTo("username", input)
                        .get()
                        .addOnSuccessListener { usernameDocs ->
                            if (!usernameDocs.isEmpty) {
                                val uid = usernameDocs.documents[0].id
                                inviteMemberToGroup(uid)
                            } else {
                                Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Error searching by username", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error searching by email", Toast.LENGTH_SHORT).show()
            }
    }


    private fun inviteMemberToGroup(uid: String) {
        db.collection("groups").document(groupId!!)
            .update("pendingInvites", com.google.firebase.firestore.FieldValue.arrayUnion(uid))
            .addOnSuccessListener {
                Toast.makeText(this, "Invitation sent", Toast.LENGTH_SHORT).show()
                loadPendingInvites()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to invite", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadPendingInvites() {
        db.collection("groups").document(groupId!!)
            .get()
            .addOnSuccessListener { groupDoc ->
                val pendingInvites = groupDoc.get("pendingInvites") as? List<String> ?: emptyList()
                val pendingTextView = findViewById<TextView>(R.id.textViewPendingInvites)

                if (pendingInvites.isEmpty()) {
                    pendingTextView.text = "No pending invites"
                    return@addOnSuccessListener
                }

                // נעבור על כל ה-UIDs ונביא את ה-username
                val inviteNames = mutableListOf<String>()
                var pendingFetches = pendingInvites.size

                for (uid in pendingInvites) {
                    db.collection("users").document(uid)
                        .get()
                        .addOnSuccessListener { userDoc ->
                            val username = userDoc.getString("username") ?: uid
                            inviteNames.add(username)
                        }
                        .addOnCompleteListener {
                            pendingFetches--
                            if (pendingFetches == 0) {
                                // סיימנו לאסוף את כל השמות, נעדכן את הטקסט
                                pendingTextView.text = "Pending Invites:\n" + inviteNames.joinToString("\n")
                            }
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load pending invites", Toast.LENGTH_SHORT).show()
            }
    }


    private fun loadSubCompetitions() {
        db.collection("groups").document(groupId!!)
            .collection("subCompetitions")
            .get()
            .addOnSuccessListener { result ->
                subCompetitionList.clear()
                for (doc in result) {
                    val subComp = SubCompetition(
                        id = doc.id,
                        name = doc.getString("name") ?: "Unnamed",
                        theme = doc.getString("theme") ?: "Unknown",
                        isActive = doc.getBoolean("isActive") ?: false
                    )
                    subCompetitionList.add(subComp)
                }
                subCompetitionAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load sub-competitions", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadGroupMembersAndLeaderboard() {
        db.collection("groups").document(groupId!!)
            .get()
            .addOnSuccessListener { groupDoc ->
                groupEndDate = groupDoc.getString("endDate")?.let { dateStr ->
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)
                }
                val members = groupDoc.get("members") as? List<String> ?: emptyList()

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
            .whereEqualTo("groupId", groupId)
            .get()
            .addOnSuccessListener { result ->
                val activityCount = mutableMapOf<String, Int>()

                for (doc in result) {
                    val uid = doc.getString("userId") ?: continue
                    if (uid in members) {
                        activityCount[uid] = activityCount.getOrDefault(uid, 0) + 1
                    }
                }

                layoutLeaderboard.removeAllViews()
                for ((uid, count) in activityCount.entries.sortedByDescending { it.value }) {
                    val username = userIdToUsername[uid] ?: "Unknown"
                    val tv = TextView(this)
                    tv.text = "$username - $count activities"
                    tv.textSize = 16f
                    layoutLeaderboard.addView(tv)
                }

                if (activityCount.isEmpty()) {
                    val tv = TextView(this)
                    tv.text = "No activities yet"
                    layoutLeaderboard.addView(tv)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load leaderboard", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteSubCompetition(subComp: SubCompetition) {
        db.collection("groups").document(groupId!!)
            .collection("subCompetitions").document(subComp.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Deleted ${subComp.name}", Toast.LENGTH_SHORT).show()
                loadSubCompetitions()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showSubCompetitionOptions(subComp: SubCompetition) {
        val dialog = AlertDialog.Builder(this).create()
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_sub_competition, null)

        val textViewSubCompName = dialogView.findViewById<TextView>(R.id.textViewSubCompName)
        val textViewDelete = dialogView.findViewById<TextView>(R.id.textViewDelete)
        val textViewCancel = dialogView.findViewById<TextView>(R.id.textViewCancel)

        textViewSubCompName.text = subComp.name

        textViewDelete.setOnClickListener {
            deleteSubCompetition(subComp)
            dialog.dismiss()
        }

        textViewCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showCreateSubCompetitionDialog() {
        if (groupEndDate == null) {
            Toast.makeText(this, "Group end date not loaded", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = AlertDialog.Builder(this).create()
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_sub_competition, null)

        val editTextName = dialogView.findViewById<EditText>(R.id.editTextChallengeName)
        val spinnerThemes = dialogView.findViewById<Spinner>(R.id.spinnerThemes)
        val buttonStartDate = dialogView.findViewById<Button>(R.id.buttonStartDate)
        val buttonEndDate = dialogView.findViewById<Button>(R.id.buttonEndDate)
        val textViewCancel = dialogView.findViewById<TextView>(R.id.textViewCancel)
        val textViewCreate = dialogView.findViewById<TextView>(R.id.textViewCreate)

        val themes = arrayOf("Sport", "Health", "Study")
        spinnerThemes.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, themes)

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        var startDate: Date? = null
        var endDate: Date? = null

        buttonStartDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                startDate = calendar.time
                buttonStartDate.text = "Start: ${dateFormat.format(startDate!!)}"
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).apply {
                datePicker.minDate = Calendar.getInstance().timeInMillis
                datePicker.maxDate = groupEndDate!!.time - 24 * 60 * 60 * 1000
            }.show()
        }

        buttonEndDate.setOnClickListener {
            if (startDate == null) {
                Toast.makeText(this, "Select start date first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val calendar = Calendar.getInstance()
            calendar.time = startDate!!
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                endDate = calendar.time
                buttonEndDate.text = "End: ${dateFormat.format(endDate!!)}"
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).apply {
                datePicker.minDate = startDate!!.time
                datePicker.maxDate = groupEndDate!!.time - 24 * 60 * 60 * 1000
            }.show()
        }

        textViewCancel.setOnClickListener { dialog.dismiss() }

        textViewCreate.setOnClickListener {
            val challengeName = editTextName.text.toString().trim()
            val theme = spinnerThemes.selectedItem.toString()
            if (challengeName.isBlank() || startDate == null || endDate == null) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val subComp = hashMapOf(
                "name" to challengeName,
                "theme" to theme,
                "isActive" to true,
                "startDate" to startDate,
                "endDate" to endDate
            )
            db.collection("groups").document(groupId!!)
                .collection("subCompetitions")
                .add(subComp)
                .addOnSuccessListener {
                    Toast.makeText(this, "Challenge Created!", Toast.LENGTH_SHORT).show()
                    loadSubCompetitions()
                    dialog.dismiss()
                }
        }

        dialog.setView(dialogView)
        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_background)
        dialog.show()
    }

}
