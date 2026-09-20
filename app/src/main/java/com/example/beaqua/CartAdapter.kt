package com.example.beaqua

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CartAdapter(
    private val cartItems: MutableList<CartItem>,
    private val onRemove: (CartItem) -> Unit
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    inner class CartViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgProduct: ImageView = itemView.findViewById(R.id.imgCartProduct)
        val tvName: TextView = itemView.findViewById(R.id.tvCartProductName)
        val tvPrice: TextView = itemView.findViewById(R.id.tvCartProductPrice)
        val tvStation: TextView = itemView.findViewById(R.id.tvCartStationName)
        val tvDetails: TextView = itemView.findViewById(R.id.tvCartDetails)
        val btnRemove: ImageButton = itemView.findViewById(R.id.btnRemoveCartItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cart, parent, false)
        return CartViewHolder(view)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        val item = cartItems[position]
        holder.tvName.text = if (item.offeringType == OFFERING_REFILL) {
            "REFILL • ${item.productName}"
        } else {
            item.productName
        }
        holder.tvPrice.text = "₱${String.format("%.2f", item.productPrice)}"
        holder.tvStation.text = item.stationName
        holder.tvDetails.text = if (item.offeringType == OFFERING_REFILL) {
            "${item.emptyContainerCount.coerceAtLeast(item.quantity)} empty containers" +
                if (item.refillInstructions.isBlank()) "" else "\n${item.refillInstructions}"
        } else {
            "Qty: ${item.quantity} | ${item.containerType}"
        }

        ContainerImageLoader.load(holder.imgProduct, item.imageUri)

        holder.btnRemove.setOnClickListener {
            onRemove(item)
        }
    }

    override fun getItemCount(): Int = cartItems.size

    fun removeItem(item: CartItem) {
        val position = cartItems.indexOf(item)
        if (position != -1) {
            cartItems.removeAt(position)
            notifyItemRemoved(position)
        }
    }
}
