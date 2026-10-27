package com.example.anbar

import android.graphics.Color
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/** هزینه‌ها و سود خالص — قرمز کسری / سبز سود */
class ExpensesActivity : AppCompatActivity() {

    private lateinit var repo: InventoryRepository
    private lateinit var summary: TextView
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        repo = InventoryRepository(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#F7F9FC"))
        }
        root.addView(TextView(this).apply {
            text = "💰 هزینه‌ها و سود خالص"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        })
        summary = TextView(this).apply {
            textSize = 15f
            setPadding(16, 16, 16, 16)
            setLineSpacing(6f, 1f)
        }
        val card = MaterialCardView(this).apply {
            radius = 16f
            cardElevation = 2f
            setCardBackgroundColor(Color.WHITE)
            addView(summary)
        }
        root.addView(card)
        root.addView(MaterialButton(this).apply {
            text = "＋ ثبت هزینه"
            setOnClickListener { addExpenseDialog() }
        })
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "هزینه‌ها"
        refresh()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun refresh() {
        val income = repo.getDailyKpi().monthSalesTotal
        val expense = repo.expensesTotal(30)
        val net = income - expense
        summary.setTextColor(if (net >= 0) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
        summary.text = buildString {
            append("درآمد ۳۰ روز: ${FormatUtils.money(income)}\n")
            append("هزینه ۳۰ روز: ${FormatUtils.money(expense)}\n")
            append(if (net >= 0) "سود خالص: ${FormatUtils.money(net)}" else "کسری: ${FormatUtils.money(-net)}")
        }

        list.removeAllViews()
        repo.getExpenses().take(50).forEach { e ->
            list.addView(TextView(this).apply {
                text = "${e.title} · ${e.category}\n${FormatUtils.money(e.amount)}  ·  ${FormatUtils.date(e.timestamp)}"
                textSize = 13f
                setPadding(8, 12, 8, 12)
                setTextColor(Color.parseColor("#1A2332"))
            })
        }
    }

    private fun addExpenseDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 16, 40, 8)
        }
        val etTitle = EditText(this).apply { hint = "عنوان هزینه" }
        val etCat = EditText(this).apply { hint = "دسته (اجاره، حقوق، …)" ; setText("سایر") }
        val etAmount = EditText(this).apply {
            hint = "مبلغ"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        box.addView(etTitle); box.addView(etCat); box.addView(etAmount)
        AlertDialog.Builder(this)
            .setTitle("ثبت هزینه")
            .setView(box)
            .setPositiveButton("ثبت") { _, _ ->
                val title = etTitle.text?.toString()?.trim().orEmpty()
                val amount = etAmount.text?.toString()?.toDoubleOrNull() ?: 0.0
                if (title.isEmpty() || amount <= 0) {
                    Toast.makeText(this, "عنوان و مبلغ لازم است", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                repo.addExpense(
                    Expense(
                        0, title,
                        etCat.text?.toString()?.trim().orEmpty().ifEmpty { "سایر" },
                        amount, TimeHelper.stamp()
                    )
                )
                refresh()
            }
            .setNegativeButton("لغو", null)
            .show()
    }
}
