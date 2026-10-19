package com.retrivedmods.wclient.render

import android.graphics.Canvas

/** A module that draws on top of the game through [RenderOverlayView]. */
interface WorldRenderer {
    fun render(canvas: Canvas)
}
