package com.openclaw.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.app.AuditEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JournalScreen(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.refreshAudit() }
    val s = LocalStrings.current
    Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 20.dp)) {
        Text(s.journalTitle, color = TextC, fontSize = 28.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp))
        Text(s.journalSubtitle, color = TextDim,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        if (vm.audit.isEmpty()) {
            Text(s.journalEmpty, color = TextDim, modifier = Modifier.padding(top = 40.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(vm.audit) { e ->
                    AuditRow(e, vm)
                    HorizontalDivider(color = BorderC)
                }
            }
        }
    }
}

private val fmt = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

@Composable
private fun AuditRow(e: AuditEntry, vm: AppViewModel) {
    val p = vm.persona(e.persona)
    val color = personaColor(p?.themeColor)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 4.dp).size(9.dp).clip(CircleShape).background(color))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text("$ ${e.command}", color = TextC, fontFamily = FontFamily.Monospace, fontSize = 14.sp, maxLines = 2)
            Row(Modifier.padding(top = 3.dp)) {
                Text(p?.name ?: e.persona, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text("  ${fmt.format(Date((e.ts * 1000).toLong()))}", color = TextDim, fontSize = 12.sp)
            }
        }
        Text(
            "exit ${e.exitCode}",
            color = TextDim,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .border(1.dp, BorderC, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
