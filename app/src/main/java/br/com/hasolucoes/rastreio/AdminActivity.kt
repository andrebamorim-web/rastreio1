package br.com.hasolucoes.rastreio

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Painel do administrador. Abre pelo código *#*#7278#*#* no discador, ou por ADB:
 * adb shell am start -n br.com.hasolucoes.rastreio/.AdminActivity
 */
class AdminActivity : Activity() {

    private val pad by lazy { (16 * resources.displayMetrics.density).toInt() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showPin()
    }

    private fun showPin() {
        val pin = EditText(this).apply {
            hint = "PIN do administrador"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val enter = Button(this).apply {
            text = "Entrar"
            setOnClickListener {
                val expected = BuildConfig.ADMIN_PIN_SHA256
                if (expected.isNotEmpty() && sha256(pin.text.toString()) == expected) {
                    showPanel()
                } else {
                    Store.get(this@AdminActivity).addEvent("PIN_INCORRETO")
                    toast("PIN incorreto")
                    pin.text.clear()
                }
            }
        }
        setContentView(column(header("Rastreio – administração"), pin, enter))
    }

    private fun showPanel() {
        val status = TextView(this).apply { textSize = 14f }
        fun refresh() { status.text = statusText() }
        refresh()

        val content = column(
            header("Rastreio – administração"),
            status,
            button("Atualizar status") { refresh() },
            button("Enviar relatório de hoje (teste)") { sendTest() },
            button("Enviar relatórios pendentes") {
                Scheduler.sendPendingNow(this); toast("Envio agendado")
            },
            button("Aplicar bloqueios e ocultar apps") {
                Policy.apply(this); refresh(); toast("Política aplicada")
            },
            button("Mostrar todos os apps") {
                toast("${Policy.unhideAll(this)} app(s) visíveis novamente")
            },
            button("Liberar bloqueios (manutenção)") {
                Policy.release(this); refresh()
                toast("Bloqueios retirados. Use \"Aplicar bloqueios\" ao terminar.")
            },
            button("Remover gerenciamento do aparelho") { confirmRemove() }
        )
        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun statusText(): String {
        val store = Store.get(this)
        val dt = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR"))
        val last = store.lastPointTs()?.let { dt.format(Date(it)) } ?: "nenhuma"
        val pending = Days.pending(this)
        return buildString {
            appendLine("Device Owner: ${if (Policy.isOwner(this@AdminActivity)) "sim" else "NÃO"}")
            appendLine("Dentro do horário de registro: ${if (Config.inWindow()) "sim" else "não"}")
            appendLine("Última posição: $last")
            appendLine("Relatórios pendentes: ${if (pending.isEmpty()) "nenhum" else pending.joinToString()}")
            appendLine("Destinatário: ${BuildConfig.MAIL_TO.ifBlank { "NÃO CONFIGURADO" }}")
        }
    }

    private fun sendTest() {
        toast("Enviando…")
        Thread {
            val msg = try {
                Mailer.send(Report.build(this, Days.today(), partial = true))
                "Relatório de teste enviado"
            } catch (e: Exception) {
                "Falha no envio: ${e.javaClass.simpleName}: ${e.message}"
            }
            runOnUiThread { toast(msg) }
        }.start()
    }

    private fun confirmRemove() {
        AlertDialog.Builder(this)
            .setTitle("Remover gerenciamento?")
            .setMessage("Retira todos os bloqueios, mostra os apps ocultos e deixa o app ser desinstalado. O rastreio para de funcionar.")
            .setPositiveButton("Remover") { _, _ ->
                Policy.removeOwnership(this)
                toast("Gerenciamento removido")
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ----- utilitários de interface -----

    private fun column(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad, pad, pad)
        views.forEach { addView(it) }
    }

    private fun header(text: String) = TextView(this).apply {
        this.text = text
        textSize = 20f
        setPadding(0, 0, 0, pad)
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun sha256(v: String): String =
        MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString("") { "%02x".format(it) }
}
