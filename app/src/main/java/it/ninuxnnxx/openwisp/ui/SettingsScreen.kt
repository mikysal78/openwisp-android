package it.ninuxnnxx.openwisp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.ninuxnnxx.openwisp.BuildConfig
import it.ninuxnnxx.openwisp.R
import it.ninuxnnxx.openwisp.data.Session
import it.ninuxnnxx.openwisp.data.SessionStore

@Composable
fun SettingsScreen(
    store: SessionStore,
    session: Session,
    lockAvailable: Boolean,
    onNotificationsChanged: (Boolean) -> Unit,
    onLogout: () -> Unit,
) {
    var lock by remember { mutableStateOf(store.appLock) }
    var notify by remember { mutableStateOf(store.notifications) }
    var confirmLogout by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_account)) },
            supportingContent = { Text("${session.username} @ ${session.server.host}") },
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_lock)) },
            supportingContent = {
                Text(stringResource(if (lockAvailable) R.string.settings_lock_text else R.string.settings_lock_unavailable))
            },
            trailingContent = {
                Switch(lock && lockAvailable, { lock = it; store.appLock = it }, enabled = lockAvailable)
            },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_notifications)) },
            supportingContent = { Text(stringResource(R.string.settings_notifications_text)) },
            trailingContent = {
                Switch(notify, { notify = it; store.notifications = it; onNotificationsChanged(it) })
            },
        )
        HorizontalDivider()
        Column(Modifier.padding(16.dp)) {
            OutlinedButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_logout))
            }
            Text(
                stringResource(R.string.settings_logout_note),
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME}",
                Modifier.padding(top = 24.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = stringResource(R.string.settings_logout),
            text = stringResource(R.string.settings_logout_confirm),
            confirm = stringResource(R.string.settings_logout),
            onConfirm = onLogout,
            onDismiss = { confirmLogout = false },
        )
    }
}
