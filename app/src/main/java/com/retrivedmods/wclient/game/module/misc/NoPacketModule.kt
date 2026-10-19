package com.retrivedmods.wclient.game.module.misc

import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory

/**
 * Port of Gato Client (PC) NoPacket: stops every packet the game sends to the server
 * while enabled (commonly used for bugs/dupes). Like PC, it only kicks in once you are
 * in a world.
 *
 * Difference from PC: packets that other modules send themselves are not blocked,
 * because they go straight to the server without passing through the module chain.
 */
class NoPacketModule : Module("NoPacket", ModuleCategory.Misc) {

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || !interceptablePacket.isServerBound) return
        if (session.localPlayer.runtimeEntityId == 0L) return
        interceptablePacket.intercept()
    }
}
