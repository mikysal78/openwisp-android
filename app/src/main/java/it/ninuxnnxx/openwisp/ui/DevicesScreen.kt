package it.ninuxnnxx.openwisp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.ninuxnnxx.openwisp.R
import it.ninuxnnxx.openwisp.data.Device
import it.ninuxnnxx.openwisp.data.OpenWispApi

private val statusOrder = listOf("critical", "problem", "unknown", "ok", "deactivated")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(api: OpenWispApi, onUnauthorized: () -> Unit, onOpen: (Device) -> Unit) {
    val loader = rememberLoader(api) { api.devices() }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }

    LoadContent(loader, onUnauthorized) { devices ->
        val counts = devices.groupingBy { it.monitoring?.status ?: "unknown" }.eachCount()
        val shown = devices
            .filter { filter == null || (it.monitoring?.status ?: "unknown") == filter }
            .filter { d ->
                query.isBlank() || listOfNotNull(d.name, d.managementIp, d.lastIp, d.macAddress, d.model)
                    .any { it.contains(query.trim(), ignoreCase = true) }
            }
            // prima i router con problemi, poi in ordine di nome
            .sortedWith(compareBy({ statusOrder.indexOf(it.monitoring?.status ?: "unknown") }, { it.name.lowercase() }))

        PullToRefreshBox(loader.refreshing, loader.reload, Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OutlinedTextField(
                            query, { query = it },
                            Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            placeholder = { Text(stringResource(R.string.devices_search)) },
                            singleLine = true,
                        )
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                filter == null, { filter = null },
                                label = { Text(stringResource(R.string.devices_all, devices.size)) },
                            )
                            statusOrder.filter { (counts[it] ?: 0) > 0 }.forEach { status ->
                                FilterChip(
                                    filter == status, { filter = if (filter == status) null else status },
                                    label = { Text("${statusLabel(status)} ${counts[status]}") },
                                    leadingIcon = { StatusDot(status) },
                                )
                            }
                        }
                    }
                }
                if (shown.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.devices_none),
                            Modifier.fillMaxWidth().padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(shown, key = { it.id }) { device ->
                    DeviceRow(device) { onOpen(device) }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: Device, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { StatusDot(device.monitoring?.status) },
        headlineContent = { Text(device.name) },
        supportingContent = {
            Column {
                Text(listOfNotNull(device.managementIp, device.model).joinToString(" · "))
                device.os?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        trailingContent = {
            val config = device.config?.status
            if (config != null && config != "applied") {
                Row(verticalAlignment = Alignment.CenterVertically) { StatusChip(config) }
            }
        },
    )
}
