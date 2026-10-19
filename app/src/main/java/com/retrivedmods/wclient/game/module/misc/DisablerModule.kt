package com.retrivedmods.wclient.game.module.misc

import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import org.cloudburstmc.protocol.bedrock.data.ClientPlayMode
import org.cloudburstmc.protocol.bedrock.data.InputMode
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket

/**
 * Port of Gato Client (PC) Disabler: spoofs input fields some anticheats rely on.
 *
 * Only the packet part is ported. Not ported:
 *  - The "Guyser" mode, which on PC only changes the game's hit result in memory.
 *  - The hit-result change in Lifeboat mode, for the same reason.
 *  - PC's "TicksAlive" edits, which have no matching field in the protocol library.
 */
class DisablerModule : Module("Disabler", ModuleCategory.Misc) {

    enum class Server { LIFEBOAT, NETHERGAMES }

    private var server by enumValue("Server", Server.LIFEBOAT, Server::class.java)

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || !interceptablePacket.isServerBound) return

        when (val packet = interceptablePacket.packet) {
            is PlayerAuthInputPacket -> {
                packet.inputMode = InputMode.TOUCH
                packet.playMode = when (server) {
                    Server.LIFEBOAT -> ClientPlayMode.SCREEN
                    Server.NETHERGAMES -> ClientPlayMode.EXIT_LEVEL_LIVING_ROOM
                }
            }

            is MovePlayerPacket -> {
                if (server == Server.NETHERGAMES) packet.tick -= 1
            }
        }
    }
}
