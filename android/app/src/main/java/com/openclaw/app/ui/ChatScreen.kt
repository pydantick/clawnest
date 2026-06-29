package com.openclaw.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.material3.RichText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.openclaw.app.Persona
import java.io.File

@Composable
fun ChatScreen(vm: AppViewModel) {
    val persona = vm.currentPersona()
    val accent = personaColor(persona?.themeColor)
    val pid = persona?.id
    val thread = vm.currentThread()
    val s = LocalStrings.current

    var modelMenu by remember { mutableStateOf(false) }
    var projectMenu by remember { mutableStateOf(false) }
    var newProject by remember { mutableStateOf(false) }
    var renameProj by remember { mutableStateOf(false) }
    var cwdDialog by remember { mutableStateOf(false) }
    var attachMenu by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.stageAttachment(it) }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.stageAttachment(it) }
    }

    Column(Modifier.fillMaxWidth().background(Bg)) {
        // ---- header ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(persona?.name ?: "?", accent, 44.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(persona?.name ?: s.agent, color = TextC, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Box {
                        Box(Modifier.clickable { if (pid != null) modelMenu = true }) {
                            ModelBadge(vm.modelFor(pid), accent)
                        }
                        DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                            vm.models.forEach { m ->
                                DropdownMenuItem(text = { Text(m.label) }, onClick = {
                                    if (pid != null) vm.setModel(pid, m.id); modelMenu = false
                                })
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Green))
                    Text(
                        "  VPS · ${vm.host.value.ifBlank { "agent" }}",
                        color = TextDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        // ---- project selector ----
        Box(Modifier.padding(horizontal = 14.dp, vertical = 2.dp)) {
            Text(
                "📁 ${vm.currentProjectName()}  ▾",
                color = TextC, fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp)).border(1.dp, BorderC, RoundedCornerShape(10.dp))
                    .clickable { projectMenu = true }.padding(horizontal = 12.dp, vertical = 7.dp),
            )
            DropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                vm.projects.forEach { pr ->
                    DropdownMenuItem(text = { Text(pr.name) }, onClick = { vm.selectProject(pr.id); projectMenu = false })
                }
                DropdownMenuItem(text = { Text("✏️ ${s.renameWord} «${vm.currentProjectName()}»") }, onClick = {
                    projectMenu = false; renameProj = true
                })
                DropdownMenuItem(text = { Text(s.workdirMenu) }, onClick = {
                    projectMenu = false; cwdDialog = true
                })
                DropdownMenuItem(
                    text = { Text(if (vm.currentProj()?.shared == true) s.sharedOn else s.sharedOff) },
                    onClick = {
                        vm.setProjectShared(vm.currentProjectId.value, vm.currentProj()?.shared != true)
                        projectMenu = false
                    },
                )
                DropdownMenuItem(text = { Text(s.newProjectMenu, color = Green) }, onClick = {
                    projectMenu = false; newProject = true
                })
            }
        }

        // ---- persona chips ----
        LazyRow(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(vm.personas) { p -> PersonaChip(p, p.id == pid) { vm.selectPersona(p.id) } }
        }

        // ---- messages ----
        val listState = rememberLazyListState()
        // Single source of truth for auto-follow: are we (roughly) at the bottom?
        // Computed from real layout, so it stays correct even while the last bubble grows
        // during streaming. The tolerance is generous (~72dp) on purpose: while text is
        // streaming the true bottom keeps moving down, so a tight threshold could never be
        // re-reached by a manual scroll — this lets following re-engage as soon as the user
        // gets near the bottom again.
        val tolerancePx = with(LocalDensity.current) { 72.dp.toPx() }
        val atBottom by remember(tolerancePx) {
            derivedStateOf {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
                if (last.index < info.totalItemsCount - 1) return@derivedStateOf false
                (last.offset + last.size) - info.viewportEndOffset <= tolerancePx
            }
        }
        // While a turn streams, keep the newest text in view — but only when parked at the
        // bottom and the user isn't actively dragging, so a touch or scroll-up is never fought.
        LaunchedEffect(vm.busy.value) {
            if (vm.busy.value) {
                // A turn just started (user sent) — snap down so their message + the reply show.
                if (thread.isNotEmpty()) listState.scrollToItem(thread.size - 1, Int.MAX_VALUE)
                while (vm.busy.value) {
                    if (atBottom && !listState.isScrollInProgress && thread.isNotEmpty()) {
                        listState.scrollToItem(thread.size - 1, Int.MAX_VALUE)
                    }
                    kotlinx.coroutines.delay(60)
                }
            }
        }
        // Land at the bottom when opening / switching persona or project.
        LaunchedEffect(pid, vm.currentProjectId.value) {
            if (thread.isNotEmpty()) listState.scrollToItem(thread.size - 1, Int.MAX_VALUE)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(thread) { msg -> MessageItem(msg, accent, vm) }
        }

        // ---- staged attachments (preview before send) ----
        if (vm.staged.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(vm.staged) { i, st -> StagedChip(st) { vm.removeStaged(i) } }
            }
        }

        // ---- input ---- (floats on Bg, not a Surface bar, so it reads as separate
        // from the bottom nav like the mockup)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                        .background(SurfaceAlt)
                        .border(1.dp, BorderC, RoundedCornerShape(16.dp))
                        .clickable { attachMenu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.AttachFile, s.attach, tint = TextDim, modifier = Modifier.size(22.dp))
                }
                DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                    DropdownMenuItem(text = { Text(s.attachPhoto) }, onClick = {
                        attachMenu = false
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                    DropdownMenuItem(text = { Text(s.attachFile) }, onClick = {
                        attachMenu = false
                        pickFile.launch(arrayOf("*/*"))
                    })
                }
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                placeholder = { Text(s.messageHint, color = TextDim) },
                maxLines = 5,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(22.dp),
                keyboardOptions = KeyboardOptions.Default,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextC, unfocusedTextColor = TextC,
                    focusedContainerColor = SurfaceAlt, unfocusedContainerColor = SurfaceAlt,
                    focusedBorderColor = BorderC, unfocusedBorderColor = BorderC,
                    cursorColor = accent,
                ),
            )
            // Send appears only with text (or Stop while busy) — matches the mockup's clean input.
            if (vm.busy.value) {
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.interrupt() }, shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorC)) { Text(s.stop) }
            } else if (input.isNotBlank() || vm.staged.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { val t = input; input = ""; vm.send(t) },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                ) { Text("▶") }
            }
        }
    }

    if (newProject) {
        var name by remember { mutableStateOf("") }
        var path by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newProject = false },
            title = { Text(s.newProjectTitle) },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, singleLine = true,
                        placeholder = { Text(s.projectNameHint) })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(path, { path = it }, singleLine = true,
                        placeholder = { Text(s.projectPathHint) })
                }
            },
            confirmButton = { TextButton(onClick = { vm.createProject(name, path); newProject = false }) { Text(s.create) } },
            dismissButton = { TextButton(onClick = { newProject = false }) { Text(s.cancel) } },
        )
    }

    if (renameProj) {
        var name by remember { mutableStateOf(vm.currentProjectName()) }
        AlertDialog(
            onDismissRequest = { renameProj = false },
            title = { Text(s.renameProjectTitle) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { vm.renameProject(vm.currentProjectId.value, name); renameProj = false }) { Text(s.renameWord) }
            },
            dismissButton = { TextButton(onClick = { renameProj = false }) { Text(s.cancel) } },
        )
    }

    if (cwdDialog) {
        var path by remember { mutableStateOf(vm.currentProj()?.cwd ?: "") }
        AlertDialog(
            onDismissRequest = { cwdDialog = false },
            title = { Text(s.workdirTitle(vm.currentProjectName())) },
            text = {
                Column {
                    Text(s.workdirHint, color = TextDim, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(path, { path = it }, singleLine = true, placeholder = { Text("/root/myapp") })
                }
            },
            confirmButton = { TextButton(onClick = { vm.setProjectCwd(vm.currentProjectId.value, path); cwdDialog = false }) { Text(s.save) } },
            dismissButton = { TextButton(onClick = { cwdDialog = false }) { Text(s.cancel) } },
        )
    }
}

