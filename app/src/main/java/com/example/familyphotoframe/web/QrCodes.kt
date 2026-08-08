package com.example.familyphotoframe.web

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders the pairing URL as an on-screen QR code (spec §15.3).
 *
 * Encoding only — no camera, no scanning, no new permission. Drawn light-on-dark to
 * match the frame's UI while keeping the quiet zone that scanners need.
 */
object QrCodes {

    /** Encode [text] as a square QR bitmap, or null if it cannot be encoded. */
    fun encode(text: String, sizePx: Int): Bitmap? = runCatching {
        val hints = mapOf(
            // Medium correction: still scannable across a room without bloating the
            // module count for a short LAN URL.
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                pixels[row + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }.getOrNull()
}
