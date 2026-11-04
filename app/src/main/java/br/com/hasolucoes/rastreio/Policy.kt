package br.com.hasolucoes.rastreio

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.view.inputmethod.InputMethodManager

/** Bloqueios e ocultação de apps, usando a API oficial de Device Owner. */
object Policy {

    private fun dpm(ctx: Context) = ctx.getSystemService(DevicePolicyManager::class.java)
    fun admin(ctx: Context) = ComponentName(ctx, AdminReceiver::class.java)
    fun isOwner(ctx: Context) = dpm(ctx).isDeviceOwnerApp(ctx.packageName)

    private fun restrictions(): List<String> {
        val list = mutableListOf(
            UserManager.DISALLOW_CONFIG_LOCATION,         // não mexe na localização
            UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS,  // não mexe em rede/dados móveis
            UserManager.DISALLOW_FACTORY_RESET,           // sem reset de fábrica
            UserManager.DISALLOW_SAFE_BOOT,               // sem modo de segurança
            UserManager.DISALLOW_ADD_USER,                // sem outros usuários
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, // só instala pela Play Store
            UserManager.DISALLOW_CONFIG_DATE_TIME         // não altera data/hora (protege o relatório)
        )
        if (Build.VERSION.SDK_INT >= 28) list += UserManager.DISALLOW_AIRPLANE_MODE
        if (Config.BLOCK_DEBUGGING) list += UserManager.DISALLOW_DEBUGGING_FEATURES
        return list
    }

    /** Aplica todos os bloqueios. Pode ser chamado várias vezes sem problema. */
    fun apply(ctx: Context) {
        if (!isOwner(ctx)) return
        val dpm = dpm(ctx)
        val admin = admin(ctx)

        // Permissões concedidas pelo próprio app: o vendedor não consegue negar.
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= 29) perms += Manifest.permission.ACCESS_BACKGROUND_LOCATION
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        for (p in perms) {
            runCatching {
                dpm.setPermissionGrantState(admin, ctx.packageName, p, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
            }
        }

        // Garante localização em alta precisão antes de travar (pode não ser aceito em todas as versões).
        runCatching {
            @Suppress("DEPRECATION")
            dpm.setSecureSetting(admin, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_HIGH_ACCURACY.toString())
        }
        @Suppress("DEPRECATION")
        runCatching { dpm.setAutoTimeRequired(admin, true) }

        for (r in restrictions()) runCatching { dpm.addUserRestriction(admin, r) }
        runCatching { dpm.setUninstallBlocked(admin, ctx.packageName, true) }
        if (Config.LOCK_STATUS_BAR) runCatching { dpm.setStatusBarDisabled(admin, true) }

        hideApps(ctx)
    }

    /** Oculta todo app com ícone na tela que não esteja na lista de mantidos. */
    fun hideApps(ctx: Context): Int {
        if (!isOwner(ctx)) return 0
        val dpm = dpm(ctx)
        val admin = admin(ctx)
        val keep = keepSet(ctx)
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchable = ctx.packageManager.queryIntentActivities(launcherIntent, 0)
            .map { it.activityInfo.packageName }
            .toSet()
        var hidden = 0
        for (pkg in launchable) {
            if (pkg in keep) continue
            if (runCatching { dpm.setApplicationHidden(admin, pkg, true) }.getOrDefault(false)) hidden++
        }
        return hidden
    }

    private fun keepSet(ctx: Context): Set<String> {
        val pm = ctx.packageManager
        val keep = HashSet(Config.KEEP_PACKAGES)
        keep += ctx.packageName
        fun addHandlers(i: Intent) = pm.queryIntentActivities(i, 0).forEach { keep += it.activityInfo.packageName }
        addHandlers(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) // tela inicial
        addHandlers(Intent(MediaStore.ACTION_IMAGE_CAPTURE))                      // câmera
        addHandlers(Intent(Intent.ACTION_DIAL))                                   // telefone
        Telephony.Sms.getDefaultSmsPackage(ctx)?.let { keep += it }               // SMS padrão
        ctx.getSystemService(InputMethodManager::class.java)?.inputMethodList
            ?.forEach { keep += it.packageName }                                  // teclados
        return keep
    }

    /** Volta a mostrar todos os apps ocultados. */
    fun unhideAll(ctx: Context): Int {
        if (!isOwner(ctx)) return 0
        val dpm = dpm(ctx)
        val admin = admin(ctx)
        var shown = 0
        val apps = ctx.packageManager.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
        for (app in apps) {
            val pkg = app.packageName
            if (runCatching { dpm.isApplicationHidden(admin, pkg) }.getOrDefault(false)) {
                if (runCatching { dpm.setApplicationHidden(admin, pkg, false) }.getOrDefault(false)) shown++
            }
        }
        return shown
    }

    /** Retira os bloqueios (manutenção). O rastreio continua funcionando. */
    fun release(ctx: Context) {
        if (!isOwner(ctx)) return
        val dpm = dpm(ctx)
        val admin = admin(ctx)
        for (r in restrictions()) runCatching { dpm.clearUserRestriction(admin, r) }
        runCatching { dpm.setStatusBarDisabled(admin, false) }
    }

    /** Remove o app como Device Owner. Depois disso ele pode ser desinstalado normalmente. */
    fun removeOwnership(ctx: Context) {
        if (!isOwner(ctx)) return
        release(ctx)
        unhideAll(ctx)
        val dpm = dpm(ctx)
        runCatching { dpm.setUninstallBlocked(admin(ctx), ctx.packageName, false) }
        @Suppress("DEPRECATION")
        dpm.clearDeviceOwnerApp(ctx.packageName)
    }
}
