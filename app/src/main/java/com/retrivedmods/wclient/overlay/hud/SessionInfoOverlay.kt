package com.retrivedmods.wclient.overlay.hud

import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrivedmods.wclient.overlay.OverlayManager
import com.retrivedmods.wclient.overlay.OverlayWindow

class SessionInfoOverlay : OverlayWindow() {

    private val _layoutParams by lazy {
        super.layoutParams.apply {
            flags = flags or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 220
        }
    }

    override val layoutParams: WindowManager.LayoutParams
        get() = _layoutParams

    private var cps by mutableIntStateOf(0)
    private var bps by mutableFloatStateOf(0f)
    private var hits by mutableIntStateOf(0)
    private var sessionSeconds by mutableLongStateOf(0L)
    private var showCps by mutableStateOf(true)
    private var showBps by mutableStateOf(true)
    private var showHits by mutableStateOf(true)
    private var showTime by mutableStateOf(true)
    private var compact by mutableStateOf(false)
    private var bgAlpha by mutableFloatStateOf(0.8f)

    companion object {
        val overlayInstance by lazy { SessionInfoOverlay() }
        private var shouldShowOverlay = false

        fun setOverlayEnabled(enabled: Boolean) {
            shouldShowOverlay = enabled
            try {
                if (enabled) OverlayManager.showOverlayWindow(overlayInstance)
                else OverlayManager.dismissOverlayWindow(overlayInstance)
            } catch (_: Exception) {}
        }

        fun updateStats(cps: Int, bps: Float, hits: Int, sessionSeconds: Long) {
            overlayInstance.cps = cps
            overlayInstance.bps = bps
            overlayInstance.hits = hits
            overlayInstance.sessionSeconds = sessionSeconds
        }

        fun updateConfig(
            showCps: Boolean,
            showBps: Boolean,
            showHits: Boolean,
            showTime: Boolean,
            compact: Boolean,
            bgAlpha: Float
        ) {
            overlayInstance.showCps = showCps
            overlayInstance.showBps = showBps
            overlayInstance.showHits = showHits
            overlayInstance.showTime = showTime
            overlayInstance.compact = compact
            overlayInstance.bgAlpha = bgAlpha
        }
    }

    @Composable
    override fun Content() {
        if (!shouldShowOverlay) return

        val accent = Color(0xFFFF5CA6)
        val accentLight = Color(0xFFFF9EC9)
        val bgBrush = Brush.verticalGradient(
            listOf(
                Color(0xFF161018).copy(alpha = bgAlpha),
                Color(0xFF241420).copy(alpha = bgAlpha)
            )
        )

        val mins = sessionSeconds / 60
        val secs = sessionSeconds % 60
        val timeStr = String.format("%02d:%02d", mins, secs)

        Box(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectDragGestures { _, drag ->
                        _layoutParams.x = (_layoutParams.x + drag.x.toInt()).coerceAtLeast(0)
                        _layoutParams.y = (_layoutParams.y + drag.y.toInt()).coerceAtLeast(0)
                        try {
                            windowManager.updateViewLayout(composeView, _layoutParams)
                        } catch (_: Exception) {}
                    }
                }
                .clip(RoundedCornerShape(12.dp))
                .background(bgBrush, RoundedCornerShape(12.dp))
                .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (compact) {
                val parts = mutableListOf<String>()
                if (showCps) parts.add("$cps CPS")
                if (showBps) parts.add("${String.format("%.1f", bps)} BPS")
                if (showHits) parts.add("$hits Hits")
                if (showTime) parts.add(timeStr)
                Text(
                    text = parts.joinToString("  •  "),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "🐾 Session Stats",
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (showCps) StatRow("Attack CPS", "$cps", accentLight)
                    if (showBps) StatRow("Speed", "${String.format("%.2f", bps)} b/s", accentLight)
                    if (showHits) StatRow("Combo / Hits", "$hits", accentLight)
                    if (showTime) StatRow("Playtime", timeStr, accentLight)
                }
            }
        }
    }

    @Composable
    private fun StatRow(label: String, value: String, valueColor: Color) {
        Row(
            modifier = Modifier.widthIn(min = 135.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "$label:", color = Color(0xFFD0B8C4), fontSize = 11.sp)
            Spacer(Modifier.width(10.dp))
            Text(text = value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
