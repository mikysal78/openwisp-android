package it.ninuxnnxx.openwisp.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import it.ninuxnnxx.openwisp.R
import it.ninuxnnxx.openwisp.data.Features
import it.ninuxnnxx.openwisp.data.OpenWispApi
import it.ninuxnnxx.openwisp.data.Session
import it.ninuxnnxx.openwisp.data.SessionStore
import kotlinx.coroutines.launch

private val Red = Color(0xFFC0392B)

@Composable
fun OpenWispTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFFFF8A80))
        else -> lightColorScheme(primary = Red)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab("devices", R.string.tab_devices, Icons.Default.Router),
    Tab("wifi", R.string.tab_wifi, Icons.Default.Wifi),
    Tab("notifications", R.string.tab_notifications, Icons.Default.Notifications),
    Tab("settings", R.string.tab_settings, Icons.Default.Settings),
)

/** Destinazione chiesta da una notifica: "device/<id>" o "notifications". */
class PendingRoute(var route: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenWispApp(
    store: SessionStore,
    session: Session,
    lockAvailable: Boolean,
    pending: PendingRoute,
    pendingKey: Int,
    onNotificationsChanged: (Boolean) -> Unit,
    onLogout: (expired: Boolean) -> Unit,
) {
    val api = remember(session) { OpenWispApi(session.server, session.token) }
    // senza risposta (rete assente) si mostra tutto: le singole schermate diranno l'errore
    val features by produceState(Features.ALL, api) {
        value = runCatching { api.features() }.getOrDefault(Features.ALL)
    }
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val snack: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }
    val unauthorized = { onLogout(true) }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && store.notifications) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(pendingKey) {
        pending.route?.let { target ->
            pending.route = null
            nav.navigate(target) { launchSingleTop = true }
        }
    }

    Scaffold(
        topBar = {
            val top = tabs.firstOrNull { it.route == route }
            TopAppBar(
                title = {
                    Text(
                        if (top != null) stringResource(top.label)
                        else if (route?.startsWith("netconsole") == true) "Netconsole"
                        else stringResource(R.string.device),
                    )
                },
                navigationIcon = {
                    if (top == null && route != null) {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.filter { it.route != "wifi" || features.radius }.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        NavHost(nav, startDestination = "devices", modifier = Modifier.padding(padding)) {
            composable("devices") {
                DevicesScreen(api, features, unauthorized) { nav.navigate("device/${it.id}") }
            }
            composable("wifi") { WifiScreen(api, unauthorized, snack) }
            composable("notifications") {
                NotificationsScreen(api, unauthorized, { nav.navigate("device/$it") }, snack)
            }
            composable("settings") {
                SettingsScreen(store, session, lockAvailable, onNotificationsChanged) { onLogout(false) }
            }
            composable("device/{id}", listOf(navArgument("id") { type = NavType.StringType })) { e ->
                val id = e.arguments?.getString("id").orEmpty()
                DeviceScreen(api, id, features, unauthorized, { nav.navigate("netconsole/$id") }, snack)
            }
            composable("netconsole/{id}", listOf(navArgument("id") { type = NavType.StringType })) { e ->
                NetconsoleScreen(api, e.arguments?.getString("id").orEmpty(), unauthorized)
            }
        }
    }
}
