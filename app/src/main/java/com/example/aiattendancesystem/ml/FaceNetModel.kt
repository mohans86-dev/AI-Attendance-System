package com.example.aiattendancesystem.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * Wrapper around the FaceNet TFLite model.
 * Takes a 160×160 RGB face bitmap and produces a 128-dimensional embedding.
 */
class FaceNetModel(context: Context) {

    private val interpreter: Interpreter
    private val inputSize = 160
    private val embeddingSize = 128

    init {
        val model = loadModelFile(context, "mobile_face_net.tflite")
        val options = Interpreter.Options().apply {
            setNumThreads(4)
        }
        interpreter = Interpreter(model, options)
    }

    /**
     * Generate a face embedding from a cropped face bitmap.
     * The bitmap will be resized to 160×160 internally.
     */
    fun getFaceEmbedding(faceBitmap: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(faceBitmap, inputSize, inputSize, true)
        val inputBuffer = bitmapToByteBuffer(resized)

        // Output shape: [1, 128]
        val output = Array(1) { FloatArray(embeddingSize) }
        interpreter.run(inputBuffer, output)

        // L2 normalize the embedding
        return l2Normalize(output[0])
    }

    /**
     * Compare two face embeddings using L2 (Euclidean) distance.
     * Lower distance = more similar faces.
     * Typical threshold: < 1.0 for same person.
     */
    fun compareFaces(embedding1: FloatArray, embedding2: FloatArray): Float {
        var sum = 0f
        val minLen = minOf(embedding1.size, embedding2.size)
        for (i in 0 until minLen) {
            val diff = embedding1[i] - embedding2[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    private fun l2Normalize(embedding: FloatArray): FloatArray {
        var sum = 0f
        for (value in embedding) {
            sum += value * value
        }
        val norm = sqrt(sum)
        if (norm > 0) {
            for (i in embedding.indices) {
                embedding[i] /= norm
            }
        }
        return embedding
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            // Normalize pixel values to [-1, 1] range (standard FaceNet preprocessing)
            byteBuffer.putFloat(((pixel shr 16 and 0xFF) - 127.5f) / 127.5f) // R
            byteBuffer.putFloat(((pixel shr 8 and 0xFF) - 127.5f) / 127.5f)  // G
            byteBuffer.putFloat(((pixel and 0xFF) - 127.5f) / 127.5f)        // B
        }
        return byteBuffer
    }

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun close() {
        interpreter.close()
    }
}
