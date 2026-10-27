package com.example.anbar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * بخش ۹: خروجی CSV (سازگار اکسل)، گزارش PDF
 */
object ExportHelper {

    private fun dir(context: Context): File {
        val d = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())

    private fun format(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString()
        else String.format(Locale.US, "%.2f", v)

    /** CSV با BOM برای باز شدن صحیح فارسی در Excel */
    private fun writeCsv(file: File, headers: List<String>, rows: List<List<String>>) {
        val bom = "\uFEFF"
        val sb = StringBuilder(bom)
        sb.append(headers.joinToString(",") { escape(it) }).append("\n")
        rows.forEach { row ->
            sb.append(row.joinToString(",") { escape(it) }).append("\n")
        }
        FileOutputStream(file).use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
    }

    private fun escape(s: String): String {
        val t = s.replace("\"", "\"\"")
        return if (t.contains(",") || t.contains("\"") || t.contains("\n")) "\"$t\"" else t
    }

    fun exportInventoryCsv(context: Context, repo: InventoryRepository): File {
        val rows = repo.getInventoryRows().map {
            listOf(
                it.productName, it.variantName, it.warehouseName,
                format(it.quantity), it.unit, it.batchNumber,
                if (it.expiryDate > 0) SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(it.expiryDate)) else "",
                it.barcode
            )
        }
        val file = File(dir(context), "inventory_${stamp()}.csv")
        writeCsv(
            file,
            listOf("نام کالا", "واریانت", "انبار", "موجودی", "واحد", "سری", "انقضا", "بارکد"),
            rows
        )
        return file
    }

    fun exportSalesCsv(context: Context, repo: InventoryRepository): File {
        val rows = mutableListOf<List<String>>()
        repo.getSales().forEach { s ->
            s.items.forEach { item ->
                rows.add(
                    listOf(
                        s.invoiceNumber,
                        FormatUtils.dateTime(s.timestamp),
                        s.customerName,
                        item.productName,
                        format(item.quantity),
                        format(item.unitPrice),
                        format(item.lineTotal),
                        format(s.total),
                        if (s.isCredit) "نسیه" else "نقدی"
                    )
                )
            }
        }
        val file = File(dir(context), "sales_${stamp()}.csv")
        writeCsv(
            file,
            listOf("شماره", "تاریخ", "مشتری", "کالا", "تعداد", "قیمت", "جمع خط", "جمع فاکتور", "نوع"),
            rows
        )
        return file
    }

    fun exportCustomersCsv(context: Context, repo: InventoryRepository): File {
        val rows = repo.getCustomers().map {
            listOf(it.name, it.phone, it.address, format(it.creditBalance), it.loyaltyPoints.toString())
        }
        val file = File(dir(context), "customers_${stamp()}.csv")
        writeCsv(file, listOf("نام", "تلفن", "آدرس", "بدهی", "امتیاز"), rows)
        return file
    }

    fun exportSuppliersCsv(context: Context, repo: InventoryRepository): File {
        val rows = repo.getSuppliers().map {
            listOf(it.name, it.phone, it.address, format(it.payableBalance))
        }
        val file = File(dir(context), "suppliers_${stamp()}.csv")
        writeCsv(file, listOf("نام", "تلفن", "آدرس", "بدهی ما"), rows)
        return file
    }

