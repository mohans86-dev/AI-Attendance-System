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
        binding.tvStatus.text = "Registering..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val person = Person(
                    name = name,
                    faceEmbedding = faceResult.embedding
                )
                val personId = database.attendanceDao().insertPerson(person)

                // Save the cropped face image
                saveFaceImage(faceResult.faceBitmap, personId)

                runOnUiThread {
                    Toast.makeText(
                        this@RegisterActivity,
                        getString(R.string.registration_success),
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
