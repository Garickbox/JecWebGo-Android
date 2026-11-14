package com.garickbox.jecwebgo.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.MY_PACKAGE_REPLACED") {
            Log.d("JecWeb", "BootReceiver: ${intent.action} -> start DownloadService")
            try { DownloadService.start(context) } catch (e: Exception) {
                Log.e("JecWeb", "BootReceiver start fail: ${e.message}")
            }
        }
    }
}
