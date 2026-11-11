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
            val pb = ProcessBuilder(lib.absolutePath).redirectErrorStream(true)
            pb.environment()["HOME"] = ctx.filesDir.absolutePath
            pb.environment()["TORRSERVER_PORT"] = "8090"
            proc = pb.start()
            Log.i(TAG, "started pid=${proc?.pid()}")
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
