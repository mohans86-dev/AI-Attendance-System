package com.example.aiattendancesystem.ui

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aiattendancesystem.data.Person
import com.example.aiattendancesystem.databinding.ItemUserBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UsersAdapter(
    private val filesDir: File,
    private val onDeleteClick: (Person) -> Unit
) : ListAdapter<Person, UsersAdapter.UserViewHolder>(UserDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding, filesDir, onDeleteClick)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class UserViewHolder(
        private val binding: ItemUserBinding,
        private val filesDir: File,
        private val onDeleteClick: (Person) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        fun bind(person: Person) {
            binding.tvPersonName.text = person.name
            binding.tvRegisteredDate.text = "Registered: ${dateFormat.format(Date(person.registeredAt))}"

            // Try to load face image
            val faceFile = File(filesDir, "face_${person.id}.jpg")
            if (faceFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(faceFile.absolutePath)
                if (bitmap != null) {
                    binding.ivFace.setImageBitmap(bitmap)
                    binding.tvInitial.visibility = View.GONE
                } else {
                    showInitial(person.name)
                }
            } else {
                showInitial(person.name)
            }

            binding.btnDelete.setOnClickListener {
                onDeleteClick(person)
            }
        }

        private fun showInitial(name: String) {
            binding.ivFace.setImageBitmap(null)
            binding.tvInitial.visibility = View.VISIBLE
            binding.tvInitial.text = if (name.isNotEmpty()) name.first().uppercase() else "?"
        }
    }

    class UserDiffCallback : DiffUtil.ItemCallback<Person>() {
        override fun areItemsTheSame(oldItem: Person, newItem: Person): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Person, newItem: Person): Boolean {
            return oldItem == newItem
        }
    }
}
