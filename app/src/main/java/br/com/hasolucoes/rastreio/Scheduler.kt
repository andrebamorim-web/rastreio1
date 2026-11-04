package br.com.hasolucoes.rastreio

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Agenda início/fim do registro e o envio do relatório. */
object Scheduler {
    const val ACTION_START = "br.com.hasolucoes.rastreio.START"
    const val ACTION_STOP = "br.com.hasolucoes.rastreio.STOP"
    const val ACTION_REPORT = "br.com.hasolucoes.rastreio.REPORT"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun scheduleAll(ctx: Context) {
        setAlarm(ctx, ACTION_START, next(Config.WINDOW_START_HOUR, 0))
        setAlarm(ctx, ACTION_STOP, next(Config.WINDOW_END_HOUR, 0))
        setAlarm(ctx, ACTION_REPORT, next(Config.WINDOW_END_HOUR, Config.REPORT_DELAY_MINUTES))

        // Rede de segurança: a cada hora tenta enviar relatórios que ficaram pendentes.
        val catchUp = PeriodicWorkRequestBuilder<ReportWorker>(1, TimeUnit.HOURS)
            .setConstraints(network)
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork("catchup", ExistingPeriodicWorkPolicy.KEEP, catchUp)
    }

    fun sendPendingNow(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<ReportWorker>()
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("report-now", ExistingWorkPolicy.REPLACE, req)
    }

    fun ensureTracking(ctx: Context) {
        if (Config.inWindow()) {
            ctx.startForegroundService(Intent(ctx, TrackingService::class.java))
        }
    }

    /** Próximo horário h:m em dia útil, a partir de agora. */
    private fun next(hour: Int, minute: Int): Long {
        val now = System.currentTimeMillis()
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        while (!Config.isWorkDay(c)) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    private fun setAlarm(ctx: Context, action: String, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, action.hashCode(),
            Intent(ctx, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }
}
