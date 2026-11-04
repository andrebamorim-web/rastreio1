package br.com.hasolucoes.rastreio

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class Attachment(val name: String, val mime: String, val bytes: ByteArray)
class ReportData(val subject: String, val body: String, val attachments: List<Attachment>)

/** Monta o relatório do dia: resumo no corpo + CSV (Excel) + KML (Google Earth / My Maps). */
object Report {

    private val ptBR = Locale("pt", "BR")

    private val eventNames = mapOf(
        "APARELHO_LIGADO" to "Aparelho ligado",
        "APARELHO_DESLIGADO" to "Aparelho desligado",
        "GPS_DESLIGADO" to "GPS desligado",
        "GPS_LIGADO" to "GPS religado",
        "DADOS_DESLIGADOS" to "Dados móveis desligados",
        "DADOS_LIGADOS" to "Dados móveis religados",
        "REGISTRO_INICIADO" to "Registro iniciado",
        "REGISTRO_ENCERRADO" to "Registro encerrado",
        "APP_ATUALIZADO" to "App atualizado",
        "GERENCIAMENTO_ATIVADO" to "Gerenciamento ativado",
        "PIN_INCORRETO" to "Tentativa de acesso ao painel com PIN incorreto",
        "ERRO_ENVIO" to "Falha ao enviar relatório",
        "ERRO" to "Erro"
    )

    fun build(ctx: Context, day: String, partial: Boolean = false): ReportData {
        val store = Store.get(ctx)
        val from = Days.startOf(day)
        val to = Days.endOf(day)
        val points = store.points(from, to)
        val events = store.events(from, to)

        val hm = SimpleDateFormat("HH:mm", ptBR)
        val dateBr = SimpleDateFormat("dd/MM/yyyy", ptBR).format(Date(from))
        val weekday = SimpleDateFormat("EEEE", ptBR).format(Date(from))
        val label = BuildConfig.DEVICE_LABEL

        val sb = StringBuilder()
        sb.appendLine("Relatório de deslocamento – $label")
        sb.appendLine("$weekday, $dateBr${if (partial) " (PARCIAL – envio de teste)" else ""}")
        sb.appendLine()

        if (points.isEmpty()) {
            sb.appendLine("Nenhuma posição registrada neste dia.")
            sb.appendLine("Possíveis causas: aparelho desligado, sem bateria ou sem sinal de GPS durante todo o expediente.")
        } else {
            val expected = (Config.WINDOW_END_HOUR - Config.WINDOW_START_HOUR) * 60 / Config.INTERVAL_MINUTES
            val good = points.filter { it.acc <= 100f && !it.mock }
            sb.appendLine("Primeiro registro: ${hm.format(Date(points.first().ts))}")
            sb.appendLine("Último registro: ${hm.format(Date(points.last().ts))}")
            sb.appendLine("Posições registradas: ${points.size} (máximo possível no dia: $expected)")
            sb.appendLine("Distância aproximada: ${"%.1f".format(ptBR, distanceKm(good))} km")
            val bStart = points.first().battery
            val bEnd = points.last().battery
            if (bStart >= 0 && bEnd >= 0) sb.appendLine("Bateria: $bStart% no início, $bEnd% no fim")

            val mocks = points.count { it.mock }
            if (mocks > 0) sb.appendLine("ATENÇÃO: $mocks posição(ões) vieram de localização simulada (possível GPS falso).")

            val gaps = gaps(points)
            sb.appendLine()
            if (gaps.isEmpty()) {
                sb.appendLine("Sem intervalos longos sem registro.")
            } else {
                sb.appendLine("Intervalos sem registro acima de ${Config.GAP_ALERT_MINUTES} min:")
                for ((a, b) in gaps) {
                    sb.appendLine("  • ${hm.format(Date(a))} a ${hm.format(Date(b))} (${(b - a) / 60_000} min)")
                }
            }
        }

        val relevant = events.filter { it.type != "REGISTRO_INICIADO" && it.type != "REGISTRO_ENCERRADO" }
        if (relevant.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Ocorrências no aparelho:")
            for (e in relevant) {
                val name = eventNames[e.type] ?: e.type
                val detail = if (e.detail.isNotBlank()) " – ${e.detail}" else ""
                sb.appendLine("  • ${hm.format(Date(e.ts))} $name$detail")
            }
        }

        sb.appendLine()
        sb.appendLine("Anexos: planilha (.csv, abre no Excel) e trajeto (.kml, abre no Google Earth ou no Google My Maps).")

        val base = "trajeto_${label.lowercase(ptBR).replace(Regex("[^a-z0-9]+"), "-")}_$day"
        val attachments = if (points.isEmpty()) emptyList() else listOf(
            Attachment("$base.csv", "text/csv; charset=utf-8", csv(points)),
            Attachment("$base.kml", "application/vnd.google-earth.kml+xml", kml(points, "$label – $dateBr"))
        )

        val subject = "Relatório de deslocamento – $label – $dateBr${if (partial) " (teste)" else ""}"
        return ReportData(subject, sb.toString(), attachments)
    }

