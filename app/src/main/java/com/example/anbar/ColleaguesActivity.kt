package com.example.anbar

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.anbar.databinding.ActivityColleaguesBinding

class ColleaguesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityColleaguesBinding
    private lateinit var repository: InventoryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocaleToActivity(this)
        super.onCreate(savedInstanceState)
        binding = ActivityColleaguesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarColleagues)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        repository = InventoryRepository(this)
        binding.recyclerColleagues.layoutManager = LinearLayoutManager(this)
        binding.btnAddColleague.setOnClickListener { showAdd() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun load() {
        val list = repository.getColleagues()
        binding.tvEmptyColleagues.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerColleagues.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE

        binding.recyclerColleagues.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val tv = TextView(parent.context).apply {
                    setPadding(32, 28, 32, 28)
                    textSize = 15f
                    setBackgroundColor(0xFFFFFFFF.toInt())
                }
                return object : RecyclerView.ViewHolder(tv) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val c = list[position]
                val bal = repository.getColleagueBalances(c.id)
                val balText = if (bal.isEmpty()) "بدون مانده"
                else "${bal.size} نوع کالا نزد ایشان"
                (holder.itemView as TextView).text = "${c.name}\n${c.phone.ifBlank { "بدون شماره" }}  ·  $balText"
                holder.itemView.setOnClickListener {
                    startActivity(Intent(this@ColleaguesActivity, ColleagueDetailActivity::class.java).apply {
                        putExtra("colleague_id", c.id)
                    })
                }
            }

            override fun getItemCount() = list.size
        }
    }

    private fun showAdd() {
        val etName = EditText(this).apply {
            hint = getString(R.string.colleague_name)
            setPadding(32, 24, 32, 12)
        }
        val etPhone = EditText(this).apply {
            hint = getString(R.string.phone)
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setPadding(32, 12, 32, 24)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 0)
            addView(etName)
            addView(etPhone)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.colleague_new))
            .setView(layout)
            .setPositiveButton("ذخیره") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, getString(R.string.name_required), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                repository.addColleague(name, etPhone.text.toString().trim())
                load()
            }
            .setNegativeButton("لغو", null)
            .show()
    }
}
