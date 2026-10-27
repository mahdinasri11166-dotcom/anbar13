package com.example.anbar

/**
 * ساخت متن چاپ حرارتی ساده (ESC/POS سازگار با اشتراک به اپ پرینتر)
 */
object EscPosHelper {
    fun receiptText(settings: AppSettings, sale: Sale): String = buildString {
        appendLine(settings.shopName.ifBlank { "کالانو" })
        if (settings.shopAddress.isNotBlank()) appendLine(settings.shopAddress)
        if (settings.shopPhone.isNotBlank()) appendLine(settings.shopPhone)
        appendLine("----------------------------")
        appendLine("فاکتور ${sale.invoiceNumber}")
        appendLine(FormatUtils.dateTime(sale.timestamp))
        appendLine("مشتری: ${sale.customerName}")
        appendLine("----------------------------")
        sale.items.forEach {
            appendLine(it.productName)
            appendLine("  ${FormatUtils.qty(it.quantity)} x ${FormatUtils.money(it.unitPrice)} = ${FormatUtils.money(it.amount())}")
        }
        appendLine("----------------------------")
        appendLine("جمع: ${FormatUtils.money(sale.total)}")
        if (sale.cashPaid > 0) appendLine("نقد: ${FormatUtils.money(sale.cashPaid)}")
        if (sale.cardPaid > 0) appendLine("کارت: ${FormatUtils.money(sale.cardPaid)}")
        if (sale.creditPaid > 0 || sale.isCredit) appendLine("نسیه: ${FormatUtils.money(sale.creditPaid.coerceAtLeast(sale.total - sale.paidAmount))}")
        appendLine(settings.invoiceFooter.ifBlank { "متشکریم" })
        if (settings.invoiceExtraNote.isNotBlank()) appendLine(settings.invoiceExtraNote)
        appendLine()
        appendLine()
    }
}
