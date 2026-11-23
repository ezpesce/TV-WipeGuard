package com.example.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Receiver registrado para habilitar privilegios de Administrador de Dispositivo
 * y permitir la invocación de DevicePolicyManager.wipeData() en Android TV.
 */
class TvDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }

    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, TvDeviceAdminReceiver::class.java)
    }
}
