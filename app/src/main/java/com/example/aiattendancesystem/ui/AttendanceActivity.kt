package com.example.aiattendancesystem.ui

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import com.example.aiattendancesystem.data.AppDatabase
import com.example.aiattendancesystem.data.AttendanceRecord
import com.example.aiattendancesystem.data.Person
import com.example.aiattendancesystem.databinding.ActivityAttendanceBinding
import com.example.aiattendancesystem.ml.FaceAnalyzer
import com.example.aiattendancesystem.ml.FaceNetModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AttendanceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAttendanceBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var faceNetModel: FaceNetModel
    private lateinit var database: AppDatabase

    private var registeredPersons: List<Person> = emptyList()
    private var isMarking = false
    private val recognitionThreshold = 1.0f // L2 distance threshold

    private var currentFaceResult: com.example.aiattendancesystem.ml.FaceResult? = null
    private var isFrontCamera = true

    // Cooldown to prevent rapid re-marking
    private var lastMarkedPersonId: Long = -1
    private var lastMarkedTime: Long = 0
    private val markingCooldownMs = 3000L // 3 seconds

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
        binding = ActivityAttendanceBinding.inflate(layoutInflater)
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

        binding.btnCapture.setOnClickListener {
            captureAndRecognize()
        }

        binding.btnSwitchCamera.setOnClickListener {
            switchCamera()
        }

        loadRegisteredPersons()
    }

    private fun loadRegisteredPersons() {
        lifecycleScope.launch(Dispatchers.IO) {
            registeredPersons = database.attendanceDao().getAllPersonsList()

            runOnUiThread {
                if (registeredPersons.isEmpty()) {
                    binding.tvRecognizedName.text = "⚠️"
                    binding.tvRecognitionStatus.text = getString(R.string.no_registered_persons)
                } else {
                    checkCameraPermission()
                }
            }
        }
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
                            rotation = result.rotation,
                            frontCamera = isFrontCamera
                        )
                        binding.tvRecognizedName.text = "Face Detected"
                        binding.tvRecognitionStatus.text = "Align face and tap Capture"
                    }
                },
                onNoFaceDetected = {
                    currentFaceResult = null
                    runOnUiThread {
                        binding.faceOverlay.clearFace()
                        binding.tvRecognizedName.text = "\uD83D\uDD0D"
                        binding.tvRecognitionStatus.text = getString(R.string.scanning)
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

    private fun captureAndRecognize() {
        if (isMarking) return

        val faceResult = currentFaceResult
        if (faceResult == null) {
            Toast.makeText(this, R.string.no_face_detected, Toast.LENGTH_SHORT).show()
            return
        }

        isMarking = true
        binding.tvRecognizedName.text = "Recognizing..."
        binding.tvRecognitionStatus.text = "Please wait"

        val embedding = faceResult.embedding

        var bestMatch: Person? = null
        var bestDistance = Float.MAX_VALUE

        for (person in registeredPersons) {
            val distance = faceNetModel.compareFaces(embedding, person.faceEmbedding)
            if (distance < bestDistance) {
                bestDistance = distance
                bestMatch = person
            }
        }

        runOnUiThread {
            if (bestMatch != null && bestDistance < recognitionThreshold) {
                // Face recognized!
                binding.faceOverlay.setFaceResult(
                    boundingBox = faceResult.boundingBox,
                    name = bestMatch.name,
                    recognized = true,
                    imgWidth = faceResult.imageWidth,
                    imgHeight = faceResult.imageHeight,
                    rotation = faceResult.rotation,
                    frontCamera = isFrontCamera
                )
                binding.tvRecognizedName.text = "✅ \"${bestMatch.name}\" recognized"
                binding.tvRecognitionStatus.text = "Distance: ${"%.2f".format(bestDistance)}"

                markAttendance(bestMatch)
            } else {
                // Face not recognized
                binding.faceOverlay.setFaceResult(
                    boundingBox = faceResult.boundingBox,
                    name = "Unknown",
                    recognized = false,
                    imgWidth = faceResult.imageWidth,
                    imgHeight = faceResult.imageHeight,
                    rotation = faceResult.rotation,
                    frontCamera = isFrontCamera
                )
                binding.tvRecognizedName.text = "❓"
                binding.tvRecognitionStatus.text = getString(R.string.face_not_recognized)
                isMarking = false
            }
        }
    }

    private fun markAttendance(person: Person) {
        isMarking = true

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Check if already marked today
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfDay = calendar.timeInMillis

                calendar.add(Calendar.DAY_OF_MONTH, 1)
                val endOfDay = calendar.timeInMillis

                val alreadyMarked = database.attendanceDao()
                    .isAlreadyMarkedToday(person.id, startOfDay, endOfDay)

                if (alreadyMarked > 0) {
                    runOnUiThread {
                        binding.tvRecognitionStatus.text =
                            getString(R.string.already_marked, person.name)
                        showAttendanceDialog(person.name, isAlreadyMarked = true)
                    }
                } else {
                    val record = AttendanceRecord(
                        personId = person.id,
                        personName = person.name
                    )
                    database.attendanceDao().insertAttendance(record)

                    runOnUiThread {
                        binding.tvRecognitionStatus.text =
                            getString(R.string.attendance_marked, person.name) + " - Present ✅"
                        showAttendanceDialog(person.name, isAlreadyMarked = false)
                    }
                }

                lastMarkedPersonId = person.id
                lastMarkedTime = System.currentTimeMillis()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isMarking = false
            }
        }
    }

    private fun showAttendanceDialog(personName: String, isAlreadyMarked: Boolean) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_attendance_result, null)

        val iconContainer = dialogView.findViewById<View>(R.id.iconContainer)
        val tvIcon = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogIcon)
        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogTitle)
        val tvName = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogPersonName)
        val tvMessage = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogMessage)
        val tvTimestamp = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogTimestamp)
        val btnDismiss = dialogView.findViewById<android.widget.TextView>(R.id.btnDialogDismiss)

        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
        val now = Date()

        tvName.text = personName
        tvTimestamp.text = "${dateFormat.format(now)} • ${timeFormat.format(now)}"

        if (isAlreadyMarked) {
            iconContainer.setBackgroundResource(R.drawable.dialog_warning_circle)
            tvIcon.text = "!"
            tvIcon.setTextColor(ContextCompat.getColor(this, R.color.colorWarning))
            tvTitle.text = getString(R.string.attendance_already_title)
            tvMessage.text = getString(R.string.attendance_already_message)
        } else {
            iconContainer.setBackgroundResource(R.drawable.dialog_success_circle)
            tvIcon.text = "✓"
            tvIcon.setTextColor(ContextCompat.getColor(this, R.color.colorSuccess))
            tvTitle.text = getString(R.string.attendance_success_title)
            tvMessage.text = getString(R.string.attendance_success_message)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnDismiss.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        // Animate the icon with a bounce-in effect
        iconContainer.scaleX = 0f
        iconContainer.scaleY = 0f
        iconContainer.alpha = 0f

        val scaleX = ObjectAnimator.ofFloat(iconContainer, "scaleX", 0f, 1f)
        val scaleY = ObjectAnimator.ofFloat(iconContainer, "scaleY", 0f, 1f)
        val alpha = ObjectAnimator.ofFloat(iconContainer, "alpha", 0f, 1f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 500
            interpolator = OvershootInterpolator(1.5f)
            startDelay = 100
            start()
        }

        // Fade in the text content
        val textViews = listOf(tvTitle, tvName, tvMessage, tvTimestamp, btnDismiss)
        textViews.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 20f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(350)
                .setStartDelay(250L + (index * 60L))
                .start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        faceNetModel.close()
    }
}

