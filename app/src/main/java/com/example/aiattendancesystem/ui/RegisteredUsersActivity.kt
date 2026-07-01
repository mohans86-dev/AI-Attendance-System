package com.example.aiattendancesystem.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aiattendancesystem.data.AppDatabase
import com.example.aiattendancesystem.data.Person
import com.example.aiattendancesystem.databinding.ActivityRegisteredUsersBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RegisteredUsersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisteredUsersBinding
    private lateinit var database: AppDatabase
    private lateinit var usersAdapter: UsersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRegisteredUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        database = AppDatabase.getDatabase(this)

        binding.btnBack.setOnClickListener { finish() }

        setupRecyclerView()
        observeUsers()
    }

    private fun setupRecyclerView() {
        usersAdapter = UsersAdapter(filesDir) { person ->
            confirmDeleteUser(person)
        }
        binding.rvUsers.layoutManager = LinearLayoutManager(this)
        binding.rvUsers.adapter = usersAdapter
    }

    private fun observeUsers() {
        database.attendanceDao().getAllPersons().observe(this) { persons ->
            usersAdapter.submitList(persons)
            if (persons.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                binding.rvUsers.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.rvUsers.visibility = View.VISIBLE
            }
        }
    }

    private fun confirmDeleteUser(person: Person) {
        AlertDialog.Builder(this)
            .setTitle("Delete User")
            .setMessage("Are you sure you want to delete ${person.name}? This will remove their face from the system, but keep their past attendance records.")
            .setPositiveButton("Delete") { _, _ ->
                deleteUser(person)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteUser(person: Person) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Delete from database
                database.attendanceDao().deletePerson(person)

                // Delete face image file
                val faceFile = File(filesDir, "face_${person.id}.jpg")
                if (faceFile.exists()) {
                    faceFile.delete()
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@RegisteredUsersActivity,
                        "${person.name} deleted",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@RegisteredUsersActivity,
                        "Failed to delete user",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
