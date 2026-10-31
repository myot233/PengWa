package app.pengwa.ui

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pengwa.MainActivity
import app.pengwa.R
import app.pengwa.pengWa
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** 底部导航三页：名片 / 集邮册 / 实验室（仅调试版）。导航结构简单，不引入 Navigation 库。 */
@Composable
fun PengWaRoot(
    requestedTab: Int?,
    onTabRequestHandled: () -> Unit,
    identityViewModel: IdentityViewModel = viewModel(),
) {
    val context = LocalContext.current
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

    // 通知栏按钮要求切换到某一页
    LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            selectedTab = requestedTab
            onTabRequestHandled()
        }
    }

    // 碰到人了（NFC 或蓝牙）：震一下并提示。新事件会顶掉还没消失的旧提示。
    LaunchedEffect(Unit) {
        context.pengWa.meetings.events.collectLatest { event ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            snackbarHostState.showSnackbar(context.getString(R.string.meeting_snackbar, event.name))
        }
    }

    // 还没设置显示名时，只显示首次设置页，不展示底部导航
    val hasIdentity = identityViewModel.identity != null

    // "展示名片"：只有名片页在前台时，别人的手机才能把我们当 NDEF 标签读到；
    // 切到别的页、退到后台、息屏都会立刻关闭。
    val tagShareGate = context.pengWa.tagShareGate
    LifecycleResumeEffect(selectedTab, hasIdentity) {
        if (hasIdentity && selectedTab == MainActivity.TAB_ME) tagShareGate.arm()
        onPauseOrDispose { tagShareGate.disarm() }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (hasIdentity) {
                NavigationBar {
                    TabItem(MainActivity.TAB_ME, "🪪", R.string.tab_me, selectedTab) { selectedTab = it }
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
            !hasIdentity || selectedTab == MainActivity.TAB_ME ->
                IdentityScreen(viewModel = identityViewModel, modifier = contentModifier)
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
