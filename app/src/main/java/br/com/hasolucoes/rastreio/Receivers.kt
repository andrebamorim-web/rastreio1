package br.com.hasolucoes.rastreio

import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Chamado quando o app vira Device Owner (comando dpm set-device-owner). */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Days.rememberFirstDay(context)
        Store.get(context).addEvent("GERENCIAMENTO_ATIVADO")
        Policy.apply(context)
        Scheduler.scheduleAll(context)
        Scheduler.ensureTracking(context)
    }
}

/** Liga, desliga e atualização do app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = Store.get(context)
        when (intent.action) {
            Intent.ACTION_SHUTDOWN, "android.intent.action.QUICKBOOT_POWEROFF" -> {
                store.addEvent("APARELHO_DESLIGADO")
                return
            }
            Intent.ACTION_BOOT_COMPLETED -> store.addEvent("APARELHO_LIGADO")
            Intent.ACTION_MY_PACKAGE_REPLACED -> store.addEvent("APP_ATUALIZADO")
        }
        Days.rememberFirstDay(context)
        Policy.apply(context)
        Scheduler.scheduleAll(context)
        Scheduler.ensureTracking(context)
    }
}

/** Alarmes agendados pelo Scheduler. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Scheduler.ACTION_START -> {
                Policy.hideApps(context)
                Scheduler.ensureTracking(context)
            }
            Scheduler.ACTION_STOP -> context.stopService(Intent(context, TrackingService::class.java))
            Scheduler.ACTION_REPORT -> Scheduler.sendPendingNow(context)
        }
        Scheduler.scheduleAll(context)
    }
}

/** *#*#7278#*#* no discador abre o painel do administrador (protegido por PIN). */
class SecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.startActivity(
            Intent(context, AdminActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
