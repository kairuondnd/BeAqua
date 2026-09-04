package com.example.beaqua

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AdminUsersActivity : AppCompatActivity() {

    private val users = mutableListOf<User>()
    private lateinit var adapter: UserAdapter
    private var editingUser: User? = null

    private lateinit var etName: EditText
    private lateinit var etContact: EditText
    private lateinit var etAddress: EditText
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var etAccountType: EditText
    private lateinit var btnAddUser: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_users)

        val btnBack = findViewById<ImageButton>(R.id.btnBackAdminUsers)
        btnBack.setOnClickListener { finish() }

        val rvUsers = findViewById<RecyclerView>(R.id.rvUsers)
        etName = findViewById(R.id.etUserName)
        etContact = findViewById(R.id.etUserContact)
        etAddress = findViewById(R.id.etUserAddress)
        etUsername = findViewById(R.id.etUserUsername)
        etPassword = findViewById(R.id.etUserPassword)
        etAccountType = findViewById(R.id.etUserAccountType)
        btnAddUser = findViewById(R.id.btnAddUser)

        adapter = UserAdapter(users,
            onEdit = { _, user ->
                editingUser = user
                etName.setText(user.name)
                etContact.setText(user.contactNumber)
                etAddress.setText(user.address)
                etUsername.setText(user.username)
                etUsername.isEnabled = false // Username is the unique identifier
                etPassword.setText(user.password)
                etAccountType.setText(user.accountType)
                btnAddUser.text = "Update User"
                Toast.makeText(this, "Editing ${user.name}", Toast.LENGTH_SHORT).show()
            },
            onDelete = { position ->
                val username = users[position].username
                FirebaseHelper.deleteUser(username).addOnSuccessListener {
                    users.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    Toast.makeText(this, "User deleted!", Toast.LENGTH_SHORT).show()
                }
            }
        )

        rvUsers.adapter = adapter
        rvUsers.layoutManager = LinearLayoutManager(this)

        loadUsers()

        btnAddUser.setOnClickListener {
            val user = (editingUser ?: User()).copy(
                name = etName.text.toString().trim(),
                contactNumber = etContact.text.toString().trim(),
                address = etAddress.text.toString().trim(),
                username = etUsername.text.toString().trim(),
                password = etPassword.text.toString().trim(),
                accountType = etAccountType.text.toString().trim()
            )

            if (user.username.isEmpty()) {
                Toast.makeText(this, "Username is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            FirebaseHelper.addUser(user).addOnSuccessListener {
                if (editingUser != null) {
                    Toast.makeText(this, "User updated!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "User registered!", Toast.LENGTH_SHORT).show()
                }
                resetFields()
                loadUsers()
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resetFields() {
        editingUser = null
        etName.text.clear()
        etContact.text.clear()
        etAddress.text.clear()
        etUsername.text.clear()
        etUsername.isEnabled = true
        etPassword.text.clear()
        etAccountType.text.clear()
        btnAddUser.text = "Register User"
    }

    private fun loadUsers() {
        FirebaseHelper.getAllUsers().addOnSuccessListener { result ->
            users.clear()
            users.addAll(result.toObjects(User::class.java))
            adapter.notifyDataSetChanged()
        }
    }
}
