package com.retrivedmods.wclient.render

import android.content.Context
import android.graphics.Canvas
import android.view.View
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleManager

class RenderOverlayView(context: Context) : View(context) {

    companion object {
        /** The attached overlay, so renderers can request a redraw when they get enabled. */
        var current: RenderOverlayView? = null
            private set
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        current = this
        invalidate() // Initial render
    }

    override fun onDetachedFromWindow() {
        if (current === this) current = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val renderers = ModuleManager.modules
            .filter { it is WorldRenderer && it.isEnabled && it.isSessionCreated } //isSessionCreated check

        renderers.forEach { (it as WorldRenderer).render(canvas) }

        if (renderers.isNotEmpty()) {
            postInvalidateOnAnimation()
        }
    }
}
