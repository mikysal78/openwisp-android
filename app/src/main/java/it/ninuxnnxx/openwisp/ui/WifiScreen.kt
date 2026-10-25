package it.ninuxnnxx.openwisp.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import it.ninuxnnxx.openwisp.R
import it.ninuxnnxx.openwisp.data.Device
import it.ninuxnnxx.openwisp.data.OpenWispApi
import it.ninuxnnxx.openwisp.data.RadiusSession
import it.ninuxnnxx.openwisp.data.User
import kotlinx.coroutines.launch

@Composable
fun WifiScreen(api: OpenWispApi, onUnauthorized: () -> Unit, snack: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.wifi_sessions_open)) })
            Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.wifi_sessions_recent)) })
            Tab(tab == 2, { tab = 2 }, text = { Text(stringResource(R.string.wifi_users)) })
        }
        when (tab) {
            0 -> Sessions(api, open = true, onUnauthorized)
            1 -> Sessions(api, open = false, onUnauthorized)
            else -> Users(api, onUnauthorized, snack)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sessions(api: OpenWispApi, open: Boolean, onUnauthorized: () -> Unit) {
    // il NAS e' l'IP di gestione del router: dai device si ricava il nome
    val loader = rememberLoader(api, open) {
        val devices = runCatching { api.devices(monitoring = false) }.getOrDefault(emptyList())
        api.sessions(open) to devices
    }
    LoadContent(loader, onUnauthorized) { (sessions, devices) ->
        val byIp = devices.filter { it.managementIp != null }.associateBy { it.managementIp!! }
        PullToRefreshBox(loader.refreshing, loader.reload, Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (sessions.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.wifi_no_sessions),
                            Modifier.fillMaxWidth().padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(sessions, key = { it.uniqueId }) { s ->
                    SessionRow(s, byIp)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SessionRow(s: RadiusSession, byIp: Map<String, Device>) {
    val router = s.nasIp?.let { byIp[it]?.name ?: it }
    ListItem(
        leadingContent = { StatusDot(if (s.stopTime == null) "ok" else "deactivated") },
        headlineContent = { Text(s.username ?: "?") },
        supportingContent = {
            Column {
                Text(listOfNotNull(router, s.framedIp, s.callingStationId).joinToString(" · "))
                Text(
                    listOfNotNull(
                        formatTime(s.startTime),
                        s.sessionTime?.let { formatDuration(it) },
                        "↓ ${formatBytes(s.outputOctets)} ↑ ${formatBytes(s.inputOctets)}",
                        s.terminateCause?.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun Users(api: OpenWispApi, onUnauthorized: () -> Unit, snack: (String) -> Unit) {
    val loader = rememberLoader(api) { api.users() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    // stato cambiato da qui, finche' non si ricarica la lista
    val changed = remember { mutableStateMapOf<String, Boolean>() }
    var confirm by remember { mutableStateOf<User?>(null) }

    LoadContent(loader, onUnauthorized) { users ->
        val shown = users
            .filter { u ->
                query.isBlank() || listOfNotNull(u.username, u.email, u.firstName, u.lastName)
                    .any { it.contains(query.trim(), ignoreCase = true) }
            }
            .sortedBy { it.username.lowercase() }
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                query, { query = it },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text(stringResource(R.string.users_search)) },
                singleLine = true,
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { user ->
                    val active = changed[user.id] ?: user.isActive
                    ListItem(
                        headlineContent = { Text(user.username) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    listOfNotNull(user.firstName, user.lastName).joinToString(" ").takeIf { it.isNotBlank() },
                                    user.email?.takeIf { it != user.username },
                                    if (user.isSuperuser) stringResource(R.string.users_superuser)
                                    else if (user.isStaff) stringResource(R.string.users_staff) else null,
                                ).joinToString(" · "),
                            )
                        },
                        trailingContent = {
                            // gli account di gestione non si bloccano da qui
                            if (!user.isStaff && !user.isSuperuser) {
                                Switch(active, { confirm = user })
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    confirm?.let { user ->
        val active = changed[user.id] ?: user.isActive
        ConfirmDialog(
            title = stringResource(if (active) R.string.users_block_title else R.string.users_unblock_title, user.username),
            text = stringResource(if (active) R.string.users_block_text else R.string.users_unblock_text),
            confirm = stringResource(if (active) R.string.users_block else R.string.users_unblock),
            onConfirm = {
                scope.launch {
                    try {
                        changed[user.id] = api.setUserActive(user.id, !active).isActive
                    } catch (e: Exception) {
                        snack(errorMessage(context, e))
                    }
                }
            },
            onDismiss = { confirm = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetconsoleScreen(api: OpenWispApi, deviceId: String, onUnauthorized: () -> Unit) {
    val loader = rememberLoader(api, deviceId) { api.netconsole(deviceId) }
    LoadContent(loader, onUnauthorized) { logs ->
        PullToRefreshBox(loader.refreshing, loader.reload, Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                when {
                    logs.all { it.lines.isEmpty() } -> item {
                        Text(stringResource(R.string.netconsole_empty), Modifier.padding(vertical = 24.dp))
                    }
                    else -> logs.filter { it.lines.isNotEmpty() }.forEach { log ->
                        item {
                            Text(
                                log.key,
                                Modifier.padding(top = 12.dp, bottom = 4.dp),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        item {
                            Text(
                                log.lines.joinToString("\n"),
                                Modifier.horizontalScroll(rememberScrollState()),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}
