package com.garickbox.jecwebgo.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

class AdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("JecWeb", "AdminReceiver enabled")
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.setLockTaskPackages(componentName(context), arrayOf(context.packageName))
            Log.d("JecWeb", "setLockTaskPackages OK")
        } catch (e: Exception) {
            Log.e("JecWeb", "setLockTaskPackages fail: ${e.message}")
        }
    }
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d("JecWeb", "AdminReceiver disabled")
    }
    companion object {
        fun componentName(ctx: Context) = ComponentName(ctx, AdminReceiver::class.java)
        fun isActive(ctx: Context): Boolean {
            return try {
                val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                dpm.isAdminActive(componentName(ctx))
            } catch (_: Exception) { false }
        }
    }
}
