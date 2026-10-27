package com.example.anbar

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 32)
            setBackgroundColor(0xFFF5F7FB.toInt())
        }

        root.addView(TextView(this).apply {
            text = "کالانو"
            textSize = 30f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF0D47A1.toInt())
        })
        root.addView(TextView(this).apply {
            text = "سامانهٔ هوشمند مدیریت انبار و فروش"
            textSize = 14f
            setTextColor(0xFF546E7A.toInt())
            setPadding(0, 4, 0, 4)
        })
        root.addView(TextView(this).apply {
            text = "نسخه ۵.۱.۱"
            textSize = 12f
            setTextColor(0xFF90A4AE.toInt())
            setPadding(0, 0, 0, 16)
        })

        fun section(title: String, body: String) {
            val card = MaterialCardView(this).apply {
                radius = 16f
                cardElevation = 0f
                useCompatPadding = true
                setCardBackgroundColor(0xFFFFFFFF.toInt())
                setContentPadding(18, 16, 18, 16)
            }
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(TextView(this).apply {
                text = title
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(0xFF1565C0.toInt())
                setPadding(0, 0, 0, 8)
            })
            col.addView(TextView(this).apply {
                text = body
                textSize = 13.5f
                setLineSpacing(6f, 1.15f)
                setTextColor(0xFF263238.toInt())
            })
            card.addView(col)
            root.addView(card)
        }

        section(
            "درباره کالانو",
            "کالانو یک سامانهٔ یکپارچه برای کسب‌وکارهای خرد و متوسط است؛ از قفسه انبار تا صندوق فروش، از فاکتور مشتری تا تحویل پیک.\n\n" +
                "هدف ما ساده است: کارهای روزمره فروشگاه و انبار را سریع‌تر، شفاف‌تر و بدون پیچیدگی انجام دهید — روی همان موبایلی که همیشه همراهتان است."
        )

        section(
            "برای چه کسانی ساخته شده؟",
            "• فروشگاه‌ها و مغازه‌های خرده‌فروشی\n" +
                "• انبارهای کوچک و متوسط\n" +
                "• تیم‌های فروش و ویزیتوری\n" +
                "• کسب‌وکارهایی که هم‌زمان ورود کالا، فروش و وصول دارند"
        )

        section(
            "چه کارهایی را آسان می‌کند؟",
            "• ثبت ورود و خروج کالا با اسکن بارکد\n" +
                "• فروش سریع در صندوق با فاکتور حرفه‌ای\n" +
                "• پیگیری بدهی مشتری و سقف اعتبار\n" +
                "• سفارش ویزیتور و وضعیت تحویل پیک\n" +
                "• گزارش فروش، سود و موجودی در چند ثانیه\n" +
                "• پشتیبان‌گیری از اطلاعات کسب‌وکار شما"
        )

        section(
            "تعهد ما",
            "کالانو با تمرکز بر زبان فارسی، سادگی استفاده و نیازهای واقعی بازار ایران طراحی شده است. " +
                "هر قابلیت برای حل یک مشکل واقعی روزمره است، نه صرفاً افزودن گزینهٔ بیشتر.\n\n" +
                "پشتیبانی چندزبانه (فارسی، انگلیسی، عربی) و تقویم شمسی/میلادی، کار با برنامه را برای تیم‌های مختلف آسان می‌کند."
        )

        section(
            "سازنده و پشتیبانی",
            "طراحی و توسعه: مهدی طریری\n" +
                "ایمیل پشتیبانی: mahditariri@gmail.com\n\n" +
                "اگر پیشنهادی برای بهتر شدن کالانو دارید، پیام بفرستید؛ بازخورد شما مسیر نسخه‌های بعدی را می‌سازد."
        )

        root.addView(MaterialButton(this).apply {
            text = "ارسال ایمیل به پشتیبانی"
            setOnClickListener {
                try {
                    startActivity(
                        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:mahditariri@gmail.com")).apply {
                            putExtra(Intent.EXTRA_SUBJECT, "پشتیبانی کالانو")
                        }
                    )
                } catch (_: Exception) { }
            }
        })

        root.addView(TextView(this).apply {
            text = "«از خرید شما متشکریم» — کالانو در کنار کسب‌وکار شما"
            textSize = 12f
            setTextColor(0xFF78909C.toInt())
            setPadding(0, 20, 0, 8)
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        })

        setContentView(ScrollView(this).apply { addView(root) })
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "درباره ما"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
