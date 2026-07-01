package com.example.aiattendancesystem.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.aiattendancesystem.data.AttendanceRecord
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates professional-looking attendance report PDFs using Android's built-in PdfDocument API.
 */
class AttendanceReportGenerator(private val context: Context) {

    companion object {
        // Page dimensions (A4 in points: 595 x 842)
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
        private const val MARGIN = 40f
        private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

        // Colors
        private const val COLOR_PRIMARY = 0xFF0D47A1.toInt()
        private const val COLOR_PRIMARY_DARK = 0xFF002171.toInt()
        private const val COLOR_ACCENT = 0xFF00BFA5.toInt()
        private const val COLOR_SUCCESS = 0xFF4CAF50.toInt()
        private const val COLOR_TEXT_PRIMARY = 0xFF212121.toInt()
        private const val COLOR_TEXT_SECONDARY = 0xFF757575.toInt()
        private const val COLOR_TABLE_HEADER = 0xFF0D47A1.toInt()
        private const val COLOR_TABLE_HEADER_TEXT = 0xFFFFFFFF.toInt()
        private const val COLOR_ROW_EVEN = 0xFFF5F5F5.toInt()
        private const val COLOR_ROW_ODD = 0xFFFFFFFF.toInt()
        private const val COLOR_BORDER = 0xFFE0E0E0.toInt()
        private const val COLOR_DIVIDER = 0xFF0D47A1.toInt()

        // Table column widths (proportions of CONTENT_WIDTH)
        private val COL_NUM_WIDTH = CONTENT_WIDTH * 0.08f
        private val COL_NAME_WIDTH = CONTENT_WIDTH * 0.42f
        private val COL_TIME_WIDTH = CONTENT_WIDTH * 0.25f
        private val COL_STATUS_WIDTH = CONTENT_WIDTH * 0.25f

        private const val ROW_HEIGHT = 30f
        private const val HEADER_ROW_HEIGHT = 35f
    }

