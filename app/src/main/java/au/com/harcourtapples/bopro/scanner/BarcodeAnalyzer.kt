package au.com.harcourtapples.bopro.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

class BarcodeAnalyzer(
    includeCartonFormats: Boolean = false,
    private val onDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(mapOf(
            DecodeHintType.POSSIBLE_FORMATS to buildList {
                add(BarcodeFormat.EAN_13); add(BarcodeFormat.EAN_8)
                // Outer-carton labels: GTIN-14 is printed as ITF-14 (or Code 128)
                if (includeCartonFormats) { add(BarcodeFormat.ITF); add(BarcodeFormat.CODE_128) }
            },
            DecodeHintType.TRY_HARDER to true
        ))
    }

    private var lastScanTime   = 0L
    private var pendingBarcode: String? = null
    private var confirmCount   = 0

    override fun analyze(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastScanTime < COOLDOWN_MS) return
            val rotation = image.imageInfo.rotationDegrees
            val (data, w, h) = extractLuminance(image, rotation)
            val source = PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false)
            try {
                val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
                val barcode = result.text
                if (barcode == pendingBarcode) confirmCount++ else { pendingBarcode = barcode; confirmCount = 1 }
                if (confirmCount >= CONFIRM_NEEDED) {
                    lastScanTime = now; pendingBarcode = null; confirmCount = 0
                    onDetected(barcode)
                }
            } catch (_: NotFoundException) {
            } finally { reader.reset() }
        } finally { image.close() }
    }

    private data class LumaImage(val data: ByteArray, val width: Int, val height: Int)

    private fun extractLuminance(image: ImageProxy, rotationDegrees: Int): LumaImage {
        val yPlane = image.planes[0]; val rowStride = yPlane.rowStride
        val width = image.width; val height = image.height; val buffer = yPlane.buffer
        val luma = ByteArray(width * height)
        if (rowStride == width) { val avail = minOf(buffer.remaining(), luma.size); buffer.get(luma, 0, avail) }
        else {
            val row = ByteArray(rowStride)
            for (y in 0 until height) {
                val toCopy = minOf(rowStride, buffer.remaining()); if (toCopy <= 0) break
                buffer.get(row, 0, toCopy); row.copyInto(luma, y * width, 0, minOf(width, toCopy))
            }
        }
        return when (rotationDegrees) {
            90  -> LumaImage(rotate90cw(luma, width, height),  height, width)
            180 -> LumaImage(rotate180(luma, width, height),   width,  height)
            270 -> LumaImage(rotate90ccw(luma, width, height), height, width)
            else -> LumaImage(luma, width, height)
        }
    }

    private fun rotate90cw(src: ByteArray, srcW: Int, srcH: Int): ByteArray {
        val dst = ByteArray(srcW * srcH)
        for (r in 0 until srcH) for (c in 0 until srcW) dst[c * srcH + (srcH - 1 - r)] = src[r * srcW + c]
        return dst
    }
    private fun rotate90ccw(src: ByteArray, srcW: Int, srcH: Int): ByteArray {
        val dst = ByteArray(srcW * srcH)
        for (r in 0 until srcH) for (c in 0 until srcW) dst[(srcW - 1 - c) * srcH + r] = src[r * srcW + c]
        return dst
    }
    private fun rotate180(src: ByteArray, srcW: Int, srcH: Int): ByteArray {
        val dst = ByteArray(srcW * srcH); for (i in src.indices) dst[src.size - 1 - i] = src[i]; return dst
    }

    companion object {
        private const val COOLDOWN_MS    = 1500L
        private const val CONFIRM_NEEDED = 2
    }
}
