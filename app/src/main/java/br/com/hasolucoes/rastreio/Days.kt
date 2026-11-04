package br.com.hasolucoes.rastreio

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Utilitários de datas no formato "yyyy-MM-dd". */
object Days {
    private const val PREFS = "rastreio"
    private const val KEY_FIRST_DAY = "first_day"
    const val DAY_MS = 24L * 60 * 60 * 1000

    private fun fmt() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun key(c: Calendar): String = fmt().format(c.time)

    fun today(): String = key(Calendar.getInstance())

    fun parse(key: String): Calendar = Calendar.getInstance().apply {
        time = fmt().parse(key)!!
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    fun startOf(key: String): Long = parse(key).timeInMillis

    /** Início do dia seguinte (correto mesmo em mudanças de horário). */
    fun endOf(key: String): Long = parse(key).apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis

    fun rememberFirstDay(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains(KEY_FIRST_DAY)) p.edit().putString(KEY_FIRST_DAY, today()).apply()
    }

    private fun firstDay(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FIRST_DAY, null) ?: today()

    /** Dias úteis já encerrados cujo relatório ainda não foi enviado. */
    fun pending(ctx: Context): List<String> {
        val store = Store.get(ctx)
        val now = Calendar.getInstance()

        val cutoffToday = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, Config.WINDOW_END_HOUR)
            set(Calendar.MINUTE, Config.REPORT_DELAY_MINUTES)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val last = parse(today())
        if (now.before(cutoffToday)) last.add(Calendar.DAY_OF_YEAR, -1)

        val oldest = parse(today()).apply { add(Calendar.DAY_OF_YEAR, -Config.RETENTION_DAYS) }
        var c = parse(firstDay(ctx))
        if (c.before(oldest)) c = oldest

        val out = mutableListOf<String>()
        while (!c.after(last)) {
            val k = key(c)
            if (Config.isWorkDay(c) && !store.isSent(k)) out += k
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        return out
    }
}
