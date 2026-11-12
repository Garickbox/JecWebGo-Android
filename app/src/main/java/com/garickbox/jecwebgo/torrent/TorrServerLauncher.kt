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
            // Создать папку для дискового кэша
            File(ctx.filesDir, "torrents_cache").mkdirs()

            val logFile = File(ctx.filesDir, "torrserver.log")
            val pb = ProcessBuilder(
                lib.absolutePath,
                "-p", "8090",
                "-d", ctx.filesDir.absolutePath
            )
                .redirectErrorStream(true)
                .redirectOutput(logFile)
            proc = pb.start()
            Log.i(TAG, "started port=8090 dir=${ctx.filesDir.absolutePath}")
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
