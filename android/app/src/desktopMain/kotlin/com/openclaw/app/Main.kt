package com.openclaw.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.openclaw.app.ui.AppViewModel
import com.openclaw.app.ui.DesktopApp
import com.openclaw.app.ui.LocalStrings
import com.openclaw.app.ui.OpenClawTheme
import com.openclaw.app.ui.Strings

fun main() = application {
    val vm = remember { AppViewModel() }
    LaunchedEffect(Unit) { vm.autoConnect() }
    val state = rememberWindowState(size = DpSize(1240.dp, 820.dp))
    Window(onCloseRequest = ::exitApplication, title = "", state = state) {
        // macOS: let the dark content extend under a transparent title bar so the native
        // traffic-lights sit inside our top bar (single unified bar, like the mockup).
        LaunchedEffect(Unit) {
            runCatching {
                window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
            }
        }
        CompositionLocalProvider(LocalStrings provides Strings(vm.lang.value == "en")) {
            OpenClawTheme { DesktopApp(vm) }
        }
    }
}
