package com.example.beaqua

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ProductAdapter(
    private val productList: MutableList<Product>,
    private val isUserView: Boolean = false,             // true if normal user
    private val onEdit: ((Int) -> Unit)? = null,        // position for admin
    private val onDelete: ((Int) -> Unit)? = null,      // position for admin
    private val onOrder: ((Product, Int) -> Unit)? = null,
    private val onSubscribe: ((Product, Int) -> Unit)? = null,
    private val isRefillView: Boolean = false
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgProduct: ImageView = itemView.findViewById(R.id.imgProduct)
        val tvName: TextView = itemView.findViewById(R.id.tvProductName)
        val tvPrice: TextView = itemView.findViewById(R.id.tvProductPrice)
        val btnEdit: View? = itemView.findViewById(R.id.btnEditProduct)
        val btnDelete: View? = itemView.findViewById(R.id.btnDeleteProduct)
        val btnOrder: View? = itemView.findViewById(R.id.btnOrderProduct)
        val btnSubscribe: View? = itemView.findViewById(R.id.btnSubscribeProduct)
        val etQuantity: EditText? = itemView.findViewById(R.id.etQuantity)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val layoutRes = if (isUserView) R.layout.item_user_product else R.layout.item_product
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = productList[position]

        holder.tvName.text = product.name
        holder.tvPrice.text = "₱${String.format("%.2f", product.price)}"
        
        ContainerImageLoader.load(holder.imgProduct, product.imageUri)

        if (isUserView) {
            holder.btnEdit?.visibility = View.GONE
            holder.btnDelete?.visibility = View.GONE
            holder.btnOrder?.visibility = View.VISIBLE
            holder.btnSubscribe?.visibility = View.GONE
            if (isRefillView) {
                holder.tvName.text = "Water Refill"
                holder.tvPrice.text = "₱${String.format("%.2f", product.price)} per container"
                holder.etQuantity?.hint = "Containers"
                holder.btnOrder?.let { (it as? TextView)?.text = "Add Refill to Cart" }
                holder.btnSubscribe?.let {
                    (it as? TextView)?.text = "Schedule recurring refill"
                }
                holder.itemView.findViewById<TextView?>(R.id.tvQuantityLabel)?.text =
                    "Number of empty containers"
            }

            holder.btnOrder?.setOnClickListener {
                val quantityText = holder.etQuantity?.text.toString()
                val quantity = quantityText.toIntOrNull() ?: 1
                onOrder?.invoke(product, quantity)
            }
            holder.btnSubscribe?.setOnClickListener {
                val quantity = holder.etQuantity?.text.toString().toIntOrNull() ?: 1
                onSubscribe?.invoke(product, quantity)
            }
        } else {
            holder.btnEdit?.visibility = View.VISIBLE
            holder.btnDelete?.visibility = View.VISIBLE
            holder.btnOrder?.visibility = View.GONE
            holder.btnSubscribe?.visibility = View.GONE
            holder.btnEdit?.setOnClickListener { onEdit?.invoke(position) }
            holder.btnDelete?.setOnClickListener { onDelete?.invoke(position) }
        }

    }

    override fun getItemCount(): Int = productList.size
}
