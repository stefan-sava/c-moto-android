package com.example.c_moto_android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.c_moto_android.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.*

class TowRequestFragment : Fragment() {

    private lateinit var etName: EditText
    private lateinit var etPhoneNumber: EditText
    private lateinit var etMaxPrice: EditText
    private lateinit var btnSubmitRequest: Button
    private lateinit var tvBids: TextView
    private lateinit var layoutBids: LinearLayout
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var database: FirebaseDatabase
    private lateinit var towRequestsRef: DatabaseReference
    private var currentLocation: Location? = null

    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    private val bidViewsMap = mutableMapOf<String, View>()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tow_request, container, false)
        etName = view.findViewById(R.id.etName)
        etPhoneNumber = view.findViewById(R.id.etPhoneNumber)
        etMaxPrice = view.findViewById(R.id.etMaxPrice)
        btnSubmitRequest = view.findViewById(R.id.btnSubmitRequest)
        tvBids = view.findViewById(R.id.tvBids)
        layoutBids = view.findViewById(R.id.layoutBids)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        database = FirebaseDatabase.getInstance()
        towRequestsRef = database.getReference("tow_requests")

        checkLocationPermission()

        btnSubmitRequest.setOnClickListener {
            submitTowRequest()
        }

        // Listen for new tow requests
        listenForTowRequests()

        return view
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            getCurrentLocation()
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    currentLocation = location
                } else {
                    Toast.makeText(requireContext(), "Nu a putut fi primita locatia curenta", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun submitTowRequest() {
        val name = etName.text.toString().trim()
        val phoneNumber = etPhoneNumber.text.toString().trim()
        val maxPrice = etMaxPrice.text.toString().toDoubleOrNull()

        if (name.isEmpty() || phoneNumber.isEmpty() || maxPrice == null || currentLocation == null) {
            Toast.makeText(requireContext(), "Introdu un nume, un numar de telefon si o suma valida, asigura-te ca locatia este pornita", Toast.LENGTH_SHORT).show()
            return
        }

        val towRequest = TowRequest(
            userId = userId,
            userName = name,
            phoneNumber = phoneNumber,
            maxPrice = maxPrice,
            latitude = currentLocation!!.latitude,
            longitude = currentLocation!!.longitude,
            timestamp = System.currentTimeMillis(),
            isCompleted = false,
            winnerName = ""
        )

        val newRequestRef = towRequestsRef.push()
        newRequestRef.setValue(towRequest).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(requireContext(), "Tow request trimis", Toast.LENGTH_SHORT).show()
                Log.d("TowRequest", "Tow request trimis cu succes")
                listenForBids(newRequestRef.key!!)
            } else {
                Toast.makeText(requireContext(), "Nu a putut fi trimis un tow request", Toast.LENGTH_SHORT).show()
                Log.e("TowRequest", "Nu a putut fi trimis un tow request", task.exception)
            }
        }
    }

    private fun listenForBids(requestId: String) {
        val bidsRef = towRequestsRef.child(requestId).child("bids")
        bidsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(dataSnapshot: DataSnapshot, previousChildName: String?) {
                val bid = dataSnapshot.getValue(Bid::class.java)
                if (bid != null) {
                    addBidToUI(bid, requestId, dataSnapshot.key!!)
                } else {
                    Log.e("TowRequest", "Licitarea este null")
                }
            }

            override fun onChildChanged(dataSnapshot: DataSnapshot, previousChildName: String?) {
                val bid = dataSnapshot.getValue(Bid::class.java)
                if (bid != null) {
                    updateBidUI(bid, requestId, dataSnapshot.key!!)
                }
            }
            override fun onChildRemoved(dataSnapshot: DataSnapshot) {
                val bidId = dataSnapshot.key!!
                removeBidFromUI(bidId)
            }
            override fun onChildMoved(dataSnapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("TowRequest", "Nu au putut fi gasite bids", databaseError.toException())
            }
        })
    }

    private fun listenForTowRequests() {
        towRequestsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(dataSnapshot: DataSnapshot, previousChildName: String?) {
                val towRequest = dataSnapshot.getValue(TowRequest::class.java)
                if (towRequest != null) {
                    addTowRequestToUI(towRequest, dataSnapshot.key!!)
                    listenForBids(dataSnapshot.key!!)
                } else {
                    Log.e("TowRequest", "TowRequest este null")
                }
            }

            override fun onChildChanged(dataSnapshot: DataSnapshot, previousChildName: String?) {
                val towRequest = dataSnapshot.getValue(TowRequest::class.java)
                if (towRequest != null) {
                    updateTowRequestUI(towRequest, dataSnapshot.key!!)
                }
            }
            override fun onChildRemoved(dataSnapshot: DataSnapshot) {
                val requestId = dataSnapshot.key!!
                removeTowRequestFromUI(requestId)
            }
            override fun onChildMoved(dataSnapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("TowRequest", "Nu a putut fi gasite cereri", databaseError.toException())
            }
        })
    }

    private fun addTowRequestToUI(towRequest: TowRequest, requestId: String) {
        if (!isAdded) return  // Check if the fragment is attached to the activity
        Log.d("TowRequest", "Adding tow request to UI: $towRequest")
        val requestView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            tag = requestId  // Use the requestId as tag for easy identification
        }

        val requestTextView = TextView(requireContext()).apply {
            text = "Cerere de tractare de la ${towRequest.userName} numar telefon: (${towRequest.phoneNumber}) pentru maxim ${towRequest.maxPrice} lei"
        }

        val navigateButton = Button(requireContext()).apply {
            text = "NAVIGHEAZĂ"
            setOnClickListener {
                val gmmIntentUri = Uri.parse("geo:${towRequest.latitude},${towRequest.longitude}?q=${towRequest.latitude},${towRequest.longitude}")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")
                startActivity(mapIntent)
            }
        }

        requestView.addView(requestTextView)
        requestView.addView(navigateButton)

        if (!towRequest.isCompleted) {
            val etBidName = EditText(requireContext()).apply {
                hint = "Introdu numele"
            }

            val etBidAmount = EditText(requireContext()).apply {
                hint = "Introdu suma"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            }

            val btnSubmitBid = Button(requireContext()).apply {
                text = "Trimite licitarea"
                setOnClickListener {
                    val bidName = etBidName.text.toString().trim()
                    val bidAmount = etBidAmount.text.toString().toDoubleOrNull()

                    if (bidName.isEmpty() || bidAmount == null || bidAmount >= towRequest.maxPrice) {
                        Toast.makeText(requireContext(), "Introdu un nume valid si o suma mai mica decat cea maxima", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    val bid = Bid(
                        userId = userId,
                        userName = bidName,
                        amount = bidAmount,
                        timestamp = System.currentTimeMillis()
                    )

                    towRequestsRef.child(requestId).child("bids").push().setValue(bid).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(requireContext(), "Licitare trimisa", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "Nu a putut fi trimisa licitarea", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            requestView.addView(etBidName)
            requestView.addView(etBidAmount)
            requestView.addView(btnSubmitBid)
        } else {
            val winnerTextView = TextView(requireContext()).apply {
                text = "Castigatorul este: ${towRequest.winnerName}"
            }
            requestView.addView(winnerTextView)
        }

        // Add a delete button for the tow request if the user is the owner
        if (towRequest.userId == userId) {
            val btnDeleteRequest = Button(requireContext()).apply {
                text = "Sterge cererea"
                setOnClickListener {
                    towRequestsRef.child(requestId).removeValue().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(requireContext(), "Cerere stearsa", Toast.LENGTH_SHORT).show()
                            layoutBids.removeView(requestView)
                        } else {
                            Toast.makeText(requireContext(), "Nu a putut fi stearsa cererea", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            requestView.addView(btnDeleteRequest)
        }

        layoutBids.addView(requestView)
    }

    private fun updateTowRequestUI(towRequest: TowRequest, requestId: String) {
        if (!isAdded) return  // Check if the fragment is attached to the activity
        // Find the existing requestView and update it
        val requestView = layoutBids.findViewWithTag<LinearLayout>(requestId) ?: return

        requestView.removeAllViews()

        val updatedRequestTextView = TextView(requireContext()).apply {
            text = "Cerere de tractare de la ${towRequest.userName} numar telefon: (${towRequest.phoneNumber}) pentru maxim ${towRequest.maxPrice} lei"
        }

        val navigateButton = Button(requireContext()).apply {
            text = "NAVIGHEAZĂ"
            setOnClickListener {
                val gmmIntentUri = Uri.parse("geo:${towRequest.latitude},${towRequest.longitude}?q=${towRequest.latitude},${towRequest.longitude}")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")
                startActivity(mapIntent)
            }
        }

        requestView.addView(updatedRequestTextView)
        requestView.addView(navigateButton)

        if (!towRequest.isCompleted) {
            val etBidName = EditText(requireContext()).apply {
                hint = "Introdu numele"
            }

            val etBidAmount = EditText(requireContext()).apply {
                hint = "Introdu suma"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            }

            val btnSubmitBid = Button(requireContext()).apply {
                text = "Trimite licitare"
                setOnClickListener {
                    val bidName = etBidName.text.toString().trim()
                    val bidAmount = etBidAmount.text.toString().toDoubleOrNull()

                    if (bidName.isEmpty() || bidAmount == null || bidAmount >= towRequest.maxPrice) {
                        Toast.makeText(requireContext(), "Introdu un nume valid si o suma mai mica decat cea a cererii", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    val bid = Bid(
                        userId = userId,
                        userName = bidName,
                        amount = bidAmount,
                        timestamp = System.currentTimeMillis()
                    )

                    towRequestsRef.child(requestId).child("bids").push().setValue(bid).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(requireContext(), "Licitare trimisa", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "Nu a putut fi stearsa licitarea", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            requestView.addView(etBidName)
            requestView.addView(etBidAmount)
            requestView.addView(btnSubmitBid)
        } else {
            val winnerTextView = TextView(requireContext()).apply {
                text = "Castigatorul este: ${towRequest.winnerName}"
            }
            requestView.addView(winnerTextView)
        }

        // Add a delete button for the tow request if the user is the owner
        if (towRequest.userId == userId) {
            val btnDeleteRequest = Button(requireContext()).apply {
                text = "Sterge cererea"
                setOnClickListener {
                    towRequestsRef.child(requestId).removeValue().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(requireContext(), "Cerere stearsa", Toast.LENGTH_SHORT).show()
                            layoutBids.removeView(requestView)
                        } else {
                            Toast.makeText(requireContext(), "Nu a putut fi stearsa cererea", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            requestView.addView(btnDeleteRequest)
        }
    }

    private fun addBidToUI(bid: Bid, requestId: String, bidId: String) {
        if (!isAdded) return  // Check if the fragment is attached to the activity
        Log.d("TowRequest", "Adaugare licitare la UI: $bid")

        // Check if the bid view already exists
        if (bidViewsMap.containsKey(bidId)) return

        val bidView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            tag = bidId  // Use the bidId as tag for easy identification
        }

        val bidTextView = TextView(requireContext()).apply {
            text = "Licitare de la ${bid.userName} cu suma de ${bid.amount}"
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            tag = "bidText"
        }

        val deleteButton = Button(requireContext()).apply {
            text = "Sterge"
            setOnClickListener {
                if (bid.userId == userId) {
                    towRequestsRef.child(requestId).child("bids").child(bidId).removeValue().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(requireContext(), "Licitare stearsa", Toast.LENGTH_SHORT).show()
                            removeBidFromUI(bidId)
                        } else {
                            Toast.makeText(requireContext(), "Nu a putut fi stearsa licitarea", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(requireContext(), "Poti sterge doar licitarile tale", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val selectWinnerButton = Button(requireContext()).apply {
            text = "Alege castigator"
            setOnClickListener {
                towRequestsRef.child(requestId).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        val towRequest = dataSnapshot.getValue(TowRequest::class.java)
                        if (towRequest != null && towRequest.userId == userId) {
                            Toast.makeText(requireContext(), "Castigatorul ales: ${bid.userName}", Toast.LENGTH_SHORT).show()
                            towRequestsRef.child(requestId).child("isCompleted").setValue(true)
                            towRequestsRef.child(requestId).child("winnerName").setValue(bid.userName)
                            // Update UI
                            updateTowRequestUI(towRequest.copy(isCompleted = true, winnerName = bid.userName), requestId)
                            Handler(Looper.getMainLooper()).postDelayed({
                                towRequestsRef.child(requestId).removeValue().addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        Toast.makeText(requireContext(), "Cerere si licitari sterse", Toast.LENGTH_SHORT).show()
                                        layoutBids.removeView(layoutBids.findViewWithTag<LinearLayout>(requestId))
                                    } else {
                                        Toast.makeText(requireContext(), "Nu a putut fi stearsa cererea si licitarile", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }, 3600000) // 1 hour in milliseconds
                        } else {
                            Toast.makeText(requireContext(), "Doar cine a plasat cererea poate alege castigatorul", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onCancelled(databaseError: DatabaseError) {
                        Log.e("TowRequest", "Nu a putut fi verificat owner-ul cererii", databaseError.toException())
                    }
                })
            }
        }

        bidView.addView(bidTextView)
        bidView.addView(deleteButton)
        bidView.addView(selectWinnerButton)
        layoutBids.addView(bidView)

        bidViewsMap[bidId] = bidView
    }

    private fun updateBidUI(bid: Bid, requestId: String, bidId: String) {
        if (!isAdded) return  // Check if the fragment is attached to the activity
        // Find the existing bidView and update it
        val bidView = bidViewsMap[bidId] ?: return

        val bidTextView = bidView.findViewWithTag<TextView>("bidText") ?: return
        bidTextView.text = "Licitare de la ${bid.userName}: ${bid.amount}"
    }

    private fun removeBidFromUI(bidId: String) {
        val bidView = bidViewsMap[bidId] ?: return
        layoutBids.removeView(bidView)
        bidViewsMap.remove(bidId)
    }

    private fun removeTowRequestFromUI(requestId: String) {
        val requestView = layoutBids.findViewWithTag<LinearLayout>(requestId) ?: return
        layoutBids.removeView(requestView)
    }
}

data class TowRequest(
    val userId: String = "",
    val userName: String = "",
    val phoneNumber: String = "",
    val maxPrice: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = 0,
    val isCompleted: Boolean = false,
    val winnerName: String = "",
    val bids: Map<String, Bid> = HashMap()
)

data class Bid(
    val userId: String = "",
    val userName: String = "",
    val amount: Double = 0.0,
    val timestamp: Long = 0
)
