package com.orbita.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbita.tv.data.Account
import com.orbita.tv.net.Xtream

/**
 * Los datos los escribe el usuario; no hay nada codificado en la app. Se acepta
 * pegar la URL completa que manda el proveedor, porque escribir a mano con el
 * mando de un televisor es el peor trabajo del mundo.
 */
@Composable
fun LoginScreen(
    initial: Account,
    error: String?,
    busy: Boolean,
    onSave: (Account) -> Unit,
    onDiagnostics: () -> Unit,
) {
    var host by remember { mutableStateOf(initial.host) }
    var port by remember { mutableStateOf(if (initial.port > 0) initial.port.toString() else "80") }
    var user by remember { mutableStateOf(initial.username) }
    var pass by remember { mutableStateOf(initial.password) }
    var pasted by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BrandMark(size = 30.sp)
        Hint("Conecta tu lista Xtream. Los datos quedan solo en este televisor.")

        Spacer(Modifier.height(8.dp))
        SectionTitle("PEGAR LA URL DEL PROVEEDOR")
        Field(
            value = pasted,
            onValue = { text ->
                pasted = text
                Xtream.parsePasted(text)?.let { a ->
                    if (a.host.isNotBlank()) host = a.host
                    if (a.port > 0) port = a.port.toString()
                    if (a.username.isNotBlank()) user = a.username
                    if (a.password.isNotBlank()) pass = a.password
                }
            },
            label = "http://servidor:puerto/get.php?username=...&password=...",
        )
        Hint("Al pegarla se rellenan los campos de abajo. También puedes escribirlos a mano.")

        Spacer(Modifier.height(8.dp))
        SectionTitle("SERVIDOR")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(host, { host = it }, "Servidor (host)", Modifier.weight(3f))
            Field(port, { port = it.filter { c -> c.isDigit() } }, "Puerto", Modifier.width(140.dp), KeyboardType.Number)
        }
        Field(user, { user = it }, "Usuario")
        Field(pass, { pass = it }, "Contraseña")

        if (error != null) {
            Text(error, color = Tint.fail, fontSize = 14.sp)
        }

        Spacer(Modifier.height(8.dp))
        // El diagnostico tiene que alcanzarse desde aqui. Si la conexion falla
        // no se pasa de esta pantalla, y sin esto quedarias encerrado sin la
        // unica herramienta que explica por que falla.
        FocusRow(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Diagnóstico de red", color = Tint.text, fontSize = 16.sp)
                Hint("Si no conecta, esto dice por qué")
            }
        }
        Spacer(Modifier.height(8.dp))
        FocusRow(
            onClick = {
                onSave(
                    Account(
                        host = host.trim(),
                        port = port.toIntOrNull() ?: 80,
                        https = false,
                        username = user.trim(),
                        password = pass.trim(),
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (busy) "Conectando…" else "Conectar",
                color = Tint.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun Field(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Tint.cardFocused,
            unfocusedContainerColor = Tint.card,
            focusedIndicatorColor = Tint.accent,
            unfocusedIndicatorColor = Tint.line,
            focusedLabelColor = Tint.accent,
            unfocusedLabelColor = Tint.textSoft,
            focusedTextColor = Tint.text,
            unfocusedTextColor = Tint.text,
            cursorColor = Tint.accent,
        ),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboard),
    )
}
