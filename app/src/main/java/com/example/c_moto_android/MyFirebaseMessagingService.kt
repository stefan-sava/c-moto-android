package com.example.c_moto_android

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        // Verifică dacă mesajul este un SOS
        remoteMessage.data["action"]?.let { action ->
            if (action == "SOS") {
                remoteMessage.data["message"]?.let { message ->
                    // Notificare locală sau actualizare UI
                    handleSOSMessage(message)
                }
            }
        }
    }

    private fun handleSOSMessage(message: String) {
        // Extrage latitudinea și longitudinea din mesajul SOS
        val regex = Regex("SOS: Latitude=(-?\\d+\\.\\d+), Longitude=(-?\\d+\\.\\d+)")
        val matchResult = regex.find(message)
        if (matchResult != null && matchResult.groupValues.size == 3) {
            val latitude = matchResult.groupValues[1].toDouble()
            val longitude = matchResult.groupValues[2].toDouble()
            // Crează o intenție pentru a actualiza UI-ul sau a afișa o notificare
            val intent = Intent("com.example.c_moto_android.UPDATE_UI")
            intent.putExtra("latitude", latitude)
            intent.putExtra("longitude", longitude)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
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
