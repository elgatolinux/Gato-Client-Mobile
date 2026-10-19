package com.retrivedmods.wclient.game.module.visual

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import com.retrivedmods.wclient.game.entity.Player
import com.retrivedmods.wclient.render.RenderOverlayView
import com.retrivedmods.wclient.render.WorldProjection
import com.retrivedmods.wclient.render.WorldRenderer
import org.cloudburstmc.math.matrix.Matrix4f
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityAbsolutePacket
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityDeltaPacket
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket
import java.util.concurrent.ConcurrentHashMap

/**
 * Port of Gato Client (PC) PearlTracker: draws the path of every ender pearl in flight
 * and labels it with the nearest player when it was thrown (its likely owner). Once a
 * pearl lands, its trail fades out over `Fade (s)`.
 *
 * Differences from PC:
 *  - Drawn on the 2D overlay (like ESP) instead of the game's 3D renderer.
 *  - One trail color fading from transparent (oldest) to opaque (newest), instead of
 *    PC's start/end color pickers.
 *  - If the server stops sending a pearl's position, its flight is simulated (gravity
 *    and drag) until the server removes it.
 */
class PearlTrackerModule : Module("PearlTracker", ModuleCategory.Visual), WorldRenderer {

    private val fov by floatValue("FOV", 110f, 40f..110f)
    private val fadeSeconds by floatValue("Fade (s)", 1.25f, 0.05f..6f)
    private val colorRed by intValue("Red", 255, 0..255)
    private val colorGreen by intValue("Green", 255, 0..255)
    private val colorBlue by intValue("Blue", 255, 0..255)
    private val strokeWidth by floatValue("Width", 3f, 1f..10f)
    private val showText by boolValue("Show Text", true)
    private val textSize by floatValue("Text Size", 0.6f, 0.1f..1.5f)
    private val box by boolValue("Box", false)
    private val boxSize by floatValue("Box Size", 0.25f, 0.05f..1f)

    private class Pearl(val uniqueEntityId: Long, var position: Vector3f, var velocity: Vector3f) {
        val points = ArrayList<Vector3f>()
        var owner: String? = null
        var updatedThisTick = true
        @Volatile var diedAt = 0L
    }

    private val pearls = ConcurrentHashMap<Long, Pearl>()

    override fun onEnabled() {
        super.onEnabled()
        RenderOverlayView.current?.invalidate()
    }

    override fun onDisabled() {
        super.onDisabled()
        pearls.clear()
        RenderOverlayView.current?.invalidate()
    }

    override fun onDisconnect(reason: String) {
        pearls.clear()
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled) return