@Composable
private fun PersonaChip(p: Persona, active: Boolean, onClick: () -> Unit) {
    val color = personaColor(p.themeColor)
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) color.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.dp, if (active) color else BorderC, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(p.name, color, 24.dp)
        Text(p.name, color = if (active) TextC else TextDim, modifier = Modifier.padding(start = 8.dp), fontSize = 14.sp)
    }
}

@Composable
private fun MessageItem(msg: ChatMsg, accent: Color, vm: AppViewModel) {
    // SelectionContainer makes every bubble's text long-press-selectable + copyable.
    if (msg.role == "user") {
        val text = msg.plain()
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            msg.segs.filterIsInstance<FileSeg>().forEach { fs ->
                Box(Modifier.widthIn(max = 300.dp)) { AttachmentBlock(fs, accent, vm) }
            }
            if (text.isNotBlank()) {
                Box(
                    Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(14.dp)).background(UserBubble).padding(12.dp),
                ) { SelectionContainer { Text(text, color = TextC) } }
            }
            msg.error?.let { Text("⚠️ $it", color = ErrorC, fontSize = 12.sp) }
        }
    } else {
        SelectionContainer {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                msg.segs.forEach { seg ->
                    when (seg) {
                        is TextSeg -> StreamedMarkdown(seg, msg.streaming)
                        is ToolSeg -> TerminalBlock(seg, accent)
                        is FileSeg -> AttachmentBlock(seg, accent, vm)
                        is QuestionSeg -> QuestionCard(seg, accent, vm)
                    }
                }
                if (msg.streaming && msg.segs.isEmpty()) Text("…", color = TextDim)
                msg.error?.let { Text("⚠️ $it", color = ErrorC) }
            }
        }
    }
}

