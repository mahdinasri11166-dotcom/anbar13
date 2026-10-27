package com.example.anbar

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * تحویل پیک پیشرفته: فیلتر وضعیت، آمار، تماس، تغییر وضعیت سریع
 */
class DeliveryActivity : AppCompatActivity() {

    private lateinit var repository: InventoryRepository
    private lateinit var listLayout: LinearLayout
    private lateinit var statsRow: LinearLayout
    private var filter = "ALL"

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        repository = InventoryRepository(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF1F5F9.toInt())
        }

        // هدر
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 12)
            setBackgroundColor(0xFF1E40AF.toInt())
        }
        header.addView(TextView(this).apply {
            text = "🚚 تحویل پیک"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        header.addView(TextView(this).apply {
            text = "آماده → در مسیر → تحویل‌شده"
            textSize = 12f
            setTextColor(0xFFBFDBFE.toInt())
        })
        root.addView(header)

        statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 12, 12, 4)
        }
        root.addView(statsRow)

        // فیلتر
        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 4, 12, 8)
        }
        listOf(
            "ALL" to "همه",
            "READY" to "آماده",
            "IN_TRANSIT" to "در مسیر",
            "DELIVERED" to "تحویل",
            "CANCELLED" to "لغو"
        ).forEach { (code, label) ->
            chipRow.addView(Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = code == filter
                setOnClickListener {
                    filter = code
                    for (i in 0 until chipRow.childCount) {
                        (chipRow.getChildAt(i) as? Chip)?.isChecked = false
                    }
                    isChecked = true
                    refresh()
                }
            })
        }
        root.addView(chipRow)

        listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 0, 12, 80)
        }
        root.addView(ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            addView(listLayout)
        })

        val frame = android.widget.FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            addView(root)
            addView(FloatingActionButton(this@DeliveryActivity).apply {
                setImageResource(android.R.drawable.ic_input_add)
                contentDescription = "سفارش جدید"
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.END
                ).apply {
                    setMargins(0, 0, 24, 24)
                }
                setOnClickListener { showAddDialog() }
            })
        }

        setContentView(frame)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.mod_delivery)
        refresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun refresh() {
        val all = repository.getDeliveries()
        val ready = all.count { it.status == "READY" }
        val transit = all.count { it.status == "IN_TRANSIT" }
        val done = all.count { it.status == "DELIVERED" }

        statsRow.removeAllViews()
        fun stat(title: String, value: String, color: String) {
            val card = MaterialCardView(this).apply {
                radius = 12f
                cardElevation = 0f
                setCardBackgroundColor(Color.parseColor(color))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(4, 0, 4, 0)
                }
            }
            card.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(8, 12, 8, 12)
                addView(TextView(this@DeliveryActivity).apply {
                    text = value
                    textSize = 18f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(0xFF0F172A.toInt())
                })
                addView(TextView(this@DeliveryActivity).apply {
                    text = title
                    textSize = 11f
                    setTextColor(0xFF475569.toInt())
                })
            })
            statsRow.addView(card)
        }
        stat("آماده", ready.toString(), "#DBEAFE")
        stat("در مسیر", transit.toString(), "#FEF3C7")
        stat("تحویل", done.toString(), "#D1FAE5")

        listLayout.removeAllViews()
        val items = if (filter == "ALL") all else all.filter { it.status == filter }
        if (items.isEmpty()) {
            listLayout.addView(TextView(this).apply {
                text = "موردی نیست — دکمه ＋ را بزنید"
                setPadding(16, 24, 16, 16)
                setTextColor(0xFF64748B.toInt())
            })
            return
        }

        items.forEach { d ->
            val statusColor = when (d.status) {
                "READY" -> "#2563EB"
                "IN_TRANSIT" -> "#D97706"
                "DELIVERED" -> "#059669"
                "CANCELLED" -> "#DC2626"
                else -> "#64748B"
            }
            val card = MaterialCardView(this).apply {
                radius = 16f
                cardElevation = 0f
                strokeWidth = 1
                strokeColor = Color.parseColor("#E2E8F0")
                setCardBackgroundColor(Color.WHITE)
                useCompatPadding = true
            }
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 14, 16, 14)
            }

            // نوار وضعیت
            box.addView(TextView(this).apply {
                text = "  ${d.statusLabel()}  "
                textSize = 11f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                background = GradientDrawable().apply {
                    cornerRadius = 20f
                    setColor(Color.parseColor(statusColor))
                }
                setPadding(12, 6, 12, 6)
            })

            box.addView(TextView(this).apply {
                text = d.customerName
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 10, 0, 4)
            })
            box.addView(TextView(this).apply {
                text = buildString {
                    append(FormatUtils.dateTime(d.createdAt))
                    append("\n")
                    if (d.customerPhone.isNotBlank()) append("☎ ${d.customerPhone}\n")
                    if (d.address.isNotBlank()) append("📍 ${d.address}\n")
                    if (d.courierName.isNotBlank()) append("🛵 پیک: ${d.courierName}\n")
                    append("💰 ${FormatUtils.money(d.total)}")
                    if (d.note.isNotBlank()) append("\n📝 ${d.note}")
                }
                textSize = 12.5f
                setTextColor(0xFF475569.toInt())
                setLineSpacing(4f, 1f)
            })

            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 10, 0, 0)
            }
            if (d.customerPhone.isNotBlank()) {
                actions.addView(MaterialButton(this).apply {
                    text = "تماس"
                    textSize = 11f
                    setOnClickListener {
                        try {
                            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${d.customerPhone}")))
                        } catch (_: Exception) {
                        }
                    }
                })
            }
            fun st(label: String, status: String) {
                actions.addView(MaterialButton(this).apply {
                    text = label
                    textSize = 11f
                    setOnClickListener {
                        repository.updateDeliveryStatus(d.id, status)
                        Toast.makeText(this@DeliveryActivity, "وضعیت: $label", Toast.LENGTH_SHORT).show()
                        refresh()
                    }
                })
            }
            when (d.status) {
                "READY" -> {
                    st("در مسیر", "IN_TRANSIT")
                    st("تحویل", "DELIVERED")
                    st("لغو", "CANCELLED")
                }
                "IN_TRANSIT" -> {
                    st("تحویل شد", "DELIVERED")
                    st("برگشت آماده", "READY")
                }
                "DELIVERED", "CANCELLED" -> {
                    st("آماده مجدد", "READY")
                }
            }
            box.addView(actions)
            card.addView(box)
            listLayout.addView(card)
        }
    }

    private fun showAddDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 16, 40, 8)
        }
        val customers = repository.getCustomers()
        val etName = AutoCompleteTextView(this).apply {
            hint = "نام مشتری * (از لیست یا دستی)"
            threshold = 1
            setAdapter(
                ArrayAdapter(
                    this@DeliveryActivity,
                    android.R.layout.simple_dropdown_item_1line,
                    customers.map { it.name }
                )
            )
        }
        val etPhone = EditText(this).apply {
            hint = "موبایل"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        val etAddress = EditText(this).apply { hint = "آدرس تحویل" }
        val etCourier = EditText(this).apply { hint = "نام پیک" }
        val etTotal = EditText(this).apply {
            hint = "مبلغ"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val etNote = EditText(this).apply { hint = "یادداشت / کالاها" }

        // تکمیل خودکار موبایل و آدرس با انتخاب مشتری
        etName.setOnItemClickListener { _, _, pos, _ ->
            val c = customers.getOrNull(pos) ?: return@setOnItemClickListener
            etPhone.setText(c.phone)
            etAddress.setText(c.address)
        }
        etName.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val q = s?.toString()?.trim().orEmpty()
                if (q.length < 2) return
                val match = customers.find { it.name.equals(q, true) }
                    ?: customers.find { it.name.contains(q, true) }
                if (match != null && etPhone.text.isNullOrBlank()) {
                    etPhone.setText(match.phone)
                    if (etAddress.text.isNullOrBlank()) etAddress.setText(match.address)
                }
            }
        })

        listOf(etName, etPhone, etAddress, etCourier, etTotal, etNote).forEach { layout.addView(it) }

        AlertDialog.Builder(this)
            .setTitle("سفارش تحویل جدید")
            .setView(layout)
            .setPositiveButton("ثبت") { _, _ ->
                val name = etName.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    Toast.makeText(this, "نام مشتری لازم است", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                repository.addDelivery(
                    name,
                    etPhone.text?.toString().orEmpty(),
                    etAddress.text?.toString().orEmpty(),
                    etCourier.text?.toString().orEmpty(),
                    etTotal.text?.toString()?.toDoubleOrNull() ?: 0.0,
                    etNote.text?.toString().orEmpty()
                )
                Toast.makeText(this, "ثبت شد — وضعیت: آماده", Toast.LENGTH_SHORT).show()
                filter = "ALL"
                refresh()
            }
            .setNegativeButton("لغو", null)
            .show()
    }
}
