package it.ninuxnnxx.openwisp.ui

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import it.ninuxnnxx.openwisp.R
import it.ninuxnnxx.openwisp.data.Build
import it.ninuxnnxx.openwisp.data.Command
import it.ninuxnnxx.openwisp.data.DeviceDetail
import it.ninuxnnxx.openwisp.data.Features
import it.ninuxnnxx.openwisp.data.FirmwareImage
import it.ninuxnnxx.openwisp.data.FirmwareStatus
import it.ninuxnnxx.openwisp.data.OpenWispApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val periods = listOf("1d", "3d", "7d", "30d")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    api: OpenWispApi,
    deviceId: String,
    features: Features,
    onUnauthorized: () -> Unit,
    onNetconsole: () -> Unit,
    snack: (String) -> Unit,
) {
    var period by rememberSaveable { mutableStateOf("1d") }
    val loader = rememberLoader(api, deviceId, period, features.monitoring) {
        api.device(deviceId, period, features.monitoring)
    }
    val connections = rememberLoader(api, deviceId) { api.connections(deviceId) }
    val commands = rememberLoader(api, deviceId) { api.commands(deviceId).results }

    LoadContent(loader, onUnauthorized) { device ->
        val sshOk = (connections.state as? Load.Ok)?.value?.any { it.enabled } == true
        PullToRefreshBox(loader.refreshing, { loader.reload(); connections.reload(); commands.reload() }, Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                item { Header(device) }
                item { InfoCard(device) }
                item {
                    Actions(api, device, features, sshOk, connections.state, snack, onNetconsole) { commands.reload() }
                }
                if (features.firmware) item { FirmwareCard(api, device, sshOk, snack) }
                item { CommandsCard(commands) }
                item { WifiCard(device) }
                if (features.monitoring) item {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        periods.forEach { p ->
                            FilterChip(period == p, { period = p }, label = { Text(stringResource(periodLabel(p))) })
                        }
                    }
                }
                device.charts.forEach { chart ->
                    item { ChartCard(chart, device.x) }
                }
                item { Spacer24() }
            }
        }
    }
}

private fun periodLabel(p: String) = when (p) {
    "1d" -> R.string.period_1d
    "3d" -> R.string.period_3d
    "7d" -> R.string.period_7d
    else -> R.string.period_30d
}

@Composable
private fun Spacer24() = Column(Modifier.padding(12.dp)) {}

@Composable
private fun Header(device: DeviceDetail) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Text(device.name, style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(device.monitoring?.status)
            StatusChip(device.config?.status)
        }
        device.config?.errorReason?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun InfoCard(device: DeviceDetail) {
    val data = device.data
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            InfoRow(stringResource(R.string.info_management_ip), device.managementIp)
            InfoRow(stringResource(R.string.info_last_ip), device.lastIp)
            InfoRow(stringResource(R.string.info_mac), device.macAddress)
            InfoRow(stringResource(R.string.info_model), device.model)
            InfoRow(stringResource(R.string.info_os), device.os)
            InfoRow(stringResource(R.string.info_system), device.system)
            data?.general?.uptime?.let { InfoRow(stringResource(R.string.info_uptime), formatDuration(it)) }
            data?.resources?.let { r ->
                if (r.load.isNotEmpty()) {
                    InfoRow(stringResource(R.string.info_load), r.load.joinToString(" ") { "%.2f".format(it) })
                }
                r.memory?.takeIf { it.total > 0 }?.let { m ->
                    val used = m.total - (m.available ?: m.free)
                    InfoRow(
                        stringResource(R.string.info_memory),
                        "${used * 100 / m.total}% (${formatBytes(used)} / ${formatBytes(m.total)})",
                    )
                }
                r.disk.filter { it.mountPoint == "/overlay" || it.mountPoint == "/" }.forEach { d ->
                    InfoRow(stringResource(R.string.info_disk, d.mountPoint), "${d.usedPercent}% / ${formatBytes(d.sizeBytes)}")
                }
            }
            InfoRow(stringResource(R.string.info_notes), device.notes)
        }
    }
}

