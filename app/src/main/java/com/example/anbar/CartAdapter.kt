package com.example.anbar

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CartAdapter(
    private val items: MutableList<CartItem>,
    private val onChanged: () -> Unit
) : RecyclerView.Adapter<CartAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.tvCartName)
        val price: TextView = v.findViewById(R.id.tvCartPrice)
        val qty: TextView = v.findViewById(R.id.tvCartQty)
        val lineTotal: TextView = v.findViewById(R.id.tvCartLineTotal)
        val btnPlus: ImageButton = v.findViewById(R.id.btnPlus)
        val btnMinus: ImageButton = v.findViewById(R.id.btnMinus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_cart, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.productName + if (item.variantName.isNotBlank()) " (${item.variantName})" else ""
        holder.price.text = "${FormatUtils.money(item.unitPrice)} / ${item.unit}"
        holder.qty.text = FormatUtils.qty(item.quantity)
        holder.lineTotal.text = FormatUtils.money(item.lineTotal)

        holder.btnPlus.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            items[pos].quantity += 1
            notifyItemChanged(pos)
            onChanged()
        }
        holder.btnMinus.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            if (items[pos].quantity > 1) {
                items[pos].quantity -= 1
                notifyItemChanged(pos)
            } else {
                items.removeAt(pos)
                notifyItemRemoved(pos)
                notifyItemRangeChanged(pos, items.size)
            }
            onChanged()
        }
        // لمس تعداد → ویرایش مستقیم
        holder.qty.setOnClickListener { editQuantity(holder) }
        // لمس طولانی ردیف → ویرایش قیمت
        holder.itemView.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnLongClickListener true
            val et = EditText(holder.itemView.context).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(items[pos].unitPrice.toLong().toString())
                setPadding(40, 30, 40, 30)
            }
            AlertDialog.Builder(holder.itemView.context)
                .setTitle("قیمت ${items[pos].productName}")
                .setView(et)
                .setPositiveButton("تأیید") { _, _ ->
                    items[pos].unitPrice = et.text.toString().toDoubleOrNull() ?: items[pos].unitPrice
                    notifyItemChanged(pos)
                    onChanged()
                }
                .setNegativeButton("لغو", null)
                .show()
            true
        }
    }

    private fun editQuantity(holder: VH) {
        val pos = holder.bindingAdapterPosition
        if (pos == RecyclerView.NO_POSITION) return
        val et = EditText(holder.itemView.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(FormatUtils.qty(items[pos].quantity).replace(",", ""))
            setPadding(40, 30, 40, 30)
            setSelectAllOnFocus(true)
        }
        AlertDialog.Builder(holder.itemView.context)
            .setTitle("تعداد ${items[pos].productName}")
            .setView(et)
            .setPositiveButton("تأیید") { _, _ ->
                val q = et.text.toString().toDoubleOrNull() ?: 1.0
                if (q <= 0) {
                    items.removeAt(pos)
                    notifyItemRemoved(pos)
                    notifyItemRangeChanged(pos, items.size)
                } else {
                    items[pos].quantity = q
                    notifyItemChanged(pos)
                }
                onChanged()
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    override fun getItemCount() = items.size
}
