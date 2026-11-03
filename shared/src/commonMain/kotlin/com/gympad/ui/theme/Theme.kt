package com.gympad.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gympad.resources.Res
import com.gympad.resources.inter_bold
import com.gympad.resources.inter_medium
import com.gympad.resources.inter_regular
import com.gympad.resources.inter_semibold
import org.jetbrains.compose.resources.Font

// White marks selection, so green and yellow can mean only one thing each: done and incomplete.
val DoneGreen = Color(0xFF30D158)
val DoneTint = DoneGreen.copy(alpha = 0.13f)
val PartialYellow = Color(0xFFFFD60A)
val OnStatus = Color(0xFF0A0A0B)

private val GymColorScheme = darkColorScheme(
    primary = Color(0xFFF5F5F7),
    onPrimary = Color(0xFF0A0A0B),
    background = Color(0xFF0A0A0B),
    onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF0A0A0B),
    onSurface = Color(0xFFF5F5F7),
    onSurfaceVariant = Color(0xFFA1A1A6),
    surfaceContainerLow = Color(0xFF111113),
    surfaceContainer = Color(0xFF161618),
    surfaceContainerHigh = Color(0xFF232326),
    surfaceContainerHighest = Color(0xFF2E2E32),
    outline = Color(0xFF808086),
    outlineVariant = Color(0xFF2C2C30),
    error = Color(0xFFFF453A),
    scrim = Color(0xCC000000),
)

private val GymShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
private fun gymTypography(): Typography {
    val inter = FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal),
        Font(Res.font.inter_medium, FontWeight.Medium),
        Font(Res.font.inter_semibold, FontWeight.SemiBold),
        Font(Res.font.inter_bold, FontWeight.Bold),
    )
    fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0, features: String? = null) = TextStyle(
        fontFamily = inter,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
        letterSpacing = tracking.sp,
        fontFeatureSettings = features,
    )
    return Typography(
        headlineSmall = style(24, 30, FontWeight.Bold, tracking = -0.5),
        titleLarge = style(19, 24, FontWeight.SemiBold, tracking = -0.2, features = "tnum"),
        titleMedium = style(17, 22, FontWeight.SemiBold, tracking = -0.2),
        titleSmall = style(15, 20, FontWeight.SemiBold, tracking = -0.1),
        bodyLarge = style(16, 22, FontWeight.Medium, tracking = -0.1),
        bodyMedium = style(15, 21, FontWeight.Normal, tracking = -0.1),
        bodySmall = style(13, 18, FontWeight.Normal),
        labelLarge = style(14, 18, FontWeight.SemiBold, tracking = -0.1),
        labelMedium = style(13, 18, FontWeight.Medium, features = "tnum"),
        labelSmall = style(11, 14, FontWeight.SemiBold, tracking = 0.7),
    )
}

@Composable
fun GymPadTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GymColorScheme,
        typography = gymTypography(),
        shapes = GymShapes,
        content = content,
    )
}