@Composable
private fun Actions(
    api: OpenWispApi,
    device: DeviceDetail,
    features: Features,
    sshOk: Boolean,
    connections: Load<*>,
    snack: (String) -> Unit,
    onNetconsole: () -> Unit,
    onCommandSent: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var confirmReboot by remember { mutableStateOf(false) }
    var customOpen by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    var confirmCustom by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun run(block: suspend () -> Unit) {
        busy = true
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                snack(errorMessage(context, e))
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.padding(top = 12.dp)) {
        Text(stringResource(R.string.actions), style = MaterialTheme.typography.titleMedium)
        if (connections is Load.Ok<*> && !sshOk) {
            Text(
                stringResource(R.string.actions_no_ssh),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // i moduli che il server non ha non compaiono proprio
            if (features.luci) AssistChip(
                onClick = {
                    run {
                        val url = api.luciLink(device.id)
                        CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
                    }
                },
                enabled = !busy && device.managementIp != null,
                label = { Text("LuCI") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)) },
            )
            if (features.netconsole) AssistChip(
                onClick = onNetconsole,
                label = { Text("Netconsole") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Article, null, Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = { customOpen = true },
                enabled = !busy && sshOk,
                label = { Text(stringResource(R.string.action_command)) },
                leadingIcon = { Icon(Icons.Default.Terminal, null, Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = { confirmReboot = true },
                enabled = !busy && sshOk,
                label = { Text(stringResource(R.string.action_reboot)) },
                leadingIcon = { Icon(Icons.Default.RestartAlt, null, Modifier.size(18.dp)) },
            )
        }
    }

    if (confirmReboot) {
        ConfirmDialog(
            title = stringResource(R.string.reboot_title, device.name),
            text = stringResource(R.string.reboot_text),
            confirm = stringResource(R.string.action_reboot),
            onConfirm = {
                run {
                    api.reboot(device.id)
                    snack(resources.getString(R.string.command_sent))
                    onCommandSent()
                }
            },
            onDismiss = { confirmReboot = false },
        )
    }
    if (customOpen) {
        AlertDialog(
            onDismissRequest = { customOpen = false },
            title = { Text(stringResource(R.string.command_title, device.name)) },
            text = {
                Column {
                    Text(stringResource(R.string.command_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        customText, { customText = it },
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        placeholder = { Text("uptime") },
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { customOpen = false; confirmCustom = true }, enabled = customText.isNotBlank()) {
                    Text(stringResource(R.string.command_next))
                }
            },
            dismissButton = { TextButton(onClick = { customOpen = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmCustom) {
        ConfirmDialog(
            title = stringResource(R.string.command_confirm_title, device.name),
            text = customText.trim(),
            confirm = stringResource(R.string.command_run),
            onConfirm = {
                run {
                    val sent = api.custom(device.id, customText.trim())
                    customText = ""
                    snack(resources.getString(R.string.command_sent))
                    onCommandSent()
                    // il comando gira in background sul server: si aspetta l'esito
                    repeat(15) {
                        delay(2000)
                        if (api.command(device.id, sent.id).status != "in-progress") {
                            onCommandSent()
                            return@run
                        }
                    }
                }
            },
            onDismiss = { confirmCustom = false },
        )
    }
}

@Composable
private fun CommandsCard(loader: Loader<List<Command>>) {
    val list = (loader.state as? Load.Ok)?.value ?: return
    if (list.isEmpty()) return
    var expanded by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(top = 12.dp)) {
        Text(stringResource(R.string.commands_recent), style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            list.take(5).forEachIndexed { i, cmd ->
                if (i > 0) HorizontalDivider()
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(
                            when (cmd.status) {
                                "success" -> "ok"
                                "failed" -> "critical"
                                else -> "problem"
                            },
                        )
                        Text(
                            commandTitle(cmd),
                            Modifier.weight(1f).padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = if (cmd.type == "custom") FontFamily.Monospace else null,
                        )
                        Text(formatTime(cmd.created), style = MaterialTheme.typography.labelSmall)
                    }
                    val output = cmd.output.orEmpty().trim()
                    if (output.isNotEmpty()) {
                        val full = expanded == cmd.id
                        Text(
                            if (full || output.length < 200) output else output.take(200) + "…",
                            Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        if (output.length >= 200) {
                            TextButton(onClick = { expanded = if (full) null else cmd.id }) {
                                Text(stringResource(if (full) R.string.less else R.string.more))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun commandTitle(cmd: Command): String = when (cmd.type) {
    "custom" -> runCatching {
        (cmd.input as kotlinx.serialization.json.JsonObject)["command"]!!
            .let { (it as kotlinx.serialization.json.JsonPrimitive).content }
    }.getOrDefault("custom")
    else -> cmd.type.replaceFirstChar { it.uppercase() }
}

@Composable
private fun FirmwareCard(api: OpenWispApi, device: DeviceDetail, sshOk: Boolean, snack: (String) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var state by remember(device.id) { mutableStateOf<Load<FirmwareStatus?>?>(null) }
    var confirm by remember { mutableStateOf<Pair<Build, FirmwareImage>?>(null) }
    var ops by remember(device.id) { mutableStateOf<List<it.ninuxnnxx.openwisp.data.UpgradeOperation>>(emptyList()) }

    fun check() {
        state = Load.Loading
        scope.launch {
            state = try {
                ops = api.upgradeOperations(device.id).results
                Load.Ok(api.firmware(device.id))
            } catch (e: Exception) {
                Load.Failed(errorMessage(context, e), false)
            }
        }
    }

    Column(Modifier.padding(top = 12.dp)) {
        Text(stringResource(R.string.firmware), style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Column(Modifier.padding(12.dp)) {
                when (val s = state) {
                    null -> OutlinedButton(onClick = ::check) {
                        Icon(Icons.Default.SystemUpdate, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.firmware_check), Modifier.padding(start = 8.dp))
                    }
                    Load.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.firmware_checking), Modifier.padding(start = 8.dp))
                    }
                    is Load.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
                    is Load.Ok -> {
                        val fw = s.value
                        if (fw?.installed == null) {
                            Text(stringResource(R.string.firmware_unknown))
                        } else {
                            InfoRow(stringResource(R.string.firmware_installed), fw.installed.version)
                            InfoRow(stringResource(R.string.firmware_image), fw.image?.type)
                            if (fw.newer.isEmpty()) {
                                Text(stringResource(R.string.firmware_latest), style = MaterialTheme.typography.bodyMedium)
                            }
                            fw.newer.take(3).forEach { pair ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(pair.first.version, style = MaterialTheme.typography.bodyMedium)
                                        Text(formatTime(pair.first.created), style = MaterialTheme.typography.labelSmall)
                                    }
                                    TextButton(onClick = { confirm = pair }, enabled = sshOk) {
                                        Text(stringResource(R.string.firmware_install))
                                    }
                                }
                            }
                        }
                        ops.firstOrNull()?.let { op ->
                            InfoRow(stringResource(R.string.firmware_last_op), "${op.status} · ${formatTime(op.modified)}")
                        }
                    }
                }
            }
        }
    }

    confirm?.let { (build, image) ->
        ConfirmDialog(
            title = stringResource(R.string.firmware_confirm_title, device.name),
            text = stringResource(R.string.firmware_confirm_text, build.version, image.type),
            confirm = stringResource(R.string.firmware_install),
            onConfirm = {
                scope.launch {
                    try {
                        api.upgrade(device.id, image.id)
                        snack(resources.getString(R.string.firmware_started))
                        ops = api.upgradeOperations(device.id).results
                    } catch (e: Exception) {
                        snack(errorMessage(context, e))
                    }
                }
            },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun WifiCard(device: DeviceDetail) {
    val radios = device.data?.interfaces?.filter { it.wireless != null }.orEmpty()
    if (radios.isEmpty()) return
    Column(Modifier.padding(top = 12.dp)) {
        Text(stringResource(R.string.wifi_interfaces), style = MaterialTheme.typography.titleMedium)
        radios.sortedBy { it.name }.forEach { iface ->
            val w = iface.wireless!!
            Card(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(if (iface.up) "ok" else "critical")
                        Text(
                            w.ssid ?: iface.name,
                            Modifier.weight(1f).padding(start = 8.dp),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(pluralStringResource(R.plurals.wifi_clients, w.clients.size, w.clients.size), style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        listOfNotNull(
                            iface.name,
                            w.mode?.replace('_', ' '),
                            w.channel?.let { "ch $it" },
                            w.frequency?.let { "$it MHz" },
                            w.htmode,
                            w.txPower?.let { "$it dBm" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    w.clients.sortedByDescending { it.signal ?: -200 }.forEach { c ->
                        Row(Modifier.padding(top = 2.dp)) {
                            Text(c.mac, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            c.signal?.let { Text("$it dBm", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}
