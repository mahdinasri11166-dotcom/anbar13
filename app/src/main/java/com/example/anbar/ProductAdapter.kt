package com.example.anbar

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProductAdapter(
    private var items: List<InventoryRow>,
    private val onItemClick: (InventoryRow) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvVariant: TextView = view.findViewById(R.id.tvVariant)
        val tvWarehouse: TextView = view.findViewById(R.id.tvWarehouse)
        val tvBatch: TextView = view.findViewById(R.id.tvBatch)
        val tvExpiry: TextView = view.findViewById(R.id.tvExpiry)
        val tvQuantity: TextView = view.findViewById(R.id.tvQuantity)
        val tvUnit: TextView = view.findViewById(R.id.tvUnit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.productName
        holder.tvWarehouse.text = "انبار: ${item.warehouseName}"
        holder.tvQuantity.text = formatQty(item.quantity)
        holder.tvUnit.text = item.unit
        when {
            item.isExpired -> holder.tvQuantity.setTextColor(Color.parseColor("#C62828"))
            item.isExpiringSoon -> holder.tvQuantity.setTextColor(Color.parseColor("#F9A825"))
            item.quantity <= 0 -> holder.tvQuantity.setTextColor(Color.parseColor("#C62828"))
            else -> holder.tvQuantity.setTextColor(Color.parseColor("#2E7D32"))
        }

        if (item.variantName.isNotBlank()) {
            holder.tvVariant.visibility = View.VISIBLE
            holder.tvVariant.text = item.variantName
        } else {
            holder.tvVariant.visibility = View.GONE
        }

        if (item.batchNumber.isNotBlank()) {
            holder.tvBatch.visibility = View.VISIBLE
            holder.tvBatch.text = "سری: ${item.batchNumber}"
        } else {
            holder.tvBatch.visibility = View.GONE
        }

        if (item.expiryDate > 0) {
            holder.tvExpiry.visibility = View.VISIBLE
            val dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(item.expiryDate))
            when {
                item.isExpired -> {
                    holder.tvExpiry.text = "منقضی: $dateStr"
                    holder.tvExpiry.setTextColor(Color.RED)
                }
                item.isExpiringSoon -> {
                    holder.tvExpiry.text = "نزدیک انقضا: $dateStr"
                    holder.tvExpiry.setTextColor(Color.parseColor("#E65100"))
                }
                else -> {
                    holder.tvExpiry.text = "انقضا: $dateStr"
                    holder.tvExpiry.setTextColor(Color.GRAY)
                }
            }
        } else {
            holder.tvExpiry.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newList: List<InventoryRow>) {
        items = newList
        notifyDataSetChanged()
    }

    private fun formatQty(q: Double): String {
        return if (q == q.toLong().toDouble()) q.toLong().toString()
        else String.format("%.2f", q)
    }
}
