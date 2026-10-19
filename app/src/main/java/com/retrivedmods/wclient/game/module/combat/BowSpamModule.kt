package com.retrivedmods.wclient.game.module.combat

import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket
import org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket

/**
 * Port of Gato Client (PC) BowSpam.
 *
 * PC reads the right mouse button. Here, "holding use" means the client sent an
 * ITEM_USE (click air) with a bow or crossbow and has not released it yet. While that
 * lasts, the module releases and re-draws server-side every `Delay` ticks, so arrows
 * keep firing while the client still shows the bow being drawn.
 */
class BowSpamModule : Module("BowSpam", ModuleCategory.Combat) {

    private var delay by intValue("Delay", 5, 3..32)

    private var useTemplate: InventoryTransactionPacket? = null
    private var isCrossbow = false
    private var drawTicks = 0

    override fun onDisabled() {
        super.onDisabled()
        useTemplate = null
    }

    override fun onDisconnect(reason: String) {
        useTemplate = null
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || !interceptablePacket.isServerBound) return

        when (val packet = interceptablePacket.packet) {
            is InventoryTransactionPacket -> when (packet.transactionType) {
                InventoryTransactionType.ITEM_USE -> {
                    val id = packet.itemInHand?.definition?.identifier
                    if (packet.actionType == ACTION_CLICK_AIR && (id == BOW || id == CROSSBOW)) {
                        useTemplate = packet.clone()
                        isCrossbow = id == CROSSBOW
                        drawTicks = 0
                    }
                }

                InventoryTransactionType.ITEM_RELEASE -> useTemplate = null
                else -> {}
            }

            is MobEquipmentPacket -> useTemplate = null

            is PlayerAuthInputPacket -> onTick()
        }
    }

    private fun onTick() {
        val template = useTemplate ?: return
        val hand = session.localPlayer.inventory.hand
        val id = hand.definition?.identifier
        if (id != BOW && id != CROSSBOW) {
            useTemplate = null
            return
        }

        drawTicks++
        if (isCrossbow) {
            // Charge, then release (loads it) and use twice: fire + start charging again.
            if (drawTicks < 18) return
            sendRelease()
            sendUse(template)
            sendUse(template)
        } else {
            if (drawTicks < delay) return
            sendRelease()
            sendUse(template)
        }
        drawTicks = 0
    }

    private fun sendUse(template: InventoryTransactionPacket) {
        val inventory = session.localPlayer.inventory
        session.serverBound(template.clone().apply {
            hotbarSlot = inventory.heldItemSlot
            itemInHand = inventory.hand
            playerPosition = session.localPlayer.vec3Position
        })
    }

    private fun sendRelease() {
        val inventory = session.localPlayer.inventory
        session.serverBound(InventoryTransactionPacket().apply {
            transactionType = InventoryTransactionType.ITEM_RELEASE
            actionType = ACTION_RELEASE
            hotbarSlot = inventory.heldItemSlot
            itemInHand = inventory.hand
            headPosition = session.localPlayer.vec3Position
            playerPosition = session.localPlayer.vec3Position
            clickPosition = Vector3f.ZERO
        })
    }

    private companion object {
        const val BOW = "minecraft:bow"
        const val CROSSBOW = "minecraft:crossbow"
        const val ACTION_CLICK_AIR = 1
        const val ACTION_RELEASE = 0
    }
}
