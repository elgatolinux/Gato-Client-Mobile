package com.retrivedmods.wclient.render

import com.retrivedmods.wclient.game.entity.LocalPlayer
import org.cloudburstmc.math.matrix.Matrix4f
import org.cloudburstmc.math.vector.Vector2f
import org.cloudburstmc.math.vector.Vector3f
import kotlin.math.cos
import kotlin.math.sin

/** Projects world positions onto the overlay, using the local player's eye as the camera. */
object WorldProjection {

    fun viewProjection(localPlayer: LocalPlayer, fov: Float, width: Int, height: Int): Matrix4f =
        Matrix4f.createPerspective(
            fov,
            width.toFloat() / height,
            0.1f,
            128f
        ).mul(
            Matrix4f.createTranslation(localPlayer.vec3Position)
                .mul(rotateY(-localPlayer.rotationYaw - 180f))
                .mul(rotateX(-localPlayer.rotationPitch))
                .invert()
        )

    /** @return the screen position, or null when the point is behind the camera. */
    fun worldToScreen(pos: Vector3f, m: Matrix4f, w: Int, h: Int): Vector2f? {
        val rw = m.get(3, 0) * pos.x + m.get(3, 1) * pos.y + m.get(3, 2) * pos.z + m.get(3, 3)
        if (rw <= 0.01f) return null

        val inv = 1f / rw
        val x = w / 2f + (m.get(0, 0) * pos.x + m.get(0, 1) * pos.y + m.get(0, 2) * pos.z + m.get(0, 3)) * inv * w / 2f
        val y = h / 2f - (m.get(1, 0) * pos.x + m.get(1, 1) * pos.y + m.get(1, 2) * pos.z + m.get(1, 3)) * inv * h / 2f
        return Vector2f.from(x, y)
    }

    private fun rotateX(a: Float): Matrix4f {
        val r = Math.toRadians(a.toDouble())
        return Matrix4f.from(
            1f, 0f, 0f, 0f,
            0f, cos(r).toFloat(), -sin(r).toFloat(), 0f,
            0f, sin(r).toFloat(), cos(r).toFloat(), 0f,
            0f, 0f, 0f, 1f
        )
    }

    private fun rotateY(a: Float): Matrix4f {
        val r = Math.toRadians(a.toDouble())
        return Matrix4f.from(
            cos(r).toFloat(), 0f, sin(r).toFloat(), 0f,
            0f, 1f, 0f, 0f,
            -sin(r).toFloat(), 0f, cos(r).toFloat(), 0f,
            0f, 0f, 0f, 1f
        )
    }
}