    fun exportPurchasesCsv(context: Context, repo: InventoryRepository): File {
        val rows = mutableListOf<List<String>>()
        repo.getPurchases().forEach { p ->
            p.items.forEach { item ->
                rows.add(
                    listOf(
                        p.invoiceNumber,
                        SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(p.timestamp)),
                        p.supplierName,
                        item.productName,
                        format(item.quantity),
                        format(item.unitCost),
                        format(item.lineTotal)
                    )
                )
            }
        }
        val file = File(dir(context), "purchases_${stamp()}.csv")
        writeCsv(
            file,
            listOf("شماره", "تاریخ", "تامین‌کننده", "کالا", "تعداد", "قیمت تمام‌شده", "جمع"),
            rows
        )
        return file
    }


    fun exportDeliveriesCsv(context: Context, repo: InventoryRepository): File {
        val rows = repo.getDeliveries().map {
            listOf(
                it.id.toString(),
                FormatUtils.dateTime(it.createdAt),
                it.customerName,
                it.customerPhone,
                it.address,
                it.courierName,
                it.statusLabel(),
                format(it.total),
                it.note
            )
        }
        val file = File(dir(context), "deliveries_${stamp()}.csv")
        writeCsv(
            file,
            listOf("شناسه", "تاریخ", "مشتری", "تلفن", "آدرس", "پیک", "وضعیت", "مبلغ", "یادداشت"),
            rows
        )
        return file
    }

    fun exportVisitorsCsv(context: Context, repo: InventoryRepository): File {
        val rows = mutableListOf<List<String>>()
        repo.getVisitorOrders().forEach { o ->
            if (o.items.isEmpty()) {
                rows.add(listOf(
                    o.id.toString(), FormatUtils.dateTime(o.createdAt), o.visitorName, o.customerName,
                    o.customerPhone, o.address, o.statusLabel(), format(o.total), "", "", ""
                ))
            } else {
                o.items.forEach { it ->
                    rows.add(listOf(
                        o.id.toString(), FormatUtils.dateTime(o.createdAt), o.visitorName, o.customerName,
                        o.customerPhone, o.address, o.statusLabel(), format(o.total),
                        it.productName, format(it.quantity), format(it.unitPrice)
                    ))
                }
            }
        }
        val file = File(dir(context), "visitors_${stamp()}.csv")
        writeCsv(
            file,
            listOf("شناسه", "تاریخ", "ویزیتور", "مشتری", "تلفن", "آدرس", "وضعیت", "جمع", "کالا", "تعداد", "قیمت"),
            rows
        )
        return file
    }

    /** چند فایل CSV مثل شیت‌های اکسل */
    fun exportAllSheets(context: Context, repo: InventoryRepository): List<File> {
        return listOf(
            exportInventoryCsv(context, repo),
            exportSalesCsv(context, repo),
            exportCustomersCsv(context, repo),
            exportSuppliersCsv(context, repo),
            exportPurchasesCsv(context, repo),
            exportDeliveriesCsv(context, repo),
            exportVisitorsCsv(context, repo)
        )
    }

    fun exportInventoryPdf(context: Context, repo: InventoryRepository): File {
        val settings = repo.getSettings()
        val items = repo.getInventoryRows()
        val lines = items.map {
            InvoicePdfHelper.Line(
                name = "${it.productName} (${it.warehouseName})",
                qty = "${format(it.quantity)} ${it.unit}",
                unitPrice = "—",
                total = "${format(it.quantity)} ${it.unit}"
            )
        }.ifEmpty { listOf(InvoicePdfHelper.Line("بدون موجودی", "-", "-", "-")) }
        return InvoicePdfHelper.build(
            context = context,
            settings = settings,
            docTitle = "گزارش موجودی",
            invoiceNo = "تعداد ردیف: ${items.size}",
            dateText = FormatUtils.dateTime(),
            customerLine = "لیست موجودی انبارها",
            items = lines.take(45),
            summaryRows = listOf("تعداد اقلام" to items.size.toString()),
            grandTotalLabel = "ردیف‌ها",
            grandTotalValue = items.size.toString(),
            footer = "گزارش موجودی کالانو",
            verse = "",
            filePrefix = "inventory"
        )
    }

    fun exportSalesPdf(context: Context, repo: InventoryRepository): File {
        val settings = repo.getSettings()
        val sales = repo.getSales().filter { it.status == "COMPLETED" }.take(50)
        val lines = sales.map {
            InvoicePdfHelper.Line(
                name = "${it.invoiceNumber} · ${it.customerName.ifBlank { "متفرقه" }}",
                qty = if (it.isCredit) "نسیه" else "نقد",
                unitPrice = FormatUtils.date(it.timestamp),
                total = FormatUtils.money(it.total)
            )
        }.ifEmpty { listOf(InvoicePdfHelper.Line("بدون فروش", "-", "-", "-")) }
        val totalSum = sales.sumOf { it.total }
        return InvoicePdfHelper.build(
            context = context,
            settings = settings,
            docTitle = "گزارش فروش",
            invoiceNo = "تعداد فاکتور: ${sales.size}",
            dateText = FormatUtils.dateTime(),
            customerLine = "آخرین فروش‌های ثبت‌شده",
            items = lines,
            summaryRows = listOf("جمع فروش" to FormatUtils.money(totalSum)),
            grandTotalLabel = "جمع کل",
            grandTotalValue = FormatUtils.money(totalSum),
            footer = "گزارش فروش کالانو",
            verse = "",
            filePrefix = "sales"
        )
    }
}
