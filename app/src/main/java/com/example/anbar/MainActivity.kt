package com.example.anbar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.anbar.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip

/**
 * دو حالت: انبار (ورود/خروج/تحویل/قیمت) و فروشگاه (POS/مشتری/فروش)
 * اسکن در هر دو حالت فعال است.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_MODE = "app_ui_mode"
        private const val MODE_WAREHOUSE = "warehouse"
        private const val MODE_STORE = "store"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: InventoryRepository
    private lateinit var adapter: ProductAdapter
    private var selectedWarehouseId: Long? = null
    private var currentMode: String = MODE_WAREHOUSE

    private data class Module(
        val title: String,
        val emoji: String,
        val color: String,
        val action: () -> Unit
    )

    private data class DrawerItem(
        val title: String,
        val emoji: String,
        val action: () -> Unit
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            LocaleHelper.applyLocaleToActivity(this)
        } catch (_: Exception) {
        }
        super.onCreate(savedInstanceState)
        if (!getSharedPreferences("kalano_onboard", MODE_PRIVATE).getBoolean("done", false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "کالانو"

        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.app_name, R.string.app_name
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.toolbar.navigationIcon?.setTint(Color.WHITE)

        repository = InventoryRepository(this)
        currentMode = getSharedPreferences(PREFS_MODE, MODE_PRIVATE)
            .getString("mode", MODE_WAREHOUSE) ?: MODE_WAREHOUSE

        if (repository.isLoginRequired() && repository.getCurrentUser() == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        try {
            adapter = ProductAdapter(emptyList()) { row -> showItemOptions(row) }
            binding.recyclerView.layoutManager = LinearLayoutManager(this)
            binding.recyclerView.adapter = adapter
            repository.getSettings() // ارز
            TimeHelper.syncOnline(this)
            applyBranding()
            applyMode(currentMode)
            setupWarehouseChips()
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در بارگذاری: ${e.message}", Toast.LENGTH_LONG).show()
        }

        
        // نوتیفیکیشن روزانه انقضا/کمبود و گزارش شبانه
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2001
                )
            }
        }

        binding.btnModeWarehouse.setOnClickListener {
            applyMode(MODE_WAREHOUSE)
            closeDrawer()
        }
        binding.btnModeStore.setOnClickListener {
            applyMode(MODE_STORE)
            closeDrawer()
        }

        binding.btnScan.setOnClickListener {
            startActivity(Intent(this, BarcodeScanActivity::class.java))
        }
        binding.btnAddProduct.setOnClickListener {
            startActivity(Intent(this, AddProductActivity::class.java))
        }
        binding.btnMore.setOnClickListener {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) closeDrawer()
            else binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        binding.btnEntry.setOnClickListener { onPrimaryAction() }
        binding.btnExit.setOnClickListener { onSecondaryAction() }
    }

    override fun onResume() {
        super.onResume()
        try {
            val s = InventoryRepository(this).getSettings()
            SessionTimeout.configure(s.autoLogoutMinutes)
            SessionTimeout.touch(this)
            if (s.cashierOnlyMode) {
                // محدودسازی به حالت فروشگاه
                if (currentMode != MODE_STORE) applyMode(MODE_STORE)
            }
        } catch (_: Exception) {}
        try {
            LocaleHelper.applyLocaleToActivity(this)
            repository.getSettings() // ارز
            TimeHelper.syncOnline(this)
            applyBranding()
            applyMode(currentMode)
            loadInventory()
            refreshKpiStrip()
            setupWarehouseChips()
        } catch (e: Exception) {
            try {
                Toast.makeText(this, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            closeDrawer()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun closeDrawer() {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun applyMode(mode: String) {
        currentMode = mode
        getSharedPreferences(PREFS_MODE, MODE_PRIVATE).edit().putString("mode", mode).apply()
        val isWh = mode == MODE_WAREHOUSE

        val clock = TimeHelper.dateTime()
        binding.tvModeBadge.text = if (isWh)
            "${getString(R.string.mode_warehouse)}  ·  $clock"
        else
            "${getString(R.string.mode_store)}  ·  $clock"

        binding.tvModulesTitle.text =
            if (isWh) getString(R.string.shortcuts_warehouse) else getString(R.string.shortcuts_store)

        styleModeButton(binding.btnModeWarehouse, selected = isWh)
        styleModeButton(binding.btnModeStore, selected = !isWh)

        if (isWh) {
            binding.btnEntry.text = "⬇️\nورود"
            binding.btnExit.text = "⬆️\nخروج"
        } else {
            binding.btnEntry.text = "💳\nفروش"
            binding.btnExit.text = "👥\nمشتری"
        }
        binding.btnAddProduct.text = "＋ کالا"

        setupModules()
        // راهنمای کوتاه یک‌بار
        val tipPrefs = getSharedPreferences("kalano_onboard", MODE_PRIVATE)
        if (!tipPrefs.getBoolean("tip_shown", false)) {
            tipPrefs.edit().putBoolean("tip_shown", true).apply()
            android.os.Handler(mainLooper).postDelayed({
                try {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("راهنمای سریع")
                        .setMessage("• بالای صفحه بین «انبار» و «فروشگاه» جابه‌جا شوید\n• برای فروش: فروشگاه ← صندوق فروش\n• برای ورود کالا: انبار ← ورود به انبار\n• جستجو همه چیز را یکجا پیدا می‌کند")
                        .setPositiveButton("باشه", null)
                        .show()
                } catch (_: Exception) {}
            }, 600)
        }
        setupDrawerItems()
    }

    private fun styleModeButton(btn: MaterialButton, selected: Boolean) {
        val primary = androidx.core.content.ContextCompat.getColor(this, R.color.primary)
        val white = androidx.core.content.ContextCompat.getColor(this, R.color.white)
        val soft = androidx.core.content.ContextCompat.getColor(this, R.color.primary_soft)
        val textSec = androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary)
        if (selected) {
            btn.setBackgroundColor(primary)
            btn.setTextColor(white)
            btn.strokeWidth = 0
        } else {
            btn.setBackgroundColor(soft)
            btn.setTextColor(textSec)
            btn.strokeWidth = 0
        }
        btn.cornerRadius = (14 * resources.displayMetrics.density).toInt()
    }

    private fun onPrimaryAction() {
        if (currentMode == MODE_WAREHOUSE) /*mode tip*/ openTransaction(true)
        else startActivity(Intent(this, PosActivity::class.java))
    }

    private fun onSecondaryAction() {
        if (currentMode == MODE_WAREHOUSE) /*mode tip*/ openTransaction(false)
        else startActivity(Intent(this, CustomersActivity::class.java))
    }

    private fun applyBranding() {
        try {
            val settings = repository.getSettings()
            val color = Color.parseColor(settings.primaryColor)
            binding.toolbar.setBackgroundColor(color)
            binding.appBar.setBackgroundColor(color)
            window.statusBarColor = color
            val user = repository.getCurrentUser()
            val subtitle = user?.let { " · ${it.displayName}" } ?: ""
            supportActionBar?.title = settings.shopName + subtitle
            binding.tvDrawerSubtitle.text = settings.shopName
        } catch (_: Exception) {
            supportActionBar?.title = "کالانو"
        }
    }

    private fun warehouseModules(): List<Module> = listOf(
        // پرکاربردها اول
        Module("کالای جدید", "➕", "#FF6D00") {
            startActivity(Intent(this, AddProductActivity::class.java))
        },
        Module("ورود به انبار", "⬇️", "#2E7D32") { openTransaction(true) },
        Module("خروج از انبار", "⬆️", "#C62828") { openTransaction(false) },
        Module("اسکن بارکد", "📷", "#1565C0") {
            startActivity(Intent(this, BarcodeScanActivity::class.java))
        },
        Module("جستجو", "🔍", "#0F766E") {
            startActivity(Intent(this, GlobalSearchActivity::class.java))
        },
        Module("گزارش موجودی", "📊", "#4527A0") {
            startActivity(Intent(this, DashboardActivity::class.java))
        },
        Module("خرید از تأمین‌کننده", "🛒", "#6A1B9A") {
            startActivity(Intent(this, PurchaseActivity::class.java))
        },
        Module("تأمین‌کنندگان", "🚚", "#E65100") {
            startActivity(Intent(this, SuppliersActivity::class.java))
        },
        Module("انبارها", "🏭", "#5D4037") {
            startActivity(Intent(this, WarehouseActivity::class.java))
        },
        Module("انتقال بین انبار", "🔄", "#00838F") {
            startActivity(Intent(this, TransferActivity::class.java))
        },
        Module("شمارش موجودی", "🧮", "#0F766E") {
            startActivity(Intent(this, StockTakeActivity::class.java))
        },
        Module("پیشنهاد خرید", "📦", "#C2410C") {
            startActivity(Intent(this, ImportDataActivity::class.java))
        },
        Module("تحویل به همکار", "🤝", "#2E7D32") {
            startActivity(Intent(this, HandoverActivity::class.java))
        },
        Module("همکاران", "👤", "#00695C") {
            startActivity(Intent(this, ColleaguesActivity::class.java))
        },
        Module("مرکز فرمان", "🛰️", "#1E3A8A") {
            startActivity(Intent(this, HomeInsightsActivity::class.java))
        },
        Module("ورود از فایل", "📥", "#7C3AED") {
            startActivity(Intent(this, ImportDataActivity::class.java))
        },
        Module("پشتیبان‌گیری", "☁️", "#37474F") {
            startActivity(Intent(this, BackupActivity::class.java))
        },
    )

    private fun storeModules(): List<Module> = listOf(
        Module("صندوق فروش", "💳", "#1565C0") {
            startActivity(Intent(this, PosActivity::class.java))
        },
        Module("اسکن و فروش", "📷", "#00838F") {
            startActivity(Intent(this, BarcodeScanActivity::class.java))
        },
        Module("فاکتورها", "📋", "#0E7490") {
            startActivity(Intent(this, SalesListActivity::class.java))
        },
        Module("مشتریان", "👥", "#6A1B9A") {
            startActivity(Intent(this, CustomersActivity::class.java))
        },
        Module("جستجو", "🔍", "#0F766E") {
            startActivity(Intent(this, GlobalSearchActivity::class.java))
        },
        Module("شیفت صندوق", "🧾", "#C62828") {
            startActivity(Intent(this, CashShiftActivity::class.java))
        },
        Module("پیش‌فاکتور", "📄", "#2E7D32") {
            startActivity(Intent(this, ProformaActivity::class.java))
        },
        Module("سفارش ویزیتور", "🛵", "#E65100") {
            startActivity(Intent(this, VisitorOrderActivity::class.java))
        },
        Module("تحویل پیک", "📦", "#5D4037") {
            startActivity(Intent(this, DeliveryActivity::class.java))
        },
        Module("برگشت از فروش", "↩️", "#B91C1C") {
            startActivity(Intent(this, SalesReturnActivity::class.java))
        },
        Module("هزینه‌ها", "💸", "#AD1457") {
            startActivity(Intent(this, ExpensesActivity::class.java))
        },
        Module("ابزارهای مالی", "💰", "#00695C") {
            startActivity(Intent(this, CommercialActivity::class.java))
        },
        Module("گزارش‌ها", "📊", "#4527A0") {
            startActivity(Intent(this, DashboardActivity::class.java))
        },
        Module("گزارش پیشرفته", "📑", "#1D4ED8") {
            startActivity(Intent(this, AdvancedReportsActivity::class.java))
        },
        Module("مرکز فرمان", "🛰️", "#1E3A8A") {
            startActivity(Intent(this, HomeInsightsActivity::class.java))
        },
        Module("خروجی فایل", "📤", "#37474F") {
            startActivity(Intent(this, ExportActivity::class.java))
        },
        Module("تنظیمات", "⚙️", "#546E7A") {
            startActivity(Intent(this, SettingsActivity::class.java))
        },
    )

    private fun setupModules()
        // راهنمای کوتاه یک‌بار
        val tipPrefs = getSharedPreferences("kalano_onboard", MODE_PRIVATE)
        if (!tipPrefs.getBoolean("tip_shown", false)) {
            tipPrefs.edit().putBoolean("tip_shown", true).apply()
            android.os.Handler(mainLooper).postDelayed({
                try {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("راهنمای سریع")
                        .setMessage("• بالای صفحه بین «انبار» و «فروشگاه» جابه‌جا شوید\n• برای فروش: فروشگاه ← صندوق فروش\n• برای ورود کالا: انبار ← ورود به انبار\n• جستجو همه چیز را یکجا پیدا می‌کند")
                        .setPositiveButton("باشه", null)
                        .show()
                } catch (_: Exception) {}
            }, 600)
        } {
        val modules = if (currentMode == MODE_WAREHOUSE) /*mode tip*/ warehouseModules() else storeModules()
        binding.gridModules.removeAllViews()
        val density = resources.displayMetrics.density
        val cardBg = androidx.core.content.ContextCompat.getColor(this, R.color.card_bg)
        val textPri = androidx.core.content.ContextCompat.getColor(this, R.color.text_primary)
        val divider = androidx.core.content.ContextCompat.getColor(this, R.color.divider)
        modules.forEach { m ->
            val card = MaterialCardView(this).apply {
                radius = 22 * density
                cardElevation = 0f
                strokeWidth = (1.2f * density).toInt().coerceAtLeast(1)
                strokeColor = divider
                setCardBackgroundColor(cardBg)
                isClickable = true
                isFocusable = true
                rippleColor = android.content.res.ColorStateList.valueOf(0x332563EB)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(
                        (6 * density).toInt(), (6 * density).toInt(),
                        (6 * density).toInt(), (6 * density).toInt()
                    )
                }
            }
            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(
                    (12 * density).toInt(), (20 * density).toInt(),
                    (12 * density).toInt(), (16 * density).toInt()
                )
            }
            val softColor = try {
                Color.parseColor(m.color.replace("#", "#30"))
            } catch (_: Exception) {
                Color.parseColor("#302563EB")
            }
            val iconBg = TextView(this).apply {
                text = m.emoji
                textSize = 28f
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(softColor)
                }
                val sz = (56 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(sz, sz).apply { gravity = Gravity.CENTER }
            }
            val title = TextView(this).apply {
                text = m.title
                textSize = 12.5f
                setTextColor(textPri)
                gravity = Gravity.CENTER
                setPadding(0, (10 * density).toInt(), 0, 0)
                maxLines = 2
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            inner.addView(iconBg)
            inner.addView(title)
            card.addView(inner)
            card.setOnClickListener { m.action() }
            binding.gridModules.addView(card)
        }
    }

    private fun warehouseDrawerItems(): List<DrawerItem> = listOf(
        DrawerItem("اسکن بارکد / QR", "📷") {
            startActivity(Intent(this, BarcodeScanActivity::class.java))
        },
        DrawerItem("ورود کالا", "⬇️") { openTransaction(true) },
        DrawerItem("خروج کالا", "⬆️") { openTransaction(false) },
        DrawerItem("افزودن کالا", "➕") {
            startActivity(Intent(this, AddProductActivity::class.java))
        },
        DrawerItem("انتقال بین انبار", "🔄") {
            startActivity(Intent(this, TransferActivity::class.java))
        },
        DrawerItem("مدیریت انبارها", "🏭") {
            startActivity(Intent(this, WarehouseActivity::class.java))
        },
        DrawerItem("تأمین‌کنندگان", "🚚") {
            startActivity(Intent(this, SuppliersActivity::class.java))
        },
        DrawerItem("ثبت خرید از تأمین‌کننده", "🛒") {
            startActivity(Intent(this, PurchaseActivity::class.java))
        },
        DrawerItem("تحویل به همکار", "🤝") {
            startActivity(Intent(this, HandoverActivity::class.java))
        },
        DrawerItem("همکاران انبار", "👤") {
            startActivity(Intent(this, ColleaguesActivity::class.java))
        },
        DrawerItem("گزارش موجودی", "📊") {
            startActivity(Intent(this, DashboardActivity::class.java))
        },
        DrawerItem("بکاپ و بازیابی", "☁️") {
            startActivity(Intent(this, BackupActivity::class.java))
        },
        DrawerItem("امنیت", "🛡️") {
            startActivity(Intent(this, SecurityActivity::class.java))
        },
        DrawerItem("تنظیمات", "⚙️") {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    )

    private fun storeDrawerItems(): List<DrawerItem> = listOf(
        DrawerItem("اسکن فروش", "📷") { startActivity(Intent(this, BarcodeScanActivity::class.java)) },
        DrawerItem("صندوق فروش (POS)", "💳") { startActivity(Intent(this, PosActivity::class.java)) },
        DrawerItem("شیفت صندوق", "🧾") { startActivity(Intent(this, CashShiftActivity::class.java)) },
        DrawerItem("مشتریان", "👥") { startActivity(Intent(this, CustomersActivity::class.java)) },
        DrawerItem("پیش‌فاکتور", "📄") { startActivity(Intent(this, ProformaActivity::class.java)) },
        DrawerItem("سفارش ویزیتور", "🛵") { startActivity(Intent(this, VisitorOrderActivity::class.java)) },
        DrawerItem("هزینه‌ها و سود", "💸") { startActivity(Intent(this, ExpensesActivity::class.java)) },
        DrawerItem("تحویل پیک", "🛵") { startActivity(Intent(this, DeliveryActivity::class.java)) },
        DrawerItem("گزارش فروش", "📈") { startActivity(Intent(this, DashboardActivity::class.java)) },
        DrawerItem("ابزار مالی", "💼") { startActivity(Intent(this, CommercialActivity::class.java)) },
        DrawerItem("خروجی Excel/PDF", "📤") { startActivity(Intent(this, ExportActivity::class.java)) },
        DrawerItem("تنظیمات", "⚙️") { startActivity(Intent(this, SettingsActivity::class.java)) },
        DrawerItem("درباره کالانو", "ℹ️") { startActivity(Intent(this, AboutActivity::class.java)) }
    )

    private fun setupDrawerItems() {
        binding.layoutDrawerItems.removeAllViews()
        val items =
            if (currentMode == MODE_WAREHOUSE) /*mode tip*/ warehouseDrawerItems() else storeDrawerItems()
        val density = resources.displayMetrics.density
        items.forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    (16 * density).toInt(), (14 * density).toInt(),
                    (16 * density).toInt(), (14 * density).toInt()
                )
                setBackgroundResource(android.R.drawable.list_selector_background)
                isClickable = true
                isFocusable = true
            }
            row.addView(TextView(this).apply {
                text = item.emoji
                textSize = 18f
                setPadding(0, 0, (12 * density).toInt(), 0)
            })
            row.addView(TextView(this).apply {
                text = item.title
                textSize = 14f
                setTextColor(androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                layoutParams =
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.setOnClickListener {
                closeDrawer()
                item.action()
            }
            binding.layoutDrawerItems.addView(row)
            binding.layoutDrawerItems.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).apply { marginStart = (48 * density).toInt() }
                setBackgroundColor(Color.parseColor("#E2E8F0"))
            })
        }
    }

    private fun openTransaction(isEntry: Boolean) {
        startActivity(
            Intent(this, TransactionActivity::class.java).putExtra("is_entry", isEntry)
        )
    }

    private fun setupWarehouseChips() {
        binding.chipGroupWarehouse.removeAllViews()
        val allChip = Chip(this).apply {
            text = getString(R.string.all_warehouses)
            isCheckable = true
            isChecked = selectedWarehouseId == null
            setOnClickListener {
                selectedWarehouseId = null
                loadInventory()
            }
        }
        binding.chipGroupWarehouse.addView(allChip)
        repository.getWarehouses().forEach { w ->
            val chip = Chip(this).apply {
                text = w.name
                isCheckable = true
                isChecked = selectedWarehouseId == w.id
                setOnClickListener {
                    selectedWarehouseId = w.id
                    loadInventory()
                }
            }
            binding.chipGroupWarehouse.addView(chip)
        }
    }

    private fun loadInventory() {
        val rows = repository.getInventoryRows(selectedWarehouseId)
        adapter.updateList(rows)
            binding.emptyText.visibility = if (rows.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            binding.emptyText.text = if (currentMode == MODE_WAREHOUSE)
            "از «کالای جدید» شروع کنید، بعد «ورود به انبار» بزنید."
        else
            "از «صندوق فروش» فاکتور بزنید یا مشتری اضافه کنید."
        if (expiring > 0) {
            binding.cardExpiryAlert.visibility = View.VISIBLE
            binding.tvExpiryAlert.text = "⚠️ $expiring قلم نزدیک انقضا یا منقضی"
        } else {
            binding.cardExpiryAlert.visibility = View.GONE
        }
    }

    private fun refreshKpiStrip() {
        try {
            val kpi = repository.getDailyKpi()
            binding.tvStripSales.text = FormatUtils.money(kpi.todaySalesTotal)
            binding.tvStripStock.text = repository.getInventoryRows().size.toString()
        } catch (_: Exception) {
        }
    }

    private fun showItemOptions(row: InventoryRow) {
        val options = if (currentMode == MODE_WAREHOUSE) /*mode tip*/ {
            arrayOf(
                getString(R.string.stock_in),
                getString(R.string.stock_out),
                getString(R.string.transfer),
                getString(R.string.delete)
            )
        } else {
            arrayOf(
                "فروش سریع",
                getString(R.string.stock_in),
                getString(R.string.stock_out),
                getString(R.string.delete)
            )
        }
        AlertDialog.Builder(this)
            .setTitle(row.productName + if (row.variantName.isNotBlank()) " (${row.variantName})" else "")
            .setItems(options) { _, which ->
                if (currentMode == MODE_STORE && which == 0) {
                    startActivity(Intent(this, PosActivity::class.java))
                    return@setItems
                }
                val w = if (currentMode == MODE_STORE) which - 1 else which
                when (w) {
                    0 -> startActivity(Intent(this, TransactionActivity::class.java).apply {
                        putExtra("is_entry", true)
                        putExtra("product_id", row.productId)
                    })
                    1 -> startActivity(Intent(this, TransactionActivity::class.java).apply {
                        putExtra("is_entry", false)
                        putExtra("product_id", row.productId)
                    })
                    2 -> if (currentMode == MODE_WAREHOUSE) /*mode tip*/ {
                        startActivity(Intent(this, TransferActivity::class.java).apply {
                            putExtra("product_id", row.productId)
                        })
                    } else {
                        confirmDelete(row.productId)
                    }
                    3 -> confirmDelete(row.productId)
                }
            }
            .show()
    }

    private fun confirmDelete(productId: Long) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete))
            .setMessage(R.string.confirm_delete)
            .setPositiveButton(R.string.yes) { _, _ ->
                repository.deleteProduct(productId)
                loadInventory()
                Toast.makeText(this, getString(R.string.deleted), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }
}
