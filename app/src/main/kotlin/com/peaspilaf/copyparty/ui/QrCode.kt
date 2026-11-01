package com.peaspilaf.copyparty.ui

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * QR codes for the server address, so a laptop or tablet can join the server by pointing a camera at
 * the phone instead of typing an IP address that changes with every network.
 */
object QrCode {
    /** Modules of white space around the code; the QR spec recommends at least four. */
    private const val QUIET_ZONE_MODULES = 4

    /** The raw module matrix, kept separate from [bitmap] so it can be asserted in a plain JVM test. */
    fun matrix(text: String, size: Int = 512): BitMatrix {
        val hints = HashMap<EncodeHintType, Any>()
        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
        hints[EncodeHintType.MARGIN] = QUIET_ZONE_MODULES
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        return QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    }

    /** A black-on-white bitmap that can be shown with Compose `Image`. */
    fun bitmap(text: String, size: Int = 1024): Bitmap {
        val matrix = matrix(text, size)
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                pixels[row + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
