package com.example.anbar

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.anbar.databinding.ActivityHandoverBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HandoverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHandoverBinding
    private lateinit var repository: InventoryRepository
    private var colleagueId: Long = -1
    private var isReturn = false
    private var products: List<Product> = emptyList()
    private var warehouses: List<Warehouse> = emptyList()
    private var selectedProduct: Product? = null
    private var selectedWarehouse: Warehouse? = null
    private val lines = mutableListOf<HandoverItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        binding = ActivityHandoverBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        repository = InventoryRepository(this)
        colleagueId = intent.getLongExtra("colleague_id", -1L)
        isReturn = intent.getBooleanExtra("is_return", false)
        val colleague = repository.getColleague(colleagueId)
        supportActionBar?.title = if (isReturn) "بازتحویل" else "تحویل کالا"
        binding.tvHandoverTitle.text = (if (isReturn) "بازتحویل از " else "تحویل به ") + (colleague?.name ?: "")

        products = repository.getProducts()
        warehouses = repository.getWarehouses()

        binding.actvWh.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, warehouses.map { it.name })
        )
        binding.actvWh.setOnClickListener { binding.actvWh.showDropDown() }
        binding.actvWh.setOnItemClickListener { _, _, pos, _ -> selectedWarehouse = warehouses[pos] }
        if (warehouses.isNotEmpty()) {
            selectedWarehouse = warehouses.first { it.isDefault }
            binding.actvWh.setText(selectedWarehouse!!.name, false)
        }

        // برای بازتحویل فقط کالاهای دارای مانده
        val productList = if (isReturn) {
            val balIds = repository.getColleagueBalances(colleagueId).map { it.productId }.toSet()
            products.filter { it.id in balIds }
        } else products

        binding.actvProd.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, productList.map { it.name })
        )
        binding.actvProd.setOnClickListener { binding.actvProd.showDropDown() }
        binding.actvProd.setOnItemClickListener { _, _, pos, _ -> selectedProduct = productList[pos] }

        binding.recyclerLines.layoutManager = LinearLayoutManager(this)
        refreshLines()

        binding.btnAddLine.setOnClickListener { addLine() }
        binding.btnConfirmHandover.setOnClickListener { confirm() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun addLine() {
        val p = selectedProduct
        val qty = binding.etQty.text?.toString()?.toDoubleOrNull() ?: 0.0
        if (p == null || qty <= 0) {
            Toast.makeText(this, "کالا و تعداد را وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }
        lines.add(HandoverItem(p.id, null, p.name, qty, p.unit))
        binding.etQty.setText("1")
        binding.actvProd.setText("", false)
        selectedProduct = null
        refreshLines()
    }

    private fun refreshLines() {
        binding.recyclerLines.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val tv = TextView(parent.context).apply {
                    setPadding(24, 16, 24, 16)
                    textSize = 14f
                }
                return object : RecyclerView.ViewHolder(tv) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val it = lines[position]
                (holder.itemView as TextView).text = "${it.productName}: ${it.quantity} ${it.unit}"
            }

            override fun getItemCount() = lines.size
        }
    }

    private fun confirm() {
        val wh = selectedWarehouse
        if (wh == null || lines.isEmpty()) {
            Toast.makeText(this, "انبار و حداقل یک کالا لازم است", Toast.LENGTH_SHORT).show()
            return
        }
        val result = if (isReturn) {
            repository.returnFromColleague(colleagueId, wh.id, lines.toList())
        } else {
            repository.handoverToColleague(colleagueId, wh.id, lines.toList())
        }
        if (result == null) {
            Toast.makeText(this, "موجودی/مانده کافی نیست یا خطا", Toast.LENGTH_LONG).show()
            return
        }
        shareReceiptPdf(result)
        finish()
    }

    private fun shareReceiptPdf(h: Handover) {
        try {
            val settings = repository.getSettings()
            val doc = PdfDocument()
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val canvas: Canvas = page.canvas
            val paint = Paint().apply { textSize = 12f; isAntiAlias = true }
            val title = Paint().apply { textSize = 16f; isFakeBoldText = true; isAntiAlias = true }
            var y = 40f
            val typeFa = if (h.type == "HANDOVER") "رسید تحویل امانی" else "رسید بازتحویل"
            canvas.drawText(settings.shopName, 50f, y, title); y += 24f
            canvas.drawText(typeFa, 50f, y, title); y += 20f
            canvas.drawText("شماره: ${h.receiptNumber}", 50f, y, paint); y += 16f
            canvas.drawText("همکار: ${h.colleagueName}", 50f, y, paint); y += 16f
            canvas.drawText(
                SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(h.timestamp)),
                50f, y, paint
            ); y += 24f
            h.items.forEach {
                canvas.drawText("${it.productName}: ${it.quantity} ${it.unit}", 50f, y, paint)
                y += 16f
            }
            y += 16f
            canvas.drawText(settings.invoiceFooter, 50f, y, paint)
            doc.finishPage(page)

            val dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: cacheDir
            val file = File(dir, "${h.receiptNumber}.pdf")
            FileOutputStream(file).use { doc.writeTo(it) }
            doc.close()

            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "ارسال رسید امانی"))
            Toast.makeText(this, getString(R.string.registered), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "ثبت شد (خطا در PDF: ${e.message})", Toast.LENGTH_LONG).show()
        }
    }
}
