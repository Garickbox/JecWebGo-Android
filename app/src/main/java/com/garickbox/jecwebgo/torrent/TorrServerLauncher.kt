package com.garickbox.jecwebgo.torrent
import android.content.Context
import android.util.Log
import java.io.File

object TorrServerLauncher {
    private const val TAG = "TorrServer"
    @Volatile private var proc: Process? = null

    fun start(ctx: Context) {
        if (proc?.isAlive == true) return
        try {
            val lib = File(ctx.applicationInfo.nativeLibraryDir, "libtorrserver.so")
            if (!lib.exists()) {
                Log.e(TAG, "not found: ${lib.absolutePath}")
                return
            }

            // Писать config.json до старта (MatriX v1.2.25 читает его при запуске)
            writeConfig(ctx)

            val logFile = File(ctx.filesDir, "torrserver.log")
            // Только -p (порт) и -d (директория). Cache/Preload задаются в config.json.
            val pb = ProcessBuilder(
                lib.absolutePath,
                "-p", "8090",
                "-d", ctx.filesDir.absolutePath
            )
                .redirectErrorStream(true)
                .redirectOutput(logFile)
            proc = pb.start()
            Log.i(TAG, "started port=8090 config=$(ctx.filesDir.absolutePath)/config.json")
        } catch (e: Exception) {
            Log.e(TAG, "start fail: ${e.message}")
        }
    }

    private fun writeConfig(ctx: Context) {
        try {
            val cfg = File(ctx.filesDir, "config.json")
            val json = """
            {
              "CacheSize": 536870912,
              "PreloadCache": 100,
              "ReaderReadAHead": 95,
              "UseDisk": false,
              "ConnectionsLimit": 50,
              "RetrackersMode": 1,
              "TorrentDisconnectTimeout": 30,
              "EnableIPv6": false,
              "DisableTCP": false,
              "DisableUTP": false,
              "DisableUPNP": true,
              "DisableDHT": false,
              "DisablePEX": false,
              "DisableUpload": false,
              "DownloadRateLimit": 0,
              "UploadRateLimit": 0,
              "PeersListenPort": 0,
              "EnableLPD": true,
              "ResponsiveMode": true,
              "EnableDLNA": false,
              "EnableRutorSearch": false,
              "EnableTorznabSearch": false,
              "StoreSettingsInJson": true,
              "StoreViewedInJson": false,
              "EnableProxy": false
            }
            """.trimIndent()
            cfg.writeText(json)
            Log.i(TAG, "config written: cache=512MB preload=100MB")
        } catch (e: Exception) {
            Log.e(TAG, "config write fail: ${e.message}")
        }
    }

    fun stop() {
        try { proc?.destroy() } catch (_: Exception) {}
        proc = null
    }

    fun isAlive(): Boolean = proc?.isAlive == true
}
