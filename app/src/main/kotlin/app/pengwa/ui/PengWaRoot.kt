package app.pengwa.ui

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pengwa.MainActivity
import app.pengwa.R
import app.pengwa.exchange.ExchangeMode
import app.pengwa.pengWa
import app.pengwa.ui.components.ExchangeRequestDialog
import app.pengwa.ui.components.PengWaIcon
import app.pengwa.ui.components.PengWaIcons
import app.pengwa.ui.theme.ContentMaxWidth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** 底部导航：我的名片、附近娃娃、集邮册；实验室仅调试版显示。 */
@Composable
fun PengWaRoot(
    requestedTab: Int?,
    onTabRequestHandled: () -> Unit,
    identityViewModel: IdentityViewModel = viewModel(),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    val debuggable = remember {
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(MainActivity.TAB_ME) }
    var nfcRole by rememberSaveable { mutableStateOf(ExchangeMode.NfcDisplay) }

    // 通知栏按钮要求切换到某一页
    LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            selectedTab = requestedTab
            onTabRequestHandled()
        }
    }

    // 碰到人了（NFC、蓝牙或二维码）：震一下并提示。新事件会顶掉还没消失的旧提示。
    LaunchedEffect(resources) {
        context.pengWa.meetings.events.collectLatest { event ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            snackbarHostState.showSnackbar(resources.getString(R.string.meeting_snackbar, event.name))
        }
    }

    // 有人通过蓝牙向我申请交换：必须我同意才会交换。应用在前台时弹对话框，在后台时是带按钮的通知
    val controller = remember { context.pengWa.bleExchange }
    val incoming by controller.incoming.collectAsStateWithLifecycle()
    incoming.firstOrNull()?.let { request ->
        ExchangeRequestDialog(
            name = request.peer.displayName, shortId = request.peer.id.toString().take(8),
            onAccept = { controller.respond(request.key, accept = true) },
            onDecline = { controller.respond(request.key, accept = false) },
        )
    }

    // 还没设置显示名时，只显示首次设置页，不展示底部导航
    val hasIdentity = identityViewModel.identity != null

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (hasIdentity) {
                PengWaNavigation(selectedTab, debuggable) { selectedTab = it }
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            val contentModifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxSize()
            when {
                !hasIdentity || selectedTab == MainActivity.TAB_ME -> HomeScreen(
                    viewModel = identityViewModel,
                    role = nfcRole,
                    onRoleChange = { nfcRole = it },
                    onMessage = showMessage,
                    modifier = contentModifier,
                )
                selectedTab == MainActivity.TAB_NEARBY -> NearbyScreen(modifier = contentModifier)
                selectedTab == MainActivity.TAB_CONTACTS ->
                    ContactsScreen(onMessage = showMessage, modifier = contentModifier, onExchange = { selectedTab = MainActivity.TAB_ME })
                else -> LabScreen(modifier = contentModifier)
            }
        }
    }
}

@Composable
internal fun PengWaNavigation(selectedTab: Int, debuggable: Boolean, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        TabItem(MainActivity.TAB_ME, PengWaIcons.Identity, PengWaIcons.IdentityFill, R.string.tab_me, selectedTab, onSelect)
        TabItem(MainActivity.TAB_NEARBY, PengWaIcons.Nearby, PengWaIcons.NearbyFill, R.string.tab_nearby, selectedTab, onSelect)
        TabItem(MainActivity.TAB_CONTACTS, PengWaIcons.Book, PengWaIcons.BookFill, R.string.tab_contacts, selectedTab, onSelect)
        if (debuggable) TabItem(MainActivity.TAB_LAB, PengWaIcons.Lab, PengWaIcons.LabFill, R.string.tab_lab, selectedTab, onSelect)
    }
}

@Composable
private fun RowScope.TabItem(
    tab: Int,
    icon: Int,
    selectedIcon: Int,
    labelRes: Int,
    selectedTab: Int,
    onSelect: (Int) -> Unit,
) {
    val selected = selectedTab == tab
    NavigationBarItem(
        selected = selected, onClick = { onSelect(tab) },
        icon = { PengWaIcon(if (selected) selectedIcon else icon, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
        label = { Text(stringResource(labelRes)) },
    )
}
