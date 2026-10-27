package com.example.anbar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * طراحی فاکتور تجاری: هدر رنگی، جدول اقلام، جمع‌ها، پاورقی.
 */
object InvoicePdfHelper {

    data class Line(
        val name: String,
        val qty: String,
        val unitPrice: String,
        val total: String
    )

    fun build(
        context: Context,
        settings: AppSettings,
        docTitle: String,
        invoiceNo: String,
        dateText: String,
        customerLine: String,
        extraLines: List<String> = emptyList(),
        items: List<Line>,
        summaryRows: List<Pair<String, String>>,
        grandTotalLabel: String,
        grandTotalValue: String,
        footer: String,
        verse: String,
        filePrefix: String
    ): File {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val c = page.canvas

        val primary = Color.parseColor("#1E40AF")
        val primarySoft = Color.parseColor("#DBEAFE")
        val lineGray = Color.parseColor("#CBD5E1")
        val textDark = Color.parseColor("#0F172A")
        val textMuted = Color.parseColor("#64748B")
        val white = Color.WHITE

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = white; textSize = 20f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = white; textSize = 11f; textAlign = Paint.Align.RIGHT
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textDark; textSize = 11f; textAlign = Paint.Align.RIGHT
        }
        val bodyBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textDark; textSize = 11f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT
        }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMuted; textSize = 10f; textAlign = Paint.Align.RIGHT
        }
        val headerCell = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primary; textSize = 10f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT
        }
        val headerCellL = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primary; textSize = 10f; isFakeBoldText = true; textAlign = Paint.Align.LEFT
        }
        val cellR = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textDark; textSize = 10f; textAlign = Paint.Align.RIGHT
        }
        val cellL = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textDark; textSize = 10f; textAlign = Paint.Align.LEFT
        }
        val fillPrimary = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primary; style = Paint.Style.FILL }
        val fillSoft = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primarySoft; style = Paint.Style.FILL }
        val fillStripe = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFC"); style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineGray; style = Paint.Style.STROKE; strokeWidth = 1f
        }
        val strokeStrong = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primary; style = Paint.Style.STROKE; strokeWidth = 1.5f
        }

        val pageW = 595f
        val margin = 36f
        val contentW = pageW - margin * 2
        var y = 0f

        // ---- هدر رنگی ----
        c.drawRect(0f, 0f, pageW, 88f, fillPrimary)
        val shop = settings.shopName.ifBlank { "کالانو" }
        c.drawText(shop, pageW - margin, 32f, titlePaint)
        val contact = buildString {
            if (settings.shopAddress.isNotBlank()) append(settings.shopAddress)
            if (settings.shopPhone.isNotBlank()) {
                if (isNotEmpty()) append("  |  ")
                append(settings.shopPhone)
            }
        }
        if (contact.isNotBlank()) c.drawText(contact, pageW - margin, 52f, subPaint)
        c.drawText(docTitle, pageW - margin, 72f, subPaint)
        y = 108f

        // ---- اطلاعات فاکتور ----
        c.drawText(invoiceNo, pageW - margin, y, bodyBold)
        c.drawText(dateText, margin, y, Paint(body).apply { textAlign = Paint.Align.LEFT })
        y += 18f
        c.drawText(customerLine, pageW - margin, y, body)
        y += 14f
        extraLines.forEach {
            if (it.isNotBlank()) {
                c.drawText(it, pageW - margin, y, muted)
                y += 13f
            }
        }
        y += 10f

        // ---- جدول اقلام ----
        // ستون‌ها از راست: کالا | تعداد | قیمت واحد | جمع
        val colRight = pageW - margin
        val colQty = margin + contentW * 0.42f
        val colPrice = margin + contentW * 0.28f
        val colTotal = margin + 4f
        val tableLeft = margin
        val tableRight = pageW - margin
        val rowH = 22f

        // هدر جدول
        c.drawRoundRect(RectF(tableLeft, y, tableRight, y + rowH), 4f, 4f, fillSoft)
        c.drawText("کالا / شرح", colRight - 4f, y + 15f, headerCell)
        c.drawText("تعداد", colQty, y + 15f, headerCell)
        c.drawText("قیمت واحد", colPrice, y + 15f, headerCell)
        c.drawText("مبلغ", colTotal + 40f, y + 15f, headerCellL)
        y += rowH

        items.forEachIndexed { idx, line ->
            if (y > 720f) return@forEachIndexed // جلوگیری از بیرون‌زدن
            if (idx % 2 == 1) {
                c.drawRect(tableLeft, y, tableRight, y + rowH, fillStripe)
            }
            c.drawLine(tableLeft, y + rowH, tableRight, y + rowH, stroke)
            // نام کالا (کوتاه)
            val name = if (line.name.length > 32) line.name.take(30) + "…" else line.name
            c.drawText(name, colRight - 4f, y + 15f, cellR)
            c.drawText(line.qty, colQty, y + 15f, cellR)
            c.drawText(line.unitPrice, colPrice, y + 15f, cellR)
            c.drawText(line.total, colTotal + 4f, y + 15f, cellL)
            y += rowH
        }
        c.drawRect(tableLeft, y - items.size * rowH - rowH, tableRight, y, strokeStrong)
        y += 16f

        // ---- جمع‌ها ----
        val boxW = 220f
        val boxX = pageW - margin - boxW
        val boxTop = y
        summaryRows.forEach { (label, value) ->
            c.drawText(label, boxX + boxW - 8f, y + 14f, muted)
            c.drawText(value, boxX + 8f, y + 14f, Paint(cellL).apply { textSize = 11f })
            y += 18f
        }
        // مبلغ نهایی
        c.drawRoundRect(RectF(boxX, y, boxX + boxW, y + 28f), 6f, 6f, fillPrimary)
        val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = white; textSize = 12f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT
        }
        val totalValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = white; textSize = 13f; isFakeBoldText = true; textAlign = Paint.Align.LEFT
        }
        c.drawText(grandTotalLabel, boxX + boxW - 10f, y + 19f, totalPaint)
        c.drawText(grandTotalValue, boxX + 10f, y + 19f, totalValPaint)
        y += 44f

        // ---- پاورقی ----
        c.drawLine(margin, y, pageW - margin, y, stroke)
        y += 16f
        c.drawText(footer.ifBlank { "از خرید شما متشکریم" }, pageW / 2f, y, Paint(body).apply {
            textAlign = Paint.Align.CENTER; textSize = 11f; isFakeBoldText = true
        })
        y += 18f
        if (verse.isNotBlank()) {
            c.drawText("« $verse »", pageW / 2f, y, Paint(muted).apply {
                textAlign = Paint.Align.CENTER; textSize = 9f
            })
            y += 14f
        }
        c.drawText(
            "صادر شده با کالانو",
            pageW / 2f,
            820f,
            Paint(muted).apply { textAlign = Paint.Align.CENTER; textSize = 8f }
        )

        doc.finishPage(page)
        val f = File(context.cacheDir, "${filePrefix}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(f).use { doc.writeTo(it) }
        doc.close()
        return f
    }

    fun formatDate(ts: Long): String = FormatUtils.dateTime(ts)
}
