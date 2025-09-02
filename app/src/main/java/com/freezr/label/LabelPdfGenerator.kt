package com.freezr.label

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object LabelPdfGenerator {
    data class Label(val title: String, val uuid: String)

    /**
     * Improved printable PDF layout for A4 (or Letter) with clean cut lines.
     * Design goals:
     *  - Dense grid of uniformly sized labels (approx 32mm / 1.25" square codes) with trim guides.
     *  - Optional tiny caption (first 6 chars of uuid) under each for manual identification if needed.
     *  - Outer margin ~12mm; inner gutter 6mm horizontally / 10mm vertically for scissors.
     *  - Works for any list size (multi-page) without pre-creating DB rows (these labels are ephemeral placeholders until scanned).
     */
    fun generate(context: Context, labels: List<Label>, fileName: String = "labels.pdf"): File {
        val doc = PdfDocument()
        val isA4 = true // Could parameterize later; using A4 dimensions at 72dpi.
        val pageWidth = if (isA4) 595 else 612   // 595x842 (A4) vs 612x792 (Letter)
        val pageHeight = if (isA4) 842 else 792
        val margin = 34f // ~12mm
        val codeSize = 90f // points (~31.75mm)
        val captionHeight = 12f
        val hGap = 18f
        val vGap = 28f
        val cutStroke = 0.6f

        val paintCodeBorder = android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = cutStroke
            color = 0xFF444444.toInt()
        }
        val paintCaption = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f
            color = 0xFF000000.toInt()
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val paintGuide = android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 0.4f
            color = 0x33888888
        }

        // Compute how many columns/rows fit
        val availW = pageWidth - 2 * margin
        val availH = pageHeight - 2 * margin
        val cellW = codeSize + hGap
        val cellH = codeSize + captionHeight + vGap
        val cols = ((availW + hGap) / cellW).toInt().coerceAtLeast(1)
        val rows = ((availH + vGap) / cellH).toInt().coerceAtLeast(1)
        val perPage = cols * rows
        val pages = labels.chunked(perPage).ifEmpty { listOf(emptyList()) }

        // Pre-render bitmaps to reuse if duplicate sizes (they all are) -> generate per label since different payload
    pages.forEachIndexed { pageIndex, pageLabels ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            pageLabels.forEachIndexed { i, label ->
                val col = i % cols
                val row = i / cols
                val x = margin + col * cellW
                val y = margin + row * cellH
                val codeRect = android.graphics.RectF(x, y, x + codeSize, y + codeSize)

                // Light outer guide rectangle (full label including caption) for cutting
                canvas.drawRect(x - 2f, y - 2f, x + codeSize + 2f, y + codeSize + captionHeight + 2f, paintGuide)

                // Render QR
                val bmpSize = 256 // reduce to speed up generation (still sufficient for scaling)
                try {
                    val matrix = QrCodeGenerator.matrix(QR_PREFIX + label.uuid, bmpSize)
                    val bmp = Bitmap.createBitmap(bmpSize, bmpSize, Bitmap.Config.ARGB_8888)
                    for (py in 0 until bmpSize) for (px in 0 until bmpSize) {
                        val on = matrix.get(px, py)
                        bmp.setPixel(px, py, if (on) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                    }
                    canvas.drawBitmap(bmp, null, codeRect, null)
                } catch (t: Throwable) {
                    Log.e("LabelPdf", "QR gen failed", t)
                }
                canvas.drawRect(codeRect, paintCodeBorder)

                val caption = label.title.ifBlank { label.uuid.take(6) }
                canvas.drawText(caption, codeRect.centerX(), y + codeSize + captionHeight - 2f, paintCaption)
            }

            // Footer (page number)
            val footerPaint = android.graphics.Paint().apply { textSize = 8f; color = 0xFF555555.toInt(); textAlign = android.graphics.Paint.Align.RIGHT }
            canvas.drawText("Page ${pageIndex + 1}", (pageWidth - margin), (pageHeight - 10f), footerPaint)

            doc.finishPage(page)
        }

        val outDir = File(context.cacheDir, "labels").apply { mkdirs() }
        val outFile = File(outDir, fileName)
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }
}
