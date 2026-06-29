package com.openclaw.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ConnectScreen(vm: AppViewModel) {
    val s = LocalStrings.current
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 56.dp, bottom = 24.dp),
    ) {
        Text(s.connectTitle, color = TextC, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            s.connectSubtitle,
            color = TextDim,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )
        LabeledField(s.fieldServerIp, vm.host.value, s.hintServerIp) { vm.host.value = it }
        LabeledField(s.fieldSshUser, vm.sshUser.value, "root") { vm.sshUser.value = it }
        LabeledField(s.fieldSshPass, vm.sshPass.value, s.hintSshPass, password = true) { vm.sshPass.value = it }
        LabeledField(s.fieldClaudeKey, vm.claudeKey.value, "sk-ant-…", password = true) { vm.claudeKey.value = it }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.connect() },
            enabled = vm.conn.value != ConnState.Connecting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black),
        ) {
            Text(
                if (vm.conn.value == ConnState.Connecting) s.connectingBtn else s.connectBtn,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (vm.statusMsg.value.isNotEmpty()) {
            Text(vm.statusMsg.value, color = TextDim, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    hint: String,
    password: Boolean = false,
    onChange: (String) -> Unit,
) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(label, color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(hint, color = TextDim) },
            singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextC,
                unfocusedTextColor = TextC,
                focusedContainerColor = Surface,
                unfocusedContainerColor = Surface,
                focusedBorderColor = BorderC,
                unfocusedBorderColor = BorderC,
                cursorColor = Green,
            ),
        )
    }
}
