package com.example.aiattendancesystem.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

object BitmapUtils {

    /**
     * Convert an ImageProxy (YUV_420_888) to a Bitmap.
     */
    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(
                nv21,
                ImageFormat.NV21,
                imageProxy.width,
                imageProxy.height,
                null
            )

            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                Rect(0, 0, imageProxy.width, imageProxy.height),
                100,
                out
            )

            val imageBytes = out.toByteArray()
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Crop a face region from the bitmap, applying rotation as needed.
     * Returns a 160×160 bitmap suitable for FaceNet.
     */
    fun cropFace(bitmap: Bitmap, boundingBox: Rect, rotationDegrees: Int): Bitmap? {
        return try {
            // First rotate the original bitmap to match ML Kit's upright coordinate space
            val rotatedBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix()
                matrix.postRotate(rotationDegrees.toFloat())
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }

            // Now crop using the boundingBox, which is relative to the upright image
            val left = boundingBox.left.coerceIn(0, rotatedBitmap.width - 1)
            val top = boundingBox.top.coerceIn(0, rotatedBitmap.height - 1)
            val right = boundingBox.right.coerceIn(left + 1, rotatedBitmap.width)
            val bottom = boundingBox.bottom.coerceIn(top + 1, rotatedBitmap.height)

            val width = right - left
            val height = bottom - top

            if (width <= 0 || height <= 0) return null

            // Crop the face from the rotated (upright) bitmap
            val croppedBitmap = Bitmap.createBitmap(rotatedBitmap, left, top, width, height)

            // Resize to 160×160 for FaceNet
            Bitmap.createScaledBitmap(croppedBitmap, 160, 160, true)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Mirror a bitmap horizontally (needed for front camera preview alignment).
     */
    fun mirrorBitmap(bitmap: Bitmap): Bitmap {
        val matrix = Matrix()
        matrix.preScale(-1f, 1f)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
