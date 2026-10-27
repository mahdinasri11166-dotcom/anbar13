package com.example.anbar

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CustomerAdapter(
    private var items: List<Customer>,
    private val onClick: (Customer) -> Unit,
    private val onLongClick: (Customer) -> Unit = {}
) : RecyclerView.Adapter<CustomerAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.tvCustName)
        val phone: TextView = v.findViewById(R.id.tvCustPhone)
        val debt: TextView = v.findViewById(R.id.tvCustDebt)
        val points: TextView = v.findViewById(R.id.tvCustPoints)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_customer, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.name.text = c.name
        holder.phone.text = buildString {
            append(c.phone.ifBlank { "بدون شماره" })
            if (c.address.isNotBlank()) append(" · ${c.address.take(28)}")
        }
        holder.points.text = c.loyaltyPoints.toString()

        if (c.creditBalance > 0) {
            holder.debt.visibility = View.VISIBLE
            holder.debt.text = "بدهی: ${format(c.creditBalance)}"
            holder.debt.setTextColor(Color.parseColor("#F44336"))
        } else {
            holder.debt.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onClick(c) }
        holder.itemView.setOnLongClickListener {
            onLongClick(c)
            true
        }
    }

    override fun getItemCount() = items.size

    fun update(list: List<Customer>) {
        items = list
        notifyDataSetChanged()
    }

    private fun format(v: Double): String =
        if (v == v.toLong().toDouble()) String.format("%,d", v.toLong())
        else String.format("%,.0f", v)
}
