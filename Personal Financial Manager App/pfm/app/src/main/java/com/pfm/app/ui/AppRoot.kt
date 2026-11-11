package com.pfm.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pfm.app.security.PinManager

/** ระบบนำทางแบบ back-stack ง่าย ๆ — ฟอร์มใช้ rememberSaveable จึงไม่หายเมื่อสลับแท็บ */
class Nav {
    val stack = mutableStateListOf("home")
    val current get() = stack.last()
    fun tab(r: String) { stack.clear(); stack.add(r) }
    fun go(r: String) { if (current != r) stack.add(r) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    val isTab get() = current in setOf("home", "records", "reports", "settings")
}

@Composable
fun AppRoot(vm: MainViewModel, pins: PinManager, s: UiState, onLockNow: () -> Unit, onRestart: () -> Unit) {
    val nav = remember { Nav() }
    val snack = remember { SnackbarHostState() }
    BackHandler(enabled = nav.stack.size > 1) { nav.back() }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = { if (nav.isTab) BottomBar(nav) },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val r = nav.current
            when {
                r == "home" -> HomeScreen(vm, s, nav, snack)
                r == "records" -> RecordsScreen(vm, s, nav)
                r == "reports" -> ReportsScreen(vm, s, nav)
                r == "settings" -> SettingsScreen(vm, s, nav, pins, onLockNow)
                r == "add" -> AddScreen(vm, s, nav, snack, editId = null)
                r.startsWith("edit:") -> AddScreen(vm, s, nav, snack, editId = r.removePrefix("edit:").toLong())
                r.startsWith("tx:") -> TransactionDetail(vm, s, nav, r.removePrefix("tx:").toLong())
                r == "scan" -> ScanScreen(vm, s, nav)
                r.startsWith("drill:") -> DrillScreen(vm, s, nav, r.removePrefix("drill:"))
                else -> ManageScreens(r, vm, s, nav, pins, onRestart)
            }
        }
    }
}

@Composable
private fun BottomBar(nav: Nav) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        fun item(route: String, label: String, icon: ImageVector) = NavigationBarItem(
            selected = nav.current == route, onClick = { nav.tab(route) },
            icon = { Icon(icon, label) }, label = { Text(label) }
        )
        item("home", "Home", Icons.Outlined.Home)
        item("records", "Records", Icons.Outlined.ListAlt)
        // ปุ่ม Add เด่นตรงกลาง
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            FilledIconButton(onClick = { nav.go("add") }, modifier = Modifier.size(56.dp), shape = CircleShape) {
                Icon(Icons.Filled.Add, "Add", modifier = Modifier.size(30.dp))
            }
        }
        item("reports", "Reports", Icons.Outlined.BarChart)
        item("settings", "Settings", Icons.Outlined.Settings)
    }
}

@Composable
fun ScreenScaffold(title: String, nav: Nav, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.back() }) { Icon(Icons.Filled.ArrowBack, "back") }
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            actions()
        }
        Column(Modifier.fillMaxSize(), content = content)
    }
}
