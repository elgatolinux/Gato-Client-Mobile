package com.retrivedmods.wclient.game.module.misc

import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Port of Gato Client (PC) Blink: holds back your movement packets while enabled and
 * sends them all at once when disabled.
 *
 * Like PC, input packets that carry an item use, an inventory request or block actions
 * are let through instead of being queued.
 */
class BlinkModule : Module("Blink", ModuleCategory.Misc) {

    private val queue = ConcurrentLinkedQueue<BedrockPacket>()

    override fun onDisabled() {
        super.onDisabled()
        if (!isSessionCreated) {
            queue.clear()
            return
        }
        while (true) {
            val packet = queue.poll() ?: break
            session.serverBound(packet)
        }
    }

    override fun onDisconnect(reason: String) {
        queue.clear()
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || !interceptablePacket.isServerBound) return

        when (val packet = interceptablePacket.packet) {
            is MovePlayerPacket -> {
                queue.add(packet)
                interceptablePacket.intercept()
            }

            is PlayerAuthInputPacket -> {
                if (packet.inputData.contains(PlayerAuthInputData.PERFORM_ITEM_INTERACTION) ||
                    packet.inputData.contains(PlayerAuthInputData.PERFORM_ITEM_STACK_REQUEST) ||
                    packet.playerActions.isNotEmpty()
                ) return

                queue.add(packet)
                interceptablePacket.intercept()
            }
        }
    }
}
