package com.retrivedmods.wclient.game.module.misc

import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket

/**
 * Port of Gato Client (PC) AutoKit: runs /kit <n> after `Rekit Delay` ticks, once or in
 * a loop.
 */
class AutoKitModule : Module("AutoKit", ModuleCategory.Misc) {

    private var kitNumber by intValue("Kit Number", 1, 1..6)
    private var rekitDelay by intValue("Rekit Delay", 100, 0..1000)
    private var loop by boolValue("Loop", false)

    private var tickCounter = 0

    override fun onEnabled() {
        super.onEnabled()
        tickCounter = 0
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || !interceptablePacket.isServerBound) return
        if (interceptablePacket.packet !is PlayerAuthInputPacket) return

        tickCounter++
        if (tickCounter < rekitDelay) return
        tickCounter = 0

        session.sendCommand("/kit $kitNumber")
        if (!loop) isEnabled = false
    }
}
