package com.example.c_moto_android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        if (remoteMessage.data.isNotEmpty()) {
            val action = remoteMessage.data["action"]
            val message = remoteMessage.data["message"]
            if (action == "SOS" && message != null) {
                showNotification(message)
            }
        }

        remoteMessage.notification?.let {
            showNotification(it.body ?: "SOS Alert")
        }
    }

    private fun showNotification(message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "sos_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SOS Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_sos)
            .setContentTitle("SOS Alert")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")

        FirebaseMessaging.getInstance().subscribeToTopic("sos_alerts")
            .addOnCompleteListener { task ->
                val msg = if (task.isSuccessful) {
                    "Subscribed to SOS alerts"
                } else {
                    "Subscription to SOS alerts failed"
                }
                Log.d(TAG, msg)
            }

        storeToken(token)
    }

    private fun storeToken(token: String) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val userId = user.uid
            val database = FirebaseDatabase.getInstance()
            val tokensRef = database.getReference("tokens")
            tokensRef.child(userId).setValue(token).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Token successfully stored")
                } else {
                    Log.d(TAG, "Failed to store token")
                }
            }
        } else {
            Log.d(TAG, "User is not authenticated, cannot store token")
        }
    }

    companion object {
        private const val TAG = "MyFirebaseMessagingServ"
    }
}
