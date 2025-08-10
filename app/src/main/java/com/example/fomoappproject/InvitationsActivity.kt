package com.example.fomoappproject

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class InvitationsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var title: TextView
    private lateinit var listContainer: LinearLayout
    private lateinit var backBtn: Button

    data class InvitationItem(
        val groupId: String,
        val groupName: String,
        val inviterId: String?,
        val inviterName: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_invitations) //

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        title = findViewById(R.id.textViewInvitationsTitle)
        listContainer = findViewById(R.id.layoutInvitationsList)
        backBtn = findViewById(R.id.buttonBack)

        backBtn.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        loadInvitations()
    }

    private fun loadInvitations() {
        val uid = auth.currentUser?.uid ?: return
        listContainer.removeAllViews()


        db.collection("groups")
            .whereArrayContains("pendingInvites", uid)
            .get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    addEmptyState()
                    return@addOnSuccessListener
                }

                // מביא גם את שם המזמין
                val tasks = snap.documents.map { gDoc ->
                    val groupId = gDoc.id
                    val groupName = gDoc.getString("name") ?: "Group"
                    val inviterId = gDoc.getString("lastInvitedBy")

                    if (inviterId.isNullOrBlank()) {
                        Tasks.forResult(
                            InvitationItem(groupId, groupName, null, "Someone")
                        )
                    } else {
                        db.collection("users").document(inviterId).get()
                            .continueWith { t ->
                                val inviterName = t.result?.getString("username") ?: "Someone"
                                InvitationItem(groupId, groupName, inviterId, inviterName)
                            }
                    }
                }

                Tasks.whenAllSuccess<InvitationItem>(tasks)
                    .addOnSuccessListener { items ->
                        if (items.isEmpty()) {
                            addEmptyState()
                        } else {
                            items.forEach { addInvitationRow(it) }
                        }
                    }
                    .addOnFailureListener {
                        addEmptyState()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load invitations", Toast.LENGTH_SHORT).show()
                addEmptyState()
            }
    }

    private fun addInvitationRow(item: InvitationItem) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_invitation, listContainer, false)

        val tv = row.findViewById<TextView>(R.id.textViewInvitationUsername)
        val btnAccept = row.findViewById<Button>(R.id.buttonAcceptInvitation)
        val btnDecline = row.findViewById<Button>(R.id.buttonDeclineInvitation)

        tv.text = "${item.inviterName} invited you to join \"${item.groupName}\""

        btnAccept.setOnClickListener { accept(item, row) }
        btnDecline.setOnClickListener { decline(item, row) }

        listContainer.addView(row)
    }

    private fun accept(item: InvitationItem, rowView: android.view.View) {
        val uid = auth.currentUser?.uid ?: return
        val groupRef = db.collection("groups").document(item.groupId)

        db.runBatch { b ->
            b.update(groupRef, "pendingInvites", FieldValue.arrayRemove(uid))
            b.update(groupRef, "members", FieldValue.arrayUnion(uid))
        }.addOnSuccessListener {
            // נרשום את המשתמש לטופיק של הקבוצה (לפוש)
            FirebaseMessaging.getInstance()
                .subscribeToTopic("group_${item.groupId}")

            Toast.makeText(this, "Joined ${item.groupName}", Toast.LENGTH_SHORT).show()
            listContainer.removeView(rowView)
            if (listContainer.childCount == 0) addEmptyState()
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to accept invitation", Toast.LENGTH_SHORT).show()
        }
    }

    private fun decline(item: InvitationItem, rowView: android.view.View) {
        val uid = auth.currentUser?.uid ?: return
        val groupRef = db.collection("groups").document(item.groupId)

        groupRef.update("pendingInvites", FieldValue.arrayRemove(uid))
            .addOnSuccessListener {
                Toast.makeText(this, "Declined invitation", Toast.LENGTH_SHORT).show()
                listContainer.removeView(rowView)
                if (listContainer.childCount == 0) addEmptyState()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to decline", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addEmptyState() {
        if (listContainer.childCount > 0) return
        val tv = TextView(this).apply {
            text = "No pending invitations"
            textSize = 16f
            setTextColor(0xFF666666.toInt())
            setPadding(8, 8, 8, 8)
        }
        listContainer.addView(tv)
    }
}
