package com.garickbox.jecwebgo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.garickbox.jecwebgo.server.HttpServer
import com.garickbox.jecwebgo.torrent.TorrServerLauncher

class JecWebService : Service() {
    companion object {
        private const val CH = "jecweb"
        private const val ID = 8809
        fun start(ctx: Context) {
            val i = Intent(ctx, JecWebService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }

    private var http: HttpServer? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CH, "JecWeb", NotificationManager.IMPORTANCE_LOW)
            )
        }
        startForeground(
            ID,
            NotificationCompat.Builder(this, CH)
                .setContentTitle("JecWeb Go")
                .setContentText("Server :8809")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .build()
        )
        TorrServerLauncher.start(this)
        http = HttpServer(this).also { it.start(8809) }
    }

    override fun onDestroy() {
        http?.stop()
        TorrServerLauncher.stop()
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null
}
