package com.example.anbar

import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class CashShiftActivity : AppCompatActivity() {

    private lateinit var repo: InventoryRepository
    private lateinit var tvInfo: TextView
    private lateinit var tvDateTime: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        repo = InventoryRepository(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFFF1F5F9.toInt())
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.shift_title)
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        tvDateTime = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF1565C0.toInt())
            setPadding(0, 8, 0, 8)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        root.addView(tvDateTime)
        tvInfo = TextView(this).apply {
            textSize = 14f
            setPadding(0, 8, 0, 16)
            setTextColor(0xFF334155.toInt())
        }
        root.addView(tvInfo)

        root.addView(MaterialButton(this).apply {
            text = getString(R.string.shift_open)
            setOnClickListener { openShiftDialog() }
        })
        root.addView(MaterialButton(this).apply {
            text = getString(R.string.shift_close)
            setOnClickListener { closeShiftDialog() }
        })

        setContentView(ScrollView(this).apply { addView(root) })
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.shift_title)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun refresh() {
        // همیشه تاریخ/ساعت فعلی (تقویم برنامه)
        tvDateTime.text = "${getString(R.string.shift_datetime)}\n${FormatUtils.dateTime()}"
        val open = repo.getOpenShift()
        if (open == null) {
            tvInfo.text = getString(R.string.shift_no_open)
            return
        }
        val sales = repo.getSales().filter {
            it.timestamp >= open.openedAt && it.status == "COMPLETED"
        }
        val salesTotal = sales.sumOf { it.total }
        val profit = sales.sumOf { it.total - it.items.sumOf { i -> i.quantity * 0.0 } }
        // approximate: use sale totals
        tvInfo.text = buildString {
            append("${getString(R.string.shift_cashier)}: ${open.openedBy.ifBlank { "—" }}\n")
            append("${getString(R.string.shift_datetime)}: ${FormatUtils.dateTime(open.openedAt)}\n")
            append("─────────────────\n")
            append("${getString(R.string.mod_sales_report)}: ${sales.size}\n")
            append("${FormatUtils.money(salesTotal)}\n")
            append("${getString(R.string.shift_opening_cash)}: ${FormatUtils.money(open.openingCash)}")
        }
    }

    private fun openShiftDialog() {
        if (repo.getOpenShift() != null) {
            Toast.makeText(this, getString(R.string.shift_already_open), Toast.LENGTH_SHORT).show()
            return
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 16, 40, 8)
        }
        val tvAuto = TextView(this).apply {
            text = "${getString(R.string.shift_datetime)}\n${FormatUtils.dateTime()}"
            setPadding(0, 0, 0, 12)
            setTextColor(0xFF1565C0.toInt())
        }
        val etName = EditText(this).apply { hint = getString(R.string.shift_cashier) }
        val etCash = EditText(this).apply {
            hint = getString(R.string.shift_opening_cash)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("0")
        }
        box.addView(tvAuto)
        box.addView(etName)
        box.addView(etCash)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.shift_open))
            .setView(box)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = etName.text?.toString()?.trim().orEmpty()
                val cash = etCash.text?.toString()?.toDoubleOrNull() ?: 0.0
                repo.openShift(cash, name)
                Toast.makeText(this, getString(R.string.shift_started), Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun closeShiftDialog() {
        val open = repo.getOpenShift() ?: run {
            Toast.makeText(this, getString(R.string.shift_no_open), Toast.LENGTH_SHORT).show()
            return
        }
        val sales = repo.getSales().filter {
            it.timestamp >= open.openedAt && it.status == "COMPLETED"
        }
        val salesTotal = sales.sumOf { it.total }
        val etClosing = EditText(this).apply {
            hint = getString(R.string.shift_opening_cash)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(salesTotal.toLong().toString())
            setPadding(40, 30, 40, 30)
        }
        val etNote = EditText(this).apply {
            hint = getString(R.string.note)
            setPadding(40, 20, 40, 30)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
            addView(TextView(this@CashShiftActivity).apply {
                text = buildString {
                    append(FormatUtils.dateTime())
                    append("\n")
                    append(getString(R.string.mod_sales_report))
                    append(": ")
                    append(sales.size)
                    append(" · ")
                    append(FormatUtils.money(salesTotal))
                }
                setPadding(0, 0, 0, 12)
            })
            addView(etClosing)
            addView(etNote)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.shift_close))
            .setView(box)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val closing = etClosing.text?.toString()?.toDoubleOrNull() ?: 0.0
                val note = etNote.text?.toString()?.trim().orEmpty()
                repo.closeShift(closing, note)
                Toast.makeText(this, getString(R.string.shift_closed), Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
