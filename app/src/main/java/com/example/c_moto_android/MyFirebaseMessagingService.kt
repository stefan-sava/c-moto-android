package com.example.c_moto_android

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        // mesajele primite de la serverul Firebase
        Log.d(TAG, "From: ${remoteMessage.from}")
        remoteMessage.data.isNotEmpty().let {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            // afisare notificari
            val message = remoteMessage.data["message"]
            if (message != null) {
                // mesaj in fragmentul hartii
                val mapsFragment = MapsFragment()
                mapsFragment.showNotification(message)
            }
        }
    }


    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // token pentru firebase
        Log.d(TAG, "Refreshed token: $token")
        // in caz ca trebuie trimis token-ul la server
    }

    companion object {
        private const val TAG = "MyFirebaseMessagingServ"
    }
}
