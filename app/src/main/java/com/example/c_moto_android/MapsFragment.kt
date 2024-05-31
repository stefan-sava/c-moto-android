package com.example.c_moto_android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.*
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage

class MapsFragment : Fragment() {

    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private lateinit var btnSOS: Button
    private lateinit var btnCancelSOS: Button
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mapFragment: SupportMapFragment
    private lateinit var btnMyLocation: Button
    private lateinit var googleMap: GoogleMap
    private var sosMarkers: MutableList<Marker> = mutableListOf()
    private var sosKey: String? = null

    private val callback = OnMapReadyCallback { googleMap ->
        this.googleMap = googleMap
        val defaultLocation = LatLng(0.0, 0.0)

        if (checkLocationPermission()) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        val currentLatLng = LatLng(location.latitude, location.longitude)
                        googleMap.addMarker(
                            MarkerOptions().position(currentLatLng).title("Your Current Location")
                        )
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Unable to get current location",
                            Toast.LENGTH_SHORT
                        ).show()
                        googleMap.addMarker(
                            MarkerOptions().position(defaultLocation).title("Default Location")
                        )
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 1f))
                    }
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Location permission is required", Toast.LENGTH_SHORT).show()
            }
        }

        arguments?.let {
            val latitude = it.getDouble("latitude", 0.0)
            val longitude = it.getDouble("longitude", 0.0)
            if (latitude != 0.0 && longitude != 0.0) {
                showSosMarker(LatLng(latitude, longitude), "")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_maps, container, false)
        btnMyLocation = view.findViewById(R.id.btnMyLocation)
        btnMyLocation.setOnClickListener {
            moveToCurrentLocation()
        }
        btnSOS = view.findViewById(R.id.btnSOS)
        btnSOS.setOnClickListener {
            sendSOS()
        }
        btnCancelSOS = view.findViewById(R.id.btnCancelSOS)
        btnCancelSOS.setOnClickListener {
            cancelSOS()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(callback)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(sosReceiver, IntentFilter("com.example.c_moto_android.UPDATE_UI"))

        // Ascultă mesajele SOS din Firebase Realtime Database
        val database = FirebaseDatabase.getInstance()
        val myRef = database.getReference("sos_messages")

        myRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(dataSnapshot: DataSnapshot, previousChildName: String?) {
                val latitude = dataSnapshot.child("latitude").getValue(Double::class.java)
                val longitude = dataSnapshot.child("longitude").getValue(Double::class.java)
                if (latitude != null && longitude != null) {
                    val message = "SOS: Latitude=$latitude, Longitude=$longitude"
                    showSosMarker(LatLng(latitude, longitude), dataSnapshot.key!!)
                    sendNotificationsToAllUsers(message)
                }
            }

            override fun onChildChanged(dataSnapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(dataSnapshot: DataSnapshot) {
                val key = dataSnapshot.key
                sosMarkers.find { it.tag == key }?.remove()
            }

            override fun onChildMoved(dataSnapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(databaseError: DatabaseError) {}
        })

        createNotificationChannel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(sosReceiver)
    }

    private val sosReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val latitude = intent.getDoubleExtra("latitude", 0.0)
            val longitude = intent.getDoubleExtra("longitude", 0.0)
            val sosLocation = LatLng(latitude, longitude)
            showSosMarker(sosLocation, "")
        }
    }

    private fun sendSOS() {
        if (checkLocationPermission()) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        val latitude = location.latitude
                        val longitude = location.longitude
                        val message = "SOS: Latitude=$latitude, Longitude=$longitude"

                        // Trimite mesaj SOS la Firebase Realtime Database
                        val database = FirebaseDatabase.getInstance()
                        val myRef = database.getReference("sos_messages")

                        val sosMessage = HashMap<String, Any>()
                        sosMessage["latitude"] = latitude
                        sosMessage["longitude"] = longitude
                        sosMessage["timestamp"] = System.currentTimeMillis()

                        val key = myRef.push().key
                        key?.let {
                            myRef.child(it).setValue(sosMessage)
                            sosKey = it

                            // Trimiterea notificării către toți utilizatorii abonați la topicul sos_alerts
                            FirebaseMessaging.getInstance().send(
                                RemoteMessage.Builder("sos_alerts@fcm.googleapis.com")
                                    .setMessageId(System.currentTimeMillis().toString())
                                    .addData("action", "SOS")
                                    .addData("message", message)
                                    .build()
                            )
                        }

                        // Actualizează vizibilitatea butoanelor
                        btnSOS.visibility = View.GONE
                        btnCancelSOS.visibility = View.VISIBLE
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Unable to get current location",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Location permission is required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendNotificationsToAllUsers(message: String) {
        val database = FirebaseDatabase.getInstance()
        val tokensRef = database.getReference("tokens")

        tokensRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val tokenList = mutableListOf<String>()
                for (tokenSnapshot in dataSnapshot.children) {
                    val token = tokenSnapshot.getValue(String::class.java)
                    token?.let {
                        tokenList.add(it)
                    }
                }
                sendNotifications(tokenList, message)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.w(TAG, "loadTokens:onCancelled", databaseError.toException())
            }
        })
    }

    private fun sendNotifications(tokens: List<String>, message: String) {
        for (token in tokens) {
            val notification = NotificationCompat.Builder(requireContext(), "sos_channel")
                .setSmallIcon(R.drawable.ic_sos)
                .setContentTitle("SOS Alert")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(token.hashCode(), notification)
        }
    }

    private fun showNotification(message: String) {
        val channelId = "sos_channel"
        val channelName = "SOS Alerts"
        val notificationManager =
            requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(requireContext(), channelId)
            .setSmallIcon(R.drawable.ic_sos)
            .setContentTitle("SOS Alert")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    private fun cancelSOS() {
        sosKey?.let {
            val database = FirebaseDatabase.getInstance()
            val myRef = database.getReference("sos_messages").child(it)
            myRef.removeValue().addOnSuccessListener {
                sosKey = null
                btnSOS.visibility = View.VISIBLE
                btnCancelSOS.visibility = View.GONE
            }.addOnFailureListener {
                Toast.makeText(
                    requireContext(),
                    "Failed to cancel SOS",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun checkLocationPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            true
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            false
        }
    }

    private fun showSosMarker(location: LatLng, key: String) {
        val markerOptions = MarkerOptions().position(location).title("SOS Location")
            .icon(bitmapDescriptorFromVector(requireContext(), R.drawable.ic_sos))
        val marker = googleMap.addMarker(markerOptions)
        marker?.tag = key
        sosMarkers.add(marker!!)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15f))
    }

    private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor {
        val vectorDrawable = ContextCompat.getDrawable(context, vectorResId)
        vectorDrawable?.setBounds(0, 0, vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
        val bitmap = Bitmap.createBitmap(
            vectorDrawable!!.intrinsicWidth,
            vectorDrawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun moveToCurrentLocation() {
        if (checkLocationPermission()) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        val currentLatLng = LatLng(location.latitude, location.longitude)
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Unable to get current location",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Location permission is required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "sos_channel"
            val channelName = "SOS Alerts"
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
            val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "MapsFragment"
    }
}