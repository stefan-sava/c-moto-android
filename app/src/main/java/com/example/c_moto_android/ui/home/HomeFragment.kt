package com.example.c_moto_android.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.c_moto_android.R

class HomeFragment : Fragment() {

    private lateinit var homeViewModel: HomeViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_home, container, false)

        val findRoutesButton: Button = root.findViewById(R.id.findRoutesButton)
        findRoutesButton.setOnClickListener {
            openGoogleMaps()
        }

        val findGroupsButton: Button = root.findViewById(R.id.findGroupsButton)
        findGroupsButton.setOnClickListener {
            openFacebookGroups()
        }

        val eventsButton: Button = root.findViewById(R.id.eventsButton)
        eventsButton.setOnClickListener {
            openFacebookEvents()
        }

        val tipsButton: Button = root.findViewById(R.id.tipsButton)
        tipsButton.setOnClickListener {
            openTipsPage()
        }

        return root
    }

    private fun openGoogleMaps() {
        val url = "https://www.google.com/maps/d/u/0/viewer?mid=1fHyrXGWb0S_uL7IEeu46_c_8yks40X5B&femb=1&ll=45.91508084068091%2C23.872525453125043&z=6"
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        startActivity(intent)
    }

    private fun openFacebookGroups() {
        val url = "https://www.facebook.com/groups/search/groups/?q=moto"
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        startActivity(intent)
    }

    private fun openFacebookEvents() {
        val url = "https://www.facebook.com/search/events?q=moto&spell=1"
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        startActivity(intent)
    }

    private fun openTipsPage() {
        val url = "https://2ride.eu/article/sfaturi-pentru-incepatori/"
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        startActivity(intent)
    }
}
