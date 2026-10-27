package com.example.anbar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * بکاپ روی Google Drive (پوشه مخفی appDataFolder)
 * ورود با حساب گوگل (OAuth) — نه ایمیل/رمز دستی
 */
class DriveBackupHelper(private val context: Context) {

    companion object {
        const val REQUEST_SIGN_IN = 9001
        private const val TAG = "DriveBackup"
        private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val FILE_NAME = "anbar_latest.anbar"
        private const val PREFS = "drive_backup_prefs"
    }

    private val executor = Executors.newSingleThreadExecutor()

    fun signInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_SCOPE))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun getLastAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    fun isSignedIn(): Boolean = getLastAccount() != null

    fun savedEmail(): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("email", "") ?: ""

    fun setDriveBackupEnabled(v: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", v).apply()
    }

    fun isDriveBackupEnabled(): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("enabled", false)

    fun handleSignInResult(data: Intent?): GoogleSignInAccount? {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return try {
            val account = task.result
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString("email", account.email ?: "").apply()
            account
        } catch (e: Exception) {
            Log.e(TAG, "sign-in failed", e)
            null
        }
    }

    fun signOut(done: () -> Unit) {
        signInClient().signOut().addOnCompleteListener {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove("email").putBoolean("enabled", false).apply()
            done()
        }
    }

    /**
     * آپلود محتوای رمزنگاری‌شده به appDataFolder
     */
    fun uploadBackup(
        account: GoogleSignInAccount,
        encryptedContent: String,
        onResult: (Boolean, String) -> Unit
    ) {
        executor.execute {
            try {
                val acc = account.account
                if (acc == null) {
                    post(onResult, false, "حساب نامعتبر. دوباره وارد شوید.")
                    return@execute
                }
                val token = getToken(acc)
                    ?: run {
                        post(onResult, false, "توکن دریافت نشد. دوباره وارد شوید.")
                        return@execute
                    }
                // حذف فایل قبلی هم‌نام
                deleteExisting(token, FILE_NAME)
                // آپلود جدید (multipart)
                val boundary = "anbar${System.currentTimeMillis()}"
                val meta = """{"name":"$FILE_NAME","parents":["appDataFolder"]}"""
                val body = buildString {
                    append("--$boundary\r\n")
                    append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                    append(meta)
                    append("\r\n--$boundary\r\n")
                    append("Content-Type: application/octet-stream\r\n\r\n")
                    append(encryptedContent)
                    append("\r\n--$boundary--\r\n")
                }
                val url = URL(
                    "https://www.googleapis.com/upload/drive/v3/files" +
                        "?uploadType=multipart&fields=id,name"
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
                val code = conn.responseCode
                val resp = readBody(conn)
                if (code in 200..299) {
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putLong("last_upload", System.currentTimeMillis()).apply()
                    post(onResult, true, "روی Drive ذخیره شد")
                } else {
                    Log.e(TAG, "upload $code $resp")
                    post(onResult, false, "خطای Drive: $code")
                }
            } catch (e: Exception) {
                Log.e(TAG, "upload", e)
                post(onResult, false, e.message ?: "خطای آپلود")
            }
        }
    }

    /**
     * دانلود آخرین بکاپ از appDataFolder
     */
    fun downloadLatest(
        account: GoogleSignInAccount,
        onResult: (Boolean, String) -> Unit
    ) {
        executor.execute {
            try {
                val acc = account.account
                if (acc == null) {
                    post(onResult, false, "حساب نامعتبر")
                    return@execute
                }
                val token = getToken(acc)
                    ?: run {
                        post(onResult, false, "توکن دریافت نشد")
                        return@execute
                    }
                val q = URLEncoder.encode(
                    "name='$FILE_NAME' and 'appDataFolder' in parents and trashed=false",
                    "UTF-8"
                )
                val listUrl = URL(
                    "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$q&fields=files(id,name,modifiedTime)&orderBy=modifiedTime desc"
                )
                val listConn = (listUrl.openConnection() as HttpURLConnection).apply {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                val listCode = listConn.responseCode
                val listBody = readBody(listConn)
                if (listCode !in 200..299) {
                    post(onResult, false, "لیست فایل: $listCode")
                    return@execute
                }
                val files = JSONObject(listBody).optJSONArray("files")
                if (files == null || files.length() == 0) {
                    post(onResult, false, "بکاپی روی این جیمیل پیدا نشد")
                    return@execute
                }
                val fileId = files.getJSONObject(0).getString("id")
                val dlUrl = URL("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                val dlConn = (dlUrl.openConnection() as HttpURLConnection).apply {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                if (dlConn.responseCode !in 200..299) {
                    post(onResult, false, "دانلود ناموفق: ${dlConn.responseCode}")
                    return@execute
                }
                val content = readBody(dlConn)
                post(onResult, true, content)
            } catch (e: Exception) {
                Log.e(TAG, "download", e)
                post(onResult, false, e.message ?: "خطای دانلود")
            }
        }
    }

    private fun deleteExisting(token: String, name: String) {
        try {
            val q = URLEncoder.encode(
                "name='$name' and 'appDataFolder' in parents and trashed=false",
                "UTF-8"
            )
            val listUrl = URL(
                "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$q&fields=files(id)"
            )
            val listConn = (listUrl.openConnection() as HttpURLConnection).apply {
                setRequestProperty("Authorization", "Bearer $token")
            }
            val body = readBody(listConn)
            val files = JSONObject(body).optJSONArray("files") ?: return
            for (i in 0 until files.length()) {
                val id = files.getJSONObject(i).getString("id")
                val del = URL("https://www.googleapis.com/drive/v3/files/$id")
                (del.openConnection() as HttpURLConnection).apply {
                    requestMethod = "DELETE"
                    setRequestProperty("Authorization", "Bearer $token")
                    responseCode
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "deleteExisting", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun getToken(account: android.accounts.Account): String? {
        return try {
            com.google.android.gms.auth.GoogleAuthUtil.getToken(
                context,
                account,
                "oauth2:$DRIVE_SCOPE"
            )
        } catch (e: Exception) {
            Log.e(TAG, "getToken", e)
            null
        }
    }

    private fun readBody(conn: HttpURLConnection): String {
        val stream = try {
            if (conn.responseCode >= 400) conn.errorStream else conn.inputStream
        } catch (_: Exception) {
            conn.inputStream
        } ?: return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun post(cb: (Boolean, String) -> Unit, ok: Boolean, msg: String) {
        if (context is Activity) {
            (context as Activity).runOnUiThread { cb(ok, msg) }
        } else {
            cb(ok, msg)
        }
    }
}
