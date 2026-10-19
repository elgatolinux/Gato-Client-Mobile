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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrivedmods.wclient.game.module.visual.CatCompanionModule
import com.retrivedmods.wclient.overlay.OverlayManager
import com.retrivedmods.wclient.overlay.OverlayWindow
import kotlinx.coroutines.delay
import kotlin.math.abs

class CatCompanionOverlay : OverlayWindow() {

    private val _layoutParams by lazy {
        super.layoutParams.apply {
            flags = flags or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            x = 180
            y = 140
        }
    }

    override val layoutParams: WindowManager.LayoutParams
        get() = _layoutParams

    private var mode by mutableStateOf(CatCompanionModule.CompanionMode.DVD_BOUNCER)
    private var speed by mutableFloatStateOf(4.0f)
    private var wallHits by mutableIntStateOf(0)
    private var cornerHits by mutableIntStateOf(0)
    private var colorIdx by mutableIntStateOf(0)
    private var lastHitAt by mutableLongStateOf(0L)
    private var comboCount by mutableIntStateOf(0)

    private val dvdColors = listOf(
        Color(0xFFFF5CA6),
        Color(0xFF00F5D4),
        Color(0xFFFFBE0B),
        Color(0xFF8338EC),
        Color(0xFF3A86FF),
        Color(0xFFFF006E),
        Color(0xFF06D6A0)
    )

    companion object {
        val overlayInstance by lazy { CatCompanionOverlay() }
        private var isEnabled = false

        fun setOverlayEnabled(enabled: Boolean) {
            isEnabled = enabled
            try {
                if (enabled) OverlayManager.showOverlayWindow(overlayInstance)
                else OverlayManager.dismissOverlayWindow(overlayInstance)
            } catch (_: Exception) {}
        }

        fun updateConfig(mode: CatCompanionModule.CompanionMode, speed: Float) {
            overlayInstance.mode = mode
            overlayInstance.speed = speed
        }

        fun notifyAttack() {
            val now = System.currentTimeMillis()
            if (now - overlayInstance.lastHitAt > 3000L) {
                overlayInstance.comboCount = 1
            } else {
                overlayInstance.comboCount++
            }
            overlayInstance.lastHitAt = now
        }
    }

    @Composable
    override fun Content() {
        if (!isEnabled) return

        val context = LocalContext.current
        var vx by remember { mutableFloatStateOf(1f) }
        var vy by remember { mutableFloatStateOf(1f) }
        var frameTick by remember { mutableIntStateOf(0) }

        LaunchedEffect(mode, speed) {
            while (true) {
                frameTick++
                if (mode == CatCompanionModule.CompanionMode.DVD_BOUNCER) {
                    val screenW = (context.resources.displayMetrics.widthPixels - 320).coerceAtLeast(200)
                    val screenH = (context.resources.displayMetrics.heightPixels - 140).coerceAtLeast(200)

                    var nextX = _layoutParams.x + (vx * speed * 2.2f).toInt()
                    var nextY = _layoutParams.y + (vy * speed * 2.2f).toInt()
                    var hitX = false
                    var hitY = false

                    if (nextX <= 0) {
                        nextX = 0; vx = abs(vx); hitX = true
                    } else if (nextX >= screenW) {
                        nextX = screenW; vx = -abs(vx); hitX = true
                    }

                    if (nextY <= 0) {
                        nextY = 0; vy = abs(vy); hitY = true
                    } else if (nextY >= screenH) {
                        nextY = screenH; vy = -abs(vy); hitY = true
                    }

                    if (hitX || hitY) {
                        wallHits++
                        colorIdx = (colorIdx + 1) % dvdColors.size
                        if (hitX && hitY) {
                            cornerHits++
                        }
                    }

                    _layoutParams.x = nextX
                    _layoutParams.y = nextY
                    try {
                        windowManager.updateViewLayout(composeView, _layoutParams)
                    } catch (_: Exception) {}
                }
                delay(16L)
            }
        }

        val activeColor = dvdColors[colorIdx % dvdColors.size]
        val isCombating = (System.currentTimeMillis() - lastHitAt) < 1800L

        val catFace = when {
            mode == CatCompanionModule.CompanionMode.DVD_BOUNCER && cornerHits > 0 && wallHits % 10 == 0 -> "(=✪ᆽ✪=)"
            isCombating -> if (frameTick % 12 < 6) "(=｀ω´=)⚔" else "(=^･ｪ･^=)⚡"
            frameTick % 60 < 30 -> "(=^･ω･^=)"
            else -> "(= - ω - =)"
        }

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
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xD915101A), RoundedCornerShape(14.dp))
                .border(2.dp, activeColor, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = catFace,
                    color = activeColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (mode == CatCompanionModule.CompanionMode.DVD_BOUNCER) {
                        "DVD Cat • Hits: $wallHits | Corner: $cornerHits"
                    } else {
                        if (isCombating) "PvP Mode! x$comboCount hits" else "Chilling Michi zZz"
                    },
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
