package com.retrivedmods.wclient.overlay.hud

import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrivedmods.wclient.overlay.OverlayManager
import com.retrivedmods.wclient.overlay.OverlayWindow
import kotlinx.coroutines.delay

class NotificationsOverlay : OverlayWindow() {

    data class HudNotification(
        val id: Long,
        val title: String,
        val subtitle: String,
        val accentColor: Color,
        val createdAt: Long,
        val durationMs: Long
    )

    private val _layoutParams by lazy {
        super.layoutParams.apply {
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.BOTTOM or Gravity.END
            x = 20
            y = 30
        }
    }

    override val layoutParams: WindowManager.LayoutParams
        get() = _layoutParams

    companion object {
        val overlayInstance by lazy { NotificationsOverlay() }
        private var isEnabled = false
        private var nextId = 1L
        val notifications = mutableStateListOf<HudNotification>()
        var durationMs = 2200L

        fun setOverlayEnabled(enabled: Boolean) {
            isEnabled = enabled
            try {
                if (enabled) OverlayManager.showOverlayWindow(overlayInstance)
                else {
                    notifications.clear()
                    OverlayManager.dismissOverlayWindow(overlayInstance)
                }
            } catch (_: Exception) {}
        }

        fun isOverlayEnabled(): Boolean = isEnabled

        fun push(title: String, subtitle: String, isPositive: Boolean = true) {
            if (!isEnabled) return
            val color = if (isPositive) Color(0xFF00E676) else Color(0xFFFF5252)
            val now = System.currentTimeMillis()
            val item = HudNotification(
                id = nextId++,
                title = title,
                subtitle = subtitle,
                accentColor = color,
                createdAt = now,
                durationMs = durationMs
            )
            if (notifications.size >= 5) {
                notifications.removeAt(0)
            }
            notifications.add(item)
        }
    }

    @Composable
    override fun Content() {
        if (!isEnabled) return

        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

        LaunchedEffect(Unit) {
            while (true) {
                now = System.currentTimeMillis()
                notifications.removeAll { now - it.createdAt >= it.durationMs }
                delay(32L)
            }
        }

        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            notifications.forEach { item ->
                key(item.id) {
                    val progress = (1f - (now - item.createdAt).toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .width(210.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xE618101C), Color(0xE6261422))
                                )
                            )
                            .border(1.dp, item.accentColor.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = item.subtitle,
                                    color = item.accentColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(3.dp)
                                    .background(item.accentColor)
                            )
                        }
                    }
                }
            }
        }
    }
}
