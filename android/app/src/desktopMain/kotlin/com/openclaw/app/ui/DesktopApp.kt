package com.openclaw.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.app.Persona
import com.openclaw.app.rememberAttachmentPicker

// Slightly darker shades for the desktop chrome (rail / panels) than the chat surface.
private val Rail = Color(0xFF0C0D10)
private val Panel = Color(0xFF101216)
private val PanelLine = Color(0xFF20242C)

private val SWATCHES = listOf(
    "#22C55E" to Green, "#8A63D2" to Purple, "#E8833A" to Orange,
    "#3B82F6" to Blue, "#AAB0BA" to Gray, "#EC4899" to Pink,
)
private val DESK_TOOLS = listOf("bash", "ssh", "nmap", "sqlmap", "git", "docker", "python", "metasploit", "burp")

/** Desktop root: same connection gating as the phone, but the main app is the 3-pane shell. */
@Composable
fun DesktopApp(vm: AppViewModel, paletteOpen: MutableState<Boolean>) {
    val ready = vm.conn.value == ConnState.Ready
    val haveUi = vm.personas.isNotEmpty()
    when {
        vm.showForm.value -> ConnectScreen(vm)
        ready || haveUi -> DesktopShell(vm, reconnecting = !ready, paletteOpen = paletteOpen)
        vm.hasSavedCreds() -> DesktopSplash(vm)
        else -> ConnectScreen(vm)
    }
}

@Composable
private fun DesktopSplash(vm: AppViewModel) {
    val s = LocalStrings.current
    Column(
        Modifier.fillMaxSize().background(Bg), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = Green, strokeWidth = 3.dp)
        Spacer(Modifier.height(18.dp))
        Text(s.signingIn, color = TextC, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (vm.host.value.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(vm.host.value, color = TextDim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(24.dp))
        Text(s.editCreds, color = TextDim, fontSize = 13.sp,
            modifier = Modifier.clickable { vm.editConnection() }.padding(8.dp))
    }
}

@Composable
private fun DesktopShell(vm: AppViewModel, reconnecting: Boolean, paletteOpen: MutableState<Boolean>) {
    val accent = personaColor(vm.currentPersona()?.themeColor)
    var screen by remember { mutableStateOf("chat") } // "chat" | "settings"
    var rightShown by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize()) {
      Column(Modifier.fillMaxSize().background(Bg)) {
        DesktopTopBar(vm, accent)
        if (reconnecting) ReconnectStrip()
        Row(Modifier.fillMaxSize()) {
            PersonaRail(vm, accent, screen) { screen = it }
            VLine()
            DialogSidebar(vm, accent)
            VLine()
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (screen) {
                    "settings" -> SettingsCenter(vm, accent)
                    else -> ChatCenter(vm, accent, rightShown) { rightShown = !rightShown }
                }
            }
            if (screen == "chat" && rightShown) {
                VLine()
                PersonaPanel(vm, accent)
            }
        }
      }
      if (paletteOpen.value) {
          CommandPalette(vm, accent, onScreen = { screen = it },
              onTogglePanel = { rightShown = !rightShown }, onClose = { paletteOpen.value = false })
      }
    }
}

@Composable private fun VLine() = Box(Modifier.fillMaxHeight().width(1.dp).background(PanelLine))

@Composable
private fun DesktopTopBar(vm: AppViewModel, accent: Color) {
    val name = vm.currentPersona()?.name ?: LocalStrings.current.agent
    // No horizontal padding: the title sits at the true window centre; the macOS traffic
    // lights overlay the empty left edge (fullWindowContent), so this reads as one bar.
    Box(Modifier.fillMaxWidth().background(Rail).height(44.dp)) {
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = TextC, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("  ·  ${vm.host.value.ifBlank { "vps" }}", color = TextDim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (vm.conn.value == ConnState.Ready) Green else Orange))
        }
        Text("⌘K", color = TextDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp))
    }
}

@Composable
private fun ReconnectStrip() = Row(
    Modifier.fillMaxWidth().background(Color(0xFF2A2614)).padding(vertical = 5.dp),
    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
) {
    CircularProgressIndicator(Modifier.size(12.dp), color = Orange, strokeWidth = 2.dp)
    Spacer(Modifier.width(8.dp))
    Text(LocalStrings.current.reconnecting, color = Orange, fontSize = 12.sp)
}

