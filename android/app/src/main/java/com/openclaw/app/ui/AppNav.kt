package com.openclaw.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private data class Tab(val route: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("chat", Icons.Filled.Home),
    Tab("agents", Icons.Filled.Person),
    Tab("journal", Icons.AutoMirrored.Filled.List),
    Tab("settings", Icons.Filled.Settings),
)

@Composable
fun AppNav(vm: AppViewModel) {
    val ready = vm.conn.value == ConnState.Ready
    val haveUi = vm.personas.isNotEmpty()
    when {
        // User explicitly chose to edit creds → the form.
        vm.showForm.value -> ConnectScreen(vm)
        // Connected, or we still hold the loaded UI (a drop after login) → main app. A
        // reconnect just shows a banner, never tears the interface down.
        ready || haveUi -> MainScaffold(vm, reconnecting = !ready)
        // Returning user, cold start, nothing loaded yet → tidy splash, not the VPS form.
        vm.hasSavedCreds() -> ConnectingSplash(vm)
        // First-ever launch → setup form.
        else -> ConnectScreen(vm)
    }
}

@Composable
private fun MainScaffold(vm: AppViewModel, reconnecting: Boolean) {
    val nav = rememberNavController()
    val accent = personaColor(vm.currentPersona()?.themeColor)
    val kbOpen = keyboardOpen()
    Scaffold(
        containerColor = Bg,
        bottomBar = { if (!kbOpen) BottomBar(nav, accent) },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            if (reconnecting) ReconnectBanner()
            NavHost(
                nav,
                startDestination = "chat",
                modifier = Modifier.weight(1f),
                enterTransition = { fadeIn(tween(110)) },
                exitTransition = { fadeOut(tween(110)) },
                popEnterTransition = { fadeIn(tween(110)) },
                popExitTransition = { fadeOut(tween(110)) },
            ) {
                composable("chat") { ChatScreen(vm) }
                composable("agents") {
                    AgentsScreen(
                        vm,
                        onOpenChat = { id -> vm.selectPersona(id); nav.navigate("chat") },
                        onEdit = { id -> nav.navigate("editor/$id") },
                        onCreate = { nav.navigate("editor/_new") },
                    )
                }
                composable("journal") { JournalScreen(vm) }
                composable("settings") { SettingsScreen(vm) }
                composable("editor/{id}") { entry ->
                    PersonaEditorScreen(vm, entry.arguments?.getString("id"), onBack = { nav.popBackStack() })
                }
            }
        }
    }
}

/** Thin, non-blocking strip shown over the live UI while a logged-in session reconnects. */
@Composable
private fun ReconnectBanner() {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF2A2614)).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(Modifier.size(13.dp), color = Orange, strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(LocalStrings.current.reconnecting, color = Orange, fontSize = 13.sp)
    }
}

/** Calm splash for a returning user while the saved connection is being restored. */
@Composable
private fun ConnectingSplash(vm: AppViewModel) {
    val s = LocalStrings.current
    Column(
        Modifier.fillMaxSize().background(Bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = Green, strokeWidth = 3.dp)
        Spacer(Modifier.height(20.dp))
        Text(s.signingIn, color = TextC, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (vm.host.value.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(vm.host.value, color = TextDim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            s.editCreds, color = TextDim, fontSize = 13.sp,
            modifier = Modifier.clickable { vm.editConnection() }.padding(8.dp),
        )
    }
}

@Composable
private fun BottomBar(nav: NavHostController, accent: Color) {
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val s = LocalStrings.current
    // Nav sits on Bg (not a Surface bar) with no pill indicator, so it reads as a
    // separate row of icons below the floating input — matching the mockup.
    NavigationBar(containerColor = Bg) {
        TABS.forEach { tab ->
            val selected = route == tab.route
            val label = when (tab.route) {
                "chat" -> s.tabChat
                "agents" -> s.tabAgents
                "journal" -> s.tabJournal
                else -> s.tabMore
            }
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) nav.navigate(tab.route) {
                        popUpTo("chat") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = accent,
                    selectedTextColor = accent,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = TextDim,
                    unselectedTextColor = TextDim,
                ),
            )
        }
    }
}

/** True while the soft keyboard (IME) is visible — used to hide the bottom bar. */
@Composable
private fun keyboardOpen(): Boolean {
    val view = LocalView.current
    var open by remember { mutableStateOf(false) }
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            open = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) ?: false
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    return open
}
