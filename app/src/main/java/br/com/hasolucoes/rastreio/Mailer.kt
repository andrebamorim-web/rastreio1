package br.com.hasolucoes.rastreio

import java.util.Properties
import javax.activation.DataHandler
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource

/** Envio por SMTP (Titan Mail: smtp.titan.email, porta 465 com SSL). Nunca chamar na thread principal. */
object Mailer {

    fun send(report: ReportData) {
        check(BuildConfig.SMTP_USER.isNotBlank() && BuildConfig.SMTP_PASSWORD.isNotBlank()) {
            "Conta de e-mail não configurada no app"
        }
        check(BuildConfig.MAIL_TO.isNotBlank()) { "Destinatário não configurado no app" }

        val port = BuildConfig.SMTP_PORT
        val props = Properties().apply {
            put("mail.smtp.host", BuildConfig.SMTP_HOST)
            put("mail.smtp.port", port.toString())
            put("mail.smtp.auth", "true")
            if (port == 465) {
                put("mail.smtp.ssl.enable", "true")
            } else {
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
            }
            put("mail.smtp.ssl.checkserveridentity", "true")
            put("mail.smtp.connectiontimeout", "30000")
            put("mail.smtp.timeout", "60000")
            put("mail.smtp.writetimeout", "60000")
        }

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(BuildConfig.SMTP_USER, BuildConfig.SMTP_PASSWORD)
        })

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(BuildConfig.MAIL_FROM, "Rastreio ${BuildConfig.DEVICE_LABEL}", "UTF-8"))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(BuildConfig.MAIL_TO))
            setSubject(report.subject, "UTF-8")

            val multipart = MimeMultipart()
            multipart.addBodyPart(MimeBodyPart().apply { setText(report.body, "UTF-8") })
            for (a in report.attachments) {
                multipart.addBodyPart(MimeBodyPart().apply {
                    dataHandler = DataHandler(ByteArrayDataSource(a.bytes, a.mime))
                    fileName = a.name
                })
            }
            setContent(multipart)
        }

        Transport.send(message)
    }
}
