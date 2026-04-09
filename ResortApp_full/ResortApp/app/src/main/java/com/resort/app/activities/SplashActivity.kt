package com.resort.app.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.resort.app.databinding.ActivitySplashBinding
import com.resort.app.utils.FirebaseHelper

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get Started button click
        binding.btnGetStarted.setOnClickListener {
            checkAuthAndNavigate()
        }
    }

    private fun checkAuthAndNavigate() {
        val user = FirebaseHelper.auth.currentUser

        if (user != null) {
            // User already logged in — get role and navigate
            FirebaseHelper.getUser(user.uid) { userObj ->

                when (userObj?.role) {
                    "kitchen" -> {
                        startActivity(
                            Intent(
                                this,
                                KitchenDashboardActivity::class.java
                            )
                        )
                    }

                    "room" -> {
                        startActivity(
                            Intent(
                                this,
                                RoomDashboardActivity::class.java
                            )
                        )
                    }

                    else -> {
                        startActivity(
                            Intent(
                                this,
                                MainActivity::class.java
                            )
                        )
                    }
                }

                finish()
            }

        } else {
            startActivity(
                Intent(
                    this,
                    LoginActivity::class.java
                )
            )
            finish()
        }
    }
}