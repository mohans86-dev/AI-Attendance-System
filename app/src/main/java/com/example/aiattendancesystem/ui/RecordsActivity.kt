package com.example.aiattendancesystem.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aiattendancesystem.R
import com.example.aiattendancesystem.data.AppDatabase
import com.example.aiattendancesystem.databinding.ActivityRecordsBinding
import com.example.aiattendancesystem.util.AttendanceReportGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class RecordsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordsBinding
    private lateinit var database: AppDatabase
    private lateinit var adapter: AttendanceAdapter
    private lateinit var reportGenerator: AttendanceReportGenerator

    private val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val selectedCalendar = Calendar.getInstance()

    // Date range selection
    private val rangeStartCalendar = Calendar.getInstance()
    private val rangeEndCalendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRecordsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        database = AppDatabase.getDatabase(this)
        reportGenerator = AttendanceReportGenerator(this)

        setupRecyclerView()
        setupDatePicker()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnExport.setOnClickListener { showExportDialog() }

        // Load today's records by default
        loadRecordsForDate()
    }

    private fun setupRecyclerView() {
        adapter = AttendanceAdapter { record ->
            confirmDeleteRecord(record)
        }
        binding.rvRecords.layoutManager = LinearLayoutManager(this)
        binding.rvRecords.adapter = adapter
    }

    private fun setupDatePicker() {
        binding.tvSelectedDate.text = dateFormat.format(selectedCalendar.time)

        binding.btnSelectDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    selectedCalendar.set(year, month, dayOfMonth)
                    binding.tvSelectedDate.text = dateFormat.format(selectedCalendar.time)
                    loadRecordsForDate()
                },
                selectedCalendar.get(Calendar.YEAR),
                selectedCalendar.get(Calendar.MONTH),
                selectedCalendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun loadRecordsForDate() {
        val calendar = selectedCalendar.clone() as Calendar
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis

        database.attendanceDao().getAttendanceForDay(startOfDay, endOfDay)
            .observe(this) { records ->
                if (records.isNullOrEmpty()) {
                    binding.rvRecords.visibility = View.GONE
                    binding.emptyState.visibility = View.VISIBLE
                    binding.tvAttendanceCount.text = "0 records"
                } else {
                    binding.rvRecords.visibility = View.VISIBLE
                    binding.emptyState.visibility = View.GONE
                    adapter.submitList(records)
                    binding.tvAttendanceCount.text =
                        "${records.size} record${if (records.size != 1) "s" else ""}"
                }
            }
    }

    // ── Export / Print ──

    private fun showExportDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_options, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val optionSelectedDate = dialogView.findViewById<View>(R.id.optionSelectedDate)
        val optionDateRange = dialogView.findViewById<View>(R.id.optionDateRange)
        val dateRangeContainer = dialogView.findViewById<View>(R.id.dateRangeContainer)
        val btnFromDate = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFromDate)
        val btnToDate = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnToDate)
        val btnExportRange = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnExportRange)

        // Show currently selected date in description
        val tvSelectedDateDesc = dialogView.findViewById<android.widget.TextView>(R.id.tvSelectedDateDesc)
        tvSelectedDateDesc.text = dateFormat.format(selectedCalendar.time)

        // Initialize range dates
        rangeStartCalendar.time = selectedCalendar.time
        rangeEndCalendar.time = selectedCalendar.time

        btnFromDate.text = shortDateFormat.format(rangeStartCalendar.time)
        btnToDate.text = shortDateFormat.format(rangeEndCalendar.time)

        // Option 1: Export selected date
        optionSelectedDate.setOnClickListener {
            dialog.dismiss()
            exportSelectedDate()
        }

        // Option 2: Show date range pickers
        optionDateRange.setOnClickListener {
            dateRangeContainer.visibility = View.VISIBLE
            optionSelectedDate.alpha = 0.5f
            optionSelectedDate.isClickable = false
            optionDateRange.alpha = 0.5f
            optionDateRange.isClickable = false
        }

        btnFromDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    rangeStartCalendar.set(year, month, dayOfMonth)
                    btnFromDate.text = shortDateFormat.format(rangeStartCalendar.time)
                },
                rangeStartCalendar.get(Calendar.YEAR),
                rangeStartCalendar.get(Calendar.MONTH),
                rangeStartCalendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        btnToDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    rangeEndCalendar.set(year, month, dayOfMonth)
                    btnToDate.text = shortDateFormat.format(rangeEndCalendar.time)
                },
                rangeEndCalendar.get(Calendar.YEAR),
                rangeEndCalendar.get(Calendar.MONTH),
                rangeEndCalendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        btnExportRange.setOnClickListener {
            dialog.dismiss()
            exportDateRange()
        }

        dialog.show()
    }

    private fun exportSelectedDate() {
        val calendar = selectedCalendar.clone() as Calendar
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis

        Toast.makeText(this, R.string.export_generating, Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val records = database.attendanceDao().getAttendanceForDayList(startOfDay, endOfDay)

                if (records.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RecordsActivity, R.string.export_no_records, Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val file = reportGenerator.generateSingleDayReport(
                    records = records,
                    date = selectedCalendar.time
                )

                withContext(Dispatchers.Main) {
                    sharePdf(file)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RecordsActivity, R.string.export_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun exportDateRange() {
        val startCal = rangeStartCalendar.clone() as Calendar
        startCal.set(Calendar.HOUR_OF_DAY, 0)
        startCal.set(Calendar.MINUTE, 0)
        startCal.set(Calendar.SECOND, 0)
        startCal.set(Calendar.MILLISECOND, 0)

        val endCal = rangeEndCalendar.clone() as Calendar
        endCal.set(Calendar.HOUR_OF_DAY, 23)
        endCal.set(Calendar.MINUTE, 59)
        endCal.set(Calendar.SECOND, 59)
        endCal.set(Calendar.MILLISECOND, 999)

        // Validate range
        if (startCal.after(endCal)) {
            Toast.makeText(this, "Start date must be before end date", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, R.string.export_generating, Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val records = database.attendanceDao().getAttendanceForDayList(
                    startCal.timeInMillis,
                    endCal.timeInMillis
                )

                if (records.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RecordsActivity, R.string.export_no_records, Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val file = reportGenerator.generateDateRangeReport(
                    records = records,
                    startDate = startCal.time,
                    endDate = endCal.time
                )

                withContext(Dispatchers.Main) {
                    sharePdf(file)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RecordsActivity, R.string.export_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sharePdf(file: java.io.File) {
        val uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Attendance Report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, getString(R.string.print_export)))
    }

    // ── Delete ──

    private fun confirmDeleteRecord(record: com.example.aiattendancesystem.data.AttendanceRecord) {
        AlertDialog.Builder(this)
            .setTitle("Delete Record")
            .setMessage("Are you sure you want to delete the attendance record for ${record.personName}?")
            .setPositiveButton("Delete") { _, _ ->
                deleteRecord(record)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteRecord(record: com.example.aiattendancesystem.data.AttendanceRecord) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                database.attendanceDao().deleteAttendance(record)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RecordsActivity, "Record deleted", Toast.LENGTH_SHORT)
                        .show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@RecordsActivity,
                        "Failed to delete record",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
