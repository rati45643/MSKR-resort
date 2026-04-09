package com.resort.app.activities

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.resort.app.R
import com.resort.app.databinding.ActivityLoginBinding
import com.resort.app.utils.FirebaseHelper

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var googleSignInClient: GoogleSignInClient
    private var selectedRole = "user"

    companion object {
        private const val RC_GOOGLE_SIGN_IN = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGoogleSignIn()
        setupRoleSelection()
        setupClickListeners()
        setupPasswordToggle() // ✅ checkbox fix
    }

    // ✅ Google setup
    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // 🔥 Always show account chooser
        googleSignInClient.signOut()
    }

    // ✅ Role selection
    private fun setupRoleSelection() {

        val roles = listOf("User", "Kitchen", "Room")

        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            roles
        )

        binding.spinnerRole.adapter = adapter

        binding.spinnerRole.setOnItemSelectedListener(object :
            android.widget.AdapterView.OnItemSelectedListener {

            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                selectedRole = when (position) {
                    0 -> "user"
                    1 -> "kitchen"
                    2 -> "room"
                    else -> "user"
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
    }

    // ✅ Click listeners
    private fun setupClickListeners() {

        binding.btnLogin.setOnClickListener { doEmailLogin() }

        binding.btnGoogleSignIn.setOnClickListener {
            googleSignInClient.signOut() // 🔥 force chooser
            startActivityForResult(googleSignInClient.signInIntent, RC_GOOGLE_SIGN_IN)
        }

        binding.tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        binding.tvSignUp.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
    }

    // ✅ Password show/hide checkbox
    private fun setupPasswordToggle() {
        binding.cbShowPassword.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.etPassword.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                binding.etPassword.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            binding.etPassword.setSelection(binding.etPassword.text?.length ?: 0)
        }
    }

    // ✅ Email login
    private fun doEmailLogin() {

        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)

        FirebaseHelper.auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                validateUserRole()
            }
            .addOnFailureListener {
                setLoading(false)
                Toast.makeText(this, "Login failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    // ✅ Role validation
    private fun validateUserRole() {
        val uid = FirebaseHelper.currentUid ?: return

        FirebaseHelper.getUser(uid) { user ->

            setLoading(false)

            if (user == null) {
                Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
                return@getUser
            }

            if (user.role != selectedRole) {
                FirebaseHelper.auth.signOut()
                Toast.makeText(this, "Wrong role selected", Toast.LENGTH_LONG).show()
                return@getUser
            }

            navigateByRole()
        }
    }

    // ✅ Google result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_GOOGLE_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)

            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: Exception) {
                Toast.makeText(this, "Google sign-in failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ✅ Google login
    private fun firebaseAuthWithGoogle(idToken: String) {

        setLoading(true)

        val credential = GoogleAuthProvider.getCredential(idToken, null)

        FirebaseHelper.auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->

                val firebaseUser = result.user ?: return@addOnSuccessListener

                FirebaseHelper.getUser(firebaseUser.uid) { existingUser ->

                    setLoading(false)

                    if (existingUser == null) {
                        FirebaseHelper.auth.signOut()
                        googleSignInClient.signOut()

                        Toast.makeText(
                            this,
                            "First create your account using Sign Up",
                            Toast.LENGTH_LONG
                        ).show()
                        return@getUser
                    }

                    if (existingUser.role != selectedRole) {
                        FirebaseHelper.auth.signOut()
                        googleSignInClient.signOut()

                        Toast.makeText(
                            this,
                            "This account is not registered as $selectedRole",
                            Toast.LENGTH_LONG
                        ).show()
                        return@getUser
                    }

                    navigateByRole()
                }
            }
            .addOnFailureListener {
                setLoading(false)
                Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
            }
    }

    // ✅ Navigation
    private fun navigateByRole() {
        val uid = FirebaseHelper.currentUid ?: return

        FirebaseHelper.getUser(uid) { user ->
            val intent = when (user?.role) {
                "kitchen" -> Intent(this, KitchenDashboardActivity::class.java)
                "room" -> Intent(this, RoomDashboardActivity::class.java)
                else -> Intent(this, MainActivity::class.java)
            }

            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    // ✅ Progress control
    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
        binding.btnGoogleSignIn.isEnabled = !loading
    }
}