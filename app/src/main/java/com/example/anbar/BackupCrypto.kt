package com.example.anbar

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * رمزنگاری بکاپ با رمز کاربر (AES-256)
 * فرمت فایل: ANBAR1:<base64(iv+cipher)>
 */
object BackupCrypto {

    private const val PREFIX = "ANBAR1:"

    fun isEncrypted(content: String): Boolean = content.trimStart().startsWith(PREFIX)

    fun encrypt(plainJson: String, password: String): String {
        require(password.length >= 4) { "رمز حداقل ۴ کاراکتر" }
        val key = keyFromPassword(password)
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        val encrypted = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        return PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(content: String, password: String): String {
        require(isEncrypted(content)) { "فایل رمزنگاری‌شده نیست" }
        val raw = Base64.decode(content.trim().removePrefix(PREFIX), Base64.NO_WRAP)
        require(raw.size > 16) { "فایل خراب است" }
        val iv = raw.copyOfRange(0, 16)
        val data = raw.copyOfRange(16, raw.size)
        val key = keyFromPassword(password)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    private fun keyFromPassword(password: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(password.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }
}
