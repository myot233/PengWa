package app.pengwa.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Immutable
data class StatusColors(val success: Color, val successContainer: Color, val warning: Color, val warningContainer: Color)

internal val LightStatusColors = StatusColors(Color(0xFF256B57), Color(0xFFD8F1E7), Color(0xFF795500), Color(0xFFFFEBC4))
internal val DarkStatusColors = StatusColors(Color(0xFF9CD8BF), Color(0xFF183E32), Color(0xFFF5CF85), Color(0xFF49370C))
val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

internal val PengWaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(24.dp),
)

/** One readable column on phones, tablets and landscape windows. */
val PageGutter @Composable get() = with(LocalDensity.current) {
    if (LocalWindowInfo.current.containerSize.width.toDp() < 360.dp) 16.dp else 20.dp
}
val ContentMaxWidth = 600.dp
