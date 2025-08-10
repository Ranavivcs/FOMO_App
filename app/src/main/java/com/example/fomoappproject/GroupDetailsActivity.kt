package com.example.fomoappproject

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.messaging.FirebaseMessaging
import android.util.Log

class GroupDetailsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore

    private lateinit var textViewTitle: TextView
    private lateinit var layoutMembersList: LinearLayout
    private lateinit var layoutLeaderboard: LinearLayout

    private lateinit var recyclerViewSubCompetitions: RecyclerView
    private lateinit var subCompetitionAdapter: SubCompetitionAdapter
    private val subCompetitionList = mutableListOf<SubCompetition>()

    private lateinit var recyclerViewFinishedSubCompetitions: RecyclerView
    private lateinit var finishedSubCompetitionAdapter: SubCompetitionAdapter
    private val finishedSubCompetitionList = mutableListOf<SubCompetition>()

    private lateinit var buttonAddSubCompetition: Button
    private lateinit var buttonInviteMember: Button

    private var groupId: String? = null
    private var groupName: String? = null
    private var groupEndDate: Date? = null

    private val userIdToUsername = mutableMapOf<String, String>()

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_details)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        groupId = intent.getStringExtra("groupId")
        groupName = intent.getStringExtra("groupName")

        if (groupId == null) {
            Toast.makeText(this, "Error: missing group ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        FirebaseMessaging.getInstance()
            .subscribeToTopic("group_${groupId}")
            .addOnSuccessListener { Log.d("FCM", "Subscribed to topic group_${groupId}") }
            .addOnFailureListener { e -> Log.w("FCM", "Topic subscribe failed: ${e.message}") }

        db = FirebaseFirestore.getInstance()

        // Views
        textViewTitle = findViewById(R.id.textViewGroupTitle)
        layoutMembersList = findViewById(R.id.layoutMembersList)
        layoutLeaderboard = findViewById(R.id.layoutLeaderboard)

        buttonAddSubCompetition = findViewById(R.id.buttonAddSubCompetition)
        buttonInviteMember = findViewById(R.id.buttonInviteMember)

        findViewById<Button>(R.id.buttonDeleteGroup).setOnClickListener {
            showDeleteDialog(
                title = "Delete Group",
                question = "Are you sure you want to delete this group?"
            ) { deleteGroup() }
        }
        findViewById<Button>(R.id.buttonBack).setOnClickListener { finish() }

        buttonAddSubCompetition.setOnClickListener { showCreateSubCompetitionDialog() }
        buttonInviteMember.setOnClickListener { showInviteMemberDialog() }

        textViewTitle.text = groupName ?: "Group Details"

        // RV: current challenges
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
                showDeleteDialog(
                    title = "Delete Challenge",
                    question = "Are you sure you want to delete this challenge?"
                ) { deleteSubCompetition(competition) }
            }
        )
        recyclerViewSubCompetitions.layoutManager = LinearLayoutManager(this)
        recyclerViewSubCompetitions.adapter = subCompetitionAdapter

        // RV: finished challenges (history)
        recyclerViewFinishedSubCompetitions = findViewById(R.id.recyclerViewFinishedSubCompetitions)
        finishedSubCompetitionAdapter = SubCompetitionAdapter(
            finishedSubCompetitionList,
            onClick = { competition ->
                val intent = Intent(this, SubCompetitionDetailsActivity::class.java)
                intent.putExtra("groupId", groupId)
                intent.putExtra("subCompetitionId", competition.id)
                startActivity(intent)
            },
            onLongClick = { /* no delete for history */ }
        )
        recyclerViewFinishedSubCompetitions.layoutManager = LinearLayoutManager(this)
        recyclerViewFinishedSubCompetitions.adapter = finishedSubCompetitionAdapter

        // Load data
        loadPendingInvites()
        loadSubCompetitions()
        loadGroupMembersAndLeaderboard()
    }

    override fun onResume() {
        super.onResume()
        closeFinishedSubCompetitions()
            .addOnSuccessListener { closeFinishedGroupIfNeeded() }
            .addOnFailureListener { closeFinishedGroupIfNeeded() } // גם אם נכשל משהו, ננסה לסגור
    }

    // ---------- UI helpers ----------
    private fun setEndedUI(ended: Boolean) {
        buttonAddSubCompetition.isEnabled = !ended
        buttonAddSubCompetition.alpha = if (ended) 0.5f else 1f

        buttonInviteMember.isEnabled = !ended
        buttonInviteMember.alpha = if (ended) 0.5f else 1f
    }

    private fun isGroupEnded(): Boolean = groupEndDate?.before(todayMidnight()) == true

    private fun todayMidnight(): Date {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    // ---------- Dialogs / CRUD ----------
    private fun showDeleteDialog(title: String, question: String, onDeleteConfirmed: () -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_generic, null)
        dialogView.findViewById<TextView>(R.id.textViewDialogTitle).text = title
        dialogView.findViewById<TextView>(R.id.textViewDialogQuestion).text = question

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialogView.findViewById<TextView>(R.id.textViewDelete).setOnClickListener {
            onDeleteConfirmed(); dialog.dismiss()
        }
        dialogView.findViewById<TextView>(R.id.textViewCancel).setOnClickListener { dialog.dismiss() }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun deleteGroup() {
        db.collection("groups").document(groupId!!)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Group deleted", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK); finish()
            }
            .addOnFailureListener { Toast.makeText(this, "Failed to delete group", Toast.LENGTH_SHORT).show() }
    }

    private fun deleteSubCompetition(subComp: SubCompetition) {
        db.collection("groups").document(groupId!!)
            .collection("subCompetitions").document(subComp.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Deleted ${subComp.name}", Toast.LENGTH_SHORT).show()
                loadSubCompetitions()
            }
            .addOnFailureListener { Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show() }
    }

    private fun showInviteMemberDialog() {
        if (isGroupEnded()) {
            Toast.makeText(this, "This group already ended — cannot invite members", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.invite_member_dialog, null)
        val autoCompleteTextView = dialogView.findViewById<AutoCompleteTextView>(R.id.autoCompleteInvite)
        val textViewInvite = dialogView.findViewById<TextView>(R.id.textViewInvite)
        val textViewCancel = dialogView.findViewById<TextView>(R.id.textViewCancel)

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        db.collection("users").get()
            .addOnSuccessListener { documents ->
                val displayNames = mutableListOf<String>()
                val userMap = mutableMapOf<String, String>()

                for (doc in documents) {
                    val uid = doc.id
                    val username = doc.getString("username") ?: ""
                    val email = doc.getString("email") ?: ""
                    if (username.isNotEmpty()) { displayNames.add(username); userMap[username] = uid }
                    if (email.isNotEmpty()) { displayNames.add(email); userMap[email] = uid }
                }

                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, displayNames)
                autoCompleteTextView.setAdapter(adapter)

                textViewInvite.setOnClickListener {
                    val inputText = autoCompleteTextView.text.toString().trim()
                    val uidFromMap = userMap[inputText]
                    if (uidFromMap != null) {
                        inviteMemberToGroup(uidFromMap); dialog.dismiss()
                    } else {
                        Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
                    }
                }
                textViewCancel.setOnClickListener { dialog.dismiss() }
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                dialog.show()
            }
    }

    private fun inviteMemberToGroup(uid: String) {
        if (isGroupEnded()) {
            Toast.makeText(this, "This group already ended — cannot invite members", Toast.LENGTH_SHORT).show()
            return
        }

        val inviterId = FirebaseAuth.getInstance().currentUser?.uid

        db.collection("groups").document(groupId!!)
            .update(
                mapOf(
                    "pendingInvites" to com.google.firebase.firestore.FieldValue.arrayUnion(uid),
                    "lastInvitedBy" to inviterId,
                    "lastInvitedAt" to com.google.firebase.Timestamp.now()
                )
            )
            .addOnSuccessListener {
                Toast.makeText(this, "Invitation sent", Toast.LENGTH_SHORT).show()
                loadPendingInvites()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to invite", Toast.LENGTH_SHORT).show()
            }
    }


    // ---------- Loads ----------
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
                                pendingTextView.text = "Pending Invites:\n" + inviteNames.joinToString("\n")
                            }
                        }
                }
            }
            .addOnFailureListener { Toast.makeText(this, "Failed to load pending invites", Toast.LENGTH_SHORT).show() }
    }

    private fun loadSubCompetitions() {
        db.collection("groups").document(groupId!!)
            .collection("subCompetitions")
            .get()
            .addOnSuccessListener { result ->
                subCompetitionList.clear()
                finishedSubCompetitionList.clear()

                for (doc in result) {
                    val closed = doc.getBoolean("closed") ?: false
                    val subComp = SubCompetition(
                        id = doc.id,
                        name = doc.getString("name") ?: "Unnamed",
                        theme = doc.getString("theme") ?: "Unknown",
                        isActive = !closed
                    )
                    if (closed) finishedSubCompetitionList.add(subComp) else subCompetitionList.add(subComp)
                }

                subCompetitionAdapter.notifyDataSetChanged()
                finishedSubCompetitionAdapter.notifyDataSetChanged()

                // כותרת "Finished" לפי נתונים (שימו לב ל-ID תואם ל-XML)
                findViewById<TextView>(R.id.textViewFinishedTitle).visibility =
                    if (finishedSubCompetitionList.isEmpty()) View.GONE else View.VISIBLE
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load sub-competitions", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadGroupLeaderboard() {
        val gid = groupId ?: return
        db.collection("groups").document(gid)
            .get()
            .addOnSuccessListener { groupDoc ->
                // קבע מצב קבוצה (מכבה כפתורים)
                groupEndDate = groupDoc.getString("endDate")?.let {
                    try { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it) } catch (_: Exception) { null }
                }
                setEndedUI(isGroupEnded())

                val raw = groupDoc.get("leaderboard") as? Map<*, *> ?: emptyMap<Any, Any>()
                val leaderboard: Map<String, Int> = raw.mapNotNull { (k, v) ->
                    val uid = k as? String ?: return@mapNotNull null
                    val pts = (v as? Number)?.toInt() ?: 0
                    uid to pts
                }.toMap()

                layoutLeaderboard.removeAllViews()

                if (leaderboard.isEmpty()) {
                    val tv = TextView(this)
                    tv.text = "No wins yet"
                    tv.textSize = 16f
                    layoutLeaderboard.addView(tv)
                    return@addOnSuccessListener
                }

                leaderboard.entries
                    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                    .forEach { (uid, count) ->
                        val username = userIdToUsername[uid] ?: "Unknown"
                        val tv = TextView(this)
                        tv.text = "$username - $count wins"
                        tv.textSize = 16f
                        layoutLeaderboard.addView(tv)
                    }
            }
            .addOnFailureListener { Toast.makeText(this, "Failed to load leaderboard", Toast.LENGTH_SHORT).show() }
    }

    private fun loadGroupMembersAndLeaderboard() {
        db.collection("groups").document(groupId!!)
            .get()
            .addOnSuccessListener { groupDoc ->
                groupEndDate = groupDoc.getString("endDate")?.let {
                    try { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it) } catch (_: Exception) { null }
                }
                val members = groupDoc.get("members") as? List<String> ?: emptyList()

                setEndedUI(isGroupEnded())

                layoutMembersList.removeAllViews()
                userIdToUsername.clear()

                if (members.isEmpty()) {
                    val tv = TextView(this)
                    tv.text = "No members"
                    layoutMembersList.addView(tv)
                    loadGroupLeaderboard()
                    return@addOnSuccessListener
                }

                for (uid in members) {
                    db.collection("users").document(uid)
                        .get()
                        .addOnSuccessListener { userDoc ->
                            val username = userDoc.getString("username") ?: "Unknown"
                            userIdToUsername[uid] = username

                            val textView = TextView(this).apply {
                                text = username
                                textSize = 16f
                            }
                            layoutMembersList.addView(textView)

                            if (userIdToUsername.size == members.size) {
                                loadGroupLeaderboard()
                            }
                        }
                }
            }
            .addOnFailureListener { Toast.makeText(this, "Failed to load group", Toast.LENGTH_SHORT).show() }
    }

    // (לא בשימוש כרגע, השארנו למקרה שתרצה דירוג לפי Activities)
    private fun loadActivitiesForLeaderboard(members: List<String>) {
        val gid = groupId ?: return
        db.collectionGroup("activities")
            .whereEqualTo("groupId", gid)
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
                if (activityCount.isEmpty()) {
                    val tv = TextView(this)
                    tv.text = "No activities yet"
                    tv.textSize = 16f
                    layoutLeaderboard.addView(tv)
                    return@addOnSuccessListener
                }
                for ((uid, count) in activityCount.entries.sortedByDescending { it.value }) {
                    val username = userIdToUsername[uid] ?: "Unknown"
                    val tv = TextView(this)
                    tv.text = "$username - $count activities"
                    tv.textSize = 16f
                    layoutLeaderboard.addView(tv)
                }
            }
            .addOnFailureListener { Toast.makeText(this, "Failed to load leaderboard", Toast.LENGTH_SHORT).show() }
    }

    // ---------- Create Sub-Competition ----------
    private fun showCreateSubCompetitionDialog() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time

        db.collection("groups").document(groupId!!).get()
            .addOnSuccessListener { g ->
                fun parseGroupDate(s: String?): Date? = s?.let { try { sdf.parse(it) } catch (_: Exception) { null } }
                val groupStart = parseGroupDate(g.getString("startDate")) ?: today
                val groupEnd   = parseGroupDate(g.getString("endDate")) ?: today

                val startMin = maxOf(today, groupStart)
                val startMax = groupEnd

                if (startMin.after(startMax)) {
                    Toast.makeText(this, "Group already ended – can't create new challenges.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val dialogView = layoutInflater.inflate(R.layout.dialog_create_sub_competition, null)
                val editTextName = dialogView.findViewById<EditText>(R.id.editTextChallengeName)
                val spinnerThemes = dialogView.findViewById<Spinner>(R.id.spinnerThemes)
                val buttonStartDate = dialogView.findViewById<Button>(R.id.buttonStartDate)
                val buttonEndDate = dialogView.findViewById<Button>(R.id.buttonEndDate)
                val textViewCancel = dialogView.findViewById<TextView>(R.id.textViewCancel)
                val textViewCreate = dialogView.findViewById<TextView>(R.id.textViewCreate)

                val dialog = AlertDialog.Builder(this).setView(dialogView).create()

                val themes = listOf("Sport", "Health", "Study")
                spinnerThemes.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, themes)

                val uiFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                var startDate: Date? = null
                var endDate: Date? = null

                buttonStartDate.setOnClickListener {
                    val cal = Calendar.getInstance().apply { time = startMin }
                    val dp = DatePickerDialog(
                        this,
                        { _, y, m, d ->
                            cal.set(y, m, d, 0, 0, 0); cal.set(Calendar.MILLISECOND, 0)
                            val picked = cal.time
                            if (picked.after(startMax)) {
                                Toast.makeText(this, "Start date cannot be after group end", Toast.LENGTH_SHORT).show()
                                return@DatePickerDialog
                            }
                            startDate = picked
                            buttonStartDate.text = "Start: ${uiFormat.format(picked)}"
                            if (endDate != null && endDate!!.before(startDate)) {
                                endDate = null
                                buttonEndDate.text = "End Date"
                            }
                        },
                        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                    )
                    dp.datePicker.minDate = startMin.time
                    dp.datePicker.maxDate = startMax.time
                    dp.show()
                }

                buttonEndDate.setOnClickListener {
                    if (startDate == null) {
                        Toast.makeText(this, "Select start date first", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val cal = Calendar.getInstance().apply { time = startDate!! }
                    val dp = DatePickerDialog(
                        this,
                        { _, y, m, d ->
                            cal.set(y, m, d, 0, 0, 0); cal.set(Calendar.MILLISECOND, 0)
                            val picked = cal.time
                            if (picked.before(startDate)) {
                                Toast.makeText(this, "End date must be after start", Toast.LENGTH_SHORT).show()
                                return@DatePickerDialog
                            }
                            endDate = picked
                            buttonEndDate.text = "End: ${uiFormat.format(picked)}"
                        },
                        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                    )
                    dp.datePicker.minDate = startDate!!.time
                    dp.datePicker.maxDate = groupEnd.time
                    dp.show()
                }

                textViewCancel.setOnClickListener { dialog.dismiss() }
                textViewCreate.setOnClickListener {
                    val name = editTextName.text.toString().trim()
                    val theme = spinnerThemes.selectedItem.toString()
                    if (name.isBlank() || startDate == null || endDate == null) {
                        Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show(); return@setOnClickListener
                    }
                    if (startDate!! < startMin || startDate!! > startMax) {
                        Toast.makeText(this, "Start date is out of group range", Toast.LENGTH_SHORT).show(); return@setOnClickListener
                    }
                    if (endDate!! < startDate!! || endDate!! > groupEnd) {
                        Toast.makeText(this, "End date must be after start and within group dates", Toast.LENGTH_SHORT).show(); return@setOnClickListener
                    }

                    val subComp = hashMapOf(
                        "name" to name,
                        "theme" to theme,
                        "isActive" to true,
                        "startDate" to startDate!!,
                        "endDate" to endDate!!
                    )
                    db.collection("groups").document(groupId!!)
                        .collection("subCompetitions")
                        .add(subComp)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Challenge Created!", Toast.LENGTH_SHORT).show()
                            loadSubCompetitions()
                            dialog.dismiss()
                        }
                        .addOnFailureListener { Toast.makeText(this, "Failed to create challenge", Toast.LENGTH_SHORT).show() }
                }

                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                dialog.show()
            }
            .addOnFailureListener { Toast.makeText(this, "Failed to load group dates", Toast.LENGTH_SHORT).show() }
    }

    // ---------- Auto-close finished sub-competitions, award trophy + leaderboard ----------

    private fun closeFinishedGroupIfNeeded() {
        val gId = groupId ?: return
        val today = todayMidnight()

        db.collection("groups").document(gId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener

                val closed = doc.getBoolean("closed") ?: false
                if (closed) return@addOnSuccessListener

                val endStr = doc.getString("endDate")
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val endDate = try { sdf.parse(endStr ?: "") } catch (_: Exception) { null }
                if (endDate != null && !endDate.after(today)) {
                    // עדכון לסגירת הקבוצה — יפעיל את onGroupClosed בשרת
                    db.collection("groups").document(gId)
                        .update("closed", true)
                }
            }
    }

    private fun closeFinishedSubCompetitions(): com.google.android.gms.tasks.Task<Void> {
        val gId = groupId ?: return com.google.android.gms.tasks.Tasks.forResult(null as Void?)
        val groupRef = db.collection("groups").document(gId)
        val today = todayMidnight()

        val tcs = com.google.android.gms.tasks.TaskCompletionSource<Void>()

        groupRef.collection("subCompetitions").get()
            .addOnSuccessListener { result ->
                val tasks = mutableListOf<com.google.android.gms.tasks.Task<*>>()

                for (doc in result) {
                    val isClosed = doc.getBoolean("closed") ?: false
                    if (isClosed) continue

                    val endAny = doc.get("endDate")
                    val endDate: Date? = when (endAny) {
                        is com.google.firebase.Timestamp -> endAny.toDate()
                        is Date -> endAny
                        is String -> {
                            val f1 = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val f2 = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                            try { f1.parse(endAny) } catch (_: Exception) {
                                try { f2.parse(endAny) } catch (_: Exception) { null }
                            }
                        }
                        else -> null
                    }
                    if (endDate == null || endDate.after(today)) continue

                    val subId = doc.id
                    val pointsMap: Map<String, Int> =
                        (doc.get("points") as? Map<*, *>)?.mapNotNull { (k, v) ->
                            val uid = k as? String ?: return@mapNotNull null
                            val pts = (v as? Number)?.toInt() ?: 0
                            uid to pts
                        }?.toMap() ?: emptyMap()

                    if (pointsMap.isEmpty()) {
                        tasks += groupRef.collection("subCompetitions").document(subId)
                            .update("closed", true)
                    } else {
                        val winnerId = pointsMap.entries
                            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                            .first().key
                        val batch = db.batch()
                        batch.update(groupRef.collection("subCompetitions").document(subId), "closed", true)
                        batch.update(groupRef, "leaderboard.$winnerId",
                            com.google.firebase.firestore.FieldValue.increment(1))
                        tasks += batch.commit()
                    }
                }

                if (tasks.isEmpty()) {
                    // אין מה לסגור — נרענן UI ונצא
                    loadGroupLeaderboard()
                    loadSubCompetitions()
                    tcs.setResult(null)
                    return@addOnSuccessListener
                }

                com.google.android.gms.tasks.Tasks.whenAllComplete(tasks)
                    .addOnSuccessListener {
                        // רענון UI אחרי שסיימנו לעדכן
                        loadGroupLeaderboard()
                        loadSubCompetitions()
                        tcs.setResult(null)
                    }
                    .addOnFailureListener { tcs.setResult(null) }
            }
            .addOnFailureListener { tcs.setResult(null) }

        return tcs.task
    }


}