        when (val packet = interceptablePacket.packet) {
            is AddEntityPacket -> {
                if (packet.identifier != PEARL) return
                val pearl = Pearl(packet.uniqueEntityId, packet.position, packet.motion ?: Vector3f.ZERO)
                pearl.owner = nearestPlayerName(packet.position)
                pearl.addPoint(packet.position)
                pearls[packet.runtimeEntityId] = pearl
            }

            is MoveEntityAbsolutePacket -> pearls[packet.runtimeEntityId]?.let { pearl ->
                pearl.moveTo(packet.position)
            }

            is MoveEntityDeltaPacket -> pearls[packet.runtimeEntityId]?.let { pearl ->
                // Level already applied the delta to the tracked entity.
                val entity = session.level.entityMap[packet.runtimeEntityId] ?: return
                pearl.moveTo(entity.vec3Position)
            }

            is SetEntityMotionPacket -> pearls[packet.runtimeEntityId]?.let { pearl ->
                pearl.velocity = packet.motion
            }

            is RemoveEntityPacket -> pearls.values
                .firstOrNull { it.uniqueEntityId == packet.uniqueEntityId && it.diedAt == 0L }
                ?.diedAt = System.currentTimeMillis()

            is PlayerAuthInputPacket -> if (interceptablePacket.isServerBound) onTick()
        }
    }

    private fun onTick() {
        val now = System.currentTimeMillis()
        val iterator = pearls.values.iterator()
        while (iterator.hasNext()) {
            val pearl = iterator.next()
            if (pearl.diedAt != 0L) {
                if (now - pearl.diedAt > fadeSeconds * 1000f) iterator.remove()
                continue
            }
            if (!pearl.updatedThisTick) {
                // No position from the server this tick: simulate the throwable physics.
                val v = pearl.velocity
                pearl.addPoint(pearl.position.add(v))
                pearl.position = pearl.position.add(v)
                pearl.velocity = Vector3f.from(v.x * DRAG, v.y * DRAG - GRAVITY, v.z * DRAG)
            }
            pearl.updatedThisTick = false
        }
    }

    private fun Pearl.moveTo(newPosition: Vector3f) {
        position = newPosition
        updatedThisTick = true
        addPoint(newPosition)
    }

    private fun Pearl.addPoint(point: Vector3f) = synchronized(points) {
        val last = points.lastOrNull()
        if (last != null && last.distanceSquared(point) < MIN_POINT_STEP_SQ) return
        points.add(point)
        if (points.size > MAX_POINTS) points.removeAt(0)
    }

    private fun nearestPlayerName(position: Vector3f): String? {
        val localPlayer = session.localPlayer
        val nearest = (session.level.entityMap.values.filterIsInstance<Player>() + localPlayer)
            .minByOrNull { it.distanceSq(position) } ?: return null
        if (nearest === localPlayer) return "You"
        return nearest.username.replace(Regex("§."), "").ifEmpty { null }
    }

    override fun render(canvas: Canvas) {
        if (!isEnabled || !isSessionCreated || pearls.isEmpty()) return

        val viewProj = WorldProjection.viewProjection(session.localPlayer, fov, canvas.width, canvas.height)
        val now = System.currentTimeMillis()

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = this@PearlTrackerModule.strokeWidth
            strokeCap = Paint.Cap.ROUND
        }

        for (pearl in pearls.values) {
            val alpha = if (pearl.diedAt == 0L) 1f
            else (1f - (now - pearl.diedAt) / (fadeSeconds * 1000f)).coerceIn(0f, 1f)
            if (alpha <= 0f) continue

            val points = synchronized(pearl.points) { pearl.points.toList() }
            val screen = points.map { WorldProjection.worldToScreen(it, viewProj, canvas.width, canvas.height) }

            for (i in 1 until screen.size) {
                val a = screen[i - 1] ?: continue
                val b = screen[i] ?: continue
                // Oldest segments are faint, newest are solid.
                val segmentAlpha = alpha * (0.15f + 0.85f * i / (screen.size - 1))
                linePaint.color = color((segmentAlpha * 255).toInt())
                canvas.drawLine(a.x, a.y, b.x, b.y, linePaint)
            }

            if (box) drawBox(canvas, pearl.position, viewProj, alpha)

            val owner = pearl.owner
            if (showText && owner != null) {
                val anchor = WorldProjection.worldToScreen(
                    pearl.position.add(0f, 0.1f, 0f), viewProj, canvas.width, canvas.height
                ) ?: continue
                drawLabel(canvas, owner, anchor.x, anchor.y, alpha)
            }
        }
    }

    private fun drawBox(canvas: Canvas, center: Vector3f, viewProj: Matrix4f, alpha: Float) {
        val s = boxSize
        val corners = listOf(
            Vector3f.from(-s, -s, -s), Vector3f.from(s, -s, -s), Vector3f.from(s, s, -s), Vector3f.from(-s, s, -s),
            Vector3f.from(-s, -s, s), Vector3f.from(s, -s, s), Vector3f.from(s, s, s), Vector3f.from(-s, s, s)
        ).map { WorldProjection.worldToScreen(center.add(it), viewProj, canvas.width, canvas.height) ?: return }

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = color((alpha * 45).toInt())
        }
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = color((alpha * 170).toInt())
        }

        val minX = corners.minOf { it.x }
        val minY = corners.minOf { it.y }
        val maxX = corners.maxOf { it.x }
        val maxY = corners.maxOf { it.y }
        canvas.drawRect(minX, minY, maxX, maxY, fill)

        val edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 4 to 5, 5 to 6, 6 to 7, 7 to 4, 0 to 4, 1 to 5, 2 to 6, 3 to 7)
        val path = Path()
        for ((a, b) in edges) {
            path.moveTo(corners[a].x, corners[a].y)
            path.lineTo(corners[b].x, corners[b].y)
        }
        canvas.drawPath(path, outline)
    }

    private fun drawLabel(canvas: Canvas, text: String, x: Float, y: Float, alpha: Float) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 48f * this@PearlTrackerModule.textSize
            textAlign = Paint.Align.CENTER
            color = Color.argb((alpha * 255).toInt(), 255, 255, 255)
        }
        val padding = textPaint.textSize * 0.3f
        val width = textPaint.measureText(text)
        val background = Paint().apply { color = Color.argb((alpha * 140).toInt(), 0, 0, 0) }
        canvas.drawRect(
            x - width / 2 - padding, y - textPaint.textSize - padding,
            x + width / 2 + padding, y + padding,
            background
        )
        canvas.drawText(text, x, y, textPaint)
    }

    private fun color(alpha: Int) = Color.argb(alpha.coerceIn(0, 255), colorRed, colorGreen, colorBlue)

    private companion object {
        const val PEARL = "minecraft:ender_pearl"
        const val MAX_POINTS = 768
        const val MIN_POINT_STEP_SQ = 0.00005f
        const val GRAVITY = 0.03f
        const val DRAG = 0.99f
    }
}