    private fun gaps(points: List<Point>): List<Pair<Long, Long>> {
        val limit = Config.GAP_ALERT_MINUTES * 60_000L
        return points.zipWithNext().filter { (a, b) -> b.ts - a.ts > limit }.map { (a, b) -> a.ts to b.ts }
    }

    private fun distanceKm(points: List<Point>): Double =
        points.zipWithNext().sumOf { (a, b) -> haversineKm(a.lat, a.lon, b.lat, b.lon) }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    /** CSV no padrão do Excel brasileiro: separador ";" e vírgula decimal. */
    private fun csv(points: List<Point>): ByteArray {
        val dt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", ptBR)
        val sb = StringBuilder("﻿")
        sb.append("data_hora;latitude;longitude;precisao_m;bateria_pct;localizacao_simulada;link_mapa\r\n")
        for (p in points) {
            sb.append(dt.format(Date(p.ts))).append(';')
                .append(decimal(p.lat, 6)).append(';')
                .append(decimal(p.lon, 6)).append(';')
                .append(p.acc.toInt()).append(';')
                .append(if (p.battery >= 0) p.battery.toString() else "").append(';')
                .append(if (p.mock) "sim" else "não").append(';')
                .append("https://maps.google.com/?q=${"%.6f".format(Locale.US, p.lat)},${"%.6f".format(Locale.US, p.lon)}")
                .append("\r\n")
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun decimal(v: Double, places: Int) = "%.${places}f".format(ptBR, v)

    private fun kml(points: List<Point>, title: String): ByteArray {
        val hm = SimpleDateFormat("HH:mm", ptBR)
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        fun coord(p: Point) = "${"%.6f".format(Locale.US, p.lon)},${"%.6f".format(Locale.US, p.lat)},0"
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document>\n")
        sb.append("<name>").append(xml(title)).append("</name>\n")
        sb.append("<Style id=\"linha\"><LineStyle><color>ffd77800</color><width>4</width></LineStyle></Style>\n")
        sb.append("<Placemark><name>Trajeto</name><styleUrl>#linha</styleUrl><LineString><tessellate>1</tessellate><coordinates>\n")
        for (p in points) if (!p.mock) sb.append(coord(p)).append('\n')
        sb.append("</coordinates></LineString></Placemark>\n")
        sb.append("<Folder><name>Posições</name>\n")
        for (p in points) {
            val desc = "Precisão: ${p.acc.toInt()} m" +
                (if (p.battery >= 0) " · Bateria: ${p.battery}%" else "") +
                (if (p.mock) " · LOCALIZAÇÃO SIMULADA" else "")
            sb.append("<Placemark><name>").append(hm.format(Date(p.ts))).append("</name>")
                .append("<description>").append(xml(desc)).append("</description>")
                .append("<TimeStamp><when>").append(iso.format(Date(p.ts))).append("</when></TimeStamp>")
                .append("<Point><coordinates>").append(coord(p)).append("</coordinates></Point></Placemark>\n")
        }
        sb.append("</Folder>\n</Document></kml>\n")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
