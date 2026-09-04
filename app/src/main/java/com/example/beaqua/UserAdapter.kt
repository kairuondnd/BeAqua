package com.example.beaqua

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class UserAdapter(
    private val users: MutableList<User>,
    private val onEdit: (position: Int, updatedUser: User) -> Unit,
    private val onDelete: (position: Int) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvUsername: TextView = itemView.findViewById(R.id.tvUsername)
        val tvAccountType: TextView = itemView.findViewById(R.id.tvAccountType)
        val btnEdit: Button = itemView.findViewById(R.id.btnEditUser)
        val btnDelete: Button = itemView.findViewById(R.id.btnDeleteUser)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun getItemCount() = users.size

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        holder.tvName.text = user.name
        holder.tvUsername.text = user.username
        holder.tvAccountType.text = user.accountType

        holder.btnEdit.setOnClickListener {
            // For simplicity, we can pass the same user. Replace with a dialog for editing in real app
            onEdit(position, user)
        }

        holder.btnDelete.setOnClickListener {
            onDelete(position)
        }
    }
}
