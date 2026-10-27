package com.example.anbar

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.anbar.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var repository: InventoryRepository
    private var applyingLive = false
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            try {
                binding.tvAppDate.text = "تاریخ: ${FormatUtils.date(TimeHelper.now())}"
                binding.tvAppClock.text = FormatUtils.time(TimeHelper.now())
            } catch (_: Exception) { }
            clockHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)

        repository = InventoryRepository(this)
        val settings = repository.getSettings()

        binding.etShopName.setText(settings.shopName)
        try {
            binding.etShopAddress.setText(settings.shopAddress)
            binding.etShopPhone.setText(settings.shopPhone)
        } catch (_: Exception) { }
        val currencies = listOf("TOMAN — تومان", "RIAL — ریال", "USD — دلار", "USDT — تتر")
        binding.spinnerCurrency.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, currencies)
        val curIdx = when {
            settings.currency.uppercase().contains("USDT") || settings.currency.contains("تتر") -> 3
            settings.currency.uppercase().contains("USD") || settings.currency.contains("دلار") -> 2
            settings.currency.uppercase().contains("RIAL") || settings.currency.contains("ریال") -> 1
            else -> 0
        }
        binding.spinnerCurrency.setSelection(curIdx)

        val calTypes = listOf("شمسی (ایرانی)", "میلادی")
        binding.spinnerCalendar.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, calTypes)
        binding.spinnerCalendar.setSelection(
            if (settings.calendarType.uppercase().contains("GREG")) 1 else 0
        )
        binding.etRateUsd.setText(settings.rateUsdToToman.toLong().toString())
        binding.etRateUsdt.setText(settings.rateUsdtToToman.toLong().toString())
        refreshDateTimeUi(synced = null)
        binding.btnSyncClock.setOnClickListener {
            binding.tvClockStatus.text = "در حال همگام‌سازی…"
            TimeHelper.syncOnline(this) { ok ->
                refreshDateTimeUi(synced = ok)
                Toast.makeText(
                    this,
                    if (ok) "ساعت با اینترنت همگام شد" else "آفلاین — از ساعت دستگاه استفاده می‌شود",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        // همگام‌سازی اولیه
        TimeHelper.syncOnline(this) { ok -> refreshDateTimeUi(synced = ok) }
        binding.etVatDefault.setText(settings.vatPercent.toString())
        binding.etPointsRate.setText(repository.getPointsPerAmount().toLong().toString())
        binding.switchSms.isChecked = repository.isSmsEnabled()
        try {
            binding.switchCashierOnly.isChecked = settings.cashierOnlyMode
            binding.etAutoLogout.setText(settings.autoLogoutMinutes.toString())
            binding.switchInvoicePoetry.isChecked = settings.invoiceShowPoetry
            binding.etInvoiceExtra.setText(settings.invoiceExtraNote)
        } catch (_: Exception) {}

        applyingLive = true
        when (LocaleHelper.getLanguage(this)) {
            "en" -> binding.rbEn.isChecked = true
            "ar" -> binding.rbAr.isChecked = true
            else -> binding.rbFa.isChecked = true
        }
        when (LocaleHelper.getThemeMode(this)) {
            "light" -> binding.rbThemeLight.isChecked = true
            "dark" -> binding.rbThemeDark.isChecked = true
            else -> binding.rbThemeSystem.isChecked = true
        }
        applyingLive = false

        // تغییر زبان در لحظه — بدون بستن دستی برنامه
        binding.rgLanguage.setOnCheckedChangeListener { _, checkedId ->
            if (applyingLive) return@setOnCheckedChangeListener
            val lang = when (checkedId) {
                R.id.rbEn -> "en"
                R.id.rbAr -> "ar"
                else -> "fa"
            }
            if (lang != LocaleHelper.getLanguage(this)) {
                LocaleHelper.setLanguage(this, lang)
                recreate()
            }
        }

        // تغییر تم در لحظه
        binding.rgTheme.setOnCheckedChangeListener { _, checkedId ->
            if (applyingLive) return@setOnCheckedChangeListener
            val mode = when (checkedId) {
                R.id.rbThemeLight -> "light"
                R.id.rbThemeDark -> "dark"
                else -> "system"
            }
            if (mode != LocaleHelper.getThemeMode(this)) {
                LocaleHelper.setThemeMode(this, mode)
                // اعمال فوری ظاهر (مثل تغییر زبان)
                recreate()
            }
        }

        binding.btnSaveSettings.setOnClickListener { save() }
        binding.btnAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    private fun refreshDateTimeUi(synced: Boolean?) {
        binding.tvAppDate.text = "تاریخ: ${FormatUtils.date(TimeHelper.now())}"
        binding.tvAppClock.text = FormatUtils.time(TimeHelper.now())
        binding.tvClockStatus.text = when (synced) {
            true -> "وضعیت: ✓ همگام با اینترنت"
            false -> "وضعیت: ساعت دستگاه (آفلاین)"
            null -> "وضعیت: در حال بررسی…"
        }
        val last = repository.getLastChangeAt()
        binding.tvLastChange.text = if (last > 0)
            "آخرین تغییر ثبت‌شده: ${FormatUtils.dateTime(last)}"
        else
            "آخرین تغییر ثبت‌شده: هنوز تغییری نبوده"
    }

    override fun onResume() {
        super.onResume()
        clockHandler.removeCallbacks(clockTick)
        clockHandler.post(clockTick)
        refreshDateTimeUi(synced = null)
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockTick)
    }

    private fun save() {
        val name = binding.etShopName.text?.toString()?.trim().orEmpty().ifEmpty { "فروشگاه من" }
        val vat = binding.etVatDefault.text?.toString()?.toDoubleOrNull() ?: 9.0
        val pointsRate = binding.etPointsRate.text?.toString()?.toDoubleOrNull() ?: 10000.0
        val current = repository.getSettings()
        val curCode = when (binding.spinnerCurrency.selectedItemPosition) {
            1 -> "RIAL"
            2 -> "USD"
            3 -> "USDT"
            else -> "TOMAN"
        }
        val rateUsd = binding.etRateUsd.text?.toString()?.toDoubleOrNull() ?: 900000.0
        val rateUsdt = binding.etRateUsdt.text?.toString()?.toDoubleOrNull() ?: 900000.0
        val calType = if (binding.spinnerCalendar.selectedItemPosition == 1) "GREGORIAN" else "SHAMSI"
        repository.saveSettings(
            current.copy(
                shopName = name,
                vatPercent = vat,
                shopAddress = try { binding.etShopAddress.text?.toString()?.trim().orEmpty() } catch (_: Exception) { "" },
                shopPhone = try { binding.etShopPhone.text?.toString()?.trim().orEmpty() } catch (_: Exception) { "" },
                currency = curCode,
                rateUsdToToman = rateUsd,
                rateUsdtToToman = rateUsdt,
                calendarType = calType,
                cashierOnlyMode = try { binding.switchCashierOnly.isChecked } catch (_: Exception) { false },
                autoLogoutMinutes = try { binding.etAutoLogout.text?.toString()?.toIntOrNull() ?: 0 } catch (_: Exception) { 0 },
                invoiceShowPoetry = try { binding.switchInvoicePoetry.isChecked } catch (_: Exception) { true },
                invoiceExtraNote = try { binding.etInvoiceExtra.text?.toString()?.trim().orEmpty() } catch (_: Exception) { "" }
            )
        )
        repository.setPointsPerAmount(pointsRate)
        repository.setSmsEnabled(binding.switchSms.isChecked)

        val lang = when {
            binding.rbEn.isChecked -> "en"
            binding.rbAr.isChecked -> "ar"
            else -> "fa"
        }
        val themeMode = when {
            binding.rbThemeLight.isChecked -> "light"
            binding.rbThemeDark.isChecked -> "dark"
            else -> "system"
        }
        LocaleHelper.setLanguage(this, lang)
        LocaleHelper.setThemeMode(this, themeMode)

        Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()
        Toast.makeText(this, getString(R.string.lang_applied), Toast.LENGTH_SHORT).show()
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
