package com.resort.app.activities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.resort.app.databinding.ActivityForgotPasswordBinding
import com.resort.app.utils.FirebaseHelper

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnSendResetLink.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isEmpty()) {
                binding.etEmail.error = "Please enter your email"
                return@setOnClickListener
            }

            binding.progressBar.visibility = View.VISIBLE
            binding.btnSendResetLink.isEnabled = false

            FirebaseHelper.auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    binding.progressBar.visibility = View.GONE
                    binding.layoutForm.visibility = View.GONE
                    binding.layoutSuccess.visibility = View.VISIBLE
                    binding.tvSuccessEmail.text = email
                }
                .addOnFailureListener {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSendResetLink.isEnabled = true
                    Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }

        binding.btnBackToLogin.setOnClickListener { finish() }
    }
}
