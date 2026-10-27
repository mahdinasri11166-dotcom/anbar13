package com.example.anbar

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.anbar.databinding.ActivitySecurityBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SecurityActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySecurityBinding
    private lateinit var repository: InventoryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySecurityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.security_title)

        repository = InventoryRepository(this)

        binding.switchBiometric.isChecked = repository.isBiometricEnabled()
        binding.switchBiometric.setOnCheckedChangeListener { _, checked ->
            repository.setBiometricEnabled(checked)
            repository.logActivity("SETTINGS", "بیومتریک: $checked")
            Toast.makeText(
                this,
                if (checked) "ورود بیومتریک فعال شد" else "غیرفعال شد",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.switchAutoBackup.isChecked = repository.isAutoBackupEnabled()
        binding.switchAutoBackup.setOnCheckedChangeListener { _, checked ->
            repository.setAutoBackupEnabled(checked)
            if (checked) {
                AutoBackupWorker.schedule(this)
                Toast.makeText(this, "بکاپ روزانه فعال شد", Toast.LENGTH_SHORT).show()
            } else {
                AutoBackupWorker.cancel(this)
                Toast.makeText(this, "بکاپ روزانه غیرفعال شد", Toast.LENGTH_SHORT).show()
            }
            repository.logActivity("SETTINGS", "بکاپ خودکار: $checked")
        }

        val last = repository.getLastAutoBackupPath()
        binding.tvLastBackup.text = if (last.isNotBlank()) "آخرین بکاپ: $last" else "هنوز بکاپ خودکاری گرفته نشده"

        binding.btnChangePin.setOnClickListener { showChangePin() }
        binding.btnEncryptedBackup.setOnClickListener { exportEncryptedBackup() }
        loadLogs()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun showChangePin() {
        val etOld = EditText(this).apply {
            hint = "PIN فعلی"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(32, 24, 32, 12)
        }
        val etNew = EditText(this).apply {
            hint = "PIN جدید (حداقل ۴ رقم)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(32, 12, 32, 24)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 0)
            addView(etOld)
            addView(etNew)
        }
        AlertDialog.Builder(this)
            .setTitle("تغییر PIN مدیر")
            .setView(layout)
            .setPositiveButton("ذخیره") { _, _ ->
                val ok = repository.changeAdminPin(etOld.text.toString(), etNew.text.toString())
                Toast.makeText(
                    this,
                    if (ok) "PIN تغییر کرد" else "PIN فعلی اشتباه است یا PIN جدید کوتاه است",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun exportEncryptedBackup() {
        val et = EditText(this).apply {
            hint = "PIN برای رمزنگاری بکاپ"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(40, 30, 40, 30)
        }
        AlertDialog.Builder(this)
            .setTitle("بکاپ رمزنگاری‌شده")
            .setMessage("فایل با PIN شما رمز می‌شود. همان PIN برای بازیابی لازم است.")
            .setView(et)
            .setPositiveButton("ساخت") { _, _ ->
                val pin = et.text.toString()
                if (pin.length < 4) {
                    Toast.makeText(this, "PIN حداقل ۴ رقم", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                try {
                    val json = repository.exportBackupJson()
                    val file = SecurityHelper.writeEncryptedBackup(this, json, pin)
                    val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "application/octet-stream"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "اشتراک بکاپ رمزنگاری‌شده"))
                    repository.logActivity("BACKUP_ENCRYPTED", file.name)
                } catch (e: Exception) {
                    Toast.makeText(this, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun loadLogs() {
        binding.layoutLogs.removeAllViews()
        val logs = repository.getActivityLogs(50)
        val df = SimpleDateFormat("MM/dd HH:mm", Locale.US)
        if (logs.isEmpty()) {
            binding.layoutLogs.addView(TextView(this).apply {
                text = "لاگی ثبت نشده"
                setPadding(8, 8, 8, 8)
                setTextColor(0xFF757575.toInt())
            })
            return
        }
        logs.forEach { log ->
            val tv = TextView(this).apply {
                text = "${df.format(Date(log.timestamp))}  ·  ${log.username}\n${log.action}: ${log.detail}"
                textSize = 12f
                setPadding(12, 8, 12, 8)
                setBackgroundColor(0xFFFFFFFF.toInt())
            }
            val card = com.google.android.material.card.MaterialCardView(this).apply {
                radius = 8f
                cardElevation = 1f
                useCompatPadding = true
                addView(tv)
            }
            binding.layoutLogs.addView(card)
        }
    }
}
