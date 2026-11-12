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
            val logFile = File(ctx.filesDir, "torrserver.log")
            // Флаги TorrServer:
            //   -p port         : 8090
            //   -d path         : data dir
            //   -c size         : cache size MB (default 64 → 512 для 4K/2GB фильмов)
            //   -l preload      : preload cache MB (default 50 → 100)
            //   -i ip           : listen only localhost (безопасность)
            val pb = ProcessBuilder(
                lib.absolutePath,
                "-p", "8090",
                "-d", ctx.filesDir.absolutePath,
                "-c", "512",
                "-l", "100",
                "-i", "127.0.0.1"
            )
                .redirectErrorStream(true)
                .redirectOutput(logFile)
            proc = pb.start()
            Log.i(TAG, "started p=8090 cache=512 preload=100 ip=127.0.0.1 log=${logFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "start fail: ${e.message}")
        }
    }

    fun stop() {
        try { proc?.destroy() } catch (_: Exception) {}
        proc = null
    }

    fun isAlive(): Boolean = proc?.isAlive == true
}
