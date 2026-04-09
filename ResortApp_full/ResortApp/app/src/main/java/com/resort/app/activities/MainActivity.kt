package com.resort.app.activities

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.navigation.NavigationView
import com.resort.app.R
import com.resort.app.databinding.ActivityMainBinding
import com.resort.app.fragments.*
import com.resort.app.utils.FirebaseHelper
import de.hdodenhof.circleimageview.CircleImageView

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbarAndDrawer()
        loadUserHeader()

        // Default fragment
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
            binding.navView.setCheckedItem(R.id.navHome)
        }
    }

    private fun setupToolbarAndDrawer() {
        setSupportActionBar(binding.toolbar)
        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navView.setNavigationItemSelectedListener(this)
    }

    // 🔥 UPDATED FUNCTION
    private fun loadUserHeader() {

        val header = binding.navView.getHeaderView(0)

        val tvName  = header.findViewById<TextView>(R.id.tvHeaderName)
        val tvEmail = header.findViewById<TextView>(R.id.tvHeaderEmail)
        val tvPhone = header.findViewById<TextView>(R.id.tvHeaderPhone) // ✅ ADD
        val ivPhoto = header.findViewById<CircleImageView>(R.id.ivHeaderPhoto)

        val uid = FirebaseHelper.currentUid ?: return

        FirebaseHelper.db.collection(FirebaseHelper.USERS_COL)
            .document(uid)
            .addSnapshotListener { snapshot, _ ->

                if (snapshot != null && snapshot.exists()) {

                    // 🔥 GET VALUES DIRECTLY (SAFER THAN MODEL)
                    val name  = snapshot.getString("name") ?: "Guest"
                    val email = snapshot.getString("email") ?: ""
                    val phone = snapshot.getString("phone") ?: "No phone"
                    val image = snapshot.getString("image") ?: ""

                    // ✅ UPDATE UI
                    tvName.text  = name
                    tvEmail.text = email
                    tvPhone.text = phone   // 🔥 SHOW PHONE

                    // ✅ UPDATE IMAGE
                    if (image.isNotEmpty()) {
                        Glide.with(this)
                            .load(image)
                            .placeholder(android.R.drawable.ic_menu_my_calendar)
                            .into(ivPhoto)
                    } else {
                        ivPhoto.setImageResource(android.R.drawable.ic_menu_my_calendar)
                    }
                }
            }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.navHome        -> loadFragment(HomeFragment())
            R.id.navRoomBooking -> loadFragment(RoomBookingFragment())
            R.id.navKitchen     -> loadFragment(KitchenFragment())
            R.id.navIssues      -> loadFragment(IssuesFragment())
            R.id.navProfile     -> loadFragment(ProfileFragment())
            R.id.navLogout      -> doLogout()
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun doLogout() {
        FirebaseHelper.auth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    // 🔥 VERY IMPORTANT (AUTO REFRESH AFTER PROFILE UPDATE)
    override fun onResume() {
        super.onResume()
        loadUserHeader()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}