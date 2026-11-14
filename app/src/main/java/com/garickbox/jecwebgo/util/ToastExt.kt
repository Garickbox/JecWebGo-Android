package com.garickbox.jecwebgo.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

/**
 * Безопасный Toast для любого потока.
 * На Main thread — сразу. На IO/Default/VLC thread — через Handler(Main).
 * Причина: Toast.makeText() требует Looper.prepare() → NPE на фоновых потоках.
 */
fun Context.toast(msg: String, duration: Int = Toast.LENGTH_SHORT) {
    val app = this.applicationContext
    if (Looper.myLooper() == Looper.getMainLooper()) {
        Toast.makeText(app, msg, duration).show()
    } else {
        mainHandler.post {
            runCatching { Toast.makeText(app, msg, duration).show() }
        }
    }
}