private val THUMB = 152.dp

@Composable
private fun AttachmentBlock(seg: FileSeg, accent: Color, vm: AppViewModel) {
    // Restored/received image without a local copy yet → fetch it so it can render inline.
    if (seg.isImage && seg.localPath == null && seg.remotePath.isNotBlank()) {
        LaunchedEffect(seg.remotePath) { vm.ensureImage(seg) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            // Neat fixed square thumbnail, centre-cropped — like the ChatGPT web chat. Tap = open full.
            seg.isImage && seg.localPath != null -> AsyncImage(
                model = File(seg.localPath!!),
                contentDescription = seg.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(THUMB).clip(RoundedCornerShape(14.dp))
                    .clickable { vm.openFileSeg(seg) },
            )
            // Image still downloading → same-size square placeholder so layout doesn't jump.
            seg.isImage -> Box(
                Modifier.size(THUMB).clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF15181C)).border(1.dp, BorderC, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (seg.error != null) Text("🖼", fontSize = 30.sp)
                else CircularProgressIndicator(Modifier.size(22.dp), color = accent, strokeWidth = 2.dp)
            }
            else -> FileChip(seg, accent) { vm.openFileSeg(seg) }
        }
        seg.error?.let { Text("⚠️ $it", color = ErrorC, fontSize = 12.sp) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionCard(seg: QuestionSeg, accent: Color, vm: AppViewModel) {
    val s = LocalStrings.current
    val selections = remember { mutableStateMapOf<Int, Set<String>>() }
    fun toggle(qi: Int, label: String, multi: Boolean) {
        val cur = selections[qi].orEmpty()
        selections[qi] = if (multi) (if (label in cur) cur - label else cur + label) else setOf(label)
    }
    fun answerText(): String =
        "${s.myAnswers}\n" + seg.questions.mapIndexed { qi, q ->
            "${q.header.ifBlank { q.question }}: ${selections[qi].orEmpty().joinToString(", ")}"
        }.joinToString("\n")

    val autoSingle = seg.questions.size == 1 && !seg.questions[0].multiSelect
    val allAnswered = seg.questions.indices.all { (selections[it]?.size ?: 0) > 0 }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF14171C))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        seg.questions.forEachIndexed { qi, q ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (q.header.isNotBlank()) Text(q.header.uppercase(), color = accent, fontSize = 11.sp, letterSpacing = 1.sp)
                Text(q.question, color = TextC, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    q.options.forEach { opt ->
                        val sel = selections[qi]?.contains(opt.label) == true
                        OptionChip(opt, sel, accent, enabled = !seg.answered && !vm.busy.value) {
                            if (autoSingle) {
                                toggle(qi, opt.label, false)
                                vm.answerQuestion(seg, answerText())
                            } else {
                                toggle(qi, opt.label, q.multiSelect)
                            }
                        }
                    }
                }
            }
        }
        when {
            seg.answered -> Text(s.answerSent, color = accent, fontSize = 13.sp)
            vm.busy.value -> Text(s.waitTurn, color = TextDim, fontSize = 12.sp)
            !autoSingle -> Button(
                onClick = { vm.answerQuestion(seg, answerText()) },
                enabled = allAnswered,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
            ) { Text(s.answer) }
        }
    }
}

