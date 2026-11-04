package br.com.hasolucoes.rastreio

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/** Envia por e-mail todos os relatórios pendentes. Se falhar, tenta de novo mais tarde. */
class ReportWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    // Envio periódico e envio imediato podem coincidir: um de cada vez, para não duplicar e-mail.
    override fun doWork(): Result = synchronized(LOCK) { sendPending() }

    private fun sendPending(): Result {
        val ctx = applicationContext
        val store = Store.get(ctx)
        store.purge(System.currentTimeMillis() - (Config.RETENTION_DAYS + 1L) * Days.DAY_MS)

        for (day in Days.pending(ctx)) {
            try {
                val report = Report.build(ctx, day)
                Mailer.send(report)
                store.markSent(day)
            } catch (e: Exception) {
                store.addEvent("ERRO_ENVIO", "${e.javaClass.simpleName}: ${(e.message ?: "").take(200)}")
                return Result.retry()
            }
        }
        return Result.success()
    }

    companion object {
        private val LOCK = Any()
    }
}
