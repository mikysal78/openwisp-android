package it.ninuxnnxx.openwisp

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import it.ninuxnnxx.openwisp.data.Session
import it.ninuxnnxx.openwisp.data.SessionStore
import it.ninuxnnxx.openwisp.data.isUuid
import it.ninuxnnxx.openwisp.ui.LoginScreen
import it.ninuxnnxx.openwisp.ui.OpenWispApp
import it.ninuxnnxx.openwisp.ui.OpenWispTheme
import it.ninuxnnxx.openwisp.ui.PendingRoute
import it.ninuxnnxx.openwisp.work.NotificationWorker
import java.time.Instant

/**
 * FragmentActivity per BiometricPrompt. Con il blocco attivo l'app chiede
 * impronta o PIN del telefono all'avvio e dopo [LOCK_AFTER_MS] in background:
 * il token da' gli stessi poteri dell'utente in OpenWISP.
 */
class MainActivity : FragmentActivity() {
    private val store by lazy { SessionStore(this) }
    private var session by mutableStateOf<Session?>(null)
    private var locked by mutableStateOf(true)
    private var expired by mutableStateOf(false)
    private var stoppedAt = 0L
    private val pending = PendingRoute(null)
    private var pendingKey by mutableIntStateOf(0)

    private val authenticators
        get() = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    private val lockAvailable
        get() = BiometricManager.from(this).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        session = store.load()
        handle(intent)
        if (session != null && store.notifications) NotificationWorker.schedule(this)
        setContent {
            OpenWispTheme {
                Surface(Modifier.fillMaxSize()) {
                    val current = session
                    when {
                        current == null -> Column {
                            if (expired) {
                                Text(
                                    stringResource(R.string.session_expired),
                                    Modifier.padding(start = 24.dp, end = 24.dp, top = 48.dp),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            LoginScreen(store.lastServer(), store.lastUsername(), ::loggedIn)
                        }
                        locked && needsLock() -> LockScreen()
                        else -> OpenWispApp(
                            store = store,
                            session = current,
                            lockAvailable = lockAvailable,
                            pending = pending,
                            pendingKey = pendingKey,
                            onNotificationsChanged = { on ->
                                if (on) NotificationWorker.schedule(this) else NotificationWorker.cancel(this)
                            },
                            onLogout = ::logout,
                        )
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun LockScreen() {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Lock, null)
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.titleMedium)
            Button(onClick = ::authenticate) { Text(stringResource(R.string.lock_unlock)) }
        }
    }

    private fun needsLock() = store.appLock && lockAvailable

    override fun onStart() {
        super.onStart()
        val away = stoppedAt == 0L || SystemClock.elapsedRealtime() - stoppedAt > LOCK_AFTER_MS
        if (away) locked = true
        if (locked && session != null && needsLock()) authenticate() else if (!needsLock()) locked = false
    }

    override fun onStop() {
        super.onStop()
        if (!locked) stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val device = intent?.getStringExtra(EXTRA_DEVICE)
        pending.route = when {
            isUuid(device) -> "device/$device"
            intent?.getBooleanExtra(EXTRA_NOTIFICATIONS, false) == true -> "notifications"
            else -> return
        }
        pendingKey++
    }

    private fun authenticate() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    locked = false
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.lock_title))
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }

    private fun loggedIn(new: Session) {
        store.save(new)
        // da qui in avanti solo le notifiche nuove
        store.lastNotification = Instant.now().toString()
        expired = false
        locked = false
        stoppedAt = SystemClock.elapsedRealtime()
        session = new
        if (store.notifications) NotificationWorker.schedule(this)
    }

    private fun logout(expired: Boolean) {
        NotificationWorker.cancel(this)
        store.clear()
        this.expired = expired
        session = null
    }

    companion object {
        const val EXTRA_DEVICE = "device"
        const val EXTRA_NOTIFICATIONS = "notifications"
        private const val LOCK_AFTER_MS = 2 * 60 * 1000L
    }
}
