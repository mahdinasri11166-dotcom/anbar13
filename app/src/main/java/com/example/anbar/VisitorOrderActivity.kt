package com.example.anbar

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.io.File

/**
 * سفارش ویزیتور پیشرفته:
 * چند قلم کالا، انتخاب از موجودی، تخفیف، ذخیره، PDF، ارسال به پیک
 */
class VisitorOrderActivity : AppCompatActivity() {

    private lateinit var repo: InventoryRepository
    private val cart = mutableListOf<VisitorLine>()
    private lateinit var itemsBox: LinearLayout
    private lateinit var tvTotal: TextView
    private lateinit var listBox: LinearLayout

    private lateinit var etVisitor: EditText
    private lateinit var etCustomer: AutoCompleteTextView
    private lateinit var etAddress: EditText
    private lateinit var etPhone: EditText
    private lateinit var etDiscount: EditText
    private lateinit var etNote: EditText
    private lateinit var actvProduct: AutoCompleteTextView
    private lateinit var etQty: EditText
    private lateinit var etPrice: EditText
    private var products: List<Product> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        repo = InventoryRepository(this)
        products = repo.getProducts()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 28)
            setBackgroundColor(0xFFF1F5F9.toInt())
        }

        root.addView(header("🛵 سفارش ویزیتور", "ثبت چندقلم، تخفیف، PDF و ارسال به پیک"))

        etVisitor = field("نام ویزیتور")
        etPhone = field("تلفن مشتری", android.text.InputType.TYPE_CLASS_PHONE)
        etAddress = field("آدرس تحویل")
        etNote = field("یادداشت")
        val custs = repo.getCustomers()
        etCustomer = AutoCompleteTextView(this).apply {
            hint = "نام مشتری * (از لیست یا دستی)"
            threshold = 1
            setPadding(28, 28, 28, 28)
            setAdapter(ArrayAdapter(this@VisitorOrderActivity, android.R.layout.simple_dropdown_item_1line, custs.map { it.name }))
            setOnItemClickListener { _, _, pos, _ ->
                val c = custs.getOrNull(pos) ?: return@setOnItemClickListener
                etPhone.setText(c.phone)
                etAddress.setText(c.address)
            }
        }
        listOf(etVisitor, etCustomer, etPhone, etAddress, etNote).forEach { root.addView(it) }

        root.addView(sectionTitle("افزودن کالا"))

        actvProduct = AutoCompleteTextView(this).apply {
            hint = "انتخاب کالا یا نام دستی"
            threshold = 1
            setAdapter(ArrayAdapter(this@VisitorOrderActivity, android.R.layout.simple_dropdown_item_1line, products.map { it.name }))
            setOnItemClickListener { _, _, pos, _ ->
                val p = products.getOrNull(pos)
                if (p != null) {
                    etPrice.setText(if (p.salePrice > 0) p.salePrice.toLong().toString() else "")
                }
            }
        }
        etQty = field("تعداد", android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL).apply { setText("1") }
        etPrice = field("قیمت واحد", android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        root.addView(actvProduct)
        root.addView(etQty)
        root.addView(etPrice)

        root.addView(MaterialButton(this).apply {
            text = "＋ افزودن به سفارش"
            setOnClickListener { addLine() }
        })

        itemsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(itemsBox)

        etDiscount = field("درصد تخفیف", android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL).apply { setText("0") }
        root.addView(etDiscount)

        tvTotal = TextView(this).apply {
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF1E40AF.toInt())
            setPadding(0, 12, 0, 12)
            text = "جمع: ۰"
        }
        root.addView(tvTotal)
        etDiscount.addTextChangedListener(simpleWatcher { updateTotal() })

        root.addView(MaterialButton(this).apply {
            text = "💾 ثبت سفارش + PDF"
            setOnClickListener { saveAndShare() }
        })
        root.addView(MaterialButton(this).apply {
            text = "🚚 ثبت و ارسال به پیک"
            setOnClickListener { saveAndDelivery() }
        })

        root.addView(sectionTitle("سفارش‌های اخیر"))
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listBox)

        setContentView(ScrollView(this).apply { addView(root) })
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.mod_visitor)
        refreshList()
        updateTotal()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun header(title: String, sub: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 12)
            addView(TextView(this@VisitorOrderActivity).apply {
                text = title
                textSize = 22f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@VisitorOrderActivity).apply {
                text = sub
                textSize = 12f
                setTextColor(0xFF64748B.toInt())
            })
        }
    }

    private fun sectionTitle(t: String) = TextView(this).apply {
        text = t
        textSize = 14f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(0, 16, 0, 8)
        setTextColor(0xFF0F172A.toInt())
    }

    private fun field(hint: String, inputType: Int = android.text.InputType.TYPE_CLASS_TEXT): EditText {
        return EditText(this).apply {
            this.hint = hint
            this.inputType = inputType
            setPadding(28, 28, 28, 28)
        }
    }

    private fun addLine() {
        val name = actvProduct.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            Toast.makeText(this, "نام کالا را وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }
        val qty = etQty.text?.toString()?.toDoubleOrNull() ?: 1.0
        val price = etPrice.text?.toString()?.toDoubleOrNull() ?: 0.0
        if (qty <= 0) {
            Toast.makeText(this, "تعداد نامعتبر", Toast.LENGTH_SHORT).show()
            return
        }
        cart.add(VisitorLine(name, qty, price))
        actvProduct.setText("")
        etQty.setText("1")
        etPrice.setText("")
        redrawCart()
        updateTotal()
    }

    private fun redrawCart() {
        itemsBox.removeAllViews()
        cart.forEachIndexed { idx, line ->
            val row = MaterialCardView(this).apply {
                radius = 12f
                cardElevation = 0f
                setCardBackgroundColor(Color.WHITE)
                useCompatPadding = true
            }
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(16, 14, 16, 14)
                gravity = Gravity.CENTER_VERTICAL
            }
            box.addView(TextView(this).apply {
                text = "${line.productName}\n${FormatUtils.qty(line.quantity)} × ${FormatUtils.money(line.unitPrice)} = ${FormatUtils.money(line.lineTotal)}"
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            box.addView(MaterialButton(this).apply {
                text = "حذف"
                setOnClickListener {
                    cart.removeAt(idx)
                    redrawCart()
                    updateTotal()
                }
            })
            row.addView(box)
            itemsBox.addView(row)
        }
    }

    private fun updateTotal() {
        val sub = cart.sumOf { it.lineTotal }
        val disc = etDiscount.text?.toString()?.toDoubleOrNull() ?: 0.0
        val total = sub * (1 - disc / 100.0)
        tvTotal.text = "جمع اقلام: ${FormatUtils.money(sub)}\nپس از تخفیف: ${FormatUtils.money(total)}"
    }

    private fun currentTotal(): Double {
        val sub = cart.sumOf { it.lineTotal }
        val disc = etDiscount.text?.toString()?.toDoubleOrNull() ?: 0.0
        return sub * (1 - disc / 100.0)
    }

    private fun saveOrder(): VisitorOrder? {
        val customer = etCustomer.text?.toString()?.trim().orEmpty()
        if (customer.isEmpty()) {
            Toast.makeText(this, "نام مشتری الزامی است", Toast.LENGTH_SHORT).show()
            return null
        }
        if (cart.isEmpty()) {
            Toast.makeText(this, "حداقل یک کالا اضافه کنید", Toast.LENGTH_SHORT).show()
            return null
        }
        val order = repo.addVisitorOrder(
            visitorName = etVisitor.text?.toString()?.trim().orEmpty(),
            customerName = customer,
            customerPhone = etPhone.text?.toString()?.trim().orEmpty(),
            address = etAddress.text?.toString()?.trim().orEmpty(),
            discountPercent = etDiscount.text?.toString()?.toDoubleOrNull() ?: 0.0,
            items = cart.toList(),
            note = etNote.text?.toString()?.trim().orEmpty()
        )
        return order
    }

    private fun saveAndShare() {
        val order = saveOrder() ?: return
        try {
            val file = buildPdf(order)
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "اشتراک سفارش ویزیتور"))
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
        }
        clearForm()
        refreshList()
        Toast.makeText(this, "سفارش ثبت شد", Toast.LENGTH_SHORT).show()
    }

    private fun saveAndDelivery() {
        val order = saveOrder() ?: return
        repo.addDelivery(
            customerName = order.customerName,
            phone = order.customerPhone,
            address = order.address,
            courier = order.visitorName,
            total = order.total,
            note = "از سفارش ویزیتور #${order.id} — ${order.items.joinToString { it.productName }}"
        )
        repo.updateVisitorStatus(order.id, "CONFIRMED")
        try {
            val file = buildPdf(order)
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "اشتراک + پیک"))
        } catch (_: Exception) {
        }
        clearForm()
        refreshList()
        Toast.makeText(this, "سفارش ثبت و به پیک ارسال شد", Toast.LENGTH_LONG).show()
    }

    private fun clearForm() {
        cart.clear()
        redrawCart()
        etCustomer.setText("")
        etPhone.setText("")
        etAddress.setText("")
        etNote.setText("")
        etDiscount.setText("0")
        updateTotal()
    }

    private fun refreshList() {
        listBox.removeAllViews()
        val orders = repo.getVisitorOrders().take(20)
        if (orders.isEmpty()) {
            listBox.addView(TextView(this).apply {
                text = "هنوز سفارشی ثبت نشده"
                setTextColor(0xFF64748B.toInt())
            })
            return
        }
        orders.forEach { o ->
            val card = MaterialCardView(this).apply {
                radius = 14f
                cardElevation = 0f
                setCardBackgroundColor(Color.WHITE)
                useCompatPadding = true
            }
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 14, 16, 14)
            }
            box.addView(TextView(this).apply {
                text = "${o.customerName}  ·  ${o.statusLabel()}"
                setTypeface(null, android.graphics.Typeface.BOLD)
                textSize = 14f
            })
            box.addView(TextView(this).apply {
                text = buildString {
                    if (o.visitorName.isNotBlank()) append("ویزیتور: ${o.visitorName}\n")
                    append(FormatUtils.dateTime(o.createdAt))
                    append("\n")
                    append(o.items.joinToString("، ") { "${it.productName}×${FormatUtils.qty(it.quantity)}" })
                    append("\n")
                    append(FormatUtils.money(o.total))
                    if (o.address.isNotBlank()) append("\n${o.address}")
                }
                textSize = 12f
                setTextColor(0xFF475569.toInt())
            })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            fun stBtn(label: String, st: String) {
                row.addView(MaterialButton(this).apply {
                    text = label
                    textSize = 11f
                    setOnClickListener {
                        repo.updateVisitorStatus(o.id, st)
                        refreshList()
                    }
                })
            }
            stBtn("تأیید", "CONFIRMED")
            stBtn("تحویل", "DELIVERED")
            stBtn("لغو", "CANCELLED")
            box.addView(row)
            card.addView(box)
            listBox.addView(card)
        }
    }

    private fun buildPdf(order: VisitorOrder): File {
        val s = repo.getSettings()
        val lines = order.items.map {
            InvoicePdfHelper.Line(
                name = it.productName,
                qty = FormatUtils.qty(it.quantity),
                unitPrice = FormatUtils.money(it.unitPrice),
                total = FormatUtils.money(it.lineTotal)
            )
        }
        val extras = mutableListOf<String>()
        if (order.visitorName.isNotBlank()) extras.add("ویزیتور: ${order.visitorName}")
        if (order.address.isNotBlank()) extras.add("آدرس: ${order.address}")
        if (order.customerPhone.isNotBlank()) extras.add("تلفن: ${order.customerPhone}")
        if (order.discountPercent > 0) extras.add("تخفیف: ${order.discountPercent}%")
        if (order.note.isNotBlank()) extras.add("یادداشت: ${order.note}")
        return InvoicePdfHelper.build(
            context = this,
            settings = s,
            docTitle = "سفارش ویزیتور",
            invoiceNo = "شماره: VO-${order.id % 100000}",
            dateText = FormatUtils.dateTime(order.createdAt),
            customerLine = "مشتری: ${order.customerName}",
            extraLines = extras,
            items = lines,
            summaryRows = listOf(
                "جمع اقلام" to FormatUtils.money(order.items.sumOf { it.lineTotal }),
                "تخفیف" to "${order.discountPercent}%"
            ),
            grandTotalLabel = "مبلغ نهایی",
            grandTotalValue = FormatUtils.money(order.total),
            footer = s.invoiceFooter.ifBlank { "از خرید شما متشکریم" },
            verse = MolanaHelper.randomVerse(this),
            filePrefix = "visitor"
        )
    }

    private fun simpleWatcher(block: () -> Unit) = object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) { block() }
    }
}
