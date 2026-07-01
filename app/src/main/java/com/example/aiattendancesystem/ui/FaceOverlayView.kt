package com.example.aiattendancesystem.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Custom overlay view that draws face bounding boxes and recognition labels
 * based on real-time face detection results.
 */
class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var faceBoundingBox: Rect? = null
    private var personName: String? = null
    private var isRecognized: Boolean = false
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var rotation: Int = 0
    private var isFrontCamera: Boolean = true

    // Paint for the detected face box
    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val cornerPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private val textBackgroundPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        isFakeBoldText = true
        isAntiAlias = true
    }

    private val cornerLength = 50f

    fun setFaceResult(
        boundingBox: Rect?,
        name: String? = null,
        recognized: Boolean = false,
        imgWidth: Int = 1,
        imgHeight: Int = 1,
        rotation: Int = 0,
        frontCamera: Boolean = true
    ) {
        this.faceBoundingBox = boundingBox
        this.personName = name
        this.isRecognized = recognized
        this.imageWidth = imgWidth
        this.imageHeight = imgHeight
        this.rotation = rotation
        this.isFrontCamera = frontCamera
        postInvalidate()
    }

    fun clearFace() {
        faceBoundingBox = null
        personName = null
        isRecognized = false
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val box = faceBoundingBox ?: return

        // Calculate coordinates mapping (CENTER_CROP logic)
        val scaleX = width.toFloat() / imageWidth
        val scaleY = height.toFloat() / imageHeight
        val scale = maxOf(scaleX, scaleY)

        val scaledImageWidth = imageWidth * scale
        val scaledImageHeight = imageHeight * scale
        val offsetX = (width - scaledImageWidth) / 2f
        val offsetY = (height - scaledImageHeight) / 2f

        var left = box.left * scale + offsetX
        var top = box.top * scale + offsetY
        var right = box.right * scale + offsetX
        var bottom = box.bottom * scale + offsetY

        // Mirror for front camera
        if (isFrontCamera) {
            val flippedLeft = width - right
            val flippedRight = width - left
            left = flippedLeft
            right = flippedRight
        }

        val scaledRect = RectF(left, top, right, bottom)

        // Draw face detection markers
        val color = when {
            isRecognized -> Color.parseColor("#4CAF50") // Green (Recognized)
            personName == "Unknown" -> Color.parseColor("#F44336") // Red (Failed/Unknown)
            else -> Color.parseColor("#00BFA5") // Teal (Detected, ready to capture)
        }
        drawFaceBox(canvas, scaledRect, color)

        // Draw label
        personName?.let { name ->
            drawLabel(canvas, scaledRect, name, color)
        }
    }

    private fun drawFaceBox(canvas: Canvas, rect: RectF, color: Int) {
        boxPaint.color = color
        boxPaint.alpha = 50
        canvas.drawRect(rect, boxPaint) // Faint full box

        cornerPaint.color = color

        // Corners
        // Top-left
        canvas.drawLine(rect.left, rect.top, rect.left + cornerLength, rect.top, cornerPaint)
        canvas.drawLine(rect.left, rect.top, rect.left, rect.top + cornerLength, cornerPaint)

        // Top-right
        canvas.drawLine(rect.right - cornerLength, rect.top, rect.right, rect.top, cornerPaint)
        canvas.drawLine(rect.right, rect.top, rect.right, rect.top + cornerLength, cornerPaint)

        // Bottom-left
        canvas.drawLine(rect.left, rect.bottom, rect.left + cornerLength, rect.bottom, cornerPaint)
        canvas.drawLine(rect.left, rect.bottom - cornerLength, rect.left, rect.bottom, cornerPaint)

        // Bottom-right
        canvas.drawLine(
            rect.right - cornerLength,
            rect.bottom,
            rect.right,
            rect.bottom,
            cornerPaint
        )
        canvas.drawLine(
            rect.right,
            rect.bottom - cornerLength,
            rect.right,
            rect.bottom,
            cornerPaint
        )
    }

    private fun drawLabel(canvas: Canvas, rect: RectF, name: String, color: Int) {
        val textWidth = textPaint.measureText(name)
        val padding = 20f
        val labelHeight = 60f

        val labelLeft = rect.left
        val labelTop = rect.top - labelHeight - 10f
        val labelRight = labelLeft + textWidth + padding * 2
        val labelBottom = rect.top - 10f

        textBackgroundPaint.color = color
        textBackgroundPaint.alpha = 220
        canvas.drawRoundRect(
            labelLeft,
            labelTop,
            labelRight,
            labelBottom,
            12f,
            12f,
            textBackgroundPaint
        )

        canvas.drawText(name, labelLeft + padding, labelBottom - 18f, textPaint)
    }
}

