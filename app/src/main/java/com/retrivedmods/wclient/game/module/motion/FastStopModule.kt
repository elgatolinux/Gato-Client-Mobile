package com.retrivedmods.wclient.game.module.motion

import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import com.retrivedmods.wclient.game.ModuleManager
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket
import kotlin.math.abs

/**
 * Port of Gato Client (PC) FastStop: when no movement input is held, the horizontal
 * motion is cut to zero so the player stops instantly instead of sliding.
 * Skipped while JetPack is on, same as PC.
 */
class FastStopModule : Module("FastStop", ModuleCategory.Motion) {

    private val jetPack by lazy { ModuleManager.modules.firstOrNull { it is JetPackModule } }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || !interceptablePacket.isServerBound) return
        val packet = interceptablePacket.packet as? PlayerAuthInputPacket ?: return
        if (jetPack?.isEnabled == true) return

        if (packet.motion.length() > 0f) return

        // Only send when there is something to stop, so it doesn't fire every idle tick.
        val delta = packet.delta ?: return
        if (abs(delta.x) < 0.01f && abs(delta.z) < 0.01f) return

        session.clientBound(SetEntityMotionPacket().apply {
            runtimeEntityId = session.localPlayer.runtimeEntityId
            motion = Vector3f.from(0f, delta.y, 0f)
        })
    }
}
