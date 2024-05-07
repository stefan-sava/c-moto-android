package com.example.c_moto_android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage

class MapsFragment : Fragment() {

    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private lateinit var btnSOS: Button
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mapFragment: SupportMapFragment
    private lateinit var btnMyLocation: Button
    private lateinit var googleMap: GoogleMap

    private val callback = OnMapReadyCallback { googleMap ->
        val defaultLocation = LatLng(0.0, 0.0)

        if (checkLocationPermission()) {
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
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(callback)
    }

    private fun checkLocationPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return false
        }
        return true
    }

    private fun moveToCurrentLocation() {
        if (checkLocationPermission()) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    mapFragment.getMapAsync { googleMap ->
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Unable to get current location",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun sendSOS() {
        if (checkLocationPermission()) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val latitude = location.latitude
                    val longitude = location.longitude
                    val message = "SOS: Latitude=$latitude, Longitude=$longitude"
                    FirebaseMessaging.getInstance().send(
                        RemoteMessage.Builder("SOS")
                            .setMessageId(java.lang.String.valueOf(System.currentTimeMillis()))
                            .setData(mapOf("message" to message))
                            .build()
                    )
                    Toast.makeText(requireContext(), "SOS Sent!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Unable to get current location",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun setupMap() {
        if (checkLocationPermission()) {
            googleMap.isMyLocationEnabled = true
            googleMap.uiSettings.isMyLocationButtonEnabled = true
            googleMap.setOnMyLocationButtonClickListener {
                moveToCurrentLocation()
                true
            }
            moveToCurrentLocation()
        }
    }
    fun showNotification(message: String) {
        // Extrage latitudinea și longitudinea din mesajul primit
        val regex = Regex("SOS: Latitude=(-?\\d+\\.\\d+), Longitude=(-?\\d+\\.\\d+)")
        val matchResult = regex.find(message)
        if (matchResult != null && matchResult.groupValues.size == 3) {
            val latitude = matchResult.groupValues[1].toDouble()
            val longitude = matchResult.groupValues[2].toDouble()
            // Afișează un marcator cu locația primită pe hartă
            val sosLocation = LatLng(latitude, longitude)
            googleMap.addMarker(
                MarkerOptions().position(sosLocation).title("SOS Location")
            )
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(sosLocation, 15f))
        } else {
            Toast.makeText(
                requireContext(),
                "Invalid SOS message format",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

}
