package com.retrivedmods.wclient.game.module.misc

import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import com.retrivedmods.wclient.game.utils.constants.Attribute
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket

/**
 * Port of Gato Client (PC) AutoHub: sends you to the hub (/hub, /lobby, /spawn...) when
 * your health drops to `Hearts` or less. It only fires once per drop: you have to heal
 * above the limit before it can fire again.
 */
class AutoHubModule : Module("AutoHub", ModuleCategory.Misc) {

    private var hearts by floatValue("Hearts", 3f, 0.5f..10f)
    private var hubCommand by stringValue("Hub Command", "/hub", null)
    private var disableAfter by boolValue("Disable After", true)

    private var wasSafe = true

    override fun onEnabled() {
        super.onEnabled()
        wasSafe = true
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || !interceptablePacket.isServerBound) return
        if (interceptablePacket.packet !is PlayerAuthInputPacket) return

        val health = session.localPlayer.attributes[Attribute.HEALTH]?.value ?: return
        if (health <= 0f) return // dead: don't send anything

        if (health > hearts * 2f) { // 1 heart = 2 HP
            wasSafe = true
            return
        }
        if (!wasSafe) return
        wasSafe = false

        session.sendCommand(hubCommand.trim().ifEmpty { "/hub" })
        session.displayClientMessage("§7AutoHub: escapando al hub (%.1f corazones)".format(health / 2f))
        if (disableAfter) isEnabled = false
    }
}
