package com.example.anbar

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.FileOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/**
 * بخش ۷: رمزنگاری بکاپ و داده‌های حساس
 * از Android Keystore + AES برای رمزنگاری فایل بکاپ استفاده می‌کند.
 */
object SecurityHelper {

    private const val SALT = "AnbarApp_v7_Salt_2024"
    private const val ITERATIONS = 10000

    fun getEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "anbar_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("anbar_secure_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    /** رمزنگاری متن با کلید مشتق‌شده از PIN */
    fun encrypt(plain: String, pin: String): String {
        val key = deriveKey(pin)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val iv = ByteArray(16)
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String, pin: String): String? {
        return try {
            val key = deriveKey(pin)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val iv = ByteArray(16)
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
            val decrypted = cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP))
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun deriveKey(pin: String): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pin.toCharArray(), SALT.toByteArray(), ITERATIONS, 256)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    fun writeEncryptedBackup(context: Context, json: String, pin: String): File {
        val encrypted = encrypt(json, pin)
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "anbar_secure_backup_${System.currentTimeMillis()}.enc")
        FileOutputStream(file).use { it.write(encrypted.toByteArray(Charsets.UTF_8)) }
        return file
    }
}
