package com.garickbox.jecwebgo.service
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.garickbox.jecwebgo.torrent.TorrServerLauncher
class TorrServerService : Service() {
    companion object {
        private const val CH = "jecweb"; private const val ID = 8809
        fun start(ctx: Context) {
            val i = Intent(ctx, TorrServerService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
            NotificationChannel(CH, "TorrServer", NotificationManager.IMPORTANCE_LOW)
        )
        startForeground(ID, NotificationCompat.Builder(this, CH)
            .setContentTitle("JecWeb Go")
            .setContentText("TorrServer running")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true).build())
        TorrServerLauncher.start(this)
    }
    override fun onDestroy() { TorrServerLauncher.stop(); super.onDestroy() }
    override fun onBind(i: Intent?): IBinder? = null
}
