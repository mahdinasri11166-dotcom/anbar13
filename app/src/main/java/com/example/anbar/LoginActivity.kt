package com.example.anbar

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.example.anbar.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var repository: InventoryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        repository = InventoryRepository(this)

        if (!repository.isLoginRequired()) {
            goMain()
            return
        }

        binding.btnLogin.setOnClickListener { tryPinLogin() }

        if (repository.isBiometricEnabled() && canUseBiometric()) {
            showBiometric()
        }
    }

    private fun canUseBiometric(): Boolean {
        val bm = BiometricManager.from(this)
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun showBiometric() {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val admin = repository.getUsers().find { it.role == "ADMIN" && it.isActive }
                if (admin != null) {
                    repository.setCurrentUserId(admin.id)
                    repository.logActivity("LOGIN_BIOMETRIC", admin.username)
                    Toast.makeText(this@LoginActivity, getString(R.string.biometric_ok), Toast.LENGTH_SHORT).show()
                    goMain()
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // continue with PIN
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_title))
            .setSubtitle(getString(R.string.biometric_subtitle))
            .setNegativeButtonText(getString(R.string.use_pin))
            .build()
        prompt.authenticate(info)
    }

    private fun tryPinLogin() {
        val user = binding.etUsername.text?.toString()?.trim().orEmpty()
        val pin = binding.etPin.text?.toString().orEmpty()
        val appUser = repository.authenticate(user, pin)
        if (appUser != null) {
            repository.setCurrentUserId(appUser.id)
            repository.logActivity("LOGIN", appUser.username)
            Toast.makeText(
                this,
                getString(R.string.welcome, appUser.displayName),
                Toast.LENGTH_SHORT
            ).show()
            goMain()
        } else {
            repository.logActivity("LOGIN_FAILED", user)
            Toast.makeText(this, getString(R.string.login_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
