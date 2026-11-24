package nl.gkandijk.geluid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.gkandijk.geluid.FaderDef
import nl.gkandijk.geluid.MainViewModel
import nl.gkandijk.geluid.Page
import nl.gkandijk.geluid.PresetDef
import nl.gkandijk.geluid.ttp.ConnState
import nl.gkandijk.geluid.ttp.Sub
import nl.gkandijk.geluid.ttp.TtpParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    val state by vm.client.state.collectAsStateWithLifecycle()
    val error by vm.client.lastError.collectAsStateWithLifecycle()
    val values by vm.client.values.collectAsStateWithLifecycle()
    val ranges by vm.client.ranges.collectAsStateWithLifecycle()

    val pages = vm.layout.pages.filter { !it.advanced || vm.unlocked }
    var selectedTab by remember { mutableIntStateOf(0) }
    val selected = if (selectedTab < pages.size) selectedTab else 0

    var showSettings by remember { mutableStateOf(vm.config.host.isBlank()) }
    var showPin by remember { mutableStateOf(false) }
    var confirmPreset by remember { mutableStateOf<PresetDef?>(null) }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(vm.layout.title) },
                actions = {
                    ConnectionBadge(state, error)
                    if (vm.unlocked) {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Instellingen")
                        }
                        TextButton(onClick = { vm.unlocked = false }) { Text("Vergrendel") }
                    } else {
                        IconButton(onClick = { showPin = true }) {
                            Icon(Icons.Filled.Lock, contentDescription = "Beheer ontgrendelen")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (pages.size > 1) {
                ScrollableTabRow(selectedTabIndex = selected, edgePadding = 8.dp) {
                    pages.forEachIndexed { i, p ->
                        Tab(selected = i == selected, onClick = { selectedTab = i }, text = { Text(p.title) })
                    }
                }
            }
            pages.getOrNull(selected)?.let { page ->
                PageContent(
                    page = page,
                    values = values,
                    ranges = ranges,
                    enabled = state == ConnState.CONNECTED,
                    onPreset = { p -> if (p.confirm) confirmPreset = p else vm.recallPreset(p) },
                    onLevel = { f, v -> vm.client.setLevel(f.tag, f.ch, v) },
                    onMute = { f, m -> vm.client.setMute(f.muteTagOrDefault, f.muteChOrDefault, m) },
                )
            }
        }
    }

    confirmPreset?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmPreset = null },
            title = { Text(p.label) },
            text = { Text("Weet je het zeker?") },
            confirmButton = {
                Button(onClick = { vm.recallPreset(p); confirmPreset = null }) { Text("Ja") }
            },
            dismissButton = { TextButton(onClick = { confirmPreset = null }) { Text("Annuleren") } },
        )
    }

    if (showPin) {
        PinDialog(
            onDismiss = { showPin = false },
            onSubmit = { entered ->
                if (vm.tryUnlock(entered)) showPin = false
                vm.unlocked
            },
        )
    }

    if (showSettings) {
        SettingsDialog(
            config = vm.config,
            pin = vm.pin,
            onDismiss = { showSettings = false },
            onSave = { c, p ->
                vm.save(c, p)
                showSettings = false
            },
        )
    }
}

@Composable
private fun ConnectionBadge(state: ConnState, error: String?) {
    val (color, text) = when (state) {
        ConnState.CONNECTED -> Color(0xFF2E7D32) to "Verbonden"
        ConnState.CONNECTING -> Color(0xFFF9A825) to "Verbinden…"
        ConnState.DISCONNECTED -> Color(0xFFC62828) to "Geen verbinding"
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(text, style = MaterialTheme.typography.labelLarge)
            if (state != ConnState.CONNECTED && error != null) {
                Text(error, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageContent(
    page: Page,
    values: Map<String, String>,
    ranges: Map<String, Pair<Float, Float>>,
    enabled: Boolean,
    onPreset: (PresetDef) -> Unit,
    onLevel: (FaderDef, Float) -> Unit,
    onMute: (FaderDef, Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        if (page.presets.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                page.presets.forEach { p ->
                    Button(
                        onClick = { onPreset(p) },
                        enabled = enabled,
                        modifier = Modifier.width(170.dp).height(64.dp),
                    ) {
                        Text(p.label, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        if (page.faders.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().weight(1f).horizontalScroll(rememberScrollState()),
            ) {
                page.faders.forEach { f ->
                    val levelKey = Sub.key(f.tag, "level", f.ch)
                    val muteKey = Sub.key(f.muteTagOrDefault, "mute", f.muteChOrDefault)
                    val dev = ranges[levelKey]
                    var lo = f.min
                    var hi = f.max
                    if (dev != null) {
                        lo = maxOf(lo, dev.first)
                        hi = minOf(hi, dev.second)
                    }
                    if (hi <= lo) {
                        lo = f.min
                        hi = f.max
                    }
                    FaderStrip(
                        def = f,
                        level = TtpParser.toFloat(values[levelKey]),
                        muted = TtpParser.toBool(values[muteKey]),
                        range = lo..hi,
                        enabled = enabled,
                        onLevel = { onLevel(f, it) },
                        onMute = { onMute(f, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FaderStrip(
    def: FaderDef,
    level: Float?,
    muted: Boolean?,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onLevel: (Float) -> Unit,
    onMute: (Boolean) -> Unit,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = (dragging ?: level ?: range.start).coerceIn(range.start, range.endInclusive)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp).fillMaxHeight(),
    ) {
        Text(
            def.label,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.height(40.dp),
        )
        if (def.mute) {
            val isMuted = muted == true
            Button(
                onClick = { onMute(!isMuted) },
                enabled = enabled && muted != null,
                colors = if (isMuted) {
                    ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828), contentColor = Color.White)
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (isMuted) "UIT" else "AAN")
            }
        }
        if (def.fader) {
            VerticalSlider(
                value = shown,
                onValueChange = {
                    dragging = it
                    onLevel(it)
                },
                onValueChangeFinished = { dragging = null },
                valueRange = range,
                enabled = enabled && level != null,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
            )
            Text(
                if (level == null && dragging == null) "–" else String.format("%.1f dB", shown),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
