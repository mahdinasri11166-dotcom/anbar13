package com.example.anbar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.anbar.databinding.ActivityUsersBinding

class UsersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUsersBinding
    private lateinit var repository: InventoryRepository

    private val roleLabels = listOf("مدیر (ADMIN)", "صندوق‌دار (CASHIER)", "انباردار (WAREHOUSE)")
    private val roleCodes = listOf("ADMIN", "CASHIER", "WAREHOUSE")

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        binding = ActivityUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "مدیریت کاربران"

        repository = InventoryRepository(this)

        // Replace MaterialSwitch with SwitchCompat in code if needed
        val switch = findViewById<SwitchCompat>(R.id.switchLoginRequired)
            ?: binding.root.findViewById(R.id.switchLoginRequired)

        // Use binding - may need fix for switch type
        try {
            binding.switchLoginRequired.isChecked = repository.isLoginRequired()
            binding.switchLoginRequired.setOnCheckedChangeListener { _, checked ->
                repository.setLoginRequired(checked)
                Toast.makeText(
                    this,
                    if (checked) "ورود با PIN فعال شد" else "ورود غیرفعال شد",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (_: Exception) {}

        binding.recyclerUsers.layoutManager = LinearLayoutManager(this)
        binding.btnAddUser.setOnClickListener { showAddDialog() }
        load()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun load() {
        val users = repository.getUsers()
        binding.recyclerUsers.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val tv = TextView(parent.context).apply {
                    setPadding(32, 28, 32, 28)
                    textSize = 15f
                    setBackgroundColor(0xFFFFFFFF.toInt())
                }
                return object : RecyclerView.ViewHolder(tv) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val u = users[position]
                val roleFa = when (u.role) {
                    "ADMIN" -> "مدیر"
                    "CASHIER" -> "صندوق‌دار"
                    "WAREHOUSE" -> "انباردار"
                    else -> u.role
                }
                (holder.itemView as TextView).text =
                    "${u.displayName} (@${u.username})\nنقش: $roleFa" +
                            if (!u.isActive) "  [غیرفعال]" else ""
            }

            override fun getItemCount() = users.size
        }
    }

    private fun showAddDialog() {
        val etUser = EditText(this).apply {
            hint = "نام کاربری"
            setPadding(32, 24, 32, 12)
        }
        val etName = EditText(this).apply {
            hint = "نام نمایشی"
            setPadding(32, 12, 32, 12)
        }
        val etPin = EditText(this).apply {
            hint = "PIN (مثلاً 1234)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(32, 12, 32, 12)
        }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@UsersActivity, android.R.layout.simple_spinner_dropdown_item, roleLabels)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 0)
            addView(etUser)
            addView(etName)
            addView(etPin)
            addView(spinner)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.user_new))
            .setView(layout)
            .setPositiveButton("ذخیره") { _, _ ->
                val username = etUser.text.toString().trim()
                val name = etName.text.toString().trim()
                val pin = etPin.text.toString()
                if (username.isEmpty() || pin.length < 4) {
                    Toast.makeText(this, "نام کاربری و PIN حداقل ۴ رقم الزامی است", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val role = roleCodes[spinner.selectedItemPosition]
                repository.addUser(username, name.ifEmpty { username }, pin, role)
                load()
                Toast.makeText(this, getString(R.string.user_added), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("لغو", null)
            .show()
    }
}
