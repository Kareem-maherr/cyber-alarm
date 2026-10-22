package com.cyberalarm.enforcement

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.cyberalarm.R
import com.cyberalarm.alarm.RingingStateStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Full-screen SYSTEM_ALERT_WINDOW "shield" shown whenever the user manages to get out of the
 * ringing screen. It swallows every touch on the app underneath; tapping it returns to the alarm.
 * Plain Views (not Compose) so it needs no lifecycle owner inside a Service.
 *
 * Must be called on the main thread.
 */
@Singleton
class OverlayManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: RingingStateStore,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var shield: View? = null

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    fun showAlarmShield() {
        if (shield != null || !canDraw()) return
        val view = buildShield()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        )
        runCatching {
            windowManager.addView(view, params)
            shield = view
        }
    }

    fun hide() {
        shield?.let { runCatching { windowManager.removeView(it) } }
        shield = null
    }

    private fun buildShield(): View {
        val display = ResourcesCompat.getFont(context, R.font.chakra_petch_bold)
        val mono = ResourcesCompat.getFont(context, R.font.share_tech_mono)
        val yellow = Color.rgb(0xF0, 0xFF, 0x00)
        fun text(s: String, sizeSp: Float, color: Int, bold: Boolean) = TextView(context).apply {
            text = s
            setTextColor(color)
            typeface = if (bold) display else mono
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            gravity = Gravity.CENTER
            letterSpacing = 0.15f
            setPadding(0, 16, 0, 16)
            setShadowLayer(18f, 0f, 0f, color)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(0xF2, 0x04, 0x08, 0x0B))
            addView(text("// NICE TRY, CHOOM //", 14f, Color.rgb(0xFF, 0x2A, 0x3D), false))
            addView(text("ALARM ACTIVE", 40f, yellow, true))
            addView(text("SOLVE THE CHALLENGE TO DISMISS", 14f, Color.rgb(0x00, 0xE5, 0xFF), false))
            addView(text("[ TAP TO RETURN ]", 16f, yellow, false))
            setOnClickListener {
                val id = store.activeAlarmId() ?: return@setOnClickListener
                context.startActivity(AlarmActivity.intent(context, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
