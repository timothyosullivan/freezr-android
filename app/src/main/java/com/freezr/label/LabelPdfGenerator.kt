package com.freezr.label

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

object LabelPdfGenerator {
    data class Label(val title: String, val uuid: String)

    fun generate(context: Context, labels: List<Label>, fileName: String = "labels.pdf"): File {
    // TODO: Support multiple pages when labels.size > page capacity (currently 40)
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 @72dpi
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        val cols = 4
        val rows = 10
        val cellW = pageInfo.pageWidth / cols
        val cellH = pageInfo.pageHeight / rows
        val size = (cellW * 0.6).toInt()
        val paint = android.graphics.Paint().apply { textSize = 10f }
        labels.take(cols*rows).forEachIndexed { idx, l ->
            val col = idx % cols
            val row = idx / cols
            val left = col * cellW + (cellW - size)/2
            val top = row * cellH + 8
            val matrix = QrCodeGenerator.matrix(l.uuid, size)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (y in 0 until size) for (x in 0 until size) {
                val on = matrix.get(x * matrix.size / size, y * matrix.size / size)
                bmp.setPixel(x,y, if (on) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
            canvas.drawBitmap(bmp, left.toFloat(), top.toFloat(), null)
            canvas.drawText(l.title.take(12), left.toFloat(), (top + size + 12).toFloat(), paint)
        }
        doc.finishPage(page)
        val outDir = File(context.cacheDir, "labels").apply { mkdirs() }
        val outFile = File(outDir, fileName)
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }
}
