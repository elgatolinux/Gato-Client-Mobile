package com.retrivedmods.wclient.overlay.gui.classic

import android.content.res.Configuration
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrivedmods.wclient.R
import com.retrivedmods.wclient.game.ModuleManager
import com.retrivedmods.wclient.overlay.FloatingButtonShape
import com.retrivedmods.wclient.overlay.GuiSettingsManager
import com.retrivedmods.wclient.overlay.OverlayManager
import com.retrivedmods.wclient.overlay.OverlayWindow
import kotlin.math.min

class OverlayButton : OverlayWindow() {

    private val _layoutParams by lazy {
        super.layoutParams.apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            windowAnimations = android.R.style.Animation_Toast
            x = 0
            y = 100
        }
    }

    override val layoutParams: WindowManager.LayoutParams
        get() = _layoutParams

    private val overlayClickGUI by lazy { OverlayClickGUI() }

    private var lastDragUpdateTime = 0L

    @Composable
    override fun Content() {
        val context = LocalContext.current
        GuiSettingsManager.ensureLoaded(context)

        val width = context.resources.displayMetrics.widthPixels
        val height = context.resources.displayMetrics.heightPixels
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        LaunchedEffect(isLandscape) {
            _layoutParams.x = min(width, _layoutParams.x)
            _layoutParams.y = min(height, _layoutParams.y)
            runCatching { windowManager.updateViewLayout(composeView, _layoutParams) }
        }

        val palette = GuiSettingsManager.currentPalette()
        val btnSize = GuiSettingsManager.buttonSizeDp.dp
        val shape = when (GuiSettingsManager.buttonShape) {
            FloatingButtonShape.CIRCLE -> CircleShape
            FloatingButtonShape.ROUNDED_SQUARE -> RoundedCornerShape(14.dp)
            FloatingButtonShape.PILL_BADGE -> RoundedCornerShape(50)
        }
        val activeCount = ModuleManager.modules.count { it.isEnabled && !it.private }

        Box(
            modifier = Modifier
                .padding(6.dp)
                .pointerInput(GuiSettingsManager.lockButtonDrag) {
                    if (!GuiSettingsManager.lockButtonDrag) {
                        detectDragGestures(
                            onDragEnd = {
                                runCatching { windowManager.updateViewLayout(composeView, _layoutParams) }
                            }
                        ) { _, drag ->
                            _layoutParams.x += drag.x.toInt()
                            _layoutParams.y += drag.y.toInt()
                            val now = System.currentTimeMillis()
                            if (now - lastDragUpdateTime >= 8L) {
                                lastDragUpdateTime = now
                                runCatching { windowManager.updateViewLayout(composeView, _layoutParams) }
                            }
                        }
                    }
                }
        ) {
            if (GuiSettingsManager.buttonShape == FloatingButtonShape.PILL_BADGE) {
                Row(
                    modifier = Modifier
                        .height(btnSize * 0.82f)
                        .shadow(8.dp, shape)
                        .clip(shape)
                        .background(palette.cardExpandedBg)
                        .let {
                            if (GuiSettingsManager.buttonBorderGlow) {
                                it.border(2.dp, palette.accent, shape)
                            } else it
                        }
                        .clickable { OverlayManager.showOverlayWindow(overlayClickGUI) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.gato_overlay_icon),
                        contentDescription = "Overlay Button Icon",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(btnSize * 0.6f)
                            .clip(CircleShape)
                    )
                    Text(
                        text = "Gato",
                        color = palette.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (GuiSettingsManager.showActiveBadge && activeCount > 0) {
                        Box(
                            modifier = Modifier
                                .background(palette.accent, CircleShape)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$activeCount",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(btnSize)
                        .shadow(8.dp, shape)
                        .clip(shape)
                        .let {
                            if (GuiSettingsManager.buttonBorderGlow) {
                                it.border(2.dp, palette.accent, shape)
                            } else it
                        }
                        .clickable { OverlayManager.showOverlayWindow(overlayClickGUI) }
                ) {
                    Image(
                        painter = painterResource(R.drawable.gato_overlay_icon),
                        contentDescription = "Overlay Button Icon",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (GuiSettingsManager.showActiveBadge && activeCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(palette.accent, CircleShape)
                            .border(1.dp, Color.White, CircleShape)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$activeCount",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
