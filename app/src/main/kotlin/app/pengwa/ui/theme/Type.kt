package app.pengwa.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontWeight = weight,
    fontSize = size.sp, lineHeight = line.sp,
)

internal val PengWaTypography = Typography(
    headlineMedium = type(24, 32, FontWeight.SemiBold),
    headlineSmall = type(22, 30, FontWeight.SemiBold),
    titleLarge = type(24, 32, FontWeight.SemiBold),
    titleMedium = type(18, 26, FontWeight.SemiBold),
    titleSmall = type(16, 24, FontWeight.Medium),
    bodyLarge = type(16, 24), bodyMedium = type(14, 20), bodySmall = type(12, 16),
    labelLarge = type(14, 20, FontWeight.Medium),
    labelMedium = type(12, 16, FontWeight.Medium), labelSmall = type(12, 16),
)
