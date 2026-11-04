package br.com.hasolucoes.rastreio

import java.util.Calendar

/**
 * Configurações do comportamento do app.
 * Dados de e-mail e PIN NÃO ficam aqui: vêm de secrets.properties / segredos do GitHub.
 */
object Config {

    /** Janela de registro: expediente 8h–18h com 1h de margem de cada lado. */
    const val WINDOW_START_HOUR = 7
    const val WINDOW_END_HOUR = 19

    /** Dias com registro e relatório (segunda a sábado). */
    val WORK_DAYS = setOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
        Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
    )

    /** Intervalo entre posições. */
    const val INTERVAL_MINUTES = 5L

    /** Minutos após o fim da janela para enviar o relatório (19h05). */
    const val REPORT_DELAY_MINUTES = 5

    /** Quantos dias de histórico ficam guardados no aparelho. */
    const val RETENTION_DAYS = 30

    /** Intervalo sem registro considerado "falha" no relatório. */
    const val GAP_ALERT_MINUTES = 15

    /** Bloqueia depuração USB e opções do desenvolvedor (impede GPS falso). */
    const val BLOCK_DEBUGGING = true

    /**
     * Bloqueia a barra de notificações inteira (inclusive atalhos rápidos).
     * Desligado: o vendedor precisa ver as notificações do WhatsApp.
     */
    const val LOCK_STATUS_BAR = false

    /** Apps que continuam visíveis. Todo o resto com ícone na tela é ocultado. */
    val KEEP_PACKAGES = setOf(
        // Configurar
        "com.android.settings",
        // Play Store
        "com.android.vending",
        // Câmera (no moto e6 plus: com.myos.camera; a câmera padrão também é detectada sozinha)
        "com.myos.camera",
        "com.motorola.camera2", "com.motorola.camera3", "com.motorola.cameraone",
        "com.android.camera2", "com.google.android.GoogleCamera",
        // Arquivos
        "com.google.android.apps.nbu.files", "com.google.android.documentsui", "com.android.documentsui",
        // Contatos
        "com.google.android.contacts", "com.android.contacts",
        // Telefone
        "com.google.android.dialer", "com.android.dialer",
        // Mensagens
        "com.google.android.apps.messaging", "com.android.mms",
        // Chrome
        "com.android.chrome",
        // Fotos
        "com.google.android.apps.photos",
        // WhatsApp (comum e Business)
        "com.whatsapp", "com.whatsapp.w4b",
        // Google Drive
        "com.google.android.apps.docs",
        // Gmail
        "com.google.android.gm",
        // Google Agenda
        "com.google.android.calendar",
        // Google Docs e Planilhas
        "com.google.android.apps.docs.editors.docs",
        "com.google.android.apps.docs.editors.sheets",
        // Integra Master (sistema de vendas)
        "br.com.prodtool.integramaster",
        // Componentes do sistema que nunca devem ser ocultados
        "com.google.android.gms", "com.google.android.gsf",
        "com.android.systemui", "com.android.phone"
    )

    fun isWorkDay(c: Calendar) = c.get(Calendar.DAY_OF_WEEK) in WORK_DAYS

    fun inWindow(c: Calendar = Calendar.getInstance()): Boolean {
        val h = c.get(Calendar.HOUR_OF_DAY)
        return isWorkDay(c) && h >= WINDOW_START_HOUR && h < WINDOW_END_HOUR
    }
}
