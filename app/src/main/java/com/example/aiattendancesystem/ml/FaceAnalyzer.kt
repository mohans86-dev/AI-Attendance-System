package com.example.aiattendancesystem.ml

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.aiattendancesystem.utils.BitmapUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions

/**
 * CameraX ImageAnalysis.Analyzer that:
 * 1. Detects faces using ML Kit
 * 2. Crops the largest detected face
 * 3. Generates a face embedding using FaceNet
 * 4. Reports results via callback
 */
class FaceAnalyzer(
    private val faceNetModel: FaceNetModel,
    private val onFaceDetected: (FaceResult) -> Unit,
    private val onNoFaceDetected: () -> Unit
) : ImageAnalysis.Analyzer {

    private val faceDetector: FaceDetector

    init {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f)
            .build()
        faceDetector = FaceDetection.getClient(options)
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            rotationDegrees
        )

        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    // Take the largest face
                    val largestFace = faces.maxByOrNull {
                        it.boundingBox.width() * it.boundingBox.height()
                    }

                    largestFace?.let { face ->
                        try {
                            val bitmap = BitmapUtils.imageProxyToBitmap(imageProxy)
                            if (bitmap != null) {
                                // Important: ML Kit bounding box is in the rotated image coordinate space
                                val croppedFace = BitmapUtils.cropFace(
                                    bitmap,
                                    face.boundingBox,
                                    rotationDegrees
                                )

                                if (croppedFace != null) {
                                    val isRotated = rotationDegrees == 90 || rotationDegrees == 270
                                    val rotatedWidth = if (isRotated) inputImage.height else inputImage.width
                                    val rotatedHeight = if (isRotated) inputImage.width else inputImage.height

                                    val embedding = faceNetModel.getFaceEmbedding(croppedFace)
                                    val result = FaceResult(
                                        boundingBox = face.boundingBox,
                                        embedding = embedding,
                                        imageWidth = rotatedWidth,
                                        imageHeight = rotatedHeight,
                                        rotation = rotationDegrees,
                                        faceBitmap = croppedFace
                                    )
                                    onFaceDetected(result)
                                } else {
                                    onNoFaceDetected()
                                }
                            } else {
                                onNoFaceDetected()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            onNoFaceDetected()
                        }
                    }
                } else {
                    onNoFaceDetected()
                }
            }
            .addOnFailureListener {
                it.printStackTrace()
                onNoFaceDetected()
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    fun close() {
        faceDetector.close()
    }
}

/**
 * Result from face analysis containing the bounding box
 * and the computed face embedding.
 */
data class FaceResult(
    val boundingBox: Rect,
    val embedding: FloatArray,
    val imageWidth: Int,
    val imageHeight: Int,
    val rotation: Int,
    val faceBitmap: Bitmap
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FaceResult
        return boundingBox == other.boundingBox && embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = boundingBox.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