@Composable
private fun OptionChip(opt: QOption, selected: Boolean, accent: Color, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.18f) else Color(0xFF1C2027))
            .border(1.dp, if (selected) accent else BorderC, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(opt.label, color = if (selected) accent else TextC, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        if (opt.description.isNotBlank()) Text(opt.description, color = TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun FileChip(seg: FileSeg, accent: Color, onClick: () -> Unit) {
    val s = LocalStrings.current
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF15181C))
            .border(1.dp, BorderC, RoundedCornerShape(12.dp))
            .clickable(enabled = !seg.downloading) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(if (seg.isImage) "🖼" else "📄", fontSize = 18.sp)
        Column(Modifier.weight(1f, fill = false)) {
            Text(seg.name, color = TextC, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    seg.downloading -> s.downloading
                    seg.localPath != null -> s.openFile
                    else -> s.downloadFile
                },
                color = TextDim, fontSize = 12.sp,
            )
        }
        if (seg.downloading) CircularProgressIndicator(Modifier.size(18.dp), color = accent, strokeWidth = 2.dp)
        else Text("↓", color = accent, fontSize = 18.sp)
    }
}

@Composable
private fun StagedChip(st: Staged, onRemove: () -> Unit) {
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).background(SurfaceAlt)
                .border(1.dp, BorderC, RoundedCornerShape(10.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (st.isImage) {
                AsyncImage(
                    model = File(st.localPath), contentDescription = st.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Text("📄", fontSize = 18.sp, modifier = Modifier.padding(start = 10.dp))
            }
            Text(
                st.name, color = TextC, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(2.dp).size(18.dp).clip(CircleShape)
                .background(Color(0xCC000000)).clickable { onRemove() },
            contentAlignment = Alignment.Center,
        ) { Text("✕", color = Color.White, fontSize = 10.sp) }
    }
}

@Composable
private fun StreamedMarkdown(seg: TextSeg, animate: Boolean) {
    // Typewriter: reveal characters smoothly toward the streamed target, decoupled
    // from bursty network chunks — like the Claude/ChatGPT web reveal.
    var shown by remember { mutableStateOf(if (animate) 0 else seg.text.length) }
    LaunchedEffect(seg.text, animate) {
        if (!animate) {
            shown = seg.text.length
            return@LaunchedEffect
        }
        if (shown > seg.text.length) shown = seg.text.length
        while (shown < seg.text.length) {
            val remaining = seg.text.length - shown
            shown += (remaining / 10).coerceIn(1, 30)
            kotlinx.coroutines.delay(18)
        }
    }
    // Hide file markers (complete, or a partial one still being typed) — they become
    // attachment blocks at turn end and should never show as raw text.
    val display = seg.text.take(shown)
        .replace(FILE_MARKER, "")
        .replace(Regex("""\[\[FILE:[^\]]*$"""), "")
    if (display.isBlank()) return
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides TextC,
    ) {
        RichText(modifier = Modifier.fillMaxWidth()) {
            Markdown(display)
        }
    }
}

@Composable
private fun TerminalBlock(seg: ToolSeg, accent: Color) {
    val s = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    val firstLine = seg.command.lineSequence().firstOrNull().orEmpty()
    val collapsedCmd = if (firstLine.length > 50) firstLine.take(50) + "…" else firstLine
    val hasMore = seg.command.trim() != collapsedCmd.trim() || seg.output.isNotBlank()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF0D1411))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable(enabled = hasMore) { expanded = !expanded },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
            Text(
                "  $ ${if (expanded) seg.command else collapsedCmd}",
                color = TextC, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                modifier = Modifier.weight(1f),
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (seg.exit == null) "…" else if (seg.exit == 0) "✓" else "exit ${seg.exit}",
                color = if (seg.exit == 0 || seg.exit == null) accent else ErrorC, fontSize = 13.sp,
            )
        }
        if (expanded && seg.output.isNotBlank()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(BorderC))
            Text(
                seg.output.take(8000), color = TextDim, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                modifier = Modifier.padding(12.dp),
            )
        }
        if (hasMore) {
            Text(
                if (expanded) s.collapse else s.showAll,
                color = accent.copy(alpha = 0.9f), fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}
