package com.freezr.label

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeGenerator {
    private const val DEFAULT_SIZE = 256
    private val hints = mapOf(EncodeHintType.MARGIN to 0)

    data class Matrix(val size: Int, val bits: BooleanArray) { fun get(x:Int,y:Int)= bits[y*size + x] }
    fun matrix(data: String, size: Int = DEFAULT_SIZE): Matrix {
        val m = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size, hints)
        val arr = BooleanArray(m.width * m.height)
        for (y in 0 until m.height) for (x in 0 until m.width) arr[y* m.width + x] = m[x,y]
        return Matrix(m.width, arr)
    }
}
