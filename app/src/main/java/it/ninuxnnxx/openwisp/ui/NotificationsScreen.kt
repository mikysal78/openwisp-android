package it.ninuxnnxx.openwisp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import it.ninuxnnxx.openwisp.R
import it.ninuxnnxx.openwisp.data.OpenWispApi
import it.ninuxnnxx.openwisp.data.deviceIdFromTarget
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    api: OpenWispApi,
    onUnauthorized: () -> Unit,
    onOpenDevice: (String) -> Unit,
    snack: (String) -> Unit,
) {
    val loader = rememberLoader(api) { api.notifications().results }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // lette da qui, finche' non si ricarica
    val read = remember { mutableStateListOf<String>() }

    LoadContent(loader, onUnauthorized) { list ->
        PullToRefreshBox(loader.refreshing, { read.clear(); loader.reload() }, Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    if (list.any { it.unread && it.id !in read }) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        api.markAllRead()
                                        read.addAll(list.map { it.id })
                                    } catch (e: Exception) {
                                        snack(errorMessage(context, e))
                                    }
                                }
                            },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) { Text(stringResource(R.string.notifications_read_all)) }
                    }
                }
                if (list.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.notifications_none),
                            Modifier.fillMaxWidth().padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(list, key = { it.id }) { n ->
                    val unread = n.unread && n.id !in read
                    val text = remember(n.id) {
                        HtmlCompat.fromHtml(n.message, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
                    }
                    val device = deviceIdFromTarget(n.targetUrl)
                    ListItem(
                        modifier = Modifier.clickable {
                            if (unread) {
                                scope.launch {
                                    runCatching { api.markRead(n.id) }.onSuccess { read.add(n.id) }
                                }
                            }
                            device?.let(onOpenDevice)
                        },
                        leadingContent = {
                            StatusDot(
                                when (n.level) {
                                    "error" -> "critical"
                                    "warning" -> "problem"
                                    "success" -> "ok"
                                    else -> if (unread) "ok" else "deactivated"
                                },
                            )
                        },
                        headlineContent = {
                            Text(
                                n.emailSubject?.substringAfter("] ")?.trim() ?: text,
                                fontWeight = if (unread) FontWeight.Bold else null,
                            )
                        },
                        supportingContent = {
                            Column {
                                Text(text, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    formatTime(n.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
