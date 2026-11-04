package br.com.hasolucoes.rastreio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Serviço em primeiro plano que registra a posição a cada 5 minutos durante o expediente.
 * Mostra uma notificação fixa: o registro é transparente para o vendedor.
 */
class TrackingService : Service() {

    private lateinit var fused: FusedLocationProviderClient
    private val handler = Handler(Looper.getMainLooper())
    private var tracking = false
    private var receiversOn = false

    private val intervalMs = Config.INTERVAL_MINUTES * 60_000L

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (loc in result.locations) record(loc)
            Status.check(this@TrackingService)
        }
    }

    // App novo instalado → oculta na hora se não estiver na lista.
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            Policy.hideApps(c)
        }
    }

    // GPS ligado/desligado → registra o evento.
    private val providerReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            Status.check(c)
        }
    }

    // Desligamento durante o expediente (o aviso de desligar só chega com o app ativo).
    private val shutdownReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            Store.get(c).addEvent("APARELHO_DESLIGADO")
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            Status.check(this@TrackingService)
            if (!Config.inWindow()) {
                stopSelf()
                return
            }
            handler.postDelayed(this, intervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        registerReceiver(packageReceiver, IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") })
        registerReceiver(providerReceiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
        registerReceiver(shutdownReceiver, IntentFilter(Intent.ACTION_SHUTDOWN))
        receiversOn = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Config.inWindow()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startUpdates()
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    private fun startUpdates() {
        if (tracking) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs - 60_000L)
            .build()
        try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
            tracking = true
            Store.get(this).addEvent("REGISTRO_INICIADO")
        } catch (e: SecurityException) {
            Store.get(this).addEvent("ERRO", "Sem permissão de localização")
        }
    }

    private fun record(loc: Location) {
        val bm = getSystemService(BatteryManager::class.java)
        val battery = runCatching { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }.getOrDefault(-1)
        @Suppress("DEPRECATION")
        val mock = loc.isFromMockProvider
        Store.get(this).addPoint(
            Point(
                ts = System.currentTimeMillis(),
                lat = loc.latitude,
                lon = loc.longitude,
                acc = loc.accuracy,
                battery = battery,
                mock = mock
            )
        )
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        if (tracking) {
            fused.removeLocationUpdates(callback)
            tracking = false
            Store.get(this).addEvent("REGISTRO_ENCERRADO")
        }
        if (receiversOn) {
            runCatching { unregisterReceiver(packageReceiver) }
            runCatching { unregisterReceiver(providerReceiver) }
            runCatching { unregisterReceiver(shutdownReceiver) }
            receiversOn = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL_ID, "Registro de expediente", NotificationManager.IMPORTANCE_LOW)
        ch.description = "Indica que a localização do expediente está sendo registrada"
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Registro de expediente ativo")
            .setContentText("Localização registrada a cada ${Config.INTERVAL_MINUTES} min, das ${Config.WINDOW_START_HOUR}h às ${Config.WINDOW_END_HOUR}h")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1
    }
}
