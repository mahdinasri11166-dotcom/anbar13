package com.example.anbar

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class OnboardingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 40, 24, 28)
            setBackgroundColor(0xFF0D47A1.toInt())
        }
        root.addView(TextView(this).apply {
            text = "شروع سریع با کالانو"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "فقط ۳ قدم — بعد آماده‌اید"
            textSize = 14f
            setTextColor(0xFFBBDEFB.toInt())
            setPadding(0, 6, 0, 20)
        })

        fun tip(n: String, title: String, body: String) {
            val c = MaterialCardView(this).apply {
                radius = 14f
                cardElevation = 0f
                useCompatPadding = true
                setCardBackgroundColor(0xFFFFFFFF.toInt())
                setContentPadding(16, 14, 16, 14)
            }
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(TextView(this).apply {
                text = "$n  $title"
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(0xFF0D47A1.toInt())
            })
            col.addView(TextView(this).apply {
                text = body
                textSize = 13f
                setTextColor(0xFF455A64.toInt())
                setPadding(0, 4, 0, 0)
            })
            c.addView(col)
            root.addView(c)
        }

        tip("۱", "حالت را انتخاب کنید", "بالای صفحه: «انبار» برای ورود کالا · «فروشگاه» برای فروش و صندوق")
        tip("۲", "کالا و مشتری بسازید", "در انبار: کالای جدید · در فروشگاه: مشتریان")
        tip("۳", "بفروشید یا ورود بزنید", "صندوق فروش یا ورود کالا — فاکتور خودکار صادر می‌شود")

        root.addView(TextView(this).apply {
            text = "نکته: از منوی کناری می‌توانید زبان، تم و تاریخ شمسی را عوض کنید."
            textSize = 12f
            setTextColor(0xFF90CAF9.toInt())
            setPadding(4, 16, 4, 16)
        })

        root.addView(MaterialButton(this).apply {
            text = "متوجه شدم — ورود به برنامه"
            setOnClickListener {
                getSharedPreferences("kalano_onboard", MODE_PRIVATE).edit().putBoolean("done", true).apply()
                startActivity(Intent(this@OnboardingActivity, MainActivity::class.java))
                finish()
            }
        })
        setContentView(root)
    }
}
