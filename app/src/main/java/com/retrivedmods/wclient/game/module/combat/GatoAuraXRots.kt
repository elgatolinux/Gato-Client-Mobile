package com.retrivedmods.wclient.game.module.combat

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Port of the GatoClient (PC) GatoAuraXRots rotation engine
 * (Client/Managers/ModuleManager/Modules/Category/Combat/GatoAuraXRots1-4.cpp).
 *
 * The 11 handlers write ctx.rotOut {pitch, yaw} (and ctx.headYaw where the
 * original does) exactly like the PC reconstruction; all constants, fold
 * quirks and call orders are preserved verbatim.
 *
 * Relay mapping notes:
 * - Target "PredBox" (stateVector pos/velocity) -> tracked entity position +
 *   per-tick motion delta.
 * - Target "RotSrc" yaw -> tracked entity rotationYaw.
 * - aabb lower/upper are rebuilt from position and metadata width/height
 *   (pos = feet center): lower = (x-w/2, y, z-w/2), upper = (x+w/2, y+h, z+w/2).
 * - RotIsKeyDown(W/A/S/D/Space) -> the touch input flags of the last
 *   PlayerAuthInputPacket seen by the module.
 * - gameTimeSec() -> System.nanoTime()/1e9 (monotonic, same role as the
 *   original's client clock).
 */
object GatoAuraXRots {

    // ---- ctx (the original's stack ctx + this+0x218/0x21C outputs) ----
    class Ctx(
        val pred50: Float,   // 50.0 -> t = pred50 / 50.0 (prediction ticks)
        val c158: Float,     // 0.9
        val c15C: Float,     // 8.0
        val c160: Float,     // 1.0
        val c164: Float,     // 3.0
        val c20C: Float,     // 0.7
        val c214: Float,     // 1.5
        val c120: Float,     // 0.1
        val c124: Float,     // 0.1
        val c11C: Int,       // 8
        val c130: Int,       // 20
        val randomizeHit: Boolean,
        val vertOffset: Float = 0.3f   // this+0x168 del original (setting "Vertical Offset")
    ) {
        var rotPitch = 0f    // *rotOut {pitch, yaw}
        var rotYaw = 0f
        var headYaw = 0f
    }

    // ---- per-target snapshot (PredBox + RotSrc + AABBShape) ----
    class Target(
        val posX: Float, val posY: Float, val posZ: Float,
        val velX: Float, val velY: Float, val velZ: Float,
        val yaw: Float,
        val width: Float,    // aabb upper.x - lower.x
        val height: Float    // aabb upper.y - lower.y
    ) {
        val lowerX: Float get() = posX - width * 0.5f
        val lowerY: Float get() = posY
        val lowerZ: Float get() = posZ - width * 0.5f
    }

    // ---- environment (local player + keys + clock) ----
    class Env(
        val lpX: Float, val lpY: Float, val lpZ: Float,
        val lpVX: Float, val lpVY: Float, val lpVZ: Float,
        val keyW: Boolean, val keyA: Boolean, val keyS: Boolean,
        val keyD: Boolean, val keySpace: Boolean,
        val timeSec: Float
    )

    // g_tick @0x18033F75C: shared global tick counter (pre-incremented by handlers)
    internal var tickCounter = 0

    // FrontStrafe per-target memory: map[target] = last target yaw (g_fsTargetYaw)
    private val fsTargetYaw = HashMap<Long, Float>()

    fun resetState() {
        tickCounter = 0
        fsTargetYaw.clear()
        resetGroup3()
        resetNewRots()
    }

    private fun normalize(vx: Float, vy: Float, vz: Float): FloatArray {
        val len = sqrt(vx * vx + vy * vy + vz * vz)
        if (len < 0.0001f) return floatArrayOf(vx, vy, vz) // guard len=0: sin normalizar
        return floatArrayOf(vx / len, vy / len, vz / len)
    }

    private fun baseRot(ctx: Ctx, dx: Float, dy: Float, dz: Float): Pair<Float, Float> {
        val dist = sqrt((dy.toDouble() * dy) + (dx.toDouble() * dx) + (dz.toDouble() * dz))
        var pitch = ctx.rotPitch
        var yaw = ctx.rotYaw
        if (dist > 0.001f) {
            pitch = (asin(dy / dist) * -57.295776f).toFloat()
            yaw = (-atan2(dx, dz) * 57.295776f).toFloat()
        }
        return pitch to yaw
    }

    private fun asin(d: Double): Double = kotlin.math.asin(d.coerceIn(-1.0, 1.0))

    private fun asin(f: Float): Float = kotlin.math.asin(f.coerceIn(-1f, 1f))

    // RotHeadYaw (0x1800DE4A0) — inferred reconstruction (see PC source note)
    private fun rotHeadYaw(ctx: Ctx, env: Env, tpx: Float, tpy: Float, tpz: Float, yawActual: Float): Float {
        val dx = tpx - env.lpX
        val dz = tpz - env.lpZ
        var targetYaw = -atan2(dx, dz) * 57.295776f
        while (targetYaw > 180.0f) targetYaw -= 360.0f
        while (targetYaw < -180.0f) targetYaw += 360.0f

        var headYaw = yawActual + (targetYaw - yawActual) * ctx.c120
        while (headYaw > 180.0f) headYaw -= 360.0f
        while (headYaw < -180.0f) headYaw += 360.0f

        ctx.headYaw = headYaw
        return headYaw
    }

    // RotSubDED40 (0x1800DED40) — inferred reconstruction: |wrap180(in)| / 180
    private fun rotSubDED40(`in`: Float): Float {
        var d = `in`
        while (d > 180.0f) d -= 360.0f
        while (d < -180.0f) d += 360.0f
        if (d < 0.0f) d = -d
        return d / 180.0f
    }

    // ===================== Unified (combina lo mejor de las demas) =====================
    // Pedido explicito: juntar las rotaciones existentes en una sola. Toma la
    // prediccion de velocidad de smooth(), el suavizado exponencial de
    // astral(), y le agrega DOS cosas que ninguno de los 13 modos de arriba
    // aplicaba de forma explicita:
    //   1) un tope duro de grados-por-tick (lo mas efectivo y simple contra
    //      un lagback: la mayoria de los anticheats marcan un giro que
    //      cambia demasiados grados en un solo tick, no la rotacion en si)
    //   2) un jitter minimo, para no repetir exactamente el mismo delta
    //      tick tras tick (un patron perfectamente identico es en si mismo
    //      una señal para algunos anticheats)
    // Esto NO es una garantia de "0 lagback" ni "100% de hits" - sigue
    // siendo una rotacion artificial, y cada servidor valida distinto. Es,
    // sí, la mas conservadora de las 14 disponibles.
    fun unified(ctx: Ctx, t: Target, env: Env) {
        val dx0 = t.posX - env.lpX
        val dy0 = t.posY - env.lpY
        val dz0 = t.posZ - env.lpZ
        val dist = sqrt((dx0 * dx0 + dy0 * dy0 + dz0 * dz0).toDouble()).toFloat()

        val k = (dist * 0.12f).coerceAtMost(4f)
        val dVX = t.velX - env.lpVX
        val dVY = t.velY - env.lpVY
        val dVZ = t.velZ - env.lpVZ
        val aimX = t.posX + dVX * k
        val aimY = t.posY + dVY * k + t.height * 0.55f + ctx.vertOffset
        val aimZ = t.posZ + dVZ * k

        val (pitch0, yaw0) = baseRot(ctx, aimX - env.lpX, aimY - env.lpY, aimZ - env.lpZ)
        var pitch = pitch0
        var yaw = yaw0
        while (pitch > 90f) pitch -= 180f
        while (pitch < -90f) pitch += 180f
        while (yaw > 180f) yaw -= 360f
        while (yaw < -180f) yaw += 360f

        var dPitch = pitch - ctx.rotPitch
        var dYaw = yaw - ctx.rotYaw
        while (dYaw > 180f) dYaw -= 360f
        while (dYaw < -180f) dYaw += 360f

        val ff = (7.5f / (dist + 3f)).coerceIn(0.12f, 0.55f)

        var stepYaw = dYaw * ff
        var stepPitch = dPitch * ff

        val maxStep = 20f + (dist * 1.5f).coerceAtMost(20f)
        stepYaw = stepYaw.coerceIn(-maxStep, maxStep)
        stepPitch = stepPitch.coerceIn(-maxStep * 0.6f, maxStep * 0.6f)

        val jitter = (Math.random().toFloat() - 0.5f) * 0.6f

        var newYaw = ctx.rotYaw + stepYaw + jitter
        var newPitch = ctx.rotPitch + stepPitch
        while (newPitch > 90f) newPitch -= 180f
        while (newPitch < -90f) newPitch += 180f
        while (newYaw > 180f) newYaw -= 360f
        while (newYaw < -180f) newYaw += 360f

        ctx.rotPitch = newPitch
        ctx.rotYaw = newYaw
        ctx.headYaw = newYaw
    }

    // ===================== Alpha (Rots1) =====================
    fun alpha(ctx: Ctx, t: Target, env: Env) {
        val tick = ctx.pred50 / 50.0f

        val counter = ++tickCounter
        val corner = (counter / 2) % 4

        val tY = t.posY
        val tZ = t.posZ
        var predX = t.posX + tick * t.velX
        val predZ0 = t.posZ + tick * t.velZ
        var predY = tY + tick * t.velY

        val height = t.height
        val width = t.width

        predY += height * 0.65f
        val w38 = width * 0.38f
        val h15 = height * 0.15f

        val d0x = predX - env.lpX
        val d0y = predY - env.lpY
        val d0z = predZ0 - env.lpZ
        val n = normalize(d0x, d0y, d0z)
        val r = normalize(-n[2], 0.0f, n[0])
        val offX = w38 * r[0]
        val offZ = w38 * r[2]

        when (corner) {
            0 -> { predY += h15; predX -= offX }                                  // izq arriba
            1 -> { predX += offX; predY += h15 }                                  // der arriba
            2 -> { predX += offX; predY -= h15 }                                  // der abajo
            3 -> { predY -= h15; predX -= offX }                                  // izq abajo
        }
        val predZ = when (corner) {
            0 -> predZ0 - offZ
            1 -> predZ0 + offZ
            2 -> predZ0 + offZ
            else -> predZ0 - offZ
        }

        val (pitch0, yaw0) = baseRot(ctx, predX - env.lpX, predY - env.lpY, predZ - env.lpZ)
        var pitch = pitch0
        var yaw = yaw0
        // folds literales del binario (el fold inferior del pitch suma 90, no 180)
        while (pitch > 90.0f) pitch -= 180.0f
        while (pitch < -90.0f) pitch += 90.0f
        while (yaw > 180.0f) yaw -= 360.0f
        while (yaw < -180.0f) yaw += 360.0f

        ctx.rotPitch = pitch
        ctx.rotYaw = yaw

        // head yaw hacia la pos cruda del target
        rotHeadYaw(ctx, env, t.posX, tY, tZ, yaw)
    }

    // ===================== Cortex (Rots1) =====================
    fun cortex(ctx: Ctx, t: Target, env: Env) {
        val tX = t.posX; val tY = t.posY; val tZ = t.posZ
        val vX = t.velX; val vY = t.velY; val vZ = t.velZ

        val extraF = t.yaw
        val tp = floatArrayOf(tX, tY, tZ)
        val r1 = rotHeadYaw(ctx, env, tp[0], tp[1], tp[2], extraF)
        val r2 = rotSubDED40(r1)

        val speed = sqrt((vY.toDouble() * vY) + (vX.toDouble() * vX) + (vZ.toDouble() * vZ)).toFloat()
        val x = 1.0f - r2 * 0.5f
        val f: Float = if (x <= 1.0f) (if (x >= 0.2f) x else 0.2f) else 1.0f
        val tick = ctx.pred50 / 50.0f
        val p = (speed * 1.5f + 3.0f) * f * tick

        val predX = tX + vX * p
        val predY = tY + vY * p
        val predZ = tZ + vZ * p

        val h = t.height
        val aimY: Float = if (vY < -0.1f) predY + 0.1f
        else if (vY > 0.1f) predY + h * 0.85f
        else {
            val dot = vX * t.lowerX + vY * t.lowerY + vZ * t.lowerZ
            if (dot > 0.05f) predY + h * 0.4f else predY + h * 0.5f
        }

        val (pitch0, yaw0) = baseRot(ctx, predX - env.lpX, aimY - env.lpY, predZ - env.lpZ)
        var pitch = pitch0
        var yaw = yaw0
        while (pitch > 90.0f) pitch -= 180.0f
        while (pitch < -90.0f) pitch += 90.0f
        while (yaw > 180.0f) yaw -= 360.0f
        while (yaw < -180.0f) yaw += 360.0f

        var dYaw = yaw - ctx.rotYaw
        var dPitch = pitch - ctx.rotPitch
        while (dYaw > 180.0f) dYaw -= 360.0f
        while (dYaw < -180.0f) dYaw += 360.0f
        while (dPitch > 180.0f) dPitch -= 360.0f
        while (dPitch < -180.0f) dPitch += 360.0f

        val l: Float = if (abs(dYaw) > 45.0f) {
            0.95f
        } else {
            val ddx = env.lpX - tX; val ddy = env.lpY - tY; val ddz = env.lpZ - tZ
            val d3 = sqrt((ddy.toDouble() * ddy) + (ddx.toDouble() * ddx) + (ddz.toDouble() * ddz)).toFloat()
            val ff = 12.0f / (d3 + 2.0f)
            if (ff < 0.4f) 0.4f else 0.85f
        }

        var newPitch = ctx.rotPitch + l * dPitch
        var newHeadYaw = ctx.rotYaw + l * dYaw
        while (newPitch > 90.0f) newPitch -= 180.0f
        while (newPitch < -90.0f) newPitch += 90.0f
        while (newHeadYaw > 180.0f) newHeadYaw -= 360.0f
        while (newHeadYaw < -180.0f) newHeadYaw += 360.0f

        ctx.rotPitch = newPitch
        ctx.rotYaw = newHeadYaw
    }

    // ===================== Smooth (Rots1) =====================
    fun smooth(ctx: Ctx, t: Target, env: Env) {
        val dist = sqrt(
            ((env.lpY - t.posY).toDouble() * (env.lpY - t.posY)) +
                ((env.lpX - t.posX).toDouble() * (env.lpX - t.posX)) +
                ((env.lpZ - t.posZ).toDouble() * (env.lpZ - t.posZ))
        ).toFloat()

        val k = dist * 0.15f
        val dVY = t.velY - env.lpVY
        val dVX = t.velX - env.lpVX
        val dVZ = t.velZ - env.lpVZ
        val predY = t.posY + dVY * k
        val predX = t.posX + dVX * k
        val predZ = t.posZ + dVZ * k

        val hgt = t.height
        val aimY = predY + hgt * 0.75f

        val (pitch0, yaw0) = baseRot(ctx, predX - env.lpX, aimY - env.lpY, predZ - env.lpZ)
        var pitch = pitch0
        var yaw = yaw0
        while (pitch > 90.0f) pitch -= 180.0f
        while (pitch < -90.0f) pitch += 90.0f
        while (yaw > 180.0f) yaw -= 360.0f
        while (yaw < -180.0f) yaw += 360.0f

        val dPitch = pitch - ctx.rotPitch
        var dYaw = yaw - ctx.rotYaw
        while (dYaw > 180.0f) dYaw -= 360.0f
        while (dYaw < -180.0f) dYaw += 360.0f

        val ff = 8.0f / (dist + 2.0f)
        val l: Float = if (ff <= 0.85f) (if (ff >= 0.4f) ff else 0.4f) else 0.85f

        var newHeadYaw = ctx.rotYaw + l * dYaw
        var newPitch = ctx.rotPitch + l * dPitch
        while (newPitch > 90.0f) newPitch -= 180.0f
        while (newPitch < -90.0f) newPitch += 90.0f
        while (newHeadYaw > 180.0f) newHeadYaw -= 360.0f
        while (newHeadYaw < -180.0f) newHeadYaw += 360.0f

        ctx.rotPitch = newPitch
        ctx.rotYaw = newHeadYaw
    }

    // ===================== Nemesis (Rots2) =====================
    fun nemesis(ctx: Ctx, t: Target, env: Env) {
        val tick = ctx.pred50 / 50.0f

        val predX0 = t.posX + t.velX * tick
        val predY = t.posY + t.velY * tick
        val predZ0 = t.posZ + t.velZ * tick

        val h = t.height

        val dxp = predX0 - env.lpX
        val dzp = predZ0 - env.lpZ
        val d = sqrt(dxp * dxp + dzp * dzp)
        var px = 0f; var pz = 0f
        if (d > 0.001f) {
            px = -(dzp / d)
            pz = dxp / d
        }

        val vOff = when {
            env.keyS -> 0.1f
            env.keyW -> if (env.keySpace) 0.25f else 0.85f
            else -> 0.5f
        }
        var aimY = vOff * h + predY

        var predX = predX0
        var predZ = predZ0
        if (env.keyA && !env.keyD) {
            predX += px * 0.45f
            predZ += pz * 0.45f
        } else if (!env.keyA && env.keyD) {
            predX -= px * 0.45f
            predZ -= pz * 0.45f
        }

        if (env.keyS && (env.keyA || env.keyD)) {
            val tf = env.timeSec
            predX += sin(tf * 25.0f) * 0.3f
            predZ += cos(tf * 25.0f) * 0.3f
            aimY += cos(tf * 18.0f) * 0.2f
        }

        val (pitch0, yaw0) = baseRot(ctx, predX - env.lpX, aimY - env.lpY, predZ - env.lpZ)
        var pitch = pitch0
        var yaw = yaw0
        while (pitch > 90.0f) pitch -= 180.0f
        while (pitch < -90.0f) pitch += 180.0f
        while (yaw > 180.0f) yaw -= 360.0f
        while (yaw < -180.0f) yaw += 360.0f

        val curP = ctx.rotPitch
        val curY = ctx.rotYaw
        var dYaw = yaw - curY
        val dPit = pitch - curP
        while (dYaw > 180.0f) dYaw -= 360.0f
        while (dYaw < -180.0f) dYaw += 360.0f
        pitch = curP + dPit * 0.9f
        yaw = curY + dYaw * 0.9f

        while (pitch > 90.0f) pitch -= 180.0f
        while (pitch < -90.0f) pitch += 180.0f
        while (yaw > 180.0f) yaw -= 360.0f
        while (yaw < -180.0f) yaw += 360.0f

        ctx.rotPitch = pitch
        ctx.rotYaw = yaw
    }

    // ===================== Vortex (Rots2) =====================
    fun vortex(ctx: Ctx, t: Target, env: Env) {
        val d0 = sqrt(
            ((env.lpY - t.posY).toDouble() * (env.lpY - t.posY)) +
                ((env.lpX - t.posX).toDouble() * (env.lpX - t.posX)) +
                ((env.lpZ - t.posZ).toDouble() * (env.lpZ - t.posZ))
        )
        val tPred = (sqrt(d0) * 0.15f).toFloat()

        var radiusBase = 0.3f
        var h = 1.8f
        if (t.width > 0f) {
            radiusBase = t.width * 0.5f
            h = t.height
        }

        val ang = env.timeSec * 16.0f
        val radius = radiusBase * 1.3f

        val aimX = t.posX + t.velX * tPred
        val aimY = t.posY + t.velY * tPred
        val aimZ = t.posZ + t.velZ * tPred

        val dx = aimX + cos(ang) * radius - env.lpX
        val dy = aimY + h * 0.5f + sin(ang * 0.5f) * (h * 0.3f) - env.lpY
        val dz = aimZ + sin(ang) * radius - env.lpZ

        val (pitch0, yaw0) = baseRot(ctx, dx, dy, dz)
        var pitch = pitch0
        var yaw = yaw0
        while (pitch > 90.0f) pitch -= 180.0f
        while (pitch < -90.0f) pitch += 180.0f
        while (yaw > 180.0f) yaw -= 360.0f
        while (yaw < -180.0f) yaw += 360.0f

        val curP = ctx.rotPitch
        val curY = ctx.rotYaw
        var dYaw = yaw - curY
        val dPit = pitch - curP
        while (dYaw > 180.0f) dYaw -= 360.0f
        while (dYaw < -180.0f) dYaw += 360.0f
        pitch = curP + dPit * 0.8f
        yaw = curY + dYaw * 0.8f

        while (pitch > 90.0f) pitch -= 180.0f
        while (pitch < -90.0f) pitch += 180.0f
        while (yaw > 180.0f) yaw -= 360.0f
        while (yaw < -180.0f) yaw += 360.0f

        ctx.rotPitch = pitch
        ctx.rotYaw = yaw
    }

    // ===================== FrontStrafe (Rots2) =====================
    fun frontStrafe(ctx: Ctx, t: Target, env: Env, targetId: Long) {
        val dx0 = t.posX - env.lpX
        val dy0 = t.posY - env.lpY
        val dz0 = t.posZ - env.lpZ
        val dy0sq = dy0.toDouble() * dy0.toDouble()
        val dist3 = sqrt(dy0sq + (dx0.toDouble() * dx0.toDouble()) + (dz0.toDouble() * dz0.toDouble())).toFloat()
        val velx = t.velX; val vely = t.velY; val velz = t.velZ
        val speed2D = sqrt(velx * velx + velz * velz)

        var pitch = ctx.rotPitch
        var yaw = ctx.rotYaw
        if (dist3 <= 3.0f) {
            // ---- CERRADO ----
            if (env.keyW || env.keyA || env.keyS || env.keyD || speed2D <= 0.1f) {
                // ruta A: orbita temporal (radio c158)
                val gt = env.timeSec
                val ang = gt * 3.0f
                val r = ctx.c158
                val dx = t.posX + cos(ang) * r - env.lpX
                val dz = t.posZ + sin(ang) * r - env.lpZ
                val d = sqrt((dx.toDouble() * dx.toDouble()) + dy0sq + (dz.toDouble() * dz.toDouble())).toFloat()
                if (d > 0.001f) {
                    pitch = (asin(dy0 / d) * -57.295776f)
                    yaw = (-atan2(dx, dz) * 57.295776f)
                }
            } else {
                // ruta B: orbita 1 bloque delante del facing del target
                val prev = fsTargetYaw[targetId] ?: t.yaw
                val cur = t.yaw
                var delta = cur - prev
                while (delta > 180.0f) delta -= 360.0f
                while (delta < -180.0f) delta += 360.0f
                val ang = (cur + delta * ctx.c160) * 0.0174532924f
                val dx = t.posX - sin(ang) - env.lpX
                val dz = t.posZ + cos(ang) - env.lpZ
                val d = sqrt((dx.toDouble() * dx.toDouble()) + dy0sq + (dz.toDouble() * dz.toDouble())).toFloat()
                if (d > 0.001f) {
                    pitch = (asin(dy0 / d) * -57.295776f)
                    yaw = (-atan2(dx, dz) * 57.295776f)
                }
            }
        } else {
            // ---- LEJOS ----
            val dxN: Float; val dyN: Float; val dzN: Float
            if (dist3 == 0.0f) { dxN = 0f; dyN = 0f; dzN = 0f }
            else { dyN = dy0 / dist3; dzN = dz0 / dist3; dxN = dx0 / dist3 }

            var rutaC = false
            if (speed2D > 0.1f) {
                val dot = velx * dxN + vely * dyN + velz * dzN
                if (dot > 0.0f) {
                    // ruta C: persecucion
                    val tp = dist3 * 0.125f
                    val pX = t.posX + velx * tp
                    val pY = t.posY + vely * tp
                    val pZ = t.posZ + velz * tp
                    val sp = (velx.toDouble() * velx) + (vely.toDouble() * vely) + (velz.toDouble() * velz)
                    val speed3 = sqrt(sp).toFloat()
                    val dirX: Float; val dirY: Float; val dirZ: Float
                    if (speed3 == 0.0f) { dirX = 0f; dirY = 0f; dirZ = 0f }
                    else { dirX = velx / speed3; dirY = vely / speed3; dirZ = velz / speed3 }
                    val ex = pX + dirX * ctx.c15C
                    val ey = pY + dirY * ctx.c15C
                    val ez = pZ + dirZ * ctx.c15C
                    val dy = ey - env.lpY
                    val dx = ex - env.lpX
                    val dz = ez - env.lpZ
                    val d = sqrt((dy.toDouble() * dy.toDouble()) + (dx.toDouble() * dx.toDouble()) + (dz.toDouble() * dz.toDouble())).toFloat()
                    if (d > 0.001f) {
                        pitch = (asin(dy / d) * -57.295776f)
                        yaw = (-atan2(dx, dz) * 57.295776f)
                    }
                    rutaC = true
                }
            }
            if (!rutaC) {
                // ruta D: directo
                val d = dist3
                pitch = (asin(dy0 / d) * -57.295776f)
                yaw = (-atan2(dx0, dz0) * 57.295776f)
            }
        }

        while (pitch > 90.0f) pitch -= 180.0f
        while (pitch < -90.0f) pitch += 180.0f
        while (yaw > 180.0f) yaw -= 360.0f
        while (yaw < -180.0f) yaw += 360.0f

        ctx.rotPitch = pitch
        ctx.rotYaw = yaw

        fsTargetYaw[targetId] = t.yaw
    }

    // ===================== AirHvHPro (Rots3) =====================
    // file-statics del original: ring de posiciones + deque de yaws (max 10)
    private const val HIST_CAP = 16
    private const val HIST_MAX = 10
    private val histPos = Array(HIST_CAP) { FloatArray(3) }
    private val histYaw = FloatArray(HIST_CAP)
    private var histOff = 0
    private var histCnt = 0
    private var yawDelta = 0f
    private var lastTargetX = 0f; private var lastTargetY = 0f; private var lastTargetZ = 0f

    private fun resetGroup3() {
        histOff = 0; histCnt = 0; yawDelta = 0f
        lastTargetX = 0f; lastTargetY = 0f; lastTargetZ = 0f
    }

    private fun recordSample(x: Float, y: Float, z: Float, yawSample: Float) {
        val slot = (histOff + histCnt) and (HIST_CAP - 1)
        histPos[slot][0] = x; histPos[slot][1] = y; histPos[slot][2] = z
        histYaw[slot] = yawSample
        if (histCnt < HIST_MAX) histCnt++ else histOff = (histOff + 1) and (HIST_CAP - 1)
    }

    private fun histYaw(logical: Int) = histYaw[(histOff + logical) and (HIST_CAP - 1)]
    private fun histP(logical: Int): FloatArray = histPos[(histOff + logical) and (HIST_CAP - 1)]

    // yVariance @0x1800DE650
    private fun histYawVariance(): Float {
        if (histCnt < 3) return 999.0f
        val v = FloatArray(HIST_MAX); var n = 0; var sum = 0f
        for (i in 1 until histCnt) {
            var d = histYaw(i) - histYaw(i - 1)
            if (d > 180.0f) d += -360.0f
            if (-180.0f > d) d += 360.0f
            v[n++] = d; sum += d
        }
        val mean = sum / n
        var `var` = 0f
        for (i in 0 until n) { val e = v[i] - mean; `var` += e * e }
        return `var` / n
    }

    // countZigzagXZ @0x1800DE9B0
    private fun countZigzagXZ(): Int {
        if (histCnt < 4) return 0
        var changes = 0; var prevSign = 0f
        var i = 0
        while (i + 2 < histCnt) {
            val p0 = histP(i); val p1 = histP(i + 1); val p2 = histP(i + 2)
            val curv = (p2[0] - 2.0f * p1[0] + p0[0]) + (p2[2] - 2.0f * p1[2] + p0[2])
            if (curv != 0.0f && prevSign != 0.0f && (curv * prevSign) < 0.0f) changes++
            prevSign = if (curv > 0.0f) 1.0f else -1.0f
            i++
        }
        return changes
    }

    // accelVariance @0x1800DED40 (group-3 reading)
    private fun histAccelVariance(): Float {
        if (histCnt < 4) return 0.0f
        val v = FloatArray(HIST_MAX); var n = 0
        for (i in 2 until histCnt) {
            val p0 = histP(i); val p1 = histP(i - 1); val p2 = histP(i - 2)
            val ax = p0[0] - 2.0f * p1[0] + p2[0]
            val ay = p0[1] - 2.0f * p1[1] + p2[1]
            val az = p0[2] - 2.0f * p1[2] + p2[2]
            v[n++] = sqrt((ax.toDouble() * ax) + (ay.toDouble() * ay) + (az.toDouble() * az)).toFloat()
        }
        var sum = 0f
        for (i in 0 until n) sum += v[i]
        val mean = sum / n
        var `var` = 0f
        for (i in 0 until n) { val e = v[i] - mean; `var` += e * e }
        return `var` / n
    }

    fun airHvHPro(ctx: Ctx, t: Target, env: Env) {
        // registrar muestra del target
        recordSample(t.posX, t.posY, t.posZ, t.yaw)

        var accX = 0f; var accY = 0f; var accZ = 0f
        var jerkX = 0f; var jerkY = 0f; var jerkZ = 0f
        var velX: Float; var velY: Float; var velZ: Float
        if (histCnt >= 3) {
            val p1 = histP((histOff - 1 + histCnt) and (HIST_CAP - 1))
            val p2 = histP((histOff - 2 + histCnt) and (HIST_CAP - 1))
            val p3 = histP((histOff - 3 + histCnt) and (HIST_CAP - 1))
            val d21x = p1[0] - p2[0]; val d21y = p1[1] - p2[1]; val d21z = p1[2] - p2[2]
            val d32x = p2[0] - p3[0]; val d32y = p2[1] - p3[1]; val d32z = p2[2] - p3[2]
            accX = d21x - d32x; accY = d21y - d32y; accZ = d21z - d32z
            velX = d21x; velY = d21y; velZ = d21z
            if (histCnt >= 4) {
                val p4 = histP((histOff - 4 + histCnt) and (HIST_CAP - 1))
                jerkX = accX - (d32x - (p3[0] - p4[0]))
                jerkY = accY - (d32y - (p3[1] - p4[1]))
                jerkZ = accZ - (d32z - (p3[2] - p4[2]))
            }
        } else {
            velX = t.velX; velY = t.velY; velZ = t.velZ
        }

        if (histCnt >= 2) {
            var yD = histYaw(histCnt - 1) - histYaw(histCnt - 2)
            if (yD > 180.0f) yD += -360.0f
            if (-180.0f > yD) yD += 360.0f
            yawDelta = yD
        }

        var state = 0
        var stateFactor = 0.5f
        if (abs(yawDelta) > 5.0f && histYawVariance() < 3.0f) {
            stateFactor = 0.9f
            state = if (yawDelta <= 0.0f) 1 else 0
        }
        if (countZigzagXZ() >= 3 && state == 0) {
            stateFactor = 0.8f
            state = 3
        }
        if (abs(velY) > sqrt(velX * velX + velZ * velZ) * 0.7f) {
            stateFactor = 0.85f
            state = 4 + (if (velY >= 0.0f) 1 else 0)
        }
        if (histAccelVariance() > 2.0f) {
            stateFactor = 0.6f
            state = 6
        }

        val dlen = sqrt((velY.toDouble() * velY) + (velX.toDouble() * velX) + (velZ.toDouble() * velZ)).toFloat()
        var speed = dlen * 0.5f * 2.0f + 3.0f
        val alen = sqrt((accY.toDouble() * accY) + (accX.toDouble() * accX) + (accZ.toDouble() * accZ)).toFloat()
        if (alen > 0.5f) speed *= 0.8f
        when (state) {
            0 -> speed *= 1.2f
            1, 2 -> {}
            3 -> speed *= 0.7f
            4, 5 -> speed *= 0.9f
            6 -> speed *= 0.5f
        }
        speed = stateFactor * ctx.c20C * speed
        speed += ctx.pred50 / 50.0f
        var spd = speed
        if (spd <= 1.0f) spd = 1.0f
        if (spd > 10.0f) spd = 10.0f

        var aimX = t.posX + spd * velX
        var aimY = t.posY + spd * velY
        var aimZ = t.posZ + spd * velZ
        val s3 = spd * spd * spd
        aimX += s3 * (0.5f * accX + (1.0f / 6.0f) * jerkX)
        aimY += s3 * (0.5f * accY + (1.0f / 6.0f) * jerkY)
        aimZ += s3 * (0.5f * accZ + (1.0f / 6.0f) * jerkZ)

        val k1 = 0.5f
        val k2 = 0.3f
        if (state == 1 || state == 2) {
            var ang = atan2(aimZ - lastTargetZ, aimX - lastTargetX)
            ang += (if (state == 1) k2 else -k2)
            aimX += cos(ang) * k1
            aimZ += sin(ang) * k1
        }
        aimY += ctx.vertOffset // this+0x168 (setting "Vertical Offset")

        val selfK = spd * 0.3f
        val dx = aimX - (env.lpX + selfK * env.lpVX)
        val dy = aimY - (env.lpY + selfK * env.lpVY)
        val dz = aimZ - (env.lpZ + selfK * env.lpVZ)

        val (pitch0, yaw0) = baseRot(ctx, dx, dy, dz)
        var pitch = pitch0
        var yaw = yaw0
        if (pitch > 90.0f) do { pitch += -180.0f } while (pitch > 90.0f)
        if (-90.0f > pitch) do { pitch += 180.0f } while (-90.0f > pitch)
        if (yaw > 180.0f) do { yaw += -360.0f } while (yaw > 180.0f)
        if (-180.0f > yaw) do { yaw += 360.0f } while (-180.0f > yaw)

        lastTargetX = t.posX; lastTargetY = t.posY; lastTargetZ = t.posZ
        ctx.rotPitch = pitch
        ctx.rotYaw = yaw
        ctx.headYaw = yaw
    }

    // ===================== FrontsX (Rots3) =====================
    fun frontsX(ctx: Ctx, t: Target, env: Env) {
        val d0x = env.lpX - t.posX
        val d0y = env.lpY - t.posY
        val d0z = env.lpZ - t.posZ
        val dist0 = sqrt((d0y.toDouble() * d0y) + (d0x.toDouble() * d0x) + (d0z.toDouble() * d0z)).toFloat()

        var tick = ctx.pred50 / 50.0f
        val rvx = t.velX - env.lpVX
        val rvy = t.velY - env.lpVY
        val rvz = t.velZ - env.lpVZ
        tick = (tick + dist0 * 0.1f) * ctx.c214
        val aimX = t.posX + rvx * tick
        val aimY = t.posY + rvy * tick
        val aimZ = t.posZ + rvz * tick

        val h = t.height
        val aimY2 = aimY + h * 0.5f + ctx.vertOffset

        val dx = aimX - env.lpX
        val dy = aimY2 - env.lpY
        val dz = aimZ - env.lpZ

        val (pitch0, yaw0) = baseRot(ctx, dx, dy, dz)
        var pitch = pitch0
        var yaw = yaw0
        if (pitch > 90.0f) do { pitch += -180.0f } while (pitch > 90.0f)
        if (-90.0f > pitch) do { pitch += 180.0f } while (-90.0f > pitch)
        if (yaw > 180.0f) do { yaw += -360.0f } while (yaw > 180.0f)
        if (-180.0f > yaw) do { yaw += 360.0f } while (-180.0f > yaw)

        // suavizado
        val lastPitch = ctx.rotPitch
        val lastYaw = ctx.rotYaw
        val dPitch = pitch - lastPitch
        var dYaw = yaw - lastYaw
        if (dYaw > 180.0f) do { dYaw += -360.0f } while (dYaw > 180.0f)
        if (-180.0f > dYaw) do { dYaw += 360.0f } while (-180.0f > dYaw)
        var f = 10.0f / (dist0 + 1.0f)
        if (f < 0.15f) f = 0.15f
        if (f > 0.8f) f = 0.8f
        pitch = lastPitch + f * dPitch
        yaw = lastYaw + f * dYaw
        if (pitch > 90.0f) do { pitch += -180.0f } while (pitch > 90.0f)
        if (-90.0f > pitch) do { pitch += 180.0f } while (-90.0f > pitch)
        if (yaw > 180.0f) do { yaw += -360.0f } while (yaw > 180.0f)
        if (-180.0f > yaw) do { yaw += 360.0f } while (-180.0f > yaw)

        ctx.rotPitch = pitch
        ctx.rotYaw = yaw
        ctx.headYaw = yaw
    }

    // ===================== Astral (Rots3) =====================
    fun astral(ctx: Ctx, t: Target, env: Env) {
        val d0x = env.lpX - t.posX
        val d0y = env.lpY - t.posY
        val d0z = env.lpZ - t.posZ
        val dist0 = sqrt((d0y.toDouble() * d0y) + (d0x.toDouble() * d0x) + (d0z.toDouble() * d0z)).toFloat()
        val tick = (ctx.pred50 / 50.0f + dist0 * 0.12f) * ctx.c214
        val aimX = t.posX + (t.velX - env.lpVX) * tick
        val aimY = t.posY + (t.velY - env.lpVY) * tick
        val aimZ = t.posZ + (t.velZ - env.lpVZ) * tick

        var radius = 0.3f
        var hY = 1.8f
        if (t.width > 0f) {
            radius = t.width * 0.5f
            hY = t.height
        }
        radius *= 1.5f

        val tsec = env.timeSec
        val wx = sin(tsec * 22.0f)
        val wz = cos(tsec * 22.0f)
        val dx = wx * radius + aimX - env.lpX
        val dy = sin(tsec * 38.0f) * (hY * 0.3f) + (hY * 0.55f + ctx.vertOffset) + aimY - env.lpY
        val dz = wz * radius + aimZ - env.lpZ

        val (pitch0, yaw0) = baseRot(ctx, dx, dy, dz)
        var pitch = pitch0
        var yaw = yaw0
        if (pitch > 90.0f) do { pitch += -180.0f } while (pitch > 90.0f)
        if (-90.0f > pitch) do { pitch += 180.0f } while (-90.0f > pitch)
        if (yaw > 180.0f) do { yaw += -360.0f } while (yaw > 180.0f)
        if (-180.0f > yaw) do { yaw += 360.0f } while (-180.0f > yaw)

        // suavizado exponencial
        val lastPitch = ctx.rotPitch
        val lastYaw = ctx.rotYaw
        var dPitch = pitch - lastPitch
        var dYaw = yaw - lastYaw
        if (dYaw > 180.0f) do { dYaw += -360.0f } while (dYaw > 180.0f)
        if (-180.0f > dYaw) do { dYaw += 360.0f } while (-180.0f > dYaw)
        if (dPitch > 180.0f) do { dPitch += -360.0f } while (dPitch > 180.0f)
        if (-180.0f > dPitch) do { dPitch += 360.0f } while (-180.0f > dPitch)
        pitch = lastPitch + 0.88f * dPitch
        yaw = lastYaw + 0.88f * dYaw
        if (pitch > 90.0f) do { pitch += -180.0f } while (pitch > 90.0f)
        if (-90.0f > pitch) do { pitch += 180.0f } while (-90.0f > pitch)
        if (yaw > 180.0f) do { yaw += -360.0f } while (yaw > 180.0f)
        if (-180.0f > yaw) do { yaw += 360.0f } while (-180.0f > yaw)

        ctx.rotPitch = pitch
        ctx.rotYaw = yaw
        ctx.headYaw = yaw
    }

    // ===================== Atomic (Rots4) =====================
    fun atomic(ctx: Ctx, t: Target, env: Env) {
        val tick = ctx.pred50 / 50.0f
        val ax = tick * t.velX
        val ay = tick * t.velY
        val az = tick * t.velZ
        val p1x = t.lowerX + ax; val p1y = t.lowerY + ay; val p1z = t.lowerZ + az
        val p2x = (t.posX + t.width * 0.5f) + ax
        val p2y = (t.posY + t.height) + ay
        val p2z = (t.posZ + t.width * 0.5f) + az

        var dy1 = (env.lpY - p1y).toDouble(); dy1 *= dy1
        var dx1 = (env.lpX - p1x).toDouble(); dx1 *= dx1
        var dz1 = (env.lpZ - p1z).toDouble(); dz1 *= dz1
        val dxy1 = dx1 + dy1
        var bestX = p1x; var bestY = p1y; var bestZ = p1z
        var best = sqrt(dxy1 + dz1).toFloat()

        val dx2 = (env.lpX - p2x).toDouble(); val dx2s = dx2 * dx2
        run {
            val cand = sqrt(dx2s + dy1 + dz1).toFloat()
            if (best > cand) { bestX = p2x; bestY = p1y; bestZ = p1z; best = cand }
        }
        val dy2 = (env.lpY - p2y).toDouble(); val dy2s = dy2 * dy2
        run {
            val cand = sqrt(dx1 + dy2s + dz1).toFloat()
            if (best > cand) { bestX = p1x; bestY = p2y; bestZ = p1z; best = cand }
        }
        run {
            val cand = sqrt(dx2s + dy2s + dz1).toFloat()
            if (best > cand) { bestX = p2x; bestY = p2y; bestZ = p1z; best = cand }
        }
        val dz2 = (env.lpZ - p2z).toDouble(); val dz2s = dz2 * dz2
        run {
            val cand = sqrt(dx1 + dy1 + dz2s).toFloat()
            if (best > cand) { bestX = p1x; bestY = p1y; bestZ = p2z; best = cand }
        }
        run {
            val cand = sqrt(dx2s + dy1 + dz2s).toFloat()
            if (best > cand) { bestX = p2x; bestY = p1y; bestZ = p2z; best = cand }
        }
        run {
            val cand = sqrt(dx1 + dy2s + dz2s).toFloat()
            if (best > cand) { bestX = p1x; bestY = p2y; bestZ = p2z; best = cand }
        }
        run {
            // el original NO actualiza 'best' en el ultimo candidato
            val cand = sqrt(dx2s + dy2s + dz2s).toFloat()
            if (best > cand) { bestX = p2x; bestY = p2y; bestZ = p2z }
        }

        val dy = bestY - env.lpY
        val dx = bestX - env.lpX
        val dz = bestZ - env.lpZ
        val (pitch0, yaw0) = baseRot(ctx, dx, dy, dz)
        var pitch = pitch0
        var yaw = yaw0
        while (pitch > 90.0f) pitch -= 180.0f
        while (pitch < -90.0f) pitch += 180.0f
        while (yaw > 180.0f) yaw -= 360.0f
        while (yaw < -180.0f) yaw += 360.0f

        ctx.rotPitch = pitch
        ctx.rotYaw = yaw
        ctx.headYaw = yaw
    }

    // ===================== Syntax (Rots4) =====================
    fun syntax(ctx: Ctx, t: Target, env: Env) {
        val ex = t.velX; val ey = t.velY; val ez = t.velZ
        val tick = ctx.pred50 / 50.0f
        val p1x = t.lowerX + tick * ex; val p1y = t.lowerY + tick * ey; val p1z = t.lowerZ + tick * ez
        val p2x = (t.posX + t.width * 0.5f) + tick * ex
        val p2y = (t.posY + t.height) + tick * ey
        val p2z = (t.posZ + t.width * 0.5f) + tick * ez

        var aimY = p1y + (p2y - p1y) * 0.75f

        val dvx = env.lpX - p1x; val dvy = env.lpY - p1y; val dvz = env.lpZ - p1z
        val n = normalize(dvx, dvy, dvz)
        val dot = n[1] * ey + n[0] * ex + n[2] * ez
        if (dot > 0.1f) aimY = p1y

        val pts = arrayOf(
            floatArrayOf(p1x, aimY, p1z),
            floatArrayOf(p2x, aimY, p1z),
            floatArrayOf(p1x, aimY, p2z),
            floatArrayOf(p2x, aimY, p2z)
        )

        val g = ++tickCounter
        val cycle = intArrayOf(0, 3, 1, 2)
        var sel = pts[cycle[g % 4]]

        if (ctx.randomizeHit) {
            val v = pts.clone()
            // SortByDistDesc: DESCENDENTE (las mas lejanas primero) — bug del original replicado
            java.util.Arrays.sort(v) { a, b ->
                val da = sqrt(((env.lpY - a[1]).toDouble() * (env.lpY - a[1])) + ((env.lpX - a[0]).toDouble() * (env.lpX - a[0])) + ((env.lpZ - a[2]).toDouble() * (env.lpZ - a[2])))
                val db = sqrt(((env.lpY - b[1]).toDouble() * (env.lpY - b[1])) + ((env.lpX - b[0]).toDouble() * (env.lpX - b[0])) + ((env.lpZ - b[2]).toDouble() * (env.lpZ - b[2])))
                db.compareTo(da)
            }
            sel = v[g % 2]
        }

        val (pitch0, yaw0) = baseRot(ctx, sel[0] - env.lpX, sel[1] - env.lpY, sel[2] - env.lpZ)
        var pitch = pitch0
        var yaw = yaw0
        while (pitch > 90.0f) pitch -= 180.0f
        while (pitch < -90.0f) pitch += 180.0f
        while (yaw > 180.0f) yaw -= 360.0f
        while (yaw < -180.0f) yaw += 360.0f

        ctx.rotPitch = pitch
        ctx.rotYaw = yaw
        ctx.headYaw = yaw
    }

    // ===================== Score (Rots5 - Noctyrex Aug 31 2026) =====================
    private var scoreCounter = 0

    fun score(ctx: Ctx, t: Target, env: Env) {
        val eyeX = env.lpX; val eyeY = env.lpY; val eyeZ = env.lpZ
        val px = t.posX; val py = t.posY; val pz = t.posZ
        val vx = t.velX; val vy = t.velY; val vz = t.velZ

        val d0 = sqrt(
            (eyeY - py).toDouble() * (eyeY - py) +
            (eyeX - px).toDouble() * (eyeX - px) +
            (eyeZ - pz).toDouble() * (eyeZ - pz)
        ).toFloat()

        val rawT = d0 * 0.15f
        var lead = if (0.5f <= rawT) rawT else 0.5f
        if (rawT > 4.5f) lead = 4.5f

        val hs = sqrt(vz * vz + vx * vx)
        if (hs > 0.15f) lead *= 1.5f

        var x = vx * lead + px
        var z = vz * lead + pz
        val yAim = t.height * 0.7f + (vy * lead + py)
        val off = t.width * 0.4f

        val phase = (++scoreCounter / 2) % 4
        when (phase) {
            0 -> { z += off; x += off }
            1 -> { x -= off; z += off }
            2 -> { x -= off; z -= off }
            3 -> { z -= off; x += off }
        }

        val dx = x - eyeX
        val dy = yAim - eyeY
        val dz = z - eyeZ
        val (pitch0, yaw0) = baseRot(ctx, dx, dy, dz)
        if (!pitch0.isFinite() || !yaw0.isFinite()) return
        var pitch = pitch0
        var yaw = yaw0
        while (pitch > 90f) pitch -= 180f
        while (pitch < -90f) pitch += 180f
        while (yaw > 180f) yaw -= 360f
        while (yaw < -180f) yaw += 360f
        ctx.rotPitch = pitch
        ctx.rotYaw = yaw
    }

    // ===================== Legion (Rots5 - Noctyrex Aug 31 2026) =====================
    fun legion(ctx: Ctx, t: Target, env: Env) {
        val eyeX = env.lpX; val eyeY = env.lpY; val eyeZ = env.lpZ
        val tpx = t.posX
        val tpy = t.posY - 1.6f
        val tpz = t.posZ

        val d0 = sqrt(
            (eyeY - tpy).toDouble() * (eyeY - tpy) +
            (eyeX - tpx).toDouble() * (eyeX - tpx) +
            (eyeZ - tpz).toDouble() * (eyeZ - tpz)
        ).toFloat()
        val k = d0 * 0.1f + ctx.pred50 / 50.0f
        val pvx = k * t.velX; val pvy = k * t.velY; val pvz = k * t.velZ

        val lx = pvx + t.lowerX; val ux = pvx + (t.posX + t.width * 0.5f)
        val ly = pvy + t.lowerY; val uy = pvy + (t.posY + t.height)
        val lz = pvz + t.lowerZ; val uz = pvz + (t.posZ + t.width * 0.5f)

        val ayL = (eyeY - ly).toDouble() * (eyeY - ly)
        val ayU = (eyeY - uy).toDouble() * (eyeY - uy)
        val bxL = (eyeX - lx).toDouble() * (eyeX - lx)
        val bxU = (eyeX - ux).toDouble() * (eyeX - ux)
        val czL = (eyeZ - lz).toDouble() * (eyeZ - lz)
        val czU = (eyeZ - uz).toDouble() * (eyeZ - uz)

        var bestX = lx; var bestY = ly; var bestZ = lz
        var best = sqrt((bxL + ayL) + czL).toFloat()

        fun tryCorner(xy: Double, zz: Double, cx: Float, cy: Float, cz: Float) {
            val d = sqrt(xy + zz).toFloat()
            if (best > d) {
                best = d
                bestX = cx; bestY = cy; bestZ = cz
            }
        }
        tryCorner(bxU + ayL, czL, ux, ly, lz)
        tryCorner(bxL + ayU, czL, lx, uy, lz)
        tryCorner(bxU + ayU, czL, ux, uy, lz)
        tryCorner(bxL + ayL, czU, lx, ly, uz)
        tryCorner(bxU + ayL, czU, ux, ly, uz)
        tryCorner(bxL + ayU, czU, lx, uy, uz)
        tryCorner(bxU + ayU, czU, ux, uy, uz)

        val ang = env.timeSec * 30.0f
        val s = sin(ang)
        val co = cos(ang)
        val dy = (bestY - 0.35f) - eyeY
        val dx = (s * 0.15f + bestX) - eyeX
        val dz = (co * 0.15f + bestZ) - eyeZ
        val (pitch0, yaw0) = baseRot(ctx, dx, dy, dz)
        if (!pitch0.isFinite() || !yaw0.isFinite()) return
        var pitch = pitch0
        var yaw = yaw0
        while (pitch > 90f) pitch -= 180f
        while (pitch < -90f) pitch += 180f
        while (yaw > 180f) yaw -= 360f
        while (yaw < -180f) yaw += 360f
        ctx.rotPitch = pitch
        ctx.rotYaw = yaw
    }

    // ===================== Valhalla (Rots6 - Master123 PC port) =====================
    private val valHistPos = Array(HIST_CAP) { FloatArray(3) }
    private val valHistYaw = FloatArray(HIST_CAP)
    private var valHistOff = 0
    private var valHistCnt = 0
    private var valYawDelta = 0f
    private var valLastTargetX = 0f; private var valLastTargetY = 0f; private var valLastTargetZ = 0f
    private var valhallaTick = 0

    private fun valRecordSample(x: Float, y: Float, z: Float, yawSample: Float) {
        val slot = (valHistOff + valHistCnt) and (HIST_CAP - 1)
        valHistPos[slot][0] = x; valHistPos[slot][1] = y; valHistPos[slot][2] = z
        valHistYaw[slot] = yawSample
        if (valHistCnt < HIST_MAX) valHistCnt++ else valHistOff = (valHistOff + 1) and (HIST_CAP - 1)
    }

    private fun valHistYawAt(logical: Int) = valHistYaw[(valHistOff + logical) and (HIST_CAP - 1)]
    private fun valHistPosAt(logical: Int) = valHistPos[(valHistOff + logical) and (HIST_CAP - 1)]

    private fun valHistYawVariance(): Float {
        if (valHistCnt < 3) return 999.0f
        val v = FloatArray(HIST_MAX); var n = 0; var sum = 0f
        for (i in 1 until valHistCnt) {
            var d = valHistYawAt(i) - valHistYawAt(i - 1)
            if (d > 180.0f) d -= 360.0f
            if (d < -180.0f) d += 360.0f
            v[n++] = d; sum += d
        }
        val mean = sum / n
        var variance = 0f
        for (i in 0 until n) { val e = v[i] - mean; variance += e * e }
        return variance / n
    }

    private fun valCountZigzagXZ(): Int {
        if (valHistCnt < 4) return 0
        var changes = 0; var prevSign = 0f
        var i = 0
        while (i + 2 < valHistCnt) {
            val p0 = valHistPosAt(i); val p1 = valHistPosAt(i + 1); val p2 = valHistPosAt(i + 2)
            val curv = (p2[0] - 2.0f * p1[0] + p0[0]) + (p2[2] - 2.0f * p1[2] + p0[2])
            if (curv != 0.0f && prevSign != 0.0f && (curv * prevSign) < 0.0f) changes++
            prevSign = if (curv > 0.0f) 1.0f else -1.0f
            i++
        }
        return changes
    }

    private fun valHistAccelVariance(): Float {
        if (valHistCnt < 4) return 0.0f
        val v = FloatArray(HIST_MAX); var n = 0
        for (i in 2 until valHistCnt) {
            val p0 = valHistPosAt(i); val p1 = valHistPosAt(i - 1); val p2 = valHistPosAt(i - 2)
            val ax = p0[0] - 2.0f * p1[0] + p2[0]
            val ay = p0[1] - 2.0f * p1[1] + p2[1]
            val az = p0[2] - 2.0f * p1[2] + p2[2]
            v[n++] = sqrt((ax.toDouble() * ax) + (ay.toDouble() * ay) + (az.toDouble() * az)).toFloat()
        }
        var sum = 0f
        for (i in 0 until n) sum += v[i]
        val mean = sum / n
        var variance = 0f
        for (i in 0 until n) { val e = v[i] - mean; variance += e * e }
        return variance / n
    }

    fun valhalla(ctx: Ctx, t: Target, env: Env) {
        valRecordSample(t.posX, t.posY, t.posZ, t.yaw)
        valhallaTick++

        var accX = 0f; var accY = 0f; var accZ = 0f
        var jerkX = 0f; var jerkY = 0f; var jerkZ = 0f
        val velX: Float; val velY: Float; val velZ: Float

        if (valHistCnt >= 3) {
            val p1 = valHistPos[(valHistOff - 1 + valHistCnt) and (HIST_CAP - 1)]
            val p2 = valHistPos[(valHistOff - 2 + valHistCnt) and (HIST_CAP - 1)]
            val p3 = valHistPos[(valHistOff - 3 + valHistCnt) and (HIST_CAP - 1)]
            val d21x = p1[0] - p2[0]; val d21y = p1[1] - p2[1]; val d21z = p1[2] - p2[2]
            val d32x = p2[0] - p3[0]; val d32y = p2[1] - p3[1]; val d32z = p2[2] - p3[2]
            accX = d21x - d32x; accY = d21y - d32y; accZ = d21z - d32z
            velX = d21x; velY = d21y; velZ = d21z
            if (valHistCnt >= 4) {
                val p4 = valHistPos[(valHistOff - 4 + valHistCnt) and (HIST_CAP - 1)]
                jerkX = accX - (d32x - (p3[0] - p4[0]))
                jerkY = accY - (d32y - (p3[1] - p4[1]))
                jerkZ = accZ - (d32z - (p3[2] - p4[2]))
            }
        } else {
            velX = t.velX; velY = t.velY; velZ = t.velZ
        }

        if (valHistCnt >= 2) {
            var yDelta = valHistYawAt(valHistCnt - 1) - valHistYawAt(valHistCnt - 2)
            if (yDelta > 180.0f) yDelta -= 360.0f
            if (yDelta < -180.0f) yDelta += 360.0f
            valYawDelta = yDelta
        }

        var state = 0
        var stateFactor = 0.5f
        if (abs(valYawDelta) > 5.0f && valHistYawVariance() < 3.0f) {
            stateFactor = 0.9f
            state = if (valYawDelta <= 0.0f) 1 else 0
        }
        if (valCountZigzagXZ() >= 3 && state == 0) {
            stateFactor = 0.8f
            state = 3
        }
        if (abs(velY) > sqrt(velX * velX + velZ * velZ) * 0.7f) {
            stateFactor = 0.85f
            state = 4 + (if (velY >= 0.0f) 1 else 0)
        }
        if (valHistAccelVariance() > 2.0f) {
            stateFactor = 0.6f
            state = 6
        }

        val dlen = sqrt((velY.toDouble() * velY) + (velX.toDouble() * velX) + (velZ.toDouble() * velZ)).toFloat()
        var speed = dlen * 0.5f * 2.0f + 3.0f
        val alen = sqrt((accY.toDouble() * accY) + (accX.toDouble() * accX) + (accZ.toDouble() * accZ)).toFloat()
        if (alen > 0.5f) speed *= 0.8f
        when (state) {
            0 -> speed *= 1.2f
            1, 2 -> {}
            3 -> speed *= 0.7f
            4, 5 -> speed *= 0.9f
            6 -> speed *= 0.5f
        }
        speed = stateFactor * ctx.c20C * speed
        speed += ctx.pred50 / 50.0f
        val spd = speed.coerceIn(1.0f, 10.0f)

        var aimX = t.posX + spd * velX
        var aimY = t.posY + spd * velY
        var aimZ = t.posZ + spd * velZ
        val s3 = spd * spd * spd
        aimX += s3 * (0.5f * accX + (1.0f / 6.0f) * jerkX)
        aimY += s3 * (0.5f * accY + (1.0f / 6.0f) * jerkY)
        aimZ += s3 * (0.5f * accZ + (1.0f / 6.0f) * jerkZ)

        val h = t.height
        val w = t.width

        val vOff = when {
            env.keyS -> 0.1f
            env.keyW -> if (env.keySpace) 0.25f else 0.85f
            else -> 0.5f
        }
        aimY = vOff * h + aimY

        if (env.keyA && !env.keyD) {
            val dxp = aimX - env.lpX
            val dzp = aimZ - env.lpZ
            val d = sqrt(dxp * dxp + dzp * dzp)
            if (d > 0.001f) {
                aimX += -(dzp / d) * 0.45f
                aimZ += (dxp / d) * 0.45f
            }
        } else if (!env.keyA && env.keyD) {
            val dxp = aimX - env.lpX
            val dzp = aimZ - env.lpZ
            val d = sqrt(dxp * dxp + dzp * dzp)
            if (d > 0.001f) {
                aimX -= -(dzp / d) * 0.45f
                aimZ -= (dxp / d) * 0.45f
            }
        }

        if (env.keyS && (env.keyA || env.keyD)) {
            val tf = env.timeSec
            aimX += sin(tf * 25.0f) * 0.3f
            aimZ += cos(tf * 25.0f) * 0.3f
            aimY += cos(tf * 18.0f) * 0.2f
        }

        if (state == 1 || state == 2) {
            var ang = atan2(aimZ - valLastTargetZ, aimX - valLastTargetX)
            ang += if (state == 1) 0.3f else -0.3f
            aimX += cos(ang) * 0.5f
            aimZ += sin(ang) * 0.5f
        }

        val dist2D = sqrt((aimX - env.lpX) * (aimX - env.lpX) + (aimZ - env.lpZ) * (aimZ - env.lpZ))
        val orbitRadius = if (dist2D > 3.0f) w * 0.5f * 0.8f else w * 0.5f * 1.3f
        val orbitSpeed = if (dist2D > 3.0f) 12.0f else 16.0f
        val orbitAng = env.timeSec * orbitSpeed
        aimX += cos(orbitAng) * orbitRadius
        aimZ += sin(orbitAng) * orbitRadius
        aimY += sin(orbitAng * 0.5f) * (h * 0.3f)

        val cornerPhase = (valhallaTick / 2) % 4
        val cornerOff = w * 0.38f
        when (cornerPhase) {
            0 -> { aimX -= cornerOff; aimZ -= cornerOff; aimY += h * 0.15f }
            1 -> { aimX += cornerOff; aimZ += cornerOff; aimY += h * 0.15f }
            2 -> { aimX += cornerOff; aimZ += cornerOff; aimY -= h * 0.15f }
            3 -> { aimX -= cornerOff; aimZ -= cornerOff; aimY -= h * 0.15f }
        }

        aimY += 0.3f

        val selfK = spd * 0.3f
        val dx = aimX - (env.lpX + selfK * env.lpVX)
        val dy = aimY - (env.lpY + selfK * env.lpVY)
        val dz = aimZ - (env.lpZ + selfK * env.lpVZ)

        val (pitch0, yaw0) = baseRot(ctx, dx, dy, dz)
        var pitch = pitch0
        var yaw = yaw0
        while (pitch > 90.0f) pitch -= 180.0f
        while (pitch < -90.0f) pitch += 180.0f
        while (yaw > 180.0f) yaw -= 360.0f
        while (yaw < -180.0f) yaw += 360.0f

        val curP = ctx.rotPitch
        val curY = ctx.rotYaw
        var dYaw = yaw - curY
        val dPit = pitch - curP
        while (dYaw > 180.0f) dYaw -= 360.0f
        while (dYaw < -180.0f) dYaw += 360.0f

        val lerpFactor = when {
            dist2D < 3.0f -> 0.9f
            dist2D > 6.0f -> 0.7f
            else -> 0.85f
        }
        pitch = curP + dPit * lerpFactor
        yaw = curY + dYaw * lerpFactor

        while (pitch > 90.0f) pitch -= 180.0f
        while (pitch < -90.0f) pitch += 180.0f
        while (yaw > 180.0f) yaw -= 360.0f
        while (yaw < -180.0f) yaw += 360.0f

        valLastTargetX = t.posX; valLastTargetY = t.posY; valLastTargetZ = t.posZ
        ctx.rotPitch = pitch
        ctx.rotYaw = yaw
        ctx.headYaw = yaw
    }

    // ===================== Berserk (Rots7 - Master123 PC port) =====================
    private val brkHistPos = Array(HIST_CAP) { FloatArray(3) }
    private var brkHistOff = 0
    private var brkHistCnt = 0
    private var brkTick = 0

    private fun brkRecordSample(x: Float, y: Float, z: Float) {
        val slot = (brkHistOff + brkHistCnt) and (HIST_CAP - 1)
        brkHistPos[slot][0] = x; brkHistPos[slot][1] = y; brkHistPos[slot][2] = z
        if (brkHistCnt < HIST_MAX) brkHistCnt++ else brkHistOff = (brkHistOff + 1) and (HIST_CAP - 1)
    }

    fun berserk(ctx: Ctx, t: Target, env: Env) {
        brkRecordSample(t.posX, t.posY, t.posZ)
        brkTick++

        var velX = t.velX; var velY = t.velY; var velZ = t.velZ
        var accX = 0f; var accY = 0f; var accZ = 0f

        if (brkHistCnt >= 3) {
            val p1 = brkHistPos[(brkHistOff - 1 + brkHistCnt) and (HIST_CAP - 1)]
            val p2 = brkHistPos[(brkHistOff - 2 + brkHistCnt) and (HIST_CAP - 1)]
            val p3 = brkHistPos[(brkHistOff - 3 + brkHistCnt) and (HIST_CAP - 1)]
            val d21x = p1[0] - p2[0]; val d21y = p1[1] - p2[1]; val d21z = p1[2] - p2[2]
            val d32x = p2[0] - p3[0]; val d32y = p2[1] - p3[1]; val d32z = p2[2] - p3[2]
            accX = d21x - d32x; accY = d21y - d32y; accZ = d21z - d32z
            velX = d21x; velY = d21y; velZ = d21z
        }

        val dx0 = t.posX - env.lpX
        val dy0 = t.posY - env.lpY
        val dz0 = t.posZ - env.lpZ
        val dist3 = sqrt((dy0.toDouble() * dy0) + (dx0.toDouble() * dx0) + (dz0.toDouble() * dz0)).toFloat()

        val speed = (dist3 * 0.1f + ctx.pred50 / 50.0f).coerceIn(1.0f, 8.0f)

        var aimX = t.posX + velX * speed + 0.5f * accX * speed * speed
        var aimY = t.posY + velY * speed + 0.5f * accY * speed * speed
        var aimZ = t.posZ + velZ * speed + 0.5f * accZ * speed * speed

        val h = t.height
        val w = t.width

        val yDiff = aimY - env.lpY
        val yNorm = yDiff / (if (dist3 > 0.001f) dist3 else 1.0f)
        if (yNorm > 0.3f) {
            aimY += h * 0.2f
        } else if (yNorm < -0.3f) {
            aimY -= h * 0.1f
        }

        if (dist3 > 6.0f) {
            val lead = dist3 * 0.15f
            aimX += velX * lead
            aimZ += velZ * lead
        }

        val orbitAng = brkTick * 0.35f
        val orbitR = if (dist3 < 4.0f) w * 0.8f else w * 0.4f
        aimX += cos(orbitAng) * orbitR
        aimZ += sin(orbitAng) * orbitR

        if (dist3 < 3.0f) {
            val corner = brkTick % 4
            val off = w * 0.3f
            when (corner) {
                0 -> { aimX -= off; aimZ -= off }
                1 -> { aimX += off; aimZ += off }
                2 -> { aimX += off; aimZ -= off }
                3 -> { aimX -= off; aimZ += off }
            }
        }

        aimY += 0.3f

        val selfK = 0.2f
        val dx = aimX - (env.lpX + selfK * env.lpVX)
        val dy = aimY - (env.lpY + selfK * env.lpVY)
        val dz = aimZ - (env.lpZ + selfK * env.lpVZ)

        val (pitch0, yaw0) = baseRot(ctx, dx, dy, dz)
        var pitch = pitch0
        var yaw = yaw0
        while (pitch > 90.0f) pitch -= 180.0f
        while (pitch < -90.0f) pitch += 180.0f
        while (yaw > 180.0f) yaw -= 360.0f
        while (yaw < -180.0f) yaw += 360.0f

        val curP = ctx.rotPitch
        val curY = ctx.rotYaw
        var dYaw = yaw - curY
        val dPit = pitch - curP
        while (dYaw > 180.0f) dYaw -= 360.0f
        while (dYaw < -180.0f) dYaw += 360.0f

        val lerpF = when {
            dist3 > 8.0f -> 0.6f
            dist3 > 4.0f -> 0.75f
            else -> 0.9f
        }
        pitch = curP + dPit * lerpF
        yaw = curY + dYaw * lerpF

        while (pitch > 90.0f) pitch -= 180.0f
        while (pitch < -90.0f) pitch += 180.0f
        while (yaw > 180.0f) yaw -= 360.0f
        while (yaw < -180.0f) yaw += 360.0f

        ctx.rotPitch = pitch
        ctx.rotYaw = yaw
        ctx.headYaw = yaw
    }

    // =========================================================================
    // Counter-HvH 2b2tpe Engine (Ragnarok, Fenrir, Eclipse)
    // =========================================================================
    // Diseñadas y validadas en simulador contra Legion, Score, Valhalla, Berserk,
    // AirHvH Pro y +999aura en escenarios de BypassFly (Vly 2.42 b/t + Descenso
    // Alternado -4.3) + Timer (1.3x-1.8x) + Velocity (0% KB).
    private class CounterState {
        var tick = 0
        var prevFeetX = 0f; var prevFeetY = 0f; var prevFeetZ = 0f
        var prevStepX = 0f; var prevStepY = 0f; var prevStepZ = 0f
        var velEmaX = 0f; var velEmaY = 0f; var velEmaZ = 0f
        var accEmaX = 0f; var accEmaY = 0f; var accEmaZ = 0f
        var prevBearing = 0f
        var hasPrev = false

        var oscAxisX = 1f; var oscAxisZ = 0f
        var flipPeriod = 0f
        var lastFlipTick = -100
        val flipTicks = IntArray(8) { -100 }
        var flipHead = 0

        var lastHeading = 0f
        var turnRate = 0f
        var headingValid = false

        fun reset() {
            tick = 0
            prevFeetX = 0f; prevFeetY = 0f; prevFeetZ = 0f
            prevStepX = 0f; prevStepY = 0f; prevStepZ = 0f
            velEmaX = 0f; velEmaY = 0f; velEmaZ = 0f
            accEmaX = 0f; accEmaY = 0f; accEmaZ = 0f
            prevBearing = 0f
            hasPrev = false
            oscAxisX = 1f; oscAxisZ = 0f
            flipPeriod = 0f; lastFlipTick = -100
            flipTicks.fill(-100); flipHead = 0
            lastHeading = 0f; turnRate = 0f; headingValid = false
        }
    }

    private val stRagnarok = CounterState()
    private val stFenrir = CounterState()
    private val stEclipse = CounterState()

    private fun resetNewRots() {
        valHistOff = 0; valHistCnt = 0; valYawDelta = 0f
        valLastTargetX = 0f; valLastTargetY = 0f; valLastTargetZ = 0f; valhallaTick = 0
        brkHistOff = 0; brkHistCnt = 0; brkTick = 0
        stRagnarok.reset(); stFenrir.reset(); stEclipse.reset()
    }

    private fun updateCounterAndPredict(
        ctx: Ctx,
        st: CounterState,
        t: Target,
        env: Env
    ): FloatArray {
        st.tick++
        // En Bedrock MovePlayerPacket, t.posY para jugadores (height >= 1.6f) viene a nivel de ojos (feetY + 1.62f)
        val feetNowX = t.posX
        val feetNowY = if (t.height >= 1.5f) t.posY - 1.62f else t.posY
        val feetNowZ = t.posZ

        // En Entity.kt, t.velX/Y/Z mide exactamente 1 paquete individual limpio (inmune al ruido 1x/2x de Timer)
        var stepX = t.velX
        var stepY = t.velY
        var stepZ = t.velZ

        if (st.hasPrev) {
            val dpx = feetNowX - st.prevFeetX
            val dpy = feetNowY - st.prevFeetY
            val dpz = feetNowZ - st.prevFeetZ
            val dpMag2 = dpx * dpx + dpy * dpy + dpz * dpz
            if (dpMag2 > 36.0f) {
                // Teleport / ender pearl
                st.reset()
                st.hasPrev = true
                st.prevFeetX = feetNowX; st.prevFeetY = feetNowY; st.prevFeetZ = feetNowZ
                return floatArrayOf(feetNowX, feetNowY, feetNowZ)
            }
            // Si en este tick local no llego paquete nuevo del servidor (jitter), reutilizar el ultimo paso limpio
            if (dpMag2 < 1e-6f && (stepX * stepX + stepZ * stepZ) < 1e-6f) {
                stepX = st.prevStepX; stepY = st.prevStepY; stepZ = st.prevStepZ
            }
        }

        val spH = sqrt(stepX * stepX + stepZ * stepZ)
        val prevSpH = sqrt(st.prevStepX * st.prevStepX + st.prevStepZ * st.prevStepZ)

        // 1) Detector Eigen-Axis 2D de Zigzag (detecta inversiones en cualquier angulo, incluido diagonal dx = -dz)
        if (st.hasPrev && spH > 0.03f && prevSpH > 0.03f) {
            val dot2D = stepX * st.prevStepX + stepZ * st.prevStepZ
            if (dot2D < -0.25f * spH * prevSpH) {
                val iv = st.tick - st.lastFlipTick
                if (iv in 2..20) {
                    st.flipPeriod = if (st.flipPeriod <= 0f) iv.toFloat() else (0.4f * st.flipPeriod + 0.6f * iv)
                }
                st.lastFlipTick = st.tick
                st.flipTicks[st.flipHead and 7] = st.tick
                st.flipHead++
                val dvx = stepX - st.prevStepX
                val dvz = stepZ - st.prevStepZ
                val dvm = sqrt(dvx * dvx + dvz * dvz)
                if (dvm > 0.01f) {
                    st.oscAxisX = dvx / dvm
                    st.oscAxisZ = dvz / dvm
                }
            }
        }

        // 2) EMA de velocidad/aceleracion y CTRV turnRate
        val oldVx = st.velEmaX; val oldVy = st.velEmaY; val oldVz = st.velEmaZ
        st.velEmaX = oldVx + 0.72f * (stepX - oldVx)
        st.velEmaY = oldVy + 0.72f * (stepY - oldVy)
        st.velEmaZ = oldVz + 0.72f * (stepZ - oldVz)
        st.accEmaX = (st.accEmaX * 0.45f + 0.55f * (st.velEmaX - oldVx)).coerceIn(-0.25f, 0.25f)
        st.accEmaY = (st.accEmaY * 0.45f + 0.55f * (st.velEmaY - oldVy)).coerceIn(-0.25f, 0.25f)
        st.accEmaZ = (st.accEmaZ * 0.45f + 0.55f * (st.velEmaZ - oldVz)).coerceIn(-0.25f, 0.25f)

        if (spH > 0.03f) {
            val h = atan2(stepZ, stepX)
            if (st.headingValid) {
                var dh = h - st.lastHeading
                while (dh > Math.PI.toFloat()) dh -= (2.0 * Math.PI).toFloat()
                while (dh < -Math.PI.toFloat()) dh += (2.0 * Math.PI).toFloat()
                st.turnRate = 0.45f * st.turnRate + 0.55f * dh.coerceIn(-0.6f, 0.6f)
            }
            st.lastHeading = h
            st.headingValid = true
        } else {
            st.turnRate *= 0.5f
            st.headingValid = false
        }

        // 3) Prediccion Hibrida: Bearing-Coupled Pass-Through (Vly+Timer+Velocity) vs Eigen-Zigzag vs CTRV Orbit
        val bearingNow = atan2(env.lpZ - feetNowZ, env.lpX - feetNowX)
        var vxPred: Float
        var vyPred: Float
        var vzPred: Float

        var recentFlips = 0
        for (i in 0 until 8) {
            if (st.tick - st.flipTicks[i] <= 26) recentFlips++
        }

        if (spH > 0.40f && st.hasPrev) {
            // Modo Anti-BypassFly / Anti-Timer / Anti-Velocity (0% KB):
            // Cuando el rival nos cruza en vuelo con su rotacion lockeada (Legion/Valhalla/Berserk),
            // su rumbo hacia nosotros gira db = bearingNow - prevBearing, rotando su vector de BypassFly!
            val db = bearingNow - st.prevBearing
            val cb = cos(db); val sb = sin(db)
            vxPred = stepX * cb - stepZ * sb
            vzPred = stepX * sb + stepZ * cb
        } else if (recentFlips >= 2 && st.flipPeriod in 2.0f..16.0f) {
            // Modo Anti-Zigzag sobre el Eigen-Axis 2D con particion sub-tick
            val ox = st.oscAxisX; val oz = st.oscAxisZ
            val tx = -oz; val tz = ox
            val vOsc = stepX * ox + stepZ * oz
            val vTan = st.velEmaX * tx + st.velEmaZ * tz
            var s = if (vOsc >= 0f) 1f else -1f
            val spdOsc = abs(vOsc)
            var phase = (st.tick - st.lastFlipTick).toFloat() + 1f
            val lead = 1.6f
            var rem = lead
            var disp = 0f
            while (rem > 1e-4f) {
                val dt = minOf(1.0f, rem)
                rem -= dt
                if (phase + dt > st.flipPeriod) {
                    val tBefore = maxOf(0f, st.flipPeriod - phase)
                    val tAfter = dt - tBefore
                    disp += s * spdOsc * tBefore - s * spdOsc * tAfter
                    s = -s
                    phase = tAfter
                } else {
                    disp += s * spdOsc * dt
                    phase += dt
                }
            }
            vxPred = ox * disp + tx * vTan * lead
            vzPred = oz * disp + tz * vTan * lead
        } else if (spH > 0.03f && abs(st.turnRate) > 0.02f) {
            // Modo CTRV Anti-Orbit (contra FrontStrafe / Vortex / Berserk)
            val h = atan2(st.velEmaZ, st.velEmaX) + st.turnRate
            val sp = sqrt(st.velEmaX * st.velEmaX + st.velEmaZ * st.velEmaZ)
            vxPred = cos(h) * sp
            vzPred = sin(h) * sp
        } else {
            vxPred = st.velEmaX + 0.35f * st.accEmaX
            vzPred = st.velEmaZ + 0.35f * st.accEmaZ
        }

        // 4) Detector del Descenso Alternado de BypassFly (Shift: vy = -4.2997 con vx=vz=0)
        if (stepY < -2.2f && spH < 0.06f && st.hasPrev) {
            vyPred = -0.1266f
            vxPred = st.prevStepX
            vzPred = st.prevStepZ
        } else {
            vyPred = stepY
        }

        if (spH > 0.02f || abs(stepY) > 0.02f) {
            st.prevStepX = stepX; st.prevStepY = stepY; st.prevStepZ = stepZ
        }
        st.prevFeetX = feetNowX; st.prevFeetY = feetNowY; st.prevFeetZ = feetNowZ
        st.prevBearing = bearingNow
        st.hasPrev = true

        // NO restamos env.lpVX/Y/Z porque nuestra posicion y rotacion viajan juntas en el mismo PlayerAuthInputPacket
        return floatArrayOf(
            feetNowX + vxPred,
            feetNowY + vyPred + ctx.vertOffset,
            feetNowZ + vzPred
        )
    }

    private fun storeCounterAim(ctx: Ctx, dx: Float, dy: Float, dz: Float) {
        val (pitch0, yaw0) = baseRot(ctx, dx, dy, dz)
        if (!pitch0.isFinite() || !yaw0.isFinite()) return
        var pitch = pitch0
        var yaw = yaw0
        while (pitch > 90.0f) pitch -= 180.0f
        while (pitch < -90.0f) pitch += 180.0f
        while (yaw > 180.0f) yaw -= 360.0f
        while (yaw < -180.0f) yaw += 360.0f
        ctx.rotPitch = pitch
        ctx.rotYaw = yaw
        ctx.headYaw = yaw
    }

    // ===================== Ragnarok (Counter de Valhalla, Berserk, AirHvH Pro y Vly+Timer) =====================
    fun ragnarok(ctx: Ctx, t: Target, env: Env) {
        val pred = updateCounterAndPredict(ctx, stRagnarok, t, env)
        val w = t.width.coerceAtLeast(0.2f)
        val h = t.height.coerceAtLeast(0.5f)
        val hw = (w * 0.5f - 0.07f).coerceAtLeast(0.05f)

        val cx = env.lpX.coerceIn(pred[0] - hw, pred[0] + hw)
        val cz = env.lpZ.coerceIn(pred[2] - hw, pred[2] + hw)
        val cy = env.lpY.coerceIn(pred[1] + 0.25f, pred[1] + h - 0.22f)

        // Micro-oscilacion aperiodica (Golden Ratio phi) acotada estrictamente dentro del hitbox
        val phi = ((stRagnarok.tick * 0.61803398875f) % 1.0f) * (2.0 * Math.PI).toFloat()
        val dx0 = pred[0] - env.lpX
        val dz0 = pred[2] - env.lpZ
        val d2 = sqrt(dx0 * dx0 + dz0 * dz0)
        val nx = if (d2 > 0.05f) -dz0 / d2 else 1.0f
        val nz = if (d2 > 0.05f) dx0 / d2 else 0.0f

        val ax = (cx + nx * sin(phi) * 0.05f).coerceIn(pred[0] - hw, pred[0] + hw)
        val az = (cz + nz * sin(phi) * 0.05f).coerceIn(pred[2] - hw, pred[2] + hw)
        val ay = (cy + cos(phi) * 0.05f).coerceIn(pred[1] + 0.20f, pred[1] + h - 0.18f)

        storeCounterAim(ctx, ax - env.lpX, ay - env.lpY, az - env.lpZ)
    }

    // ===================== Fenrir (Counter de Legion, Score, FrontStrafe y Vortex) =====================
    fun fenrir(ctx: Ctx, t: Target, env: Env) {
        val pred = updateCounterAndPredict(ctx, stFenrir, t, env)
        val w = t.width.coerceAtLeast(0.2f)
        val h = t.height.coerceAtLeast(0.5f)
        val hw = (w * 0.5f - 0.05f).coerceAtLeast(0.05f)

        val vx = stFenrir.velEmaX
        val vz = stFenrir.velEmaZ
        val sp = sqrt(vx * vx + vz * vz)
        val leadEdgeX = if (sp > 0.04f) (vx / sp) * 0.09f else 0.0f
        val leadEdgeZ = if (sp > 0.04f) (vz / sp) * 0.09f else 0.0f

        val ax = (env.lpX.coerceIn(pred[0] - hw, pred[0] + hw) + leadEdgeX).coerceIn(pred[0] - hw, pred[0] + hw)
        val az = (env.lpZ.coerceIn(pred[2] - hw, pred[2] + hw) + leadEdgeZ).coerceIn(pred[2] - hw, pred[2] + hw)
        val ay = env.lpY.coerceIn(pred[1] + 0.22f, pred[1] + h - 0.20f)

        storeCounterAim(ctx, ax - env.lpX, ay - env.lpY, az - env.lpZ)
    }

    // ===================== Eclipse (Counter 3D Multipunto de +999aura / Rooster) =====================
    fun eclipse(ctx: Ctx, t: Target, env: Env) {
        val pred = updateCounterAndPredict(ctx, stEclipse, t, env)
        val w = t.width.coerceAtLeast(0.2f)
        val h = t.height.coerceAtLeast(0.5f)
        val hw = (w * 0.5f - 0.05f).coerceAtLeast(0.05f)
        val yLow = pred[1] + 0.22f
        val yMid = pred[1] + h * 0.52f
        val yHigh = pred[1] + h - 0.20f

        val cands = arrayOf(
            floatArrayOf(env.lpX.coerceIn(pred[0] - hw, pred[0] + hw), env.lpY.coerceIn(yLow, yHigh), env.lpZ.coerceIn(pred[2] - hw, pred[2] + hw)),
            floatArrayOf(pred[0] - hw, yMid, pred[2] - hw),
            floatArrayOf(pred[0] + hw, yMid, pred[2] - hw),
            floatArrayOf(pred[0] - hw, yMid, pred[2] + hw),
            floatArrayOf(pred[0] + hw, yMid, pred[2] + hw),
            floatArrayOf(pred[0], yLow, pred[2]),
            floatArrayOf(pred[0], yMid, pred[2]),
            floatArrayOf(pred[0], yHigh, pred[2]),
            floatArrayOf(env.lpX.coerceIn(pred[0] - hw, pred[0] + hw), yMid, env.lpZ.coerceIn(pred[2] - hw, pred[2] + hw))
        )

        var best = cands[0]
        var bestDistSq = Float.MAX_VALUE
        for (c in cands) {
            val dx = c[0] - env.lpX
            val dy = c[1] - env.lpY
            val dz = c[2] - env.lpZ
            val d2 = dx * dx + dy * dy + dz * dz
            if (d2 < bestDistSq) {
                bestDistSq = d2
                best = c
            }
        }

        storeCounterAim(ctx, best[0] - env.lpX, best[1] - env.lpY, best[2] - env.lpZ)
    }
}
