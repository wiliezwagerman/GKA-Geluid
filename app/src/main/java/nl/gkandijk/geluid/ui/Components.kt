package nl.gkandijk.geluid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import nl.gkandijk.geluid.MeterRange
import nl.gkandijk.geluid.ttp.ConnConfig
import nl.gkandijk.geluid.ttp.Protocol

/** Een gewone Slider, 90 graden gedraaid zodat hij verticaal staat (onderaan = minimum). */
@Composable
fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        enabled = enabled,
        modifier = modifier
            .graphicsLayer {
                rotationZ = 270f
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints(
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth,
                    ),
                )
                layout(placeable.height, placeable.width) {
                    placeable.place(-placeable.width, 0)
                }
            },
    )
}

@Composable
fun PinDialog(onDismiss: () -> Unit, onSubmit: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Beheer ontgrendelen") },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it; wrong = false },
                    label = { Text("Pincode") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = wrong,
                )
                if (wrong) Text("Onjuiste pincode")
            }
        },
        confirmButton = { Button(onClick = { wrong = !onSubmit(pin) }) { Text("Ontgrendel") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    config: ConnConfig,
    pin: String,
    onDismiss: () -> Unit,
    onSave: (ConnConfig, String) -> Unit,
) {
    var host by remember { mutableStateOf(config.host) }
    var protocol by remember { mutableStateOf(config.protocol) }
    var port by remember { mutableStateOf(config.port.toString()) }
    var user by remember { mutableStateOf(config.user) }
    var password by remember { mutableStateOf(config.password) }
    var newPin by remember { mutableStateOf(pin) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verbinding met de Tesira") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim() },
                    label = { Text("IP-adres Tesira") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = protocol == Protocol.TELNET,
                        onClick = { protocol = Protocol.TELNET; port = "23" },
                        label = { Text("Telnet") },
                    )
                    FilterChip(
                        selected = protocol == Protocol.SSH,
                        onClick = { protocol = Protocol.SSH; port = "22" },
                        label = { Text("SSH") },
                    )
                }
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit) },
                    label = { Text("Poort") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (protocol == Protocol.SSH) {
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text("Gebruiker") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Wachtwoord (leeg als er geen is)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter(Char::isDigit) },
                    label = { Text("Beheer-pincode") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val p = port.toIntOrNull() ?: if (protocol == Protocol.SSH) 22 else 23
                onSave(ConnConfig(host, protocol, p, user.ifBlank { "default" }, password), newPin)
            }) { Text("Opslaan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

/** Verticale meterbalk: groen, boven [MeterRange.warn] geel, boven [MeterRange.clip] rood. */
@Composable
fun MeterBar(db: Float?, range: MeterRange, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Color(0xFF263238))
        if (db == null || range.max <= range.min) return@Canvas
        fun y(v: Float): Float {
            val f = ((v - range.min) / (range.max - range.min)).coerceIn(0f, 1f)
            return size.height * (1f - f)
        }
        val top = y(db)
        val warnY = y(range.warn)
        val clipY = y(range.clip)
        // Groen deel
        val greenTop = maxOf(top, warnY)
        if (greenTop < size.height) {
            drawRect(Color(0xFF43A047), Offset(0f, greenTop), Size(size.width, size.height - greenTop))
        }
        // Geel deel
        if (top < warnY) {
            val yellowTop = maxOf(top, clipY)
            drawRect(Color(0xFFFDD835), Offset(0f, yellowTop), Size(size.width, warnY - yellowTop))
        }
        // Rood deel
        if (top < clipY) {
            drawRect(Color(0xFFE53935), Offset(0f, top), Size(size.width, clipY - top))
        }
    }
}
