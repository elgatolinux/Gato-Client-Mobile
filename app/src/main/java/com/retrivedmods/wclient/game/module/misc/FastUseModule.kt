package com.retrivedmods.wclient.game.module.misc

import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket
import org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket

/**
 * Port of Gato Client (PC) FastUse: uses throwables (XP bottles, pearls, snowballs,
 * splash potions...) much faster.
 *
 * PC checks the right mouse button. Here, "holding use" means the client sent a use
 * (click air) with a throwable in the last 300 ms; while holding, the game keeps
 * resending it, which keeps that window open. Inside it, the module repeats the use
 * `UsePerTick` times every `Delay` ticks.
 */
class FastUseModule : Module("FastUse", ModuleCategory.Misc) {

    private var delay by intValue("Delay", 1, 0..20)
    private var usePerTick by intValue("UsePerTick", 1, 1..64)
    private var onlyXP by boolValue("OnlyXP", false)

    private var useTemplate: InventoryTransactionPacket? = null
    private var lastClientUse = 0L
    private var tickDelay = 0

    override fun onDisconnect(reason: String) {
        useTemplate = null
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || !interceptablePacket.isServerBound) return

        when (val packet = interceptablePacket.packet) {
            is InventoryTransactionPacket -> {
                if (packet.transactionType == InventoryTransactionType.ITEM_USE &&
                    packet.actionType == ACTION_CLICK_AIR &&
                    isAllowed(packet.itemInHand?.definition?.identifier)
                ) {
                    useTemplate = packet.clone()
                    lastClientUse = System.currentTimeMillis()
                }
            }

            is MobEquipmentPacket -> useTemplate = null

            is PlayerAuthInputPacket -> onTick()
        }
    }

    private fun onTick() {
        val template = useTemplate ?: return
        if (System.currentTimeMillis() - lastClientUse > HOLD_WINDOW_MS) return

        val inventory = session.localPlayer.inventory
        if (!isAllowed(inventory.hand.definition?.identifier)) {
            useTemplate = null
            return
        }

        if (tickDelay < delay) {
            tickDelay++
            return
        }
        tickDelay = 0

        repeat(usePerTick) {
            session.serverBound(template.clone().apply {
                hotbarSlot = inventory.heldItemSlot
                itemInHand = inventory.hand
                playerPosition = session.localPlayer.vec3Position
            })
        }
    }

    private fun isAllowed(identifier: String?): Boolean {
        if (identifier == null) return false
        if (onlyXP) return identifier == XP_BOTTLE
        return identifier in THROWABLES
    }

    private companion object {
        const val ACTION_CLICK_AIR = 1
        const val HOLD_WINDOW_MS = 300L
        const val XP_BOTTLE = "minecraft:experience_bottle"
        val THROWABLES = setOf(
            XP_BOTTLE,
            "minecraft:ender_pearl",
            "minecraft:snowball",
            "minecraft:egg",
            "minecraft:splash_potion",
            "minecraft:lingering_potion",
            "minecraft:ender_eye",
            "minecraft:wind_charge"
        )
    }
}