// ---------- left rail: persona switcher + settings ----------

@Composable
private fun PersonaRail(vm: AppViewModel, accent: Color, screen: String, onScreen: (String) -> Unit) {
    Column(
        Modifier.width(64.dp).fillMaxHeight().background(Rail).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // brand mark
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(Green.copy(alpha = 0.16f))
            .border(1.dp, Green.copy(alpha = 0.5f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Text("⌘", color = Green, fontSize = 20.sp)
        }
        Spacer(Modifier.height(2.dp))
        vm.personas.forEach { p ->
            val sel = p.id == vm.currentPersonaId.value && screen == "chat"
            val c = personaColor(p.themeColor)
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
                    .background(c.copy(alpha = if (sel) 0.22f else 0.10f))
                    .border(if (sel) 2.dp else 1.dp, c.copy(alpha = if (sel) 0.9f else 0.4f), RoundedCornerShape(13.dp))
                    .clickable { vm.selectPersona(p.id); onScreen("chat") },
                contentAlignment = Alignment.Center,
            ) {
                Text(p.name.take(1).uppercase(), color = c, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        val gear = screen == "settings"
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
                .background(if (gear) accent.copy(alpha = 0.18f) else Color.Transparent)
                .clickable { onScreen("settings") },
            contentAlignment = Alignment.Center,
        ) { Text("⚙", color = if (gear) accent else TextDim, fontSize = 20.sp) }
    }
}

// ---------- left sidebar: persona header + dialog history (projects) ----------

@Composable
private fun DialogSidebar(vm: AppViewModel, accent: Color) {
    val s = LocalStrings.current
    val persona = vm.currentPersona()
    var newDialog by remember { mutableStateOf(false) }
    Column(Modifier.width(264.dp).fillMaxHeight().background(Panel).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(persona?.name ?: "?", accent, 40.dp)
            Column(Modifier.padding(start = 10.dp)) {
                Text(persona?.name ?: s.agent, color = TextC, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(personaTagline(persona), color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.14f)).border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .clickable { newDialog = true }.padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text("+ ${s.newDialog}", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }

        Text(s.history.uppercase(), color = TextDim, fontSize = 11.sp, letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(vm.projects) { pr ->
                val sel = pr.id == vm.currentProjectId.value
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (sel) accent.copy(alpha = 0.16f) else Color.Transparent)
                        .border(1.dp, if (sel) accent.copy(alpha = 0.45f) else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { vm.selectProject(pr.id) }.padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(pr.name, color = if (sel) TextC else TextDim, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (pr.shared) Text(s.sharedOn.removePrefix("✓ "), color = TextDim, fontSize = 11.sp)
                }
            }
        }
    }
    if (newDialog) {
        var nm by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newDialog = false },
            title = { Text(s.newProjectTitle) },
            text = { OutlinedTextField(nm, { nm = it }, singleLine = true, placeholder = { Text(s.projectNameHint) }) },
            confirmButton = { TextButton(onClick = { vm.createProject(nm); newDialog = false }) { Text(s.create) } },
            dismissButton = { TextButton(onClick = { newDialog = false }) { Text(s.cancel) } },
        )
    }
}

private fun personaTagline(p: Persona?): String = when {
    p == null -> ""
    p.allTools -> "all tools"
    p.allowedTools.isNotEmpty() -> p.allowedTools.take(3).joinToString(" · ")
    else -> ""
}

// ---------- center: chat ----------

@Composable
private fun ChatCenter(vm: AppViewModel, accent: Color, rightShown: Boolean, onToggleRight: () -> Unit) {
    val s = LocalStrings.current
    val persona = vm.currentPersona()
    val thread = vm.currentThread()
    var input by remember { mutableStateOf("") }
    var attachMenu by remember { mutableStateOf(false) }
    val pickPhoto = rememberAttachmentPicker(imagesOnly = true) { it?.let(vm::stageAttachment) }
    val pickFile = rememberAttachmentPicker(imagesOnly = false) { it?.let(vm::stageAttachment) }

    Column(Modifier.fillMaxSize().background(Bg)) {
        // header
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(vm.currentProjectName(), color = TextC, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("${persona?.name ?: s.agent} · ${s.agentBranch}", color = TextDim, fontSize = 12.sp)
            }
            ModelBadge(vm.modelFor(persona?.id), accent)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                .background(if (rightShown) accent.copy(alpha = 0.16f) else Color.Transparent)
                .border(1.dp, PanelLine, RoundedCornerShape(8.dp)).clickable { onToggleRight() },
                contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ViewSidebar, s.cmdPanel, tint = if (rightShown) accent else TextDim, modifier = Modifier.size(16.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(PanelLine))

        // messages
        val listState = rememberLazyListState()
        val atBottom by remember {
            derivedStateOf {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
                if (last.index < info.totalItemsCount - 1) return@derivedStateOf false
                (last.offset + last.size) - info.viewportEndOffset <= 80
            }
        }
        LaunchedFollow(vm, thread.size, listState, atBottom)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 28.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(thread) { msg -> MessageItem(msg, accent, vm) }
        }

        // staged previews
        if (vm.staged.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.staged.forEachIndexed { i, st ->
                    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(SurfaceAlt)
                        .border(1.dp, BorderC, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(if (st.isImage) "🖼 " else "📄 ", fontSize = 13.sp)
                        Text(st.name, color = TextC, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 140.dp))
                        Text("  ✕", color = TextDim, fontSize = 12.sp, modifier = Modifier.clickable { vm.removeStaged(i) })
                    }
                }
            }
        }

        // input — attach lives inside the field; ⏎ sends, Shift+⏎ = newline (matches the mockup)
        fun fire() {
            if (!vm.busy.value && (input.isNotBlank() || vm.staged.isNotEmpty())) { val t = input; input = ""; vm.send(t) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                placeholder = { Text("${s.messageHint} · ⏎", color = TextDim) },
                maxLines = 6,
                modifier = Modifier.weight(1f).onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && !e.isShiftPressed) { fire(); true } else false
                },
                shape = RoundedCornerShape(14.dp),
                trailingIcon = {
                    Box {
                        Icon(Icons.Outlined.AttachFile, s.attach, tint = TextDim,
                            modifier = Modifier.clickable { attachMenu = true }.padding(end = 6.dp).size(22.dp))
                        androidx.compose.material3.DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                            androidx.compose.material3.DropdownMenuItem(text = { Text(s.attachPhoto) }, onClick = { attachMenu = false; pickPhoto() })
                            androidx.compose.material3.DropdownMenuItem(text = { Text(s.attachFile) }, onClick = { attachMenu = false; pickFile() })
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextC, unfocusedTextColor = TextC,
                    focusedContainerColor = SurfaceAlt, unfocusedContainerColor = SurfaceAlt,
                    focusedBorderColor = BorderC, unfocusedBorderColor = BorderC, cursorColor = accent,
                ),
            )
            if (vm.busy.value) {
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.interrupt() }, shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorC)) { Text(s.stop) }
            }
        }
    }
}

