package com.garickbox.jecwebgo.admin

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Accessibility Service — блокирует системные свайпы и жесты через
 * TYPE_ACCESSIBILITY_OVERLAY (доступен без SYSTEM_ALERT_WINDOW).
 * Единственный способ блокировать Home/шторку без Device Owner.
 */
class TouchBlockerService : AccessibilityService() {
    companion object {
        const val ACTION_UNLOCK = "com.garickbox.jecwebgo.TOUCH_UNLOCK"
        @Volatile var instance: TouchBlockerService? = null
        @Volatile var blocking = false

        fun isEnabled(ctx: Context): Boolean {
            return try {
                val enabled = android.provider.Settings.Secure.getString(
                    ctx.contentResolver,
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""
                enabled.contains(ctx.packageName + "/" + TouchBlockerService::class.java.name)
            } catch (_: Exception) { false }
        }

        fun start(ctx: Context): Boolean {
            if (!isEnabled(ctx)) return false
            blocking = true
            instance?.showOverlay()
            Log.d("JecWeb", "TouchBlocker start")
            return true
        }

        fun stop(ctx: Context) {
            blocking = false
            instance?.hideOverlay()
            Log.d("JecWeb", "TouchBlocker stop")
        }
    }

    private var overlay: View? = null
    private var wm: WindowManager? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        Log.d("JecWeb", "TouchBlockerService connected, blocking=$blocking")
        if (blocking) showOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        hideOverlay()
        super.onDestroy()
    }

    private fun showOverlay() {
        if (overlay != null) return
        val w = wm ?: return
        val root = FrameLayout(this).apply {
            setBackgroundColor(0x00000000)
            setOnTouchListener { _, _ -> true }
            isClickable = true
            isFocusable = true
        }
        val unlock = TextView(this).apply {
            text = "🔓"
            textSize = 22f
            setTextColor(0x99FFFFFF.toInt())
            setPadding(32, 32, 32, 32)
            setOnClickListener {
                Log.d("JecWeb", "TouchBlocker unlock tap")
                hideOverlay()
                blocking = false
                sendBroadcast(Intent(ACTION_UNLOCK).setPackage(packageName))
            }
        }
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = 180
            rightMargin = 24
        }
        root.addView(unlock, lp)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        try {
            w.addView(root, params)
            overlay = root
            Log.d("JecWeb", "TouchBlocker overlay shown")
        } catch (e: Exception) {
            Log.e("JecWeb", "overlay fail: ${e.message}")
        }
    }

    private fun hideOverlay() {
        overlay?.let {
            try { wm?.removeView(it) } catch (_: Exception) {}
        }
        overlay = null
    }
}