    // Paints
    private val titlePaint = Paint().apply {
        color = COLOR_PRIMARY
        textSize = 22f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val subtitlePaint = Paint().apply {
        color = COLOR_TEXT_SECONDARY
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        isAntiAlias = true
    }

    private val headerInfoPaint = Paint().apply {
        color = COLOR_TEXT_PRIMARY
        textSize = 11f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        isAntiAlias = true
    }

    private val headerInfoBoldPaint = Paint().apply {
        color = COLOR_TEXT_PRIMARY
        textSize = 11f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val tableHeaderPaint = Paint().apply {
        color = COLOR_TABLE_HEADER_TEXT
        textSize = 11f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val tableCellPaint = Paint().apply {
        color = COLOR_TEXT_PRIMARY
        textSize = 10f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        isAntiAlias = true
    }

    private val statusPaint = Paint().apply {
        color = COLOR_SUCCESS
        textSize = 10f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = COLOR_BORDER
        strokeWidth = 0.5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val footerPaint = Paint().apply {
        color = COLOR_TEXT_SECONDARY
        textSize = 9f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        isAntiAlias = true
    }

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val dateDisplayFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
    private val dateShortFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * Generate a PDF for a single date's attendance records.
     */
    fun generateSingleDayReport(
        records: List<AttendanceRecord>,
        date: Date
    ): File {
        val document = PdfDocument()
        var pageNumber = 1
        var currentY = MARGIN

        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        // Draw header
        currentY = drawHeader(canvas, currentY, dateDisplayFormat.format(date), records.size)

        // Draw table header
        currentY = drawTableHeader(canvas, currentY)

        // Draw records
        for ((index, record) in records.withIndex()) {
            // Check if we need a new page
            if (currentY + ROW_HEIGHT > PAGE_HEIGHT - 80f) {
                drawFooter(canvas, pageNumber)
                document.finishPage(page)

                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                currentY = MARGIN + 20f

                currentY = drawTableHeader(canvas, currentY)
            }

            currentY = drawTableRow(canvas, currentY, index + 1, record, index % 2 == 0)
        }

        // Draw signature section and footer
        currentY = drawSignatureSection(canvas, currentY + 30f)
        drawFooter(canvas, pageNumber)
        document.finishPage(page)

        // Save to file
        val file = getOutputFile("attendance_${fileDateFormat.format(date)}")
        FileOutputStream(file).use { outputStream ->
            document.writeTo(outputStream)
        }
        document.close()

        return file
    }

    /**
     * Generate a PDF for a date range's attendance records.
     */
    fun generateDateRangeReport(
        records: List<AttendanceRecord>,
        startDate: Date,
        endDate: Date
    ): File {
        val document = PdfDocument()
        var pageNumber = 1
        var currentY = MARGIN

        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        // Draw header
        val dateRange = "${dateShortFormat.format(startDate)} — ${dateShortFormat.format(endDate)}"
        currentY = drawHeader(canvas, currentY, dateRange, records.size)

        // Group records by date
        val groupedRecords = records.groupBy { record ->
            dateShortFormat.format(Date(record.timestamp))
        }

        var globalIndex = 0

        for ((dateStr, dayRecords) in groupedRecords) {
            // Check if we need a new page for the date sub-header
            if (currentY + HEADER_ROW_HEIGHT + ROW_HEIGHT > PAGE_HEIGHT - 80f) {
                drawFooter(canvas, pageNumber)
                document.finishPage(page)

                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                currentY = MARGIN + 20f
            }

            // Draw date sub-header
            currentY = drawDateSubHeader(canvas, currentY, dateStr, dayRecords.size)

            // Draw table header
            currentY = drawTableHeader(canvas, currentY)

            // Draw records for this date
            for ((localIndex, record) in dayRecords.withIndex()) {
                if (currentY + ROW_HEIGHT > PAGE_HEIGHT - 80f) {
                    drawFooter(canvas, pageNumber)
                    document.finishPage(page)

                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = MARGIN + 20f

                    currentY = drawTableHeader(canvas, currentY)
                }

                globalIndex++
                currentY = drawTableRow(canvas, currentY, localIndex + 1, record, localIndex % 2 == 0)
            }

            currentY += 15f // Gap between date groups
        }

        // Draw signature section and footer
        if (currentY + 100f > PAGE_HEIGHT - 80f) {
            drawFooter(canvas, pageNumber)
            document.finishPage(page)

            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            currentY = MARGIN + 20f
        }
        currentY = drawSignatureSection(canvas, currentY + 20f)
        drawFooter(canvas, pageNumber)
        document.finishPage(page)

        // Save to file
        val file = getOutputFile(
            "attendance_${fileDateFormat.format(startDate)}_to_${fileDateFormat.format(endDate)}"
        )
        FileOutputStream(file).use { outputStream ->
            document.writeTo(outputStream)
        }
        document.close()

        return file
    }

    // ── Drawing helpers ──

    private fun drawHeader(canvas: Canvas, startY: Float, dateText: String, totalRecords: Int): Float {
        var y = startY

        // App title
        canvas.drawText("AI ATTENDANCE SYSTEM", MARGIN, y + 20f, titlePaint)
        y += 40f

        // Subtitle
        subtitlePaint.textSize = 14f
        canvas.drawText("Attendance Report", MARGIN, y, subtitlePaint)
        y += 25f

        // Divider
        linePaint.color = COLOR_DIVIDER
        linePaint.strokeWidth = 1f
        canvas.drawLine(MARGIN, y, MARGIN + CONTENT_WIDTH, y, linePaint)
        linePaint.strokeWidth = 0.5f
        linePaint.color = COLOR_BORDER
        y += 18f

        // Info rows
        canvas.drawText("Date:", MARGIN, y, headerInfoBoldPaint)
        canvas.drawText(dateText, MARGIN + 80f, y, headerInfoPaint)
        y += 16f

        canvas.drawText("Generated:", MARGIN, y, headerInfoBoldPaint)
        canvas.drawText(dateTimeFormat.format(Date()), MARGIN + 80f, y, headerInfoPaint)
        y += 16f

        canvas.drawText("Total Present:", MARGIN, y, headerInfoBoldPaint)
        statusPaint.textSize = 11f
        canvas.drawText("$totalRecords", MARGIN + 80f, y, statusPaint)
        statusPaint.textSize = 10f
        y += 20f

        // Bottom divider
        linePaint.color = COLOR_DIVIDER
        linePaint.strokeWidth = 1f
        canvas.drawLine(MARGIN, y, MARGIN + CONTENT_WIDTH, y, linePaint)
        linePaint.strokeWidth = 0.5f
        linePaint.color = COLOR_BORDER
        y += 15f

        return y
    }

    private fun drawDateSubHeader(canvas: Canvas, startY: Float, dateStr: String, count: Int): Float {
        var y = startY

        // Background
        fillPaint.color = 0xFFE8EAF6.toInt()
        canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + 25f, fillPaint)

        // Date text
        headerInfoBoldPaint.color = COLOR_PRIMARY
        canvas.drawText("📅  $dateStr  —  $count record${if (count != 1) "s" else ""}", MARGIN + 10f, y + 17f, headerInfoBoldPaint)
        headerInfoBoldPaint.color = COLOR_TEXT_PRIMARY

        y += 25f
        return y
    }

    private fun drawTableHeader(canvas: Canvas, startY: Float): Float {
        val y = startY

        // Header background
        fillPaint.color = COLOR_TABLE_HEADER
        canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + HEADER_ROW_HEIGHT, fillPaint)

        // Column headers
        var x = MARGIN + 8f
        canvas.drawText("#", x, y + 23f, tableHeaderPaint)
        x += COL_NUM_WIDTH

        canvas.drawText("Name", x, y + 23f, tableHeaderPaint)
        x += COL_NAME_WIDTH

        canvas.drawText("Time", x, y + 23f, tableHeaderPaint)
        x += COL_TIME_WIDTH

        canvas.drawText("Status", x, y + 23f, tableHeaderPaint)

        return y + HEADER_ROW_HEIGHT
    }

    private fun drawTableRow(
        canvas: Canvas,
        startY: Float,
        index: Int,
        record: AttendanceRecord,
        isEven: Boolean
    ): Float {
        val y = startY

        // Row background
        fillPaint.color = if (isEven) COLOR_ROW_EVEN else COLOR_ROW_ODD
        canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + ROW_HEIGHT, fillPaint)

        // Bottom border
        canvas.drawLine(MARGIN, y + ROW_HEIGHT, MARGIN + CONTENT_WIDTH, y + ROW_HEIGHT, linePaint)

        // Cell content
        var x = MARGIN + 8f

        // Index
        tableCellPaint.color = COLOR_TEXT_SECONDARY
        canvas.drawText("$index", x, y + 20f, tableCellPaint)
        x += COL_NUM_WIDTH

        // Name
        tableCellPaint.color = COLOR_TEXT_PRIMARY
        val displayName = if (record.personName.length > 25) {
            record.personName.take(22) + "..."
        } else {
            record.personName
        }
        canvas.drawText(displayName, x, y + 20f, tableCellPaint)
        x += COL_NAME_WIDTH

        // Time
        tableCellPaint.color = COLOR_TEXT_SECONDARY
        canvas.drawText(timeFormat.format(Date(record.timestamp)), x, y + 20f, tableCellPaint)
        x += COL_TIME_WIDTH

        // Status
        canvas.drawText("✔ Present", x, y + 20f, statusPaint)

        // Vertical separators
        var separatorX = MARGIN + COL_NUM_WIDTH
        canvas.drawLine(separatorX, y, separatorX, y + ROW_HEIGHT, linePaint)
        separatorX += COL_NAME_WIDTH
        canvas.drawLine(separatorX, y, separatorX, y + ROW_HEIGHT, linePaint)
        separatorX += COL_TIME_WIDTH
        canvas.drawLine(separatorX, y, separatorX, y + ROW_HEIGHT, linePaint)

        // Left and right borders
        canvas.drawLine(MARGIN, y, MARGIN, y + ROW_HEIGHT, linePaint)
        canvas.drawLine(MARGIN + CONTENT_WIDTH, y, MARGIN + CONTENT_WIDTH, y + ROW_HEIGHT, linePaint)

        return y + ROW_HEIGHT
    }

    private fun drawSignatureSection(canvas: Canvas, startY: Float): Float {
        var y = startY

        canvas.drawText("Authorized Signature:", MARGIN, y, headerInfoBoldPaint)
        y += 8f

        // Signature line
        linePaint.color = COLOR_TEXT_SECONDARY
        linePaint.strokeWidth = 0.8f
        canvas.drawLine(MARGIN, y + 30f, MARGIN + 200f, y + 30f, linePaint)
        linePaint.strokeWidth = 0.5f
        linePaint.color = COLOR_BORDER

        y += 45f

        canvas.drawText("Date: _______________", MARGIN, y, headerInfoPaint)

        return y + 20f
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int) {
        val y = PAGE_HEIGHT - 30f

        // Divider
        linePaint.color = COLOR_DIVIDER
        linePaint.strokeWidth = 0.5f
        canvas.drawLine(MARGIN, y - 10f, MARGIN + CONTENT_WIDTH, y - 10f, linePaint)
        linePaint.color = COLOR_BORDER

        // Footer text
        canvas.drawText("Generated by AI Attendance System", MARGIN, y, footerPaint)

        // Page number (right-aligned)
        val pageText = "Page $pageNumber"
        val pageTextWidth = footerPaint.measureText(pageText)
        canvas.drawText(pageText, MARGIN + CONTENT_WIDTH - pageTextWidth, y, footerPaint)
    }

    private fun getOutputFile(name: String): File {
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) {
            reportsDir.mkdirs()
        }
        return File(reportsDir, "$name.pdf")
    }
}
