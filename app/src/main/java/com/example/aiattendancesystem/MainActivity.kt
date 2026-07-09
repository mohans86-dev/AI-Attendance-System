package com.example.aiattendancesystem

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.aiattendancesystem.data.AppDatabase
import com.example.aiattendancesystem.databinding.ActivityMainBinding
import com.example.aiattendancesystem.ui.AttendanceActivity
import com.example.aiattendancesystem.ui.RecordsActivity
import com.example.aiattendancesystem.ui.RegisterActivity
import com.example.aiattendancesystem.ui.RegisteredUsersActivity
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        database = AppDatabase.getDatabase(this)
        setupClickListeners()
        observeStats()
    }

    private fun setupClickListeners() {
        binding.totalRegisteredCard.setOnClickListener {
            startActivity(Intent(this, RegisteredUsersActivity::class.java))
        }

        binding.allRecordsCard.setOnClickListener {
            startActivity(Intent(this, RecordsActivity::class.java))
        }

        binding.cardRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.cardAttendance.setOnClickListener {
            startActivity(Intent(this, AttendanceActivity::class.java))
        }

        binding.cardRecords.setOnClickListener {
            startActivity(Intent(this, RecordsActivity::class.java))
        }

        binding.cardUsers.setOnClickListener {
            startActivity(Intent(this, RegisteredUsersActivity::class.java))
        }
    }

    private fun observeStats() {
        val dao = database.attendanceDao()

        // Observe registered person count
        dao.getPersonCount().observe(this) { count ->
            binding.tvRegisteredCount.text = (count ?: 0).toString()
        }

        // Observe today's attendance count
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis

        dao.getTodayAttendanceCount(startOfDay, endOfDay).observe(this) { count ->
            binding.tvTodayCount.text = (count ?: 0).toString()
        }
    }
}