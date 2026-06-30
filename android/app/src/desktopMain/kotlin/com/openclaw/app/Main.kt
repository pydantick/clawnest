package com.openclaw.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
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
    val paletteOpen = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.autoConnect() }
    val state = rememberWindowState(size = DpSize(1240.dp, 820.dp))
    Window(
        onCloseRequest = ::exitApplication, title = "", state = state,
        onPreviewKeyEvent = { e ->
            if (e.type == KeyEventType.KeyDown && e.isMetaPressed && e.key == Key.K) {
                paletteOpen.value = !paletteOpen.value; true
            } else false
        },
    ) {
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
            OpenClawTheme { DesktopApp(vm, paletteOpen) }
        }
    }
}
