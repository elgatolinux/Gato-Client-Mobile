package com.retrivedmods.wclient.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.platform.compositionContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.retrivedmods.wclient.game.ModuleManager
import com.retrivedmods.wclient.overlay.gui.classic.OverlayButton
import com.retrivedmods.wclient.overlay.gui.classic.OverlayShortcutButton
import com.retrivedmods.wclient.ui.theme.WClientTheme
import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak")
@Suppress("MemberVisibilityCanBePrivate")
object OverlayManager {

    private val overlayWindows = ArrayList<OverlayWindow>()

    var currentContext: Context? = null
        private set

    var isShowing = false
        private set

    private var currentOverlayButton: OverlayWindow? = null

    private fun initializeOverlays(context: Context) {
        GuiSettingsManager.ensureLoaded(context)

        overlayWindows.clear()
        currentOverlayButton = OverlayButton().apply {
            layoutParams.alpha = GuiSettingsManager.overlayOpacity
        }

        val shortcuts = ModuleManager
            .modules
            .filter { it.isShortcutDisplayed }
            .map {
                it.overlayShortcutButton.apply {
                    layoutParams.alpha = GuiSettingsManager.shortcutOpacity
                }
            }
        overlayWindows.addAll(shortcuts)
        currentOverlayButton?.let { overlayWindows.add(it) }
    }

    fun showOverlayWindow(overlayWindow: OverlayWindow) {
        if (!overlayWindows.contains(overlayWindow)) {
            overlayWindows.add(overlayWindow)
        }

        val context = currentContext
        if (isShowing && context != null) {
            showOverlayWindow(context, overlayWindow)
        }
    }

    fun dismissOverlayWindow(overlayWindow: OverlayWindow) {
        overlayWindows.remove(overlayWindow)

        val context = currentContext
        if (isShowing && context != null) {
            dismissOverlayWindow(context, overlayWindow)
        }
    }

    fun show(context: Context) {
        currentContext = context
        initializeOverlays(context)

        overlayWindows.toList().forEach {
            showOverlayWindow(context, it)
        }

        isShowing = true
    }

    fun dismiss() {
        val context = currentContext
        if (context != null) {
            overlayWindows.toList().forEach {
                dismissOverlayWindow(context, it)
            }
            isShowing = false
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    private fun showOverlayWindow(context: Context, overlayWindow: OverlayWindow) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val layoutParams = overlayWindow.layoutParams
        val composeView = overlayWindow.composeView
        composeView.setContent {
            WClientTheme {
                overlayWindow.Content()
            }
        }
        val lifecycleOwner = overlayWindow.lifecycleOwner
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView.setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore
                get() = overlayWindow.viewModelStore
        })
        composeView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        composeView.compositionContext = overlayWindow.recomposer
        if (overlayWindow.firstRun) {
            overlayWindow.composeScope.launch {
                overlayWindow.recomposer.runRecomposeAndApplyChanges()
            }
            overlayWindow.firstRun = false
        }

        try {
            windowManager.addView(composeView, layoutParams)
        } catch (_: Exception) {
        }
    }

    private fun dismissOverlayWindow(context: Context, overlayWindow: OverlayWindow) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val composeView = overlayWindow.composeView

        try {
            windowManager.removeView(composeView)
        } catch (_: Exception) {
        }
    }

    fun updateOverlayOpacity(opacity: Float) {
        overlayWindows.find { it is OverlayButton }?.let { button ->
            button.layoutParams.alpha = opacity
            currentContext?.let { context ->
                runCatching {
                    (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                        .updateViewLayout(button.composeView, button.layoutParams)
                }
            }
        }
    }

    fun updateShortcutOpacity(opacity: Float) {
        overlayWindows.filter { it is OverlayShortcutButton }.forEach { button ->
            button.layoutParams.alpha = opacity
            currentContext?.let { context ->
                runCatching {
                    (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                        .updateViewLayout(button.composeView, button.layoutParams)
                }
            }
        }
    }
}
