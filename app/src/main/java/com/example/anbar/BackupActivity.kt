package com.example.anbar

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.anbar.databinding.ActivityBackupBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBackupBinding
    private lateinit var repository: InventoryRepository
    private lateinit var driveHelper: DriveBackupHelper

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            val content = contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                ?: return@registerForActivityResult
            if (BackupCrypto.isEncrypted(content)) {
                askPassword("رمز فایل بکاپ") { pass ->
                    restoreFromContent(content, pass)
                }
            } else {
                restoreFromContent(content, null)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        binding = ActivityBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "بکاپ و شعبه‌ها"

        repository = InventoryRepository(this)
        driveHelper = DriveBackupHelper(this)
        refreshDriveStatus()

        binding.btnDriveSignIn.setOnClickListener {
            try {
                startActivityForResult(driveHelper.signInClient().signInIntent, DriveBackupHelper.REQUEST_SIGN_IN)
            } catch (e: Exception) {
                Toast.makeText(this, "سرویس گوگل در دسترس نیست: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        binding.btnDriveUpload.setOnClickListener { uploadToDrive() }
        binding.btnDriveRestore.setOnClickListener { restoreFromDrive() }
        binding.btnDriveSignOut.setOnClickListener {
            driveHelper.signOut {
                refreshDriveStatus()
                Toast.makeText(this, "قطع شد", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnExportBackup.setOnClickListener { startEncryptedBackup() }
        binding.btnImportBackup.setOnClickListener {
            importLauncher.launch("*/*")
        }
        binding.btnAddBranch.setOnClickListener { showAddBranch() }

        loadBranches()
        loadSavedBackups()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DriveBackupHelper.REQUEST_SIGN_IN) {
            val account = driveHelper.handleSignInResult(data)
            if (account != null) {
                driveHelper.setDriveBackupEnabled(true)
                Toast.makeText(this, "متصل شد: ${account.email}", Toast.LENGTH_LONG).show()
                refreshDriveStatus()
            } else {
                Toast.makeText(this, "ورود ناموفق بود", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshDriveStatus() {
        val email = driveHelper.savedEmail().ifBlank {
            driveHelper.getLastAccount()?.email.orEmpty()
        }
        binding.tvDriveStatus.text = if (email.isNotBlank()) {
            "متصل: $email"
        } else {
            "متصل نیست — برای بکاپ ابری وارد جیمیل شوید"
        }
        val on = email.isNotBlank()
        binding.btnDriveUpload.isEnabled = on
        binding.btnDriveRestore.isEnabled = on
    }

    private fun uploadToDrive() {
        val account = driveHelper.getLastAccount()
        if (account == null) {
            Toast.makeText(this, "اول با جیمیل وارد شوید", Toast.LENGTH_SHORT).show()
            return
        }
        askPassword("رمز بکاپ ابری (حداقل ۴ کاراکتر)", confirm = true) { password ->
            try {
                val json = repository.exportBackupJson()
                val encrypted = BackupCrypto.encrypt(json, password)
                // ذخیره محلی هم
                val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                File(backupDir(), "anbar_backup_$date.anbar").writeText(encrypted, Charsets.UTF_8)
                loadSavedBackups()
                binding.tvBackupStatus.text = "در حال آپلود به Drive…"
                driveHelper.uploadBackup(account, encrypted) { ok, msg ->
                    binding.tvBackupStatus.text = msg
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun restoreFromDrive() {
        val account = driveHelper.getLastAccount()
        if (account == null) {
            Toast.makeText(this, "اول با همان جیمیل وارد شوید", Toast.LENGTH_SHORT).show()
            startActivityForResult(driveHelper.signInClient().signInIntent, DriveBackupHelper.REQUEST_SIGN_IN)
            return
        }
        binding.tvBackupStatus.text = "در حال دریافت از Drive…"
        driveHelper.downloadLatest(account) { ok, contentOrMsg ->
            if (!ok) {
                binding.tvBackupStatus.text = contentOrMsg
                Toast.makeText(this, contentOrMsg, Toast.LENGTH_LONG).show()
                return@downloadLatest
            }
            if (BackupCrypto.isEncrypted(contentOrMsg)) {
                askPassword("رمز بکاپ ابری را وارد کنید") { pass ->
                    restoreFromContent(contentOrMsg, pass)
                }
            } else {
                restoreFromContent(contentOrMsg, null)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /** پوشه بکاپ‌های داخل برنامه */
    private fun backupDir(): File {
        val dir = File(getExternalFilesDir(null), "backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun startEncryptedBackup() {
        askPassword("رمز بکاپ (حداقل ۴ کاراکتر)", confirm = true) { password ->
            try {
                val json = repository.exportBackupJson()
                val encrypted = BackupCrypto.encrypt(json, password)
                val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val file = File(backupDir(), "anbar_backup_$date.anbar")
                file.writeText(encrypted, Charsets.UTF_8)

                binding.tvBackupStatus.text = "ذخیره شد: ${file.name}"
                Toast.makeText(this, "بکاپ با رمز ذخیره شد", Toast.LENGTH_SHORT).show()
                loadSavedBackups()

                // امکان اشتراک به Drive / پیام‌رسان
                AlertDialog.Builder(this)
                    .setTitle("بکاپ آماده است")
                    .setMessage("در برنامه ذخیره شد.\nمی‌خواهید فایل را هم به Drive یا جای دیگر بفرستید؟")
                    .setPositiveButton("اشتراک‌گذاری") { _, _ -> shareFile(file) }
                    .setNegativeButton("بعداً", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun shareFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "بکاپ آپ انبار")
                putExtra(Intent.EXTRA_TEXT, "فایل بکاپ رمزنگاری‌شده آپ انبار")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, "ارسال بکاپ به"))
        } catch (e: Exception) {
            Toast.makeText(this, "اشتراک: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSavedBackups() {
        binding.layoutSavedBackups.removeAllViews()
        val files = backupDir().listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".anbar") || it.name.endsWith(".json")) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        if (files.isEmpty()) {
            binding.tvNoBackups.visibility = View.VISIBLE
            return
        }
        binding.tvNoBackups.visibility = View.GONE

        files.forEach { file ->
            val card = MaterialCardView(this).apply {
                radius = 12f
                cardElevation = 2f
                useCompatPadding = true
                setCardBackgroundColor(Color.WHITE)
            }
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(28, 24, 28, 24)
            }
            val sizeKb = file.length() / 1024.0
            val dateStr = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
                .format(Date(file.lastModified()))
            box.addView(TextView(this).apply {
                text = file.name
                textSize = 14f
                setTextColor(Color.parseColor("#1A1A2E"))
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            box.addView(TextView(this).apply {
                text = "$dateStr  ·  ${String.format(Locale.US, "%.1f", sizeKb)} KB"
                textSize = 12f
                setTextColor(Color.GRAY)
            })
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 12, 0, 0)
            }
            val btnRestore = com.google.android.material.button.MaterialButton(this).apply {
                text = "بازیابی"
                textSize = 12f
                setOnClickListener { restoreFromFile(file) }
            }
            val btnShare = com.google.android.material.button.MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = "ارسال"
                textSize = 12f
                setOnClickListener { shareFile(file) }
            }
            val btnDelete = com.google.android.material.button.MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = "حذف"
                textSize = 12f
                setTextColor(Color.RED)
                setOnClickListener {
                    AlertDialog.Builder(this@BackupActivity)
                        .setTitle("حذف بکاپ؟")
                        .setMessage(file.name)
                        .setPositiveButton("حذف") { _, _ ->
                            file.delete()
                            loadSavedBackups()
                        }
                        .setNegativeButton("لغو", null)
                        .show()
                }
            }
            row.addView(btnRestore)
            row.addView(btnShare)
            row.addView(btnDelete)
            box.addView(row)
            card.addView(box)
            binding.layoutSavedBackups.addView(card)
        }
    }

    private fun restoreFromFile(file: File) {
        try {
            val content = file.readText(Charsets.UTF_8)
            if (BackupCrypto.isEncrypted(content)) {
                askPassword("رمز این بکاپ را وارد کنید") { pass ->
                    restoreFromContent(content, pass)
                }
            } else {
                AlertDialog.Builder(this)
                    .setTitle("بازیابی؟")
                    .setMessage("داده‌های فعلی با این بکاپ جایگزین می‌شوند.")
                    .setPositiveButton("بازیابی") { _, _ -> restoreFromContent(content, null) }
                    .setNegativeButton("لغو", null)
                    .show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun restoreFromContent(content: String, password: String?) {
        try {
            val json = if (password != null) {
                BackupCrypto.decrypt(content, password)
            } else {
                content
            }
            if (repository.importBackupJson(json)) {
                binding.tvBackupStatus.text = "بازیابی موفق. یک‌بار برنامه را ببندید و دوباره باز کنید."
                Toast.makeText(this, getString(R.string.backup_ok), Toast.LENGTH_LONG).show()
                loadBranches()
            } else {
                Toast.makeText(this, getString(R.string.invalid_file), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "رمز اشتباه یا فایل خراب است", Toast.LENGTH_LONG).show()
        }
    }

    private fun askPassword(
        title: String,
        confirm: Boolean = false,
        onOk: (String) -> Unit
    ) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val et = EditText(this).apply {
            hint = "رمز"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(et)
        val et2 = if (confirm) {
            EditText(this).apply {
                hint = "تکرار رمز"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }.also { layout.addView(it) }
        } else null

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton("تأیید") { _, _ ->
                val p1 = et.text?.toString().orEmpty()
                if (p1.length < 4) {
                    Toast.makeText(this, "رمز حداقل ۴ کاراکتر", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (et2 != null && p1 != et2.text?.toString()) {
                    Toast.makeText(this, "رمز و تکرار یکی نیستند", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                onOk(p1)
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun loadBranches() {
        binding.layoutBranches.removeAllViews()
        repository.getBranches().forEach { b ->
            val whName = repository.getWarehouses().find { it.id == b.warehouseId }?.name ?: "-"
            val tv = TextView(this).apply {
                text = "${b.name}  →  انبار: $whName"
                textSize = 14f
                setPadding(8, 12, 8, 12)
                setTextColor(Color.parseColor("#1A1A2E"))
            }
            binding.layoutBranches.addView(tv)
        }
    }

    private fun showAddBranch() {
        val warehouses = repository.getWarehouses()
        if (warehouses.isEmpty()) {
            Toast.makeText(this, getString(R.string.create_warehouse_first), Toast.LENGTH_SHORT).show()
            return
        }
        val et = EditText(this).apply {
            hint = getString(R.string.branch_name)
            setPadding(40, 30, 40, 30)
        }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@BackupActivity,
                android.R.layout.simple_spinner_dropdown_item,
                warehouses.map { it.name }
            )
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
            addView(et)
            addView(TextView(this@BackupActivity).apply {
                text = getString(R.string.related_warehouse)
                setPadding(0, 16, 0, 4)
            })
            addView(spinner)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.branch_new))
            .setView(layout)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = et.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) return@setPositiveButton
                val wh = warehouses[spinner.selectedItemPosition]
                repository.addBranch(name, wh.id)
                Toast.makeText(this, getString(R.string.branch_added), Toast.LENGTH_SHORT).show()
                loadBranches()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
