package it.ninuxnnxx.openwisp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.ninuxnnxx.openwisp.R
import it.ninuxnnxx.openwisp.data.ApiException
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.coroutines.cancellation.CancellationException

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Failed(val message: String, val unauthorized: Boolean) : Load<Nothing>
}

class Loader<T>(val state: Load<T>, val refreshing: Boolean, val reload: () -> Unit)

/**
 * Carica [block] quando cambiano le chiavi o con reload(); durante un reload
 * resta visibile il dato precedente (refreshing = true).
 */
@Composable
fun <T> rememberLoader(vararg keys: Any?, block: suspend () -> T): Loader<T> {
    var counter by remember { mutableIntStateOf(0) }
    var state by remember(*keys) { mutableStateOf<Load<T>>(Load.Loading) }
    var refreshing by remember(*keys) { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(*keys, counter) {
        refreshing = state is Load.Ok
        state = try {
            Load.Ok(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Failed(errorMessage(context, e), e is ApiException && e.code == 401)
        }
        refreshing = false
    }
    return Loader(state, refreshing) { counter++ }
}

fun errorMessage(context: android.content.Context, e: Throwable): String = when (e) {
    is ApiException -> when (e.code) {
        401 -> context.getString(R.string.error_unauthorized)
        403 -> context.getString(R.string.error_forbidden)
        else -> e.message ?: "HTTP ${e.code}"
    }
    is java.net.UnknownHostException -> context.getString(R.string.error_host)
    is javax.net.ssl.SSLException -> context.getString(R.string.error_tls, e.message ?: "")
    is IOException -> context.getString(R.string.error_network, e.message ?: e.javaClass.simpleName)
    else -> e.message ?: e.javaClass.simpleName
}

@Composable
fun <T> LoadContent(loader: Loader<T>, onUnauthorized: () -> Unit, content: @Composable (T) -> Unit) {
    when (val s = loader.state) {
        Load.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is Load.Failed -> {
            if (s.unauthorized) LaunchedEffect(Unit) { onUnauthorized() }
            ErrorBox(s.message, loader.reload)
        }
        is Load.Ok -> content(s.value)
    }
}

@Composable
fun ErrorBox(message: String, retry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
        Button(onClick = retry, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// --- stati di monitoring e configurazione ---

fun statusColor(status: String?): Color = when (status) {
    "ok", "applied" -> Color(0xFF2E7D32)
    "problem", "modified" -> Color(0xFFF9A825)
    "critical", "error" -> Color(0xFFC62828)
    "deactivated", "deactivating" -> Color(0xFF616161)
    else -> Color(0xFF9E9E9E)
}

@Composable
fun statusLabel(status: String?): String = when (status) {
    "ok" -> stringResource(R.string.status_ok)
    "problem" -> stringResource(R.string.status_problem)
    "critical" -> stringResource(R.string.status_critical)
    "deactivated" -> stringResource(R.string.status_deactivated)
    "applied" -> stringResource(R.string.config_applied)
    "modified" -> stringResource(R.string.config_modified)
    "error" -> stringResource(R.string.config_error)
    else -> stringResource(R.string.status_unknown)
}

@Composable
fun StatusDot(status: String?, modifier: Modifier = Modifier) {
    Surface(modifier.size(12.dp), shape = CircleShape, color = statusColor(status)) {}
}

@Composable
fun StatusChip(status: String?) {
    Surface(shape = MaterialTheme.shapes.small, color = statusColor(status).copy(alpha = 0.15f)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(status)
            Text(statusLabel(status), Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(0.4f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.6f), style = MaterialTheme.typography.bodyMedium)
    }
}

// --- formati ---

private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

fun formatTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching { dateTime.format(Instant.parse(iso).atZone(ZoneId.systemDefault())) }.getOrDefault(iso)
}

fun formatBytes(bytes: Long?): String {
    if (bytes == null) return ""
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "$bytes B" else "%.1f %s".format(value, units[unit])
}

@Composable
fun formatDuration(seconds: Long?): String {
    if (seconds == null) return ""
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        days > 0 -> stringResource(R.string.duration_days, days, hours)
        hours > 0 -> stringResource(R.string.duration_hours, hours, minutes)
        else -> stringResource(R.string.duration_minutes, minutes)
    }
}
