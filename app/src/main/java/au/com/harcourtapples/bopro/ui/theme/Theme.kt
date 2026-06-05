package au.com.harcourtapples.bopro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Blue80  = Color(0xFF90CAF9)
private val Blue40  = Color(0xFF1565C0)
private val Teal80  = Color(0xFF80CBC4)
private val Teal40  = Color(0xFF00695C)

private val DarkColors  = darkColorScheme(primary = Blue80,  secondary = Teal80, tertiary = Blue80)
private val LightColors = lightColorScheme(primary = Blue40, secondary = Teal40, tertiary = Blue40)

private val AppTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
)

@Composable
fun BoproTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography   = AppTypography,
        content      = content
    )
}
