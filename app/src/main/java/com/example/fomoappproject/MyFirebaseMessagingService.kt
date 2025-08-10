package com.example.fomoappproject

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val CHANNEL_ID = "fomo_channel"
        private const val CHANNEL_NAME = "FOMO Notifications"
        private const val CHANNEL_DESC = "Notifications from the FOMO app"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .update("fcmTokens", FieldValue.arrayUnion(token))
            .addOnFailureListener {
                // אם אין שדה עדיין – ניצור
                FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .set(mapOf("fcmTokens" to listOf(token)), SetOptions.merge())
            }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "FOMO"

        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: "You have a new notification"

        // העבר גם את ה-data
        showNotification(title, body, remoteMessage.data)
    }

    // חתימה חדשה עם data
    private fun showNotification(title: String, message: String, data: Map<String, String>) {
        // === יצירת ערוץ כמו שכבר יש לך ===
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = CHANNEL_DESC }
            mgr.createNotificationChannel(ch)
        }

        // אם הגיעו מזהים – נפתח את מסך התת־תחרות; אחרת את ה-Main
        val intent = if (data["groupId"] != null && data["subCompetitionId"] != null) {
            Intent(this, SubCompetitionDetailsActivity::class.java).apply {
                putExtra("groupId", data["groupId"])
                putExtra("subCompetitionId", data["subCompetitionId"])
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_fomo)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .apply {
                try {
                    setColor(ContextCompat.getColor(this@MyFirebaseMessagingService, R.color.fomo_purple))
                } catch (_: Exception) {}
            }

        val canNotify = Build.VERSION.SDK_INT < 33 ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        if (canNotify) {
            NotificationManagerCompat.from(this)
                .notify(System.currentTimeMillis().toInt(), builder.build())
        } else {
            Log.w("FCM", "No POST_NOTIFICATIONS permission, skipping notify()")
        }
    }

    // -------- Optional: שמירת טוקן למשתמש המחובר --------
    private fun saveTokenForCurrentUser(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid)
            .update("fcmTokens", FieldValue.arrayUnion(token))
            .addOnSuccessListener { Log.d("FCM", "Token saved for user $uid") }
            .addOnFailureListener { e -> Log.w("FCM", "Failed to save token: ${e.message}") }
    }
}
