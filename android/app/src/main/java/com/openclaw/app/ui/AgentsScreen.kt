package com.openclaw.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.app.Persona

@Composable
fun AgentsScreen(
    vm: AppViewModel,
    onOpenChat: (String) -> Unit,
    onEdit: (String) -> Unit,
    onCreate: () -> Unit,
) {
    val s = LocalStrings.current
    Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 20.dp)) {
        Text(s.agentsTitle, color = TextC, fontSize = 28.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp))
        Text(s.agentsSubtitle, color = TextDim, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(vm.personas) { p -> AgentCard(p, vm, onOpenChat, onEdit) }
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    OutlinedButton(onClick = onCreate, modifier = Modifier.weight(1f)) { Text(s.createAgent) }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = { }, modifier = Modifier.weight(1f)) { Text(s.importAgent) }
                }
            }
        }
    }
}

@Composable
private fun AgentCard(p: Persona, vm: AppViewModel, onOpenChat: (String) -> Unit, onEdit: (String) -> Unit) {
    val s = LocalStrings.current
    val color = personaColor(p.themeColor)
    val desc = if (p.allowedTools.isNotEmpty()) p.allowedTools.joinToString(" · ")
    else p.systemPrompt.take(48).ifBlank { s.agentFallback }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface)
            .border(1.dp, BorderC, RoundedCornerShape(16.dp)),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.name, color, 52.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, color = TextC, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    ModelBadge(vm.modelFor(p.id), color)
                }
                Text(desc, color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(BorderC))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            TextButton(onClick = { onOpenChat(p.id) }, modifier = Modifier.weight(1f)) {
                Text(s.openChat, color = TextC)
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(BorderC))
            TextButton(onClick = { onEdit(p.id) }, modifier = Modifier.weight(1f)) {
                Text(s.edit, color = color, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
