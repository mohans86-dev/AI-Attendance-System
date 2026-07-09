package com.example.aiattendancesystem.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.aiattendancesystem.R
import java.io.File
import java.io.FileOutputStream
import android.graphics.Bitmap
import com.example.aiattendancesystem.data.AppDatabase
import com.example.aiattendancesystem.data.Person
import com.example.aiattendancesystem.databinding.ActivityRegisterBinding
import com.example.aiattendancesystem.ml.FaceAnalyzer
import com.example.aiattendancesystem.ml.FaceNetModel
import com.example.aiattendancesystem.ml.FaceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var faceNetModel: FaceNetModel
    private lateinit var database: AppDatabase

    private var currentFaceResult: FaceResult? = null
    private var isProcessing = false
    private var isFrontCamera = true

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        database = AppDatabase.getDatabase(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        faceNetModel = FaceNetModel(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnCapture.setOnClickListener { captureAndRegister() }
        binding.btnSwitchCamera.setOnClickListener { switchCamera() }

        checkCameraPermission()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val faceAnalyzer = FaceAnalyzer(
                faceNetModel = faceNetModel,
                onFaceDetected = { result ->
                    currentFaceResult = result
                    runOnUiThread {
                        binding.faceOverlay.setFaceResult(
                            boundingBox = result.boundingBox,
                            recognized = false,
                            imgWidth = result.imageWidth,
                            imgHeight = result.imageHeight,
                            frontCamera = isFrontCamera
                        )
                        binding.tvStatus.text = getString(R.string.capture_face)
                    }
                },
                onNoFaceDetected = {
                    currentFaceResult = null
                    runOnUiThread {
                        binding.faceOverlay.clearFace()
                        binding.tvStatus.text = getString(R.string.position_face)
                    }
                }
            )

            imageAnalysis.setAnalyzer(cameraExecutor, faceAnalyzer)

            val cameraSelector = if (isFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun switchCamera() {
        isFrontCamera = !isFrontCamera
        binding.faceOverlay.clearFace()
        currentFaceResult = null

        startCamera()
    }

    private fun captureAndRegister() {
        if (isProcessing) return

        val name = binding.etPersonName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.name_required, Toast.LENGTH_SHORT).show()
            return
        }

        val faceResult = currentFaceResult
        if (faceResult == null) {
            Toast.makeText(this, R.string.no_face_detected, Toast.LENGTH_SHORT).show()
            return
        }

        isProcessing = true
        binding.btnCapture.isEnabled = false
        binding.tvStatus.text = "Confirming registration..."

        showConfirmationDialog(name, faceResult)
    }

    private fun showConfirmationDialog(name: String, faceResult: FaceResult) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_registration, null)
        val ivFacePreview = dialogView.findViewById<android.widget.ImageView>(R.id.ivFacePreview)
        val tvConfirmName = dialogView.findViewById<android.widget.TextView>(R.id.tvConfirmName)
        val btnConfirmRetake = dialogView.findViewById<android.widget.TextView>(R.id.btnConfirmRetake)
        val btnConfirmSave = dialogView.findViewById<android.widget.TextView>(R.id.btnConfirmSave)

        ivFacePreview.setImageBitmap(faceResult.faceBitmap)
        tvConfirmName.text = name

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnConfirmRetake.setOnClickListener {
            dialog.dismiss()
            isProcessing = false
            binding.btnCapture.isEnabled = true
            binding.tvStatus.text = getString(R.string.position_face)
        }

        btnConfirmSave.setOnClickListener {
            dialog.dismiss()
            saveProfile(name, faceResult)
        }

        dialog.show()
    }

    private fun saveProfile(name: String, faceResult: FaceResult) {
        binding.tvStatus.text = "Registering..."
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Check if any person with the exact same name exists in the database
                val existingPersons = database.attendanceDao().getPersonsByName(name)
                var matchedPerson: Person? = null

                // Compare embeddings to see if it's the same person
                for (person in existingPersons) {
                    val distance = faceNetModel.compareFaces(faceResult.embedding, person.faceEmbedding)
                    if (distance < 1.0f) { // Threshold for same person
                        matchedPerson = person
                        break
                    }
                }

                val personId: Long
                if (matchedPerson != null) {
                    // Update/Replace existing person's face embedding
                    val updatedPerson = matchedPerson.copy(
                        faceEmbedding = faceResult.embedding,
                        registeredAt = System.currentTimeMillis()
                    )
                    database.attendanceDao().updatePerson(updatedPerson)
                    personId = matchedPerson.id
                } else {
                    // Save as a new registration
                    val newPerson = Person(
                        name = name,
                        faceEmbedding = faceResult.embedding
                    )
                    personId = database.attendanceDao().insertPerson(newPerson)
                }

                // Overwrite/Save the cropped face image
                saveFaceImage(faceResult.faceBitmap, personId)

                runOnUiThread {
                    Toast.makeText(
                        this@RegisterActivity,
                        if (matchedPerson != null) "Profile updated successfully" else getString(R.string.registration_success),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(
                        this@RegisterActivity,
                        getString(R.string.registration_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    isProcessing = false
                    binding.btnCapture.isEnabled = true
                    binding.tvStatus.text = getString(R.string.position_face)
                }
            }
        }
    }

    private fun saveFaceImage(bitmap: Bitmap, personId: Long) {
        try {
            val file = File(filesDir, "face_$personId.jpg")
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.flush()
            outputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        faceNetModel.close()
    }
}
