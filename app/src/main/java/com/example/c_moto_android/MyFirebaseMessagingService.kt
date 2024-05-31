package com.example.c_moto_android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        remoteMessage.data["action"]?.let { action ->
            if (action == "SOS") {
                remoteMessage.data["message"]?.let { message ->
                    handleSOSMessage(message)
                    showNotification(message)
                }
            }
        }
    }

    private fun handleSOSMessage(message: String) {
        val regex = Regex("SOS: Latitude=(-?\\d+\\.\\d+), Longitude=(-?\\d+\\.\\d+)")
        val matchResult = regex.find(message)
        if (matchResult != null && matchResult.groupValues.size == 3) {
            val latitude = matchResult.groupValues[1].toDouble()
            val longitude = matchResult.groupValues[2].toDouble()
            val intent = Intent("com.example.c_moto_android.UPDATE_UI")
            intent.putExtra("latitude", latitude)
            intent.putExtra("longitude", longitude)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }
    }

    private fun showNotification(message: String) {
        Log.d(TAG, "showNotification called with message: $message")
        val regex = Regex("SOS: Latitude=(-?\\d+\\.\\d+), Longitude=(-?\\d+\\.\\d+)")
        val matchResult = regex.find(message)
        var latitude = 0.0
        var longitude = 0.0
        if (matchResult != null && matchResult.groupValues.size == 3) {
            latitude = matchResult.groupValues[1].toDouble()
            longitude = matchResult.groupValues[2].toDouble()
            Log.d(TAG, "Extracted coordinates: Latitude=$latitude, Longitude=$longitude")
        } else {
            Log.d(TAG, "Failed to extract coordinates from message: $message")
        }

        val mapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("latitude", latitude)
            putExtra("longitude", longitude)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mapIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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
            .setContentText("SOS: Latitude=$latitude, Longitude=$longitude")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")

        // Subscribe to the SOS alerts topic
        FirebaseMessaging.getInstance().subscribeToTopic("sos_alerts")
            .addOnCompleteListener { task ->
                var msg = "Subscribed to SOS alerts"
                if (!task.isSuccessful) {
                    msg = "Subscription to SOS alerts failed"
                }
                Log.d(TAG, msg)
            }

        // Store the token in Firebase Realtime Database
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