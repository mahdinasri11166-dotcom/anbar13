package com.example.anbar

import android.graphics.Color
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.anbar.databinding.ActivityCommercialDetailBinding
import com.google.android.material.card.MaterialCardView
import java.util.Calendar

class CommercialDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCommercialDetailBinding
    private lateinit var repository: InventoryRepository
    private var mode = "shift"

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        binding = ActivityCommercialDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        repository = InventoryRepository(this)
        mode = intent.getStringExtra("mode") ?: "shift"
        supportActionBar?.title = titleFor(mode)
        binding.tvDetailTitle.text = titleFor(mode)
        binding.btnPrimary.setOnClickListener { onPrimary() }
        refresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun titleFor(m: String) = when (m) {
        "shift" -> "شیفت صندوق"
        "stocktake" -> "شمارش انبار"
        "checks" -> "چک‌ها"
        "expenses" -> "هزینه‌ها"
        "proforma" -> "پیش‌فاکتور"
        "visit" -> "سفارش ویزیتور"
        "prices" -> "قیمت چندسطحی"
        "bundles" -> "باندل تخفیف"
        else -> getString(R.string.financial_tools)
    }

    private fun refresh() {
        binding.layoutContent.removeAllViews()
        when (mode) {
            "shift" -> showShift()
            "stocktake" -> showStockTakes()
            "checks" -> showChecks()
            "expenses" -> showExpenses()
            "proforma" -> showProformas()
            "visit" -> showVisits()
            "prices" -> showPrices()
            "bundles" -> showBundles()
        }
    }

    private fun onPrimary() {
        when (mode) {
            "shift" -> shiftAction()
            "stocktake" -> newStockTake()
            "checks" -> newCheck()
            "expenses" -> newExpense()
            "proforma" -> newProforma()
            "visit" -> newVisit()
            "prices" -> editPrice()
            "bundles" -> newBundle()
        }
    }

    private fun addCard(text: String, color: Int = Color.WHITE) {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 13f
            setPadding(14, 12, 14, 12)
            setTextColor(Color.parseColor("#1A1A2E"))
            setBackgroundColor(color)
        }
        binding.layoutContent.addView(MaterialCardView(this).apply {
            radius = 10f
            cardElevation = 1f
            useCompatPadding = true
            addView(tv)
        })
    }

    // ----- Shift -----
    private fun showShift() {
        val open = repository.getOpenShift()
        if (open != null) {
            binding.tvDetailInfo.text = "شیفت باز از ${FormatUtils.dateTime(open.openedAt)} · اول صندوق: ${FormatUtils.money(open.openingCash)}"
            binding.btnPrimary.text = "بستن شیفت"
        } else {
            binding.tvDetailInfo.text = "شیفتی باز نیست"
            binding.btnPrimary.text = "باز کردن شیفت"
        }
        repository.getShifts().take(15).forEach { s ->
            val st = if (s.isOpen) "باز" else "بسته · واریانس ${FormatUtils.money(s.variance)}"
            addCard("${FormatUtils.dateTime(s.openedAt)}\n$st · توسط ${s.openedBy}")
        }
    }

    private fun shiftAction() {
        val open = repository.getOpenShift()
        if (open == null) {
            val et = EditText(this).apply {
                hint = "موجودی اول صندوق"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setPadding(40, 30, 40, 30)
            }
            AlertDialog.Builder(this).setTitle("باز کردن شیفت").setView(et)
                .setPositiveButton("باز کن") { _, _ ->
                    val v = et.text.toString().toDoubleOrNull() ?: 0.0
                    repository.openShift(v)
                    Toast.makeText(this, "شیفت باز شد", Toast.LENGTH_SHORT).show()
                    refresh()
                }.setNegativeButton("لغو", null).show()
        } else {
            val et = EditText(this).apply {
                hint = "موجودی پایان صندوق"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setPadding(40, 30, 40, 30)
            }
            AlertDialog.Builder(this).setTitle("بستن شیفت").setView(et)
                .setPositiveButton("ببند") { _, _ ->
                    val v = et.text.toString().toDoubleOrNull() ?: 0.0
                    val closed = repository.closeShift(v)
                    Toast.makeText(
                        this,
                        "بسته شد · واریانس: ${FormatUtils.money(closed?.variance ?: 0.0)}",
                        Toast.LENGTH_LONG
                    ).show()
                    refresh()
                }.setNegativeButton("لغو", null).show()
        }
    }

    // ----- Stock take -----
    private fun showStockTakes() {
        binding.btnPrimary.text = "شمارش جدید"
        binding.tvDetailInfo.text = "مغایرت سیستم و فیزیکی"
        repository.getStockTakes().take(10).forEach { st ->
            val diffs = st.lines.count { kotlin.math.abs(it.diff) > 0.001 }
            addCard("${st.warehouseName} · ${FormatUtils.dateTime(st.timestamp)}\n${st.lines.size} قلم · $diffs مغایرت")
        }
    }

    private fun newStockTake() {
        val warehouses = repository.getWarehouses()
        if (warehouses.isEmpty()) {
            Toast.makeText(this, "ابتدا انبار بسازید", Toast.LENGTH_SHORT).show()
            return
        }
        val products = repository.getProducts()
        val counts = mutableMapOf<Long, EditText>()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 0)
        }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@CommercialDetailActivity, android.R.layout.simple_spinner_dropdown_item, warehouses.map { it.name })
        }
        layout.addView(TextView(this).apply { text = "انبار:"; setPadding(0, 0, 0, 8) })
        layout.addView(spinner)
        products.take(20).forEach { p ->
            val sys = repository.getAllStock().filter { it.productId == p.id }.sumOf { it.quantity }
            layout.addView(TextView(this).apply {
                text = "${p.name} (سیستم: ${FormatUtils.qty(sys)})"
                setPadding(0, 12, 0, 0)
            })
            val et = EditText(this).apply {
                hint = "شمارش شده"
                setText(FormatUtils.qty(sys))
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            counts[p.id] = et
            layout.addView(et)
        }
        val cb = CheckBox(this).apply { text = "اعمال تعدیل موجودی"; isChecked = true }
        layout.addView(cb)
        AlertDialog.Builder(this).setTitle("شمارش انبار").setView(layout)
            .setPositiveButton("ثبت") { _, _ ->
                val wh = warehouses[spinner.selectedItemPosition]
                val lines = products.take(20).map { p ->
                    val sys = repository.getAllStock().filter { it.productId == p.id }.sumOf { it.quantity }
                    val counted = counts[p.id]?.text?.toString()?.toDoubleOrNull() ?: sys
                    StockTakeLine(p.id, p.name, sys, counted, p.unit)
                }
                repository.saveStockTake(wh.id, lines, "", cb.isChecked)
                Toast.makeText(this, "شمارش ثبت شد", Toast.LENGTH_SHORT).show()
                refresh()
            }.setNegativeButton("لغو", null).show()
    }

    // ----- Checks -----
    private fun showChecks() {
        binding.btnPrimary.text = "ثبت چک"
        val due = repository.getDueChecks(7)
        binding.tvDetailInfo.text = if (due.isEmpty()) "چک سررسید نزدیک: ۰" else "⚠ ${due.size} چک تا ۷ روز آینده"
        repository.getChecks().take(20).forEach { c ->
            val type = if (c.type == "RECEIVABLE") "دریافتنی" else "پرداختنی"
            addCard("$type · ${c.personName}\n${FormatUtils.money(c.amount)} · سررسید ${FormatUtils.date(c.dueDate)} · ${c.status}")
        }
    }

    private fun newCheck() {
        val etName = EditText(this).apply { hint = "نام شخص"; setPadding(32, 20, 32, 8) }
        val etAmount = EditText(this).apply {
            hint = "مبلغ"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(32, 8, 32, 8)
        }
        val etDays = EditText(this).apply {
            hint = "سررسید (چند روز دیگر)"
            setText("30")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(32, 8, 32, 8)
        }
        val etBank = EditText(this).apply { hint = "بانک / شماره چک"; setPadding(32, 8, 32, 20) }
        val sp = Spinner(this).apply {
            adapter = ArrayAdapter(this@CommercialDetailActivity, android.R.layout.simple_spinner_dropdown_item, listOf("دریافتنی", "پرداختنی"))
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 0)
            addView(sp); addView(etName); addView(etAmount); addView(etDays); addView(etBank)
        }
        AlertDialog.Builder(this).setTitle("ثبت چک").setView(layout)
            .setPositiveButton("ذخیره") { _, _ ->
                val days = etDays.text.toString().toIntOrNull() ?: 30
                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }
                repository.addCheck(
                    BankCheck(
                        id = 0,
                        type = if (sp.selectedItemPosition == 0) "RECEIVABLE" else "PAYABLE",
                        personName = etName.text.toString().trim().ifEmpty { "-" },
                        amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0,
                        dueDate = cal.timeInMillis,
                        bankName = etBank.text.toString().trim()
                    )
                )
                Toast.makeText(this, "ثبت شد", Toast.LENGTH_SHORT).show()
                refresh()
            }.setNegativeButton("لغو", null).show()
    }

    // ----- Expenses -----
    private fun showExpenses() {
        binding.btnPrimary.text = "ثبت هزینه"
        binding.tvDetailInfo.text = "جمع ۳۰ روز: ${FormatUtils.money(repository.expensesTotal(30))}"
        repository.getExpenses().take(20).forEach { e ->
            addCard("${e.category} · ${e.title}\n${FormatUtils.money(e.amount)} · ${FormatUtils.date(e.timestamp)}")
        }
    }

    private fun newExpense() {
        val etTitle = EditText(this).apply { hint = "عنوان"; setPadding(32, 20, 32, 8) }
        val etAmount = EditText(this).apply {
            hint = "مبلغ"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(32, 8, 32, 20)
        }
        val cats = listOf("اجاره", "حمل", "بسته‌بندی", "حقوق", "سایر")
        val sp = Spinner(this).apply {
            adapter = ArrayAdapter(this@CommercialDetailActivity, android.R.layout.simple_spinner_dropdown_item, cats)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 0)
            addView(sp); addView(etTitle); addView(etAmount)
        }
        AlertDialog.Builder(this).setTitle("هزینه جدید").setView(layout)
            .setPositiveButton("ذخیره") { _, _ ->
                repository.addExpense(
                    Expense(
                        0, etTitle.text.toString().trim().ifEmpty { cats[sp.selectedItemPosition] },
                        cats[sp.selectedItemPosition],
                        etAmount.text.toString().toDoubleOrNull() ?: 0.0,
                        System.currentTimeMillis()
                    )
                )
                Toast.makeText(this, "ثبت شد", Toast.LENGTH_SHORT).show()
                refresh()
            }.setNegativeButton("لغو", null).show()
    }

    // ----- Proforma -----
    private fun showProformas() {
        binding.btnPrimary.text = "پیش‌فاکتور جدید"
        binding.tvDetailInfo.text = "رزرو موقت موجودی / پیش‌فاکتور"
        repository.getProformas().take(15).forEach { p ->
            addCard("${p.number} · ${p.customerName}\n${FormatUtils.money(p.total)} · ${p.status} · تا ${FormatUtils.dateTime(p.expiresAt)}")
        }
    }

    private fun newProforma() {
        val products = repository.getProducts()
        if (products.isEmpty()) {
            Toast.makeText(this, "کالایی نیست", Toast.LENGTH_SHORT).show()
            return
        }
        val etName = EditText(this).apply { hint = "نام مشتری"; setPadding(32, 20, 32, 8) }
        val etQty = EditText(this).apply {
            hint = "تعداد"
            setText("1")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(32, 8, 32, 8)
        }
        val etPrice = EditText(this).apply {
            hint = "قیمت واحد"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(32, 8, 32, 20)
        }
        val sp = Spinner(this).apply {
            adapter = ArrayAdapter(this@CommercialDetailActivity, android.R.layout.simple_spinner_dropdown_item, products.map { it.name })
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 0)
            addView(etName); addView(sp); addView(etQty); addView(etPrice)
        }
        AlertDialog.Builder(this).setTitle("پیش‌فاکتور").setView(layout)
            .setPositiveButton("ثبت (۲۴س)") { _, _ ->
                val p = products[sp.selectedItemPosition]
                val qty = etQty.text.toString().toDoubleOrNull() ?: 1.0
                val price = etPrice.text.toString().toDoubleOrNull() ?: 0.0
                val item = SaleItem(productId = p.id, productName = p.name, unit = p.unit, quantity = qty, unitPrice = price, lineTotal = qty * price)
                repository.saveProforma(null, etName.text.toString().trim(), listOf(item), 24)
                Toast.makeText(this, "پیش‌فاکتور ثبت شد", Toast.LENGTH_SHORT).show()
                refresh()
            }.setNegativeButton("لغو", null).show()
    }

    // ----- Visit -----
    private fun showVisits() {
        binding.btnPrimary.text = "سفارش میدانی"
        binding.tvDetailInfo.text = "سفارش ویزیتور در محل مشتری"
        repository.getVisitOrders().take(15).forEach { v ->
            addCard("${v.number} · ${v.customerName}\n${FormatUtils.money(v.total)} · ${v.status}")
        }
    }

    private fun newVisit() {
        val products = repository.getProducts()
        if (products.isEmpty()) return
        val etName = EditText(this).apply { hint = "نام مشتری"; setPadding(32, 20, 32, 8) }
        val etPhone = EditText(this).apply {
            hint = "تلفن"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setPadding(32, 8, 32, 8)
        }
        val etQty = EditText(this).apply {
            hint = "تعداد"
            setText("1")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(32, 8, 32, 8)
        }
        val etPrice = EditText(this).apply {
            hint = "قیمت"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(32, 8, 32, 20)
        }
        val sp = Spinner(this).apply {
            adapter = ArrayAdapter(this@CommercialDetailActivity, android.R.layout.simple_spinner_dropdown_item, products.map { it.name })
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 0)
            addView(etName); addView(etPhone); addView(sp); addView(etQty); addView(etPrice)
        }
        AlertDialog.Builder(this).setTitle("سفارش ویزیتور").setView(layout)
            .setPositiveButton("ثبت") { _, _ ->
                val p = products[sp.selectedItemPosition]
                val q = etQty.text.toString().toDoubleOrNull() ?: 1.0
                val pr = etPrice.text.toString().toDoubleOrNull() ?: 0.0
                val item = SaleItem(
                    productId = p.id,
                    productName = p.name,
                    unit = p.unit,
                    quantity = q,
                    unitPrice = pr,
                    lineTotal = q * pr
                )
                repository.addVisitOrder(
                    etName.text.toString().trim().ifEmpty { "مشتری" },
                    etPhone.text.toString().trim(),
                    listOf(item), "", ""
                )
                Toast.makeText(this, "سفارش ثبت شد", Toast.LENGTH_SHORT).show()
                refresh()
            }.setNegativeButton("لغو", null).show()
    }

    // ----- Prices -----
    private fun showPrices() {
        binding.btnPrimary.text = "ویرایش قیمت"
        binding.tvDetailInfo.text = "خرده / عمده / VIP / همکار"
        val prices = repository.getProductPrices()
        repository.getProducts().forEach { p ->
            val pp = prices[p.id]
            val line = if (pp == null) "${p.name}: تعریف نشده"
            else "${p.name}\nخرده ${FormatUtils.money(pp.retail)} · عمده ${FormatUtils.money(pp.wholesale)} · VIP ${FormatUtils.money(pp.vip)}"
            addCard(line)
        }
    }

    private fun editPrice() {
        val products = repository.getProducts()
        if (products.isEmpty()) return
        val sp = Spinner(this).apply {
            adapter = ArrayAdapter(this@CommercialDetailActivity, android.R.layout.simple_spinner_dropdown_item, products.map { it.name })
        }
        fun moneyEt(h: String) = EditText(this).apply {
            hint = h
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(32, 8, 32, 8)
        }
        val etR = moneyEt("خرده‌فروشی"); val etW = moneyEt("عمده"); val etV = moneyEt("VIP"); val etC = moneyEt("همکار")
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 0)
            addView(sp); addView(etR); addView(etW); addView(etV); addView(etC)
        }
        AlertDialog.Builder(this).setTitle("قیمت چندسطحی").setView(layout)
            .setPositiveButton("ذخیره") { _, _ ->
                val p = products[sp.selectedItemPosition]
                repository.setProductPrice(
                    ProductPrice(
                        p.id,
                        etR.text.toString().toDoubleOrNull() ?: 0.0,
                        etW.text.toString().toDoubleOrNull() ?: 0.0,
                        etV.text.toString().toDoubleOrNull() ?: 0.0,
                        etC.text.toString().toDoubleOrNull() ?: 0.0
                    )
                )
                Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
                refresh()
            }.setNegativeButton("لغو", null).show()
    }

    // ----- Bundles -----
    private fun showBundles() {
        binding.btnPrimary.text = "باندل جدید"
        binding.tvDetailInfo.text = "خرید هم‌زمان چند کالا با تخفیف"
        repository.getBundles().forEach { b ->
            addCard("${b.name}\n${b.productIds.size} کالا · ${b.discountPercent}% تخفیف")
        }
    }

    private fun newBundle() {
        val products = repository.getProducts()
        if (products.size < 2) {
            Toast.makeText(this, "حداقل ۲ کالا لازم است", Toast.LENGTH_SHORT).show()
            return
        }
        val etName = EditText(this).apply { hint = "نام باندل"; setPadding(32, 20, 32, 8) }
        val etDisc = EditText(this).apply {
            hint = "درصد تخفیف"
            setText("10")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(32, 8, 32, 8)
        }
        val checks = products.take(10).map { p ->
            CheckBox(this).apply { text = p.name; tag = p.id }
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 0)
            addView(etName); addView(etDisc)
            checks.forEach { addView(it) }
        }
        AlertDialog.Builder(this).setTitle("باندل").setView(layout)
            .setPositiveButton("ذخیره") { _, _ ->
                val ids = checks.filter { it.isChecked }.map { it.tag as Long }
                if (ids.size < 2) {
                    Toast.makeText(this, "حداقل ۲ کالا", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                repository.addBundle(
                    etName.text.toString().trim().ifEmpty { "باندل" },
                    ids,
                    etDisc.text.toString().toDoubleOrNull() ?: 10.0
                )
                Toast.makeText(this, "باندل ذخیره شد", Toast.LENGTH_SHORT).show()
                refresh()
            }.setNegativeButton("لغو", null).show()
    }
}
