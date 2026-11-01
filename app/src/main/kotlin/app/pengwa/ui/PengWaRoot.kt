package app.pengwa.ui

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pengwa.MainActivity
import app.pengwa.R
import app.pengwa.pengWa
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** 底部导航三页：主页 / 集邮册 / 实验室（仅调试版）。导航结构简单，不引入 Navigation 库。 */
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
    var homePage by rememberSaveable { mutableIntStateOf(PAGE_FACE_TO_FACE) }

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
        AlertDialog(
            // 点对话框外面或返回键不算决定：必须明确选"同意"或"拒绝"
            onDismissRequest = {},
            title = { Text(stringResource(R.string.request_title, request.peer.displayName)) },
            text = { Text(stringResource(R.string.request_body, request.peer.id.toString().take(8))) },
            confirmButton = {
                TextButton(onClick = { controller.respond(request.key, accept = true) }) {
                    Text(stringResource(R.string.request_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = { controller.respond(request.key, accept = false) }) {
                    Text(stringResource(R.string.request_decline))
                }
            },
        )
    }

    // 还没设置显示名时，只显示首次设置页，不展示底部导航
    val hasIdentity = identityViewModel.identity != null

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (hasIdentity) {
                NavigationBar {
                    TabItem(MainActivity.TAB_ME, "🏠", R.string.tab_me, selectedTab) { selectedTab = it }
                    TabItem(MainActivity.TAB_CONTACTS, "📖", R.string.tab_contacts, selectedTab) { selectedTab = it }
                    if (debuggable) {
                        TabItem(MainActivity.TAB_LAB, "🧪", R.string.tab_lab, selectedTab) { selectedTab = it }
                    }
                }
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when {
            !hasIdentity || selectedTab == MainActivity.TAB_ME -> HomeScreen(
                viewModel = identityViewModel,
                page = homePage,
                onPageSettled = { homePage = it },
                onMessage = showMessage,
                modifier = contentModifier,
            )
            selectedTab == MainActivity.TAB_CONTACTS ->
                ContactsScreen(onMessage = showMessage, modifier = contentModifier)
            else -> LabScreen(modifier = contentModifier)
        }
    }
}

@Composable
private fun RowScope.TabItem(
    tab: Int,
    emoji: String,
    labelRes: Int,
    selectedTab: Int,
    onSelect: (Int) -> Unit,
) {
    NavigationBarItem(
        selected = selectedTab == tab,
        onClick = { onSelect(tab) },
        // 先用 emoji 占位，不为三个图标引入 material-icons 依赖；换成正式图标时再改
        icon = { Text(emoji) },
        label = { Text(stringResource(labelRes)) },
    )
}
