package it.ninuxnnxx.openwisp.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.ninuxnnxx.openwisp.MainActivity
import it.ninuxnnxx.openwisp.R
import it.ninuxnnxx.openwisp.data.ApiException
import it.ninuxnnxx.openwisp.data.OpenWispApi
import it.ninuxnnxx.openwisp.data.SessionStore
import it.ninuxnnxx.openwisp.data.deviceIdFromTarget
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Controlla le notifiche di OpenWISP ogni 15 minuti (il minimo di WorkManager)
 * e mostra quelle nuove. Niente servizi push esterni: il telefono interroga
 * solo il server OpenWISP, con il token dell'utente.
 */
class NotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = SessionStore(applicationContext)
        if (!store.notifications) return Result.success()
        val session = store.load() ?: return Result.success()
        val api = OpenWispApi(session.server, session.token)
        val page = try {
            api.notifications(unreadOnly = true, pageSize = 20)
        } catch (e: ApiException) {
            // 401/403: token revocato, inutile riprovare finche' non si rientra
            return if (e.code in 400..499) Result.success() else Result.retry()
        } catch (_: Exception) {
            return Result.retry()
        }
        val last = store.lastNotification?.let(::parseInstant)
        val newest = page.results.mapNotNull { parseInstant(it.timestamp) }.maxOrNull()
        if (last == null) {
            // primo controllo: si prende solo il segno, niente raffica di vecchie notifiche
            store.lastNotification = (newest ?: Instant.now()).toString()
            return Result.success()
        }
        val fresh = page.results
            .filter { n -> parseInstant(n.timestamp)?.let { it > last } == true }
            .sortedBy { it.timestamp }
        if (newest != null && newest > last) store.lastNotification = newest.toString()
        if (fresh.isEmpty()) return Result.success()
        show(applicationContext, fresh.takeLast(MAX_SHOWN).map {
            val text = HtmlCompat.fromHtml(it.message, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
            Shown(it.id, it.emailSubject?.substringAfter("] ")?.trim() ?: text, text, it.level, deviceIdFromTarget(it.targetUrl))
        })
        return Result.success()
    }

    private data class Shown(val id: String, val title: String, val text: String, val level: String?, val deviceId: String?)

    private fun show(context: Context, items: List<Shown>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        items.forEach { item ->
            val intent = Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_DEVICE, item.deviceId)
                .putExtra(MainActivity.EXTRA_NOTIFICATIONS, item.deviceId == null)
            val pending = PendingIntent.getActivity(
                context, item.id.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(item.title)
                .setContentText(item.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(item.text))
                .setPriority(
                    if (item.level == "error") NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT,
                )
                .setGroup(GROUP)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            manager.notify(item.id.hashCode(), notification)
        }
    }

    companion object {
        const val CHANNEL = "openwisp_notifications"
        private const val GROUP = "openwisp"
        private const val WORK = "openwisp_notifications"
        private const val MAX_SHOWN = 5

        fun parseInstant(text: String): Instant? = runCatching { Instant.parse(text) }.getOrNull()

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL,
                        context.getString(R.string.channel_notifications),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ),
                )
            }
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NotificationWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
        }
    }
}
