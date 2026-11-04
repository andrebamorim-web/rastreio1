package br.com.hasolucoes.rastreio

import android.content.Context
import android.location.LocationManager
import android.telephony.TelephonyManager

/** Registra quando GPS ou dados móveis mudam de estado (aparece no relatório). */
object Status {
    private const val PREFS = "status"

    fun check(ctx: Context) {
        val lm = ctx.getSystemService(LocationManager::class.java)
        val gps = runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrNull()
        if (gps != null) transition(ctx, "gps", gps, "GPS_LIGADO", "GPS_DESLIGADO")

        val tm = ctx.getSystemService(TelephonyManager::class.java)
        val data = runCatching { tm.isDataEnabled }.getOrNull()
        if (data != null) transition(ctx, "data", data, "DADOS_LIGADOS", "DADOS_DESLIGADOS")
    }

    private fun transition(ctx: Context, key: String, on: Boolean, onType: String, offType: String) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val prev = prefs.getInt(key, -1)
        val cur = if (on) 1 else 0
        if (prev == cur) return
        // No primeiro registro, só anota se já começou desligado.
        if (prev != -1 || !on) Store.get(ctx).addEvent(if (on) onType else offType)
        prefs.edit().putInt(key, cur).apply()
    }
}
