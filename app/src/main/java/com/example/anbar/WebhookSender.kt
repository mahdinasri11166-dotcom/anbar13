package com.example.anbar

import android.content.Context
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/**
 * ارسال رویداد به وب‌هوک خارجی (بدون سرور واسط — مستقیم از گوشی)
 */
object WebhookSender {

    private val executor = Executors.newSingleThreadExecutor()

    fun sendEvent(context: Context, event: String, payload: JSONObject) {
        val repo = InventoryRepository(context)
        val config = repo.getWebhookConfig()
        if (!config.enabled || config.url.isBlank()) return

        when (event) {
            "sale" -> if (!config.onSale) return
            "purchase" -> if (!config.onPurchase) return
            "stock" -> if (!config.onStockChange) return
        }

        executor.execute {
            try {
                val body = JSONObject().apply {
                    put("event", event)
                    put("timestamp", System.currentTimeMillis())
                    put("shop", repo.getSettings().shopName)
                    put("data", payload)
                }.toString()

                val url = URL(config.url)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("User-Agent", "UpAnbar-Webhook/1.0")
                    if (config.secret.isNotBlank()) {
                        setRequestProperty("X-Anbar-Signature", hmac(body, config.secret))
                    }
                    doOutput = true
                    connectTimeout = 15000
                    readTimeout = 15000
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
                conn.responseCode // trigger
                conn.disconnect()
                repo.logActivity("WEBHOOK", "$event → ${conn.responseCode}")
            } catch (e: Exception) {
                repo.logActivity("WEBHOOK_FAIL", e.message ?: "error")
            }
        }
    }

    private fun hmac(data: String, secret: String): String {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            Base64.encodeToString(mac.doFinal(data.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }

    fun notifySale(context: Context, sale: Sale) {
        val data = JSONObject().apply {
            put("invoice", sale.invoiceNumber)
            put("total", sale.total)
            put("customer", sale.customerName)
            put("isCredit", sale.isCredit)
        }
        sendEvent(context, "sale", data)
    }

    fun notifyPurchase(context: Context, purchase: Purchase) {
        val data = JSONObject().apply {
            put("invoice", purchase.invoiceNumber)
            put("total", purchase.subtotal)
            put("supplier", purchase.supplierName)
        }
        sendEvent(context, "purchase", data)
    }
}