@Composable
private fun LaunchedFollow(vm: AppViewModel, size: Int, listState: androidx.compose.foundation.lazy.LazyListState, atBottom: Boolean) {
    androidx.compose.runtime.LaunchedEffect(vm.busy.value) {
        if (vm.busy.value) {
            if (size > 0) listState.scrollToItem(size - 1, Int.MAX_VALUE)
            while (vm.busy.value) {
                if (atBottom && !listState.isScrollInProgress && size > 0) listState.scrollToItem(size - 1, Int.MAX_VALUE)
                kotlinx.coroutines.delay(60)
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(vm.currentPersonaId.value, vm.currentProjectId.value) {
        if (size > 0) listState.scrollToItem(size - 1, Int.MAX_VALUE)
    }
}

// ---------- right: persona detail + audit ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonaPanel(vm: AppViewModel, accent: Color) {
    val s = LocalStrings.current
    val persona = vm.currentPersona() ?: return
    val pid = persona.id
    var prompt by remember(pid) { mutableStateOf(persona.systemPrompt) }
    Column(
        Modifier.width(300.dp).fillMaxHeight().background(Panel)
            .verticalScroll(rememberScrollState()).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(persona.name, accent, 76.dp)
        Spacer(Modifier.height(10.dp))
        Text(persona.name, color = TextC, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(personaTagline(persona), color = TextDim, fontSize = 12.sp)

        PanelLabel(s.model)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Surface).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            vm.models.take(3).forEach { m ->
                val sel = vm.modelFor(pid) == m.id
                Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(if (sel) accent else Color.Transparent).clickable { vm.setModel(pid, m.id) }
                    .padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                    Text(m.label, color = if (sel) Color.Black else TextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        PanelLabel(s.themeColor)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SWATCHES.forEach { (hex, c) ->
                val sel = hex.equals(persona.themeColor, ignoreCase = true)
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(c)
                    .border(if (sel) 3.dp else 0.dp, Color.White, RoundedCornerShape(11.dp))
                    .clickable { vm.savePersona(persona.copy(themeColor = hex)) })
            }
        }

        PanelLabel(s.systemPrompt)
        OutlinedTextField(
            prompt, { prompt = it }, modifier = Modifier.fillMaxWidth().height(120.dp),
            shape = RoundedCornerShape(11.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextC),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Surface, unfocusedContainerColor = Surface,
                focusedBorderColor = BorderC, unfocusedBorderColor = BorderC, cursorColor = accent,
            ),
        )
        if (prompt != persona.systemPrompt) {
            Spacer(Modifier.height(6.dp))
            Button(onClick = { vm.savePersona(persona.copy(systemPrompt = prompt)) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)) { Text(s.save) }
        }

        // all-tools switch — label + toggle on one row, subtitle full-width below
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(s.allTools.uppercase(), color = TextDim, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = persona.allTools,
                onCheckedChange = { vm.savePersona(persona.copy(allTools = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.White),
            )
        }
        Text(s.allToolsSub, color = TextDim, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))

        PanelLabel(s.allowedTools)
        FlowRow(
            Modifier.fillMaxWidth().alpha(if (persona.allTools) 0.4f else 1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DESK_TOOLS.forEach { tname ->
                val on = tname in persona.allowedTools
                Text(tname, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    color = if (on) Color.Black else TextDim,
                    modifier = Modifier.clip(RoundedCornerShape(9.dp))
                        .background(if (on) accent else Color.Transparent)
                        .border(1.dp, if (on) accent else BorderC, RoundedCornerShape(9.dp))
                        .clickable {
                            val nt = if (on) persona.allowedTools - tname else persona.allowedTools + tname
                            vm.savePersona(persona.copy(allowedTools = nt))
                        }.padding(horizontal = 11.dp, vertical = 7.dp))
            }
        }

        PanelLabel(s.commandLog)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.audit.isEmpty()) Text(s.noCommands, color = TextDim, fontSize = 12.sp)
            vm.audit.take(12).forEach { e ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 5.dp).size(7.dp).clip(CircleShape)
                        .background(if (e.exitCode == 0) Green else ErrorC))
                    Column(Modifier.padding(start = 8.dp)) {
                        Text("$ ${e.command}", color = TextC, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${e.persona} · exit ${e.exitCode}", color = TextDim, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelLabel(text: String) = Text(
    text.uppercase(), color = TextDim, fontSize = 11.sp, letterSpacing = 1.sp,
    modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
)

// ---------- center: settings ----------

@Composable
private fun SettingsCenter(vm: AppViewModel, accent: Color) {
    val s = LocalStrings.current
    Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(32.dp)) {
        Text(s.settingsTitle, color = TextC, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(s.settingsSubtitle, color = TextDim, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))

        // VPS card
        Column(Modifier.widthIn(max = 540.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Panel).border(1.dp, PanelLine, RoundedCornerShape(14.dp)).padding(18.dp)) {
            Text("VPS", color = TextDim, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (vm.conn.value == ConnState.Ready) Green else Orange))
                Text("  ${vm.host.value.ifBlank { "—" }}", color = TextC, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(6.dp))
            Text(s.channelDesc, color = TextDim, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.connect() }, shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Surface, contentColor = TextC)) { Text(s.reconnectBtn) }
                Button(onClick = { vm.editConnection() }, shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Surface, contentColor = TextC)) { Text(s.editCreds) }
            }
        }

        Spacer(Modifier.height(16.dp))
        // Language
        Row(Modifier.widthIn(max = 540.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Panel).border(1.dp, PanelLine, RoundedCornerShape(14.dp)).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.sectionLanguage, color = TextC, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(if (vm.lang.value == "en") "English" else "Русский", color = TextDim, fontSize = 12.sp)
            }
            Row(Modifier.clip(RoundedCornerShape(10.dp)).background(Surface).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("ru" to "RU", "en" to "EN").forEach { (code, lbl) ->
                    val sel = vm.lang.value == code
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (sel) accent else Color.Transparent)
                        .clickable { vm.setLang(code) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(lbl, color = if (sel) Color.Black else TextDim, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        // Danger
        Box(Modifier.widthIn(max = 540.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .border(1.dp, ErrorC.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .clickable { vm.disconnect() }.padding(16.dp)) {
            Text("⏻  ${s.disconnect}", color = ErrorC, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(24.dp))
        Text("ClawNest for macOS · MIT · ${s.aboutNoTrackers}", color = TextDim, fontSize = 12.sp)
    }
}

// ---------- ⌘K command palette ----------

private data class Cmd(val label: String, val hint: String, val run: () -> Unit)

@Composable
private fun CommandPalette(vm: AppViewModel, accent: Color, onScreen: (String) -> Unit, onTogglePanel: () -> Unit, onClose: () -> Unit) {
    val s = LocalStrings.current
    var q by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf(0) }
    val fr = remember { FocusRequester() }

    val cmds = buildList {
        vm.personas.forEach { p -> add(Cmd("${s.cmdOpen} ${p.name}", s.cmdHintAgent) { vm.selectPersona(p.id); onScreen("chat"); onClose() }) }
        vm.projects.forEach { pr -> add(Cmd(pr.name, s.cmdHintDialog) { vm.selectProject(pr.id); onScreen("chat"); onClose() }) }
        add(Cmd(s.settingsTitle, "⚙") { onScreen("settings"); onClose() })
        add(Cmd(s.cmdPanel, "⌥") { onTogglePanel(); onClose() })
        add(Cmd(s.cmdLang, "RU/EN") { vm.setLang(if (vm.lang.value == "en") "ru" else "en"); onClose() })
        add(Cmd(s.disconnect, "⏻") { vm.disconnect(); onClose() })
    }
    val filtered = cmds.filter { q.isBlank() || it.label.contains(q, true) || it.hint.contains(q, true) }
    val selClamped = sel.coerceIn(0, (filtered.size - 1).coerceAtLeast(0))

    Box(
        Modifier.fillMaxSize().background(Color(0xAA000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.padding(top = 90.dp).width(560.dp).clip(RoundedCornerShape(14.dp))
                .background(Surface).border(1.dp, BorderC, RoundedCornerShape(14.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            OutlinedTextField(
                value = q, onValueChange = { q = it; sel = 0 },
                placeholder = { Text(s.cmdSearch, color = TextDim) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(fr).onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionDown -> { sel = (selClamped + 1).coerceAtMost(filtered.size - 1); true }
                        Key.DirectionUp -> { sel = (selClamped - 1).coerceAtLeast(0); true }
                        Key.Enter -> { filtered.getOrNull(selClamped)?.run?.invoke(); true }
                        Key.Escape -> { onClose(); true }
                        else -> false
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextC, unfocusedTextColor = TextC,
                    focusedContainerColor = Surface, unfocusedContainerColor = Surface,
                    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, cursorColor = accent,
                ),
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(BorderC))
            if (filtered.isEmpty()) {
                Text(s.cmdEmpty, color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    itemsIndexed(filtered) { i, c ->
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (i == selClamped) accent.copy(alpha = 0.16f) else Color.Transparent)
                                .clickable { c.run() }.padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(c.label, color = TextC, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(c.hint, color = TextDim, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { fr.requestFocus() }
}
