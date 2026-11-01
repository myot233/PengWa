package app.pengwa.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pengwa.MainActivity
import app.pengwa.R
import app.pengwa.ui.components.PengWaIcon
import app.pengwa.ui.components.PengWaIcons
import app.pengwa.ui.theme.ContentMaxWidth

private data class Destination(val tab: Int, val icon: Int, val selectedIcon: Int, val label: Int)

private val destinations = listOf(
    Destination(MainActivity.TAB_ME, PengWaIcons.Identity, PengWaIcons.IdentityFill, R.string.tab_me),
    Destination(MainActivity.TAB_NEARBY, PengWaIcons.Nearby, PengWaIcons.NearbyFill, R.string.tab_nearby),
    Destination(MainActivity.TAB_CONTACTS, PengWaIcons.Book, PengWaIcons.BookFill, R.string.tab_contacts),
    Destination(MainActivity.TAB_LAB, PengWaIcons.Lab, PengWaIcons.LabFill, R.string.tab_lab),
)

/** Bottom tabs on phones; a scrollable rail leaves enough height for an intact QR code in short landscape windows. */
@Composable
internal fun PengWaScaffold(
    selectedTab: Int,
    debuggable: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showNavigation: Boolean = true,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    val size = LocalWindowInfo.current.containerSize
    val useRail = with(LocalDensity.current) { size.height.toDp() < 480.dp && size.width > size.height }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = snackbarHost,
        bottomBar = { if (showNavigation && !useRail) PengWaNavigation(selectedTab, debuggable, onSelect) },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (showNavigation && useRail) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState()),
                    containerColor = MaterialTheme.colorScheme.surface,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                ) {
                    destinations.take(if (debuggable) 4 else 3).forEach { item ->
                        NavigationRailItem(
                            selected = selectedTab == item.tab,
                            onClick = { onSelect(item.tab) },
                            icon = { DestinationIcon(item, selectedTab == item.tab) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                content(Modifier.widthIn(max = ContentMaxWidth).fillMaxSize())
            }
        }
    }
}

@Composable
internal fun PengWaNavigation(selectedTab: Int, debuggable: Boolean, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        destinations.take(if (debuggable) 4 else 3).forEach { item ->
            NavigationBarItem(
                selected = selectedTab == item.tab,
                onClick = { onSelect(item.tab) },
                icon = { DestinationIcon(item, selectedTab == item.tab) },
                label = { Text(stringResource(item.label)) },
            )
        }
    }
}

@Composable
private fun DestinationIcon(item: Destination, selected: Boolean) {
    PengWaIcon(
        if (selected) item.selectedIcon else item.icon,
        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
