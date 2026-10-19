package com.retrivedmods.wclient.game.module.visual

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import com.retrivedmods.wclient.render.RenderOverlayView
import com.retrivedmods.wclient.render.WorldProjection
import com.retrivedmods.wclient.render.WorldRenderer
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType
import org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.cos
import kotlin.math.sin

class HitEffectsModule : Module("HitEffects", ModuleCategory.Visual), WorldRenderer {

    enum class EffectStyle {
        NEON_SPARKS,
        SHOCKWAVE_RINGS,
        SAKURA_STARS
    }

    private val style by enumValue("Style", EffectStyle.NEON_SPARKS, EffectStyle::class.java)
    private val fov by floatValue("FOV", 110f, 40f..110f)
    private val durationSec by floatValue("Duration (s)", 0.8f, 0.2f..2.5f)
    private val showPlayerTrail by boolValue("Player Trail", true)

    private data class HitBurst(
        val worldPos: Vector3f,
        val createdAt: Long,
        val seed: Int
    )

    private data class TrailPoint(
        val worldPos: Vector3f,
        val createdAt: Long
    )

    private val bursts = CopyOnWriteArrayList<HitBurst>()
    private val trail = CopyOnWriteArrayList<TrailPoint>()

    override fun onEnabled() {
        super.onEnabled()
        RenderOverlayView.current?.invalidate()
    }

    override fun onDisabled() {
        super.onDisabled()
        bursts.clear()
        trail.clear()
        RenderOverlayView.current?.invalidate()
    }

    override fun onDisconnect(reason: String) {
        bursts.clear()
        trail.clear()
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || !isSessionCreated) return

        val now = System.currentTimeMillis()
        when (val packet = interceptablePacket.packet) {
            is InventoryTransactionPacket -> {
                if (packet.transactionType == InventoryTransactionType.ITEM_USE_ON_ENTITY && packet.actionType == 1) {
                    val entity = session.level.entityMap[packet.runtimeEntityId] ?: return
                    if (bursts.size > 24) bursts.removeAt(0)
                    bursts.add(HitBurst(entity.vec3Position, now, (now and 0xFFFF).toInt()))
                    RenderOverlayView.current?.postInvalidate()
                }
            }
            is EntityEventPacket -> {
                if (packet.type == EntityEventType.HURT && packet.runtimeEntityId != session.localPlayer.runtimeEntityId) {
                    val entity = session.level.entityMap[packet.runtimeEntityId] ?: return
                    if (bursts.size > 24) bursts.removeAt(0)
                    bursts.add(HitBurst(entity.vec3Position, now, (now and 0xFFFF).toInt()))
                    RenderOverlayView.current?.postInvalidate()
                }
            }
            is PlayerAuthInputPacket -> {
                if (showPlayerTrail && interceptablePacket.isServerBound) {
                    val p = Vector3f.from(packet.position.x, packet.position.y - 1.4f, packet.position.z)
                    val last = trail.lastOrNull()
                    if (last == null || last.worldPos.distanceSquared(p) > 0.04f) {
                        if (trail.size > 60) trail.removeAt(0)
                        trail.add(TrailPoint(p, now))
                    }
                }
            }
        }
    }

    override fun render(canvas: Canvas) {
        if (!isEnabled || !isSessionCreated) return
        val now = System.currentTimeMillis()
        val maxLife = (durationSec * 1000f).toLong().coerceAtLeast(200L)

        bursts.removeAll { now - it.createdAt > maxLife }
        trail.removeAll { now - it.createdAt > 1500L }

        if (bursts.isEmpty() && trail.isEmpty()) return

        val viewProj = WorldProjection.viewProjection(session.localPlayer, fov, canvas.width, canvas.height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Draw rainbow player trail
        if (showPlayerTrail && trail.size >= 2) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4.5f
            paint.strokeCap = Paint.Cap.ROUND
            val pts = trail.map { WorldProjection.worldToScreen(it.worldPos, viewProj, canvas.width, canvas.height) to it.createdAt }
            for (i in 1 until pts.size) {
                val a = pts[i - 1].first ?: continue
                val b = pts[i].first ?: continue
                val ageRatio = (1f - (now - pts[i].second) / 1500f).coerceIn(0f, 1f)
                val hue = ((now * 0.08f + i * 12f) % 360f)
                val baseCol = Color.HSVToColor((ageRatio * 220).toInt(), floatArrayOf(hue, 0.85f, 1.0f))
                paint.color = baseCol
                canvas.drawLine(a.x, a.y, b.x, b.y, paint)
            }
        }

        // Draw hit bursts
        for (burst in bursts) {
            val progress = ((now - burst.createdAt).toFloat() / maxLife.toFloat()).coerceIn(0f, 1f)
            val alpha = ((1f - progress) * 255).toInt().coerceIn(0, 255)
            val screen = WorldProjection.worldToScreen(burst.worldPos, viewProj, canvas.width, canvas.height) ?: continue

            when (style) {
                EffectStyle.NEON_SPARKS -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3.5f
                    val rays = 10
                    val r0 = 12f + progress * 45f
                    val r1 = 28f + progress * 95f
                    for (k in 0 until rays) {
                        val ang = (k * (2.0 * Math.PI / rays) + burst.seed * 0.1).toFloat()
                        val hue = ((k * 36f + now * 0.15f) % 360f)
                        paint.color = Color.HSVToColor(alpha, floatArrayOf(hue, 0.85f, 1.0f))
                        canvas.drawLine(
                            screen.x + cos(ang) * r0,
                            screen.y + sin(ang) * r0,
                            screen.x + cos(ang) * r1,
                            screen.y + sin(ang) * r1,
                            paint
                        )
                    }
                }
                EffectStyle.SHOCKWAVE_RINGS -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 4f * (1f - progress * 0.5f)
                    paint.color = Color.argb(alpha, 255, 92, 166)
                    canvas.drawCircle(screen.x, screen.y, 18f + progress * 90f, paint)
                    paint.color = Color.argb((alpha * 0.7f).toInt(), 0, 245, 212)
                    canvas.drawCircle(screen.x, screen.y, 8f + progress * 55f, paint)
                }
                EffectStyle.SAKURA_STARS -> {
                    paint.style = Paint.Style.FILL
                    val count = 8
                    val radius = 15f + progress * 80f
                    for (k in 0 until count) {
                        val ang = (k * (2.0 * Math.PI / count) + progress * 2.2f).toFloat()
                        val px = screen.x + cos(ang) * radius
                        val py = screen.y + sin(ang) * radius - progress * 30f
                        paint.color = if (k % 2 == 0) Color.argb(alpha, 255, 110, 180) else Color.argb(alpha, 255, 220, 240)
                        canvas.drawCircle(px, py, (1f - progress * 0.5f) * 8f, paint)
                    }
                }
            }
        }
    }
}
