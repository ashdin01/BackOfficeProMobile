package au.com.harcourtapples.bopro.ui.receive.receive

import android.os.Build
import android.view.ViewGroup
import android.widget.EditText
import android.widget.NumberPicker
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Dates travel as ISO "yyyy-MM-dd" strings. java.time needs API 26 and minSdk is 24,
// so this uses Calendar / SimpleDateFormat.
private fun isoFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

fun isoDateInDays(days: Int): String {
    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }
    return isoFormat().format(cal.time)
}

/** "Fri 20 Nov" for display; falls back to the raw string if it can't be parsed. */
fun prettyDate(iso: String): String = try {
    val d: Date = isoFormat().parse(iso)!!
    SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(d)
} catch (_: Exception) { iso }

private fun daysInMonth(year: Int, month: Int): Int =
    Calendar.getInstance().apply { clear(); set(year, month - 1, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH)

private fun parseIso(iso: String?): Triple<Int, Int, Int>? = try {
    val cal = Calendar.getInstance().apply { time = isoFormat().parse(iso!!)!! }
    Triple(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
} catch (_: Exception) { null }

private val MONTHS = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** One scrolling wheel (native NumberPicker) with a label above it. */
@Composable
private fun Wheel(label: String, value: Int, min: Int, max: Int, textColor: Int,
                  formatter: ((Int) -> String)? = null, modifier: Modifier = Modifier,
                  onChange: (Int) -> Unit) {
    val current by rememberUpdatedState(onChange)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AndroidView(factory = { ctx ->
            NumberPicker(ctx).apply {
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS   // wheel only, no keyboard
                wrapSelectorWheel = false
                formatter?.let { setFormatter { v -> it(v) } }
                setOnValueChangedListener { _, _, new -> current(new) }
            }
        }, update = { np ->
            // Order matters: widen the range before setting a value that may sit outside the old one.
            np.minValue = min
            np.maxValue = max
            np.value = value.coerceIn(min, max)
            applyWheelColor(np, textColor)
        })
    }
}

/** Tint the wheel text. NumberPicker.setTextColor is API 29+; older devices keep the default colour. */
private fun applyWheelColor(np: NumberPicker, color: Int) {
    if (Build.VERSION.SDK_INT >= 29) np.textColor = color
    for (i in 0 until np.childCount) (np.getChildAt(i) as? EditText)?.setTextColor(color)
}

/**
 * Use-by entry: day / month / year wheels, plus quick offsets. The value stays null
 * ("not set") until the user touches a wheel or a chip, so a date is never saved by default.
 */
@Composable
fun UseByPicker(value: String?, onChange: (String?) -> Unit) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val today = remember { Calendar.getInstance() }
    // Wheels show the chosen date, or today until one is chosen.
    val shown = parseIso(value) ?: Triple(today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1, today.get(Calendar.DAY_OF_MONTH))
    val (year, month, day) = shown
    val thisYear = today.get(Calendar.YEAR)

    fun emit(y: Int, m: Int, d: Int) {
        val dd = d.coerceAtMost(daysInMonth(y, m))   // e.g. 31 Jan -> Feb clamps to 28/29
        onChange("%04d-%02d-%02d".format(Locale.US, y, m, dd))
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (value != null) "Use-by: ${prettyDate(value)}" else "Use-by: not set — spin the wheels or tap a shortcut",
            style = MaterialTheme.typography.titleSmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Wheel("Day", day, 1, daysInMonth(year, month), textColor, modifier = Modifier.weight(1f)) { emit(year, month, it) }
            Wheel("Month", month, 1, 12, textColor, formatter = { MONTHS[it - 1] }, modifier = Modifier.weight(1f)) { emit(year, it, day) }
            Wheel("Year", year, thisYear, thisYear + 5, textColor, modifier = Modifier.weight(1f)) { emit(it, month, day) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            for ((text, days) in listOf("Today" to 0, "+3d" to 3, "+5d" to 5, "+7d" to 7)) {
                FilterChip(selected = value == isoDateInDays(days), onClick = { onChange(isoDateInDays(days)) },
                    label = { Text(text) })
            }
            if (value != null) TextButton(onClick = { onChange(null) }) { Text("Clear") }
        }
    }
}
