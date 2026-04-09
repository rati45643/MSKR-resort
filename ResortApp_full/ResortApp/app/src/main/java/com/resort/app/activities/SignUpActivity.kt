package com.resort.app.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.resort.app.R
import com.resort.app.databinding.ActivitySignUpBinding
import com.resort.app.models.User
import com.resort.app.utils.FirebaseHelper

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpBinding
    private lateinit var googleSignInClient: GoogleSignInClient
    private var selectedRole = "user"

    companion object {
        private const val RC_GOOGLE_SIGN_IN = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGoogleSignIn()
        setupRoleSelection()
        setupClickListeners()
        //SHOW PASSWORD
                binding.cbShowPassword.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        binding.etPassword.inputType =
                            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    } else {
                        binding.etPassword.inputType =
                            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                    }
                    binding.etPassword.setSelection(binding.etPassword.text?.length ?: 0)
                }

        // SHOW CONFIRM PASSWORD
        binding.cbShowConfirmPassword.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.etConfirmPassword.inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                binding.etConfirmPassword.inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            binding.etConfirmPassword.setSelection(binding.etConfirmPassword.text?.length ?: 0)
        }
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // 🔥 FORCE account chooser every time
        googleSignInClient.signOut()
    }

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

    private fun setupClickListeners() {

        binding.btnSignUp.setOnClickListener { doEmailSignUp() }

        binding.btnGoogleSignUp.setOnClickListener {
            googleSignInClient.signOut() // 🔥 force chooser
            startActivityForResult(googleSignInClient.signInIntent, RC_GOOGLE_SIGN_IN)
        }

        binding.tvLogin.setOnClickListener { finish() }
    }

    private fun doEmailSignUp() {

        val fullName = binding.etFullName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        // Validation
        if (fullName.isEmpty()) {
            binding.etFullName.error = "Required"; return
        }
        if (email.isEmpty()) {
            binding.etEmail.error = "Required"; return
        }
        if (password.length < 6) {
            binding.etPassword.error = "Min 6 characters"; return
        }
        if (password != confirmPassword) {
            binding.etConfirmPassword.error = "Passwords do not match"
            return
        }

        setLoading(true)

        // 🔥 CHECK duplicate email
        FirebaseHelper.auth.fetchSignInMethodsForEmail(email)
            .addOnSuccessListener { result ->

                if (!result.signInMethods.isNullOrEmpty()) {
                    setLoading(false)
                    Toast.makeText(this, "Email already registered", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                // Create account
                FirebaseHelper.auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener { authResult ->

                        val firebaseUser = authResult.user ?: return@addOnSuccessListener

                        val user = User(
                            uid = firebaseUser.uid,
                            fullName = fullName,
                            email = email,
                            role = selectedRole
                        )

                        FirebaseHelper.saveUser(user) { success, error ->
                            setLoading(false)
                            if (success) {
                                navigateByRole()
                            } else {
                                Toast.makeText(this, "Save failed: $error", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    .addOnFailureListener {
                        setLoading(false)
                        Toast.makeText(this, "Sign up failed: ${it.message}", Toast.LENGTH_LONG).show()
                    }
            }
    }

    @Deprecated("Deprecated")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_GOOGLE_SIGN_IN) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                    .getResult(ApiException::class.java)

                firebaseAuthWithGoogle(account.idToken!!)

            } catch (e: Exception) {
                Toast.makeText(this, "Google sign-up failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {

        setLoading(true)

        val credential = GoogleAuthProvider.getCredential(idToken, null)

        FirebaseHelper.auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->

                val firebaseUser = result.user ?: return@addOnSuccessListener

                // 🔥 CHECK duplicate user
                FirebaseHelper.getUser(firebaseUser.uid) { existingUser ->

                    if (existingUser != null) {
                        setLoading(false)

                        FirebaseHelper.auth.signOut()
                        googleSignInClient.signOut()

                        Toast.makeText(
                            this,
                            "Account already exists. Please login.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@getUser
                    }

                    // New user
                    val user = User(
                        uid = firebaseUser.uid,
                        fullName = firebaseUser.displayName ?: "",
                        email = firebaseUser.email ?: "",
                        role = selectedRole,
                        photoUrl = firebaseUser.photoUrl?.toString() ?: ""
                    )

                    FirebaseHelper.saveUser(user) { success, _ ->
                        setLoading(false)
                        if (success) {
                            navigateByRole()
                        }
                    }
                }
            }
            .addOnFailureListener {
                setLoading(false)
                Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateByRole() {
        val intent = when (selectedRole) {
            "kitchen" -> Intent(this, KitchenDashboardActivity::class.java)
            "room" -> Intent(this, RoomDashboardActivity::class.java)
            else -> Intent(this, MainActivity::class.java)
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSignUp.isEnabled = !loading
        binding.btnGoogleSignUp.isEnabled = !loading
    }
}