package au.com.harcourtapples.bopro.scanner

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Beep + vibration so the user knows what a scan did without looking at the screen:
 * one short high beep and buzz for a good scan, a low double beep and long double buzz for a bad one.
 */
class ScanFeedback(context: Context) {

    @Suppress("DEPRECATION")
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val tone: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME)
    } catch (_: RuntimeException) { null }   // no audio resources available — vibrate only

    fun confirm() {
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS)
        vibrate(longArrayOf(0, VIBRATE_MS))
    }

    fun reject() {
        tone?.startTone(ToneGenerator.TONE_PROP_NACK, REJECT_BEEP_MS)
        vibrate(longArrayOf(0, REJECT_PULSE_MS, REJECT_GAP_MS, REJECT_PULSE_MS))
    }

    private fun vibrate(pattern: LongArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION") v.vibrate(pattern, -1)
        }
    }

    fun release() { tone?.release() }

    private companion object {
        const val TONE_VOLUME = 80    // 0-100, relative to the media volume
        const val BEEP_MS     = 120
        const val VIBRATE_MS  = 80L
        const val REJECT_BEEP_MS   = 400
        const val REJECT_PULSE_MS  = 150L
        const val REJECT_GAP_MS    = 100L
    }
}

@Composable
fun rememberScanFeedback(): ScanFeedback {
    val context = LocalContext.current
    val feedback = remember { ScanFeedback(context) }
    DisposableEffect(feedback) { onDispose { feedback.release() } }
    return feedback
}
