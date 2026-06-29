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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.app.Persona

private val SWATCHES = listOf(
    "#22C55E" to Green, "#8A63D2" to Purple, "#E8833A" to Orange,
    "#3B82F6" to Blue, "#AAB0BA" to Gray, "#EC4899" to Pink,
)
private val MODELS3 = listOf("Opus" to "claude-opus-4-8", "Sonnet" to "claude-sonnet-4-6", "Haiku" to "claude-haiku-4-5")
private val ALL_TOOLS = listOf("bash", "ssh", "nmap", "sqlmap", "git", "docker", "python", "metasploit", "burp")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonaEditorScreen(vm: AppViewModel, idArg: String?, onBack: () -> Unit) {
    val existing = if (idArg == "_new") null else vm.persona(idArg)

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var colorHex by remember { mutableStateOf(existing?.themeColor ?: "#22C55E") }
    var model by remember { mutableStateOf(existing?.defaultModel ?: "claude-opus-4-8") }
    var prompt by remember { mutableStateOf(existing?.systemPrompt ?: "") }
    var allTools by remember { mutableStateOf(existing?.allTools ?: false) }
    val tools = remember { mutableStateListOf<String>().apply { addAll(existing?.allowedTools ?: listOf("bash")) } }

    val accent = personaColor(colorHex)
    val s = LocalStrings.current

    Column(
        Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = TextC, fontSize = 28.sp, modifier = Modifier.clickable { onBack() }.padding(end = 14.dp))
            Text(s.editorTitle, color = TextC, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Avatar(name.ifBlank { "A" }, accent, 88.dp)
        }

        SectionLabel(s.name)
        OutlinedTextField(
            name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp), colors = fieldColors(accent),
            placeholder = { Text(s.nameHint, color = TextDim) },
        )

        SectionLabel(s.themeColor)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SWATCHES.forEach { (hex, c) ->
                val sel = hex.equals(colorHex, ignoreCase = true)
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(c)
                        .border(if (sel) 3.dp else 0.dp, Color.White, RoundedCornerShape(14.dp))
                        .clickable { colorHex = hex },
                )
            }
        }

        SectionLabel(s.defaultModel)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MODELS3.forEach { (label, id) ->
                val sel = id == model
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                        .background(if (sel) accent else Color.Transparent)
                        .clickable { model = id }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (sel) Color.Black else TextDim, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        SectionLabel(s.systemPrompt)
        OutlinedTextField(
            prompt, { prompt = it }, modifier = Modifier.fillMaxWidth().height(150.dp),
            shape = RoundedCornerShape(12.dp), colors = fieldColors(accent),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = TextC),
            placeholder = { Text(s.promptHint, color = TextDim) },
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(s.allTools.uppercase(), color = TextDim, fontSize = 12.sp, letterSpacing = 1.sp)
                Text(s.allToolsSub, color = TextDim, fontSize = 12.sp)
            }
            Switch(
                checked = allTools,
                onCheckedChange = { allTools = it },
                colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.White),
            )
        }

        SectionLabel(s.allowedTools)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.alpha(if (allTools) 0.4f else 1f),
        ) {
            ALL_TOOLS.forEach { t ->
                val sel = t in tools
                Text(
                    t, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                    color = if (sel) Color.Black else TextDim,
                    modifier = Modifier.padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sel) accent else Color.Transparent)
                        .border(1.dp, if (sel) accent else BorderC, RoundedCornerShape(10.dp))
                        .clickable { if (sel) tools.remove(t) else tools.add(t) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { }, modifier = Modifier.weight(1f).height(52.dp)) { Text(s.exportJson, color = TextC) }
            Button(
                onClick = {
                    val id = existing?.id ?: name.trim().lowercase()
                        .replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "agent" }
                    vm.savePersona(
                        Persona(
                            id = id,
                            name = name.trim().ifBlank { s.agent },
                            emoji = existing?.emoji ?: "",
                            defaultModel = model,
                            themeColor = colorHex,
                            allowedTools = tools.toList(),
                            systemPrompt = prompt,
                            allTools = allTools,
                        )
                    )
                    onBack()
                },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
            ) { Text(s.save, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun fieldColors(accent: Color) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextC, unfocusedTextColor = TextC,
    focusedContainerColor = Surface, unfocusedContainerColor = Surface,
    focusedBorderColor = BorderC, unfocusedBorderColor = BorderC,
    cursorColor = accent,
)
