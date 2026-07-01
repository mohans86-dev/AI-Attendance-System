package com.example.aiattendancesystem.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aiattendancesystem.data.AttendanceRecord
import com.example.aiattendancesystem.databinding.ItemAttendanceRecordBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AttendanceAdapter(
    private val onDeleteClick: (AttendanceRecord) -> Unit
) : ListAdapter<AttendanceRecord, AttendanceAdapter.ViewHolder>(DiffCallback()) {

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAttendanceRecordBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemAttendanceRecordBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(record: AttendanceRecord) {
            binding.tvPersonName.text = record.personName
            binding.tvTimestamp.text = timeFormat.format(Date(record.timestamp))
            binding.tvStatus.text = record.status

            // Show first initial in the circle
            binding.tvInitial.text = record.personName
                .firstOrNull()?.uppercase() ?: "?"

            binding.btnDelete.setOnClickListener {
                onDeleteClick(record)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AttendanceRecord>() {
        override fun areItemsTheSame(oldItem: AttendanceRecord, newItem: AttendanceRecord): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AttendanceRecord, newItem: AttendanceRecord): Boolean {
            return oldItem == newItem
        }
    }
}
