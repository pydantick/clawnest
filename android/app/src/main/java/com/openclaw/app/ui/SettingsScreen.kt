package com.openclaw.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = LocalStrings.current
    Column(
        Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 24.dp),
    ) {
        Text(s.settingsTitle, color = TextC, fontSize = 28.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp))
        Text(s.settingsSubtitle, color = TextDim, modifier = Modifier.padding(top = 4.dp))

        SectionLabel(s.sectionLanguage)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("ru" to "Русский", "en" to "English").forEach { (code, label) ->
                val sel = vm.lang.value == code
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                        .background(if (sel) Green else Color.Transparent)
                        .clickable { vm.setLang(code) }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (sel) Color.Black else TextDim, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        SectionLabel(s.sectionVps)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surface).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(Green))
            Text(
                vm.host.value.ifBlank { "—" },
                color = TextC,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Button(
            onClick = { vm.editConnection() },
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(top = 10.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Surface, contentColor = TextC),
        ) { Text(s.connectNewVps) }

        SectionLabel(s.sectionSecurity)
        ToggleRow(s.encryptHistory, s.encryptHistorySub)
        ToggleRow(s.auditLog, s.auditLogSub)

        Text(
            "${s.about}\n${s.aboutSub}",
            color = TextDim,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        )
    }
}

@Composable
private fun ToggleRow(title: String, sub: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp))
            .background(Surface).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextC, fontWeight = FontWeight.Medium)
            Text(sub, color = TextDim, fontSize = 13.sp)
        }
        Box(
            Modifier.size(width = 44.dp, height = 26.dp).clip(RoundedCornerShape(13.dp)).background(Green),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(Modifier.padding(end = 3.dp).size(20.dp).clip(CircleShape).background(Color.White))
        }
    }
}
