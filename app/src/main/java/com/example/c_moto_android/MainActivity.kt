package com.example.c_moto_android

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.c_moto_android.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        auth = FirebaseAuth.getInstance()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarMain.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.MapsFragment, R.id.LoginFragment, R.id.nav_home
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        checkUserAuthentication()
        handleIntent(intent)
        subscribeToSosAlerts()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun checkUserAuthentication() {
        val user: FirebaseUser? = auth.currentUser
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        if (user == null) {
            navController.navigate(R.id.LoginFragment)
        } else {
            Snackbar.make(binding.root, "Bine ai venit, ${user.displayName}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun handleIntent(intent: Intent) {
        val latitude = intent.getDoubleExtra("latitude", 0.0)
        val longitude = intent.getDoubleExtra("longitude", 0.0)
        if (latitude != 0.0 && longitude != 0.0) {
            val bundle = Bundle().apply {
                putDouble("latitude", latitude)
                putDouble("longitude", longitude)
            }
            val navController = findNavController(R.id.nav_host_fragment_content_main)
            navController.navigate(R.id.MapsFragment, bundle)
        }
    }

    private fun subscribeToSosAlerts() {
        FirebaseMessaging.getInstance().subscribeToTopic("sos_alerts")
            .addOnCompleteListener { task ->
                var msg = "Subscribed to SOS alerts"
                if (!task.isSuccessful) {
                    msg = "Subscription to SOS alerts failed"
                }
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
            }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}