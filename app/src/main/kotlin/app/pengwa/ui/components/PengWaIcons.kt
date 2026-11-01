package app.pengwa.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.pengwa.R

/** Phosphor Core, pinned and licensed in third_party/README.md. */
object PengWaIcons {
    val Identity = R.drawable.ph_identification_card
    val IdentityFill = R.drawable.ph_identification_card_fill
    val Nearby = R.drawable.ph_users
    val NearbyFill = R.drawable.ph_users_fill
    val Book = R.drawable.ph_address_book
    val BookFill = R.drawable.ph_address_book_fill
    val Lab = R.drawable.ph_flask
    val LabFill = R.drawable.ph_flask_fill
    val Tap = R.drawable.ph_contactless_payment
    val Camera = R.drawable.ph_camera
    val Bluetooth = R.drawable.ph_bluetooth
    val Qr = R.drawable.ph_qr_code
    val Settings = R.drawable.ph_gear_six
    val Edit = R.drawable.ph_pencil_simple
    val Close = R.drawable.ph_x
    val Success = R.drawable.ph_check_circle
    val Next = R.drawable.ph_arrow_right
}

@Composable
fun PengWaIcon(
    icon: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Icon(painterResource(icon), contentDescription, modifier.size(24.dp), tint)
}
