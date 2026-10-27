package com.example.anbar

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.anbar.databinding.ActivityPosBinding
import com.example.anbar.databinding.DialogSelectCustomerBinding

class PosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPosBinding
    private lateinit var repository: InventoryRepository
    private val cart = mutableListOf<CartItem>()
    private var priceTier: String = "RETAIL"
    private lateinit var cartAdapter: CartAdapter
    private var sellable: List<Triple<Product, ProductVariant?, Double>> = emptyList()
    private var warehouseId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        binding = ActivityPosBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        repository = InventoryRepository(this)
        // سطح قیمت: خرده / عمده / همکار / VIP
        supportActionBar?.subtitle = "قیمت: خرده‌فروشی  (منو ⋮ برای تغییر)"
        binding.root.setOnLongClickListener {
            pickPriceTier()
            true
        }
        // دکمه تغییر سطح قیمت از عنوان
        title = "صندوق فروش"
        warehouseId = repository.getDefaultWarehouse().id
        val settings = repository.getSettings()
        binding.tvShopName.text = "صندوق · ${settings.shopName}"
        binding.etVat.setText(settings.vatPercent.toString())

        cartAdapter = CartAdapter(cart) { updateTotals() }
        binding.recyclerCart.layoutManager = LinearLayoutManager(this)
        binding.recyclerCart.adapter = cartAdapter

        loadSellable()
        setupSearch()

        // کالای آمده از اسکن هوشمند
        val preId = intent.getLongExtra("product_id", -1L)
        if (preId > 0) {
            val match = sellable.find { it.first.id == preId }
            if (match != null) {
                addToCart(match.first, match.second, match.third)
            } else {
                repository.getProduct(preId)?.let { prod ->
                    val price = repository.priceFor(prod.id, priceTier)
                    addToCart(prod, null, price.coerceAtLeast(0.0))
                }
            }
        }

        binding.etDiscount.addTextChangedListener(simpleWatcher { updateTotals() })
        binding.etVat.addTextChangedListener(simpleWatcher { updateTotals() })

        binding.btnScanPos.setOnClickListener {
            startActivity(Intent(this, BarcodeScanActivity::class.java))
        }
        binding.btnClosePos.setOnClickListener { finish() }
        try { binding.btnManualPos.setOnClickListener { addManualLine() } } catch (_: Exception) { }
        binding.btnCash.setOnClickListener { checkoutCashMixed() }
        binding.btnCredit.setOnClickListener { showCreditDialog() }

        updateTotals()
    }

    override fun onResume() {
        super.onResume()
        loadSellable()
    }

    private fun loadSellable() {
        sellable = repository.getSellableProducts(warehouseId, priceTier)
        val labels = sellable.map { (p, v, price) ->
            val name = p.name + if (v != null) " (${v.name})" else ""
            "$name  —  ${formatMoney(price)}"
        }
        binding.actvSearchProduct.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels)
        )
    }

    private fun setupSearch() {
        binding.actvSearchProduct.setOnClickListener { binding.actvSearchProduct.showDropDown() }
        binding.actvSearchProduct.setOnItemClickListener { _, _, pos, _ ->
            val (product, variant, price) = sellable[pos]
            addToCart(product, variant, price)
            binding.actvSearchProduct.setText("", false)
        }
    }

    private fun addToCart(product: Product, variant: ProductVariant?, price: Double) {
        val existing = cart.find {
            it.productId == product.id && it.variantId == variant?.id
        }
        if (existing != null) {
            existing.quantity += 1
            cartAdapter.notifyDataSetChanged()
        } else {
            val finalPrice = when {
                price > 0 -> price
                product.salePrice > 0 -> product.salePrice
                else -> 0.0
            }
            cart.add(
                CartItem(
                    productId = product.id,
                    variantId = variant?.id,
                    productName = product.name,
                    variantName = variant?.name ?: "",
                    unit = product.unit,
                    quantity = 1.0,
                    unitPrice = finalPrice
                )
            )
            cartAdapter.notifyItemInserted(cart.size - 1)
        }
        if (cart.last().unitPrice <= 0) {
            askPrice(cart.last())
        }
        updateTotals()
    }

    private fun askPrice(item: CartItem) {
        val et = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "قیمت واحد"
            setPadding(40, 30, 40, 30)
        }
        AlertDialog.Builder(this)
            .setTitle("قیمت ${item.productName}")
            .setView(et)
            .setPositiveButton("تأیید") { _, _ ->
                item.unitPrice = et.text.toString().toDoubleOrNull() ?: 0.0
                cartAdapter.notifyDataSetChanged()
                updateTotals()
            }
            .setCancelable(false)
            .show()
    }

    private fun updateTotals() {
        binding.tvEmptyCart.visibility = if (cart.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerCart.visibility = if (cart.isEmpty()) View.GONE else View.VISIBLE

        val subtotal = cart.sumOf { it.lineTotal }
        val discPct = binding.etDiscount.text?.toString()?.toDoubleOrNull() ?: 0.0
        val vatPct = binding.etVat.text?.toString()?.toDoubleOrNull() ?: 0.0
        val discountAmount = subtotal * (discPct / 100.0)
        val afterDisc = subtotal - discountAmount
        val vat = afterDisc * (vatPct / 100.0)
        val total = afterDisc + vat

        binding.tvSubtotal.text = formatMoney(subtotal)
        binding.tvTotal.text = formatMoney(total)
    }


    private fun checkoutCashMixed() {
        if (cart.isEmpty()) {
            Toast.makeText(this, "سبد خالی است — کالا اضافه کنید", Toast.LENGTH_SHORT).show()
            return
        }
        val discPct = binding.etDiscount.text?.toString()?.toDoubleOrNull() ?: 0.0
        val vatPct = binding.etVat.text?.toString()?.toDoubleOrNull() ?: 0.0
        val subtotal = cart.sumOf { it.lineTotal }
        val total = (subtotal * (1 - discPct / 100.0)) * (1 + vatPct / 100.0)
        val etCash = EditText(this).apply {
            hint = "مبلغ نقد"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(total.toLong().toString())
            setPadding(40, 20, 40, 12)
        }
        val etCard = EditText(this).apply {
            hint = "مبلغ کارت"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("0")
            setPadding(40, 12, 40, 20)
        }
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
            addView(TextView(this@PosActivity).apply {
                text = "مبلغ کل: ${FormatUtils.money(total)}"
                setPadding(0, 0, 0, 8)
            })
            addView(etCash)
            addView(etCard)
        }
        AlertDialog.Builder(this)
            .setTitle("پرداخت نقد / کارت")
            .setView(box)
            .setPositiveButton("ثبت") { _, _ ->
                val cash = etCash.text?.toString()?.toDoubleOrNull() ?: total
                val card = etCard.text?.toString()?.toDoubleOrNull() ?: 0.0
                checkout(
                    isCredit = false,
                    paidNow = cash + card,
                    cashPaid = cash,
                    cardPaid = card
                )
            }
            .setNeutralButton("همه نقد") { _, _ -> checkout(isCredit = false) }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun checkout(isCredit: Boolean, customerId: Long? = null, paidNow: Double = 0.0, cashPaid: Double = -1.0, cardPaid: Double = 0.0) {
        if (cart.isEmpty()) {
            Toast.makeText(this, "ابتدا کالا را با جستجو یا اسکن اضافه کنید", Toast.LENGTH_SHORT).show()
            return
        }
        val zeroPrice = cart.filter { it.unitPrice <= 0 }
        if (zeroPrice.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("قیمت صفر")
                .setMessage("برای «${zeroPrice.joinToString("، ") { it.productName }}» قیمت وارد نشده.\nابتدا با لمس طولانی قیمت را تنظیم کنید.")
                .setPositiveButton("باشه", null)
                .show()
            return
        }
        val discPct = binding.etDiscount.text?.toString()?.toDoubleOrNull() ?: 0.0
        val vatPct = binding.etVat.text?.toString()?.toDoubleOrNull() ?: 0.0
        val subtotal = cart.sumOf { it.lineTotal }
        val total = (subtotal * (1 - discPct / 100.0)) * (1 + vatPct / 100.0)
        if (total < 0) {
            Toast.makeText(this, "مبلغ نهایی نامعتبر است", Toast.LENGTH_SHORT).show()
            return
        }
        val paid = if (isCredit) paidNow else total

        val sale = repository.completeSale(
            cart = cart.toList(),
            warehouseId = warehouseId,
            customerId = customerId,
            discountPercent = discPct,
            vatPercent = vatPct,
            paidAmount = paid,
            isCredit = isCredit,
            paymentMethod = when {
                isCredit -> "CREDIT"
                cardPaid > 0 && cashPaid > 0 -> "MIXED"
                cardPaid > 0 -> "CARD"
                else -> "CASH"
            },
            cashPaid = cashPaid,
            cardPaid = cardPaid
        )

        if (sale == null) {
            val msg = repository.lastError.ifBlank { "ثبت فروش ناموفق بود" }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            return
        }

        cart.clear()
        cartAdapter.notifyDataSetChanged()
        updateTotals()

        try { WebhookSender.notifySale(this, sale) } catch (_: Exception) { }

        Toast.makeText(this, "فروش ثبت شد · ${FormatUtils.money(sale.total)}", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, InvoiceActivity::class.java).apply {
            putExtra("sale_id", sale.id)
        })
    }

    private fun showCreditDialog() {
        if (cart.isEmpty()) {
            Toast.makeText(this, "سبد خالی است — کالا اضافه کنید", Toast.LENGTH_SHORT).show()
            return
        }
        val dialogBinding = DialogSelectCustomerBinding.inflate(layoutInflater)
        val customers = repository.getCustomers()
        val names = customers.map { "${it.name}" + if (it.creditBalance > 0) " (بدهی: ${formatMoney(it.creditBalance)})" else "" }
        dialogBinding.actvCustomer.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
        )
        dialogBinding.actvCustomer.setOnClickListener { dialogBinding.actvCustomer.showDropDown() }

        var selectedCustomer: Customer? = null
        dialogBinding.actvCustomer.setOnItemClickListener { _, _, pos, _ ->
            selectedCustomer = customers[pos]
        }

        AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("ثبت نسیه") { _, _ ->
                val newName = dialogBinding.etNewCustomerName.text?.toString()?.trim().orEmpty()
                val newPhone = dialogBinding.etNewCustomerPhone.text?.toString()?.trim().orEmpty()
                val paidNow = dialogBinding.etPaidNow.text?.toString()?.toDoubleOrNull() ?: 0.0

                val customerId = when {
                    selectedCustomer != null -> selectedCustomer!!.id
                    newName.isNotBlank() -> repository.addCustomer(newName, newPhone).id
                    else -> null
                }
                if (customerId == null) {
                    Toast.makeText(this, "مشتری را انتخاب یا وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                checkout(isCredit = true, customerId = customerId, paidNow = paidNow)
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun simpleWatcher(block: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { block() }
    }

    private fun formatMoney(v: Double): String {
        return if (v == v.toLong().toDouble()) String.format("%,d", v.toLong())
        else String.format("%,.0f", v)
    }


    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menu.add(0, 1, 0, "سطح قیمت")
        menu.add(0, 2, 1, "افزودن دستی کالا")
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            1 -> { pickPriceTier(); return true }
            2 -> { addManualLine(); return true }
        }
        return super.onOptionsItemSelected(item)
    }

    /** فروش بدون انتخاب از موجودی — نام و قیمت دستی */
    private fun addManualLine() {
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 16, 40, 8)
        }
        val etName = EditText(this).apply { hint = "نام کالا / خدمت" }
        val etPrice = EditText(this).apply {
            hint = "قیمت واحد"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val etQty = EditText(this).apply {
            hint = "تعداد"
            setText("1")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        box.addView(etName); box.addView(etPrice); box.addView(etQty)
        AlertDialog.Builder(this)
            .setTitle("افزودن دستی به سبد")
            .setView(box)
            .setPositiveButton("افزودن") { _, _ ->
                val name = etName.text?.toString()?.trim().orEmpty().ifEmpty { "کالای دستی" }
                val price = etPrice.text?.toString()?.toDoubleOrNull() ?: 0.0
                val qty = etQty.text?.toString()?.toDoubleOrNull() ?: 1.0
                cart.add(
                    CartItem(
                        productId = 0L,
                        productName = name,
                        unit = "عدد",
                        quantity = qty.coerceAtLeast(0.001),
                        unitPrice = price
                    )
                )
                cartAdapter.notifyItemInserted(cart.size - 1)
                if (price <= 0) askPrice(cart.last())
                updateTotals()
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun pickPriceTier() {
        val labels = arrayOf("خرده‌فروشی", "عمده", "همکار", "VIP")
        val codes = arrayOf("RETAIL", "WHOLESALE", "COLLEAGUE", "VIP")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("سطح قیمت")
            .setItems(labels) { _, which ->
                priceTier = codes[which]
                supportActionBar?.subtitle = "قیمت: ${labels[which]}"
                android.widget.Toast.makeText(this, "سطح قیمت: ${labels[which]}", android.widget.Toast.LENGTH_SHORT).show()
                // بازسازی لیست قیمت‌ها
                try { recreateSellable() } catch (_: Exception) {}
            }
            .show()
    }

    private fun recreateSellable() {
        sellable = repository.getSellableProducts(warehouseId, priceTier)
        val labels = sellable.map { (p, v, price) ->
            val vName = v?.let { " (${it.name})" } ?: ""
            val pStr = if (price > 0) " — ${FormatUtils.money(price)}" else ""
            "${p.name}$vName$pStr"
        }
        binding.actvSearchProduct.setAdapter(
            android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels)
        )
    }
}
