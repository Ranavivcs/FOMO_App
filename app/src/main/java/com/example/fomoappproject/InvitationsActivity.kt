package com.example.fomoappproject

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class InvitationsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var layoutInvites: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_invitations)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        layoutInvites = findViewById(R.id.layoutInvitationsList)

        findViewById<Button>(R.id.buttonBack).setOnClickListener {
            finish()
        }

        fetchPendingInvites()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun fetchPendingInvites() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("groups")
            .whereArrayContains("pendingInvites", userId)
            .get()
            .addOnSuccessListener { documents ->
                layoutInvites.removeAllViews()

                if (documents.isEmpty) {
                    val emptyText = TextView(this)
                    emptyText.text = "No pending invitations"
                    layoutInvites.addView(emptyText)
                    return@addOnSuccessListener
                }

                for (doc in documents) {
                    val groupName = doc.getString("name") ?: "Unknown Group"
                    val startDate = doc.getString("startDate") ?: "Unknown"
                    val endDate = doc.getString("endDate") ?: "Unknown"
                    val groupId = doc.id

                    // נביא את ה-username של המזמין (groupOwner)
                    val groupOwnerId = doc.getString("groupOwner") ?: ""

                    db.collection("users").document(groupOwnerId)
                        .get()
                        .addOnSuccessListener { ownerDoc ->
                            val ownerName = ownerDoc.getString("username") ?: "Unknown"

                            // בונים את ה-layout להזמנה
                            val inviteLayout = LinearLayout(this)
                            inviteLayout.orientation = LinearLayout.VERTICAL
                            inviteLayout.setPadding(0, 0, 0, 32)

                            val groupInfo = TextView(this)
                            groupInfo.text = "Group: $groupName\nOwner: $ownerName\nStart: $startDate - End: $endDate"
                            inviteLayout.addView(groupInfo)

                            val acceptButton = Button(this)
                            acceptButton.text = "Accept"
                            acceptButton.setOnClickListener { acceptInvite(groupId, userId) }
                            inviteLayout.addView(acceptButton)

                            val declineButton = Button(this)
                            declineButton.text = "Decline"
                            declineButton.setOnClickListener { declineInvite(groupId, userId) }
                            inviteLayout.addView(declineButton)

                            layoutInvites.addView(inviteLayout)
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Failed to load inviter's name", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load invitations", Toast.LENGTH_SHORT).show()
            }
    }


    private fun acceptInvite(groupId: String, userId: String) {
        val groupRef = db.collection("groups").document(groupId)

        groupRef.get().addOnSuccessListener { doc ->
            val members = doc.get("members") as? List<String> ?: emptyList()

            if (userId in members) {
                // כבר חבר -> רק להסיר מהpendingInvites
                groupRef.update("pendingInvites", com.google.firebase.firestore.FieldValue.arrayRemove(userId))
                    .addOnSuccessListener {
                        Toast.makeText(this, "Invitation removed (already a member)", Toast.LENGTH_SHORT).show()
                        fetchPendingInvites()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to update invitation", Toast.LENGTH_SHORT).show()
                    }
            } else {
                // להוסיף למembers ולהסיר מpendingInvites
                db.runBatch { batch ->
                    batch.update(groupRef, "pendingInvites", com.google.firebase.firestore.FieldValue.arrayRemove(userId))
                    batch.update(groupRef, "members", com.google.firebase.firestore.FieldValue.arrayUnion(userId))
                }.addOnSuccessListener {
                    Toast.makeText(this, "You joined the group!", Toast.LENGTH_SHORT).show()
                    fetchPendingInvites()
                }.addOnFailureListener {
                    Toast.makeText(this, "Failed to accept invitation", Toast.LENGTH_SHORT).show()
                }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to fetch group", Toast.LENGTH_SHORT).show()
        }
    }


    private fun declineInvite(groupId: String, userId: String) {
        db.collection("groups").document(groupId)
            .update("pendingInvites", com.google.firebase.firestore.FieldValue.arrayRemove(userId))
            .addOnSuccessListener {
                Toast.makeText(this, "Invitation declined", Toast.LENGTH_SHORT).show()
                fetchPendingInvites()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to decline invitation", Toast.LENGTH_SHORT).show()
            }
    }
}
