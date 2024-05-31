package com.example.c_moto_android

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
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

class MapsFragment : Fragment(), GoogleMap.OnMarkerClickListener {

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
        this.googleMap.setOnMarkerClickListener(this) // Set the marker click listener

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

        // Check SOS state in Firebase
        checkSOSState()

        // Listen to SOS messages in Firebase Realtime Database
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
                updateButtonStates(false)
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
                        val sosMessage = HashMap<String, Any>()
                        sosMessage["latitude"] = latitude
                        sosMessage["longitude"] = longitude
                        sosMessage["timestamp"] = System.currentTimeMillis()
                        sosMessage["active"] = true

                        val database = FirebaseDatabase.getInstance()
                        val myRef = database.getReference("sos_messages")

                        val key = myRef.push().key
                        key?.let {
                            myRef.child(it).setValue(sosMessage)
                            sosKey = it
                        }

                        updateButtonStates(true)
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

    private fun cancelSOS() {
        sosKey?.let {
            val database = FirebaseDatabase.getInstance()
            val myRef = database.getReference("sos_messages").child(it)
            myRef.removeValue().addOnSuccessListener {
                sosKey = null
                updateButtonStates(false)
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

    private fun updateButtonStates(isSOSActive: Boolean) {
        if (isSOSActive) {
            btnSOS.visibility = View.GONE
            btnCancelSOS.visibility = View.VISIBLE
        } else {
            btnSOS.visibility = View.VISIBLE
            btnCancelSOS.visibility = View.GONE
        }
    }

    private fun checkSOSState() {
        val database = FirebaseDatabase.getInstance()
        val myRef = database.getReference("sos_messages")

        myRef.orderByChild("active").equalTo(true).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    dataSnapshot.children.forEach {
                        val latitude = it.child("latitude").getValue(Double::class.java)
                        val longitude = it.child("longitude").getValue(Double::class.java)
                        if (latitude != null && longitude != null) {
                            sosKey = it.key
                            showSosMarker(LatLng(latitude, longitude), it.key!!)
                            updateButtonStates(true)
                        }
                    }
                } else {
                    updateButtonStates(false)
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.w(TAG, "checkSOSState:onCancelled", databaseError.toException())
            }
        })
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        val position = marker.position
        AlertDialog.Builder(requireContext())
            .setTitle("Navigheaza catre locatie")
            .setMessage("Vrei sa navighezi catre aceasta locatie?")
            .setPositiveButton("Da") { _, _ ->
                val uri = Uri.parse("google.navigation:q=${position.latitude},${position.longitude}")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.setPackage("com.google.android.apps.maps")
                if (intent.resolveActivity(requireContext().packageManager) != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(requireContext(), "Google Maps not installed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Nu", null)
            .show()
        return true
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
                if (tokenList.isNotEmpty()) {
                    sendNotification(tokenList, message)
                } else {
                    Log.w(TAG, "No tokens found for sending notifications.")
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.w(TAG, "loadTokens:onCancelled", databaseError.toException())
            }
        })
    }

    private fun sendNotification(tokens: List<String>, message: String) {
        val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        for (token in tokens) {
            val notification = NotificationCompat.Builder(requireContext(), "sos_channel")
                .setSmallIcon(R.drawable.ic_sos)
                .setContentTitle("SOS Alert")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(token.hashCode(), notification)
        }
    }

    companion object {
        private const val TAG = "MapsFragment"
    }
}