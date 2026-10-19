package com.retrivedmods.wclient.overlay.gui.classic

import android.content.res.Configuration
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.overlay.GuiSettingsManager
import com.retrivedmods.wclient.overlay.OverlayWindow
import com.retrivedmods.wclient.util.translatedSelf
import kotlin.math.min

class OverlayShortcutButton(
    private val module: Module
) : OverlayWindow() {

    private val _layoutParams by lazy {
        super.layoutParams.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            windowAnimations = android.R.style.Animation_Toast
            x = module.shortcutX
            y = module.shortcutY
        }
    }

    override val layoutParams: WindowManager.LayoutParams
        get() = _layoutParams

    private var lastDragUpdateTime = 0L

    @Composable
    override fun Content() {
        val context = LocalContext.current
        GuiSettingsManager.ensureLoaded(context)

        val width = context.resources.displayMetrics.widthPixels
        val height = context.resources.displayMetrics.heightPixels
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val palette = GuiSettingsManager.currentPalette()
        val scale = GuiSettingsManager.shortcutScale

        val borderColor by animateColorAsState(
            targetValue = if (module.isEnabled) palette.accent else palette.border.copy(alpha = 0.5f),
            label = "borderColor"
        )
        val textColor by animateColorAsState(
            targetValue = if (module.isEnabled) palette.accent else palette.textPrimary,
            label = "textColor"
        )

        val bg = Brush.verticalGradient(
            listOf(
                palette.windowBgTop,
                palette.windowBgBottom
            )
        )

        LaunchedEffect(isLandscape) {
            _layoutParams.x = min(width, _layoutParams.x)
            _layoutParams.y = min(height, _layoutParams.y)
            runCatching { windowManager.updateViewLayout(composeView, _layoutParams) }
            updateShortcut()
        }

        val shape = RoundedCornerShape((14 * scale).dp)

        Box(
            modifier = Modifier
                .width((110 * scale).dp)
                .height((44 * scale).dp)
                .padding(5.dp)
                .pointerInput(GuiSettingsManager.lockShortcutsDrag) {
                    if (!GuiSettingsManager.lockShortcutsDrag) {
                        detectDragGestures(
                            onDragEnd = {
                                runCatching { windowManager.updateViewLayout(composeView, _layoutParams) }
                                updateShortcut()
                            }
                        ) { _, drag ->
                            _layoutParams.x += drag.x.toInt()
                            _layoutParams.y += drag.y.toInt()
                            val now = System.currentTimeMillis()
                            if (now - lastDragUpdateTime >= 8L) {
                                lastDragUpdateTime = now
                                runCatching { windowManager.updateViewLayout(composeView, _layoutParams) }
                                updateShortcut()
                            }
                        }
                    }
                }
                .shadow(6.dp, shape)
                .background(bg, shape)
                .border(if (module.isEnabled) 2.dp else 1.dp, borderColor, shape)
                .clickable { module.isEnabled = !module.isEnabled }
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = module.name.translatedSelf,
                    color = textColor,
                    fontSize = (12 * scale).sp,
                    fontWeight = if (module.isEnabled) FontWeight.Bold else FontWeight.Medium,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }

    private fun updateShortcut() {
        module.shortcutX = _layoutParams.x
        module.shortcutY = _layoutParams.y
        com.retrivedmods.wclient.util.ConfigAutoSaver.scheduleSave()
    }
}
