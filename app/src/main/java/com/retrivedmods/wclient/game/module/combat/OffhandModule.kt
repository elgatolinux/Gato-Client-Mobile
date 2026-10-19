package com.retrivedmods.wclient.game.module.combat

import android.util.Log
import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import com.retrivedmods.wclient.game.inventory.PlayerInventory
import com.retrivedmods.wclient.game.utils.constants.Attribute
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket

/**
 * Port of Gato Client (PC) Offhand.
 *
 * Keeps a totem or a shield in the offhand. Smart mode keeps a shield while health is
 * at or above "Swap Shield" and falls back to a totem at or below "Swap Totem".
 *
 * Not ported: SurroundOnly, because it needs the blocks around the player and the
 * relay does not track blocks yet.
 */
class OffhandModule : Module("Offhand", ModuleCategory.Combat) {

    enum class OffhandItem { TOTEM, SHIELD }

    private var item by enumValue("Item", OffhandItem.TOTEM, OffhandItem::class.java)
    private var delay by intValue("Delay", 0, 0..20)
    private var smart by boolValue("Smart", false)
    private var swapToTotemHealth by floatValue("Swap Totem", 0f, 0f..20f)
    private var swapToShieldHealth by floatValue("Swap Shield", 0f, 0f..20f)
    private var debug by boolValue("Debug", false)

    private var shouldUseShield = false
    private var delayTicks = 0

    // Swap in flight: wait for the server instead of resending every tick, which made
    // it reject duplicates and resync the inventory (offhand flickering after a pop).
    private var pendingTicks = 0
    private var pendingSlot = -1
    private var pendingId: String? = null

    override fun onEnabled() {
        super.onEnabled()
        delayTicks = 0
        pendingTicks = 0
        pendingSlot = -1
        pendingId = null
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || !interceptablePacket.isServerBound) return
        if (interceptablePacket.packet !is PlayerAuthInputPacket) return

        val player = session.localPlayer
        val inventory = player.inventory
        val health = player.attributes[Attribute.HEALTH]?.value ?: 20f

        if (debug) session.displayClientMessage("§7Offhand health: $health")

        if (pendingTicks > 0) {
            val offhandHasIt = inventory.offhand.id() == pendingId
            val sourceMoved = pendingSlot < 0 || inventory.content[pendingSlot].id() != pendingId
            if (offhandHasIt || sourceMoved) {
                pendingTicks = 0
            } else if (--pendingTicks > 0) {
                return
            }
        }

        val wanted = if (smart) {
            if (health >= swapToShieldHealth) shouldUseShield = true
            if (health <= swapToTotemHealth) shouldUseShield = false
            val hasShield = inventory.offhand.id() == SHIELD ||
                    (0 until 36).any { inventory.content[it].id() == SHIELD }
            if (shouldUseShield && hasShield) SHIELD else TOTEM
        } else {
            if (item == OffhandItem.TOTEM) TOTEM else SHIELD
        }

        if (inventory.offhand.id() == wanted) return

        if (delayTicks < delay) {
            delayTicks++
            return
        }
        delayTicks = 0

        val sourceSlot = (0 until 36).firstOrNull { inventory.content[it].id() == wanted } ?: return

        try {
            val sourceItem = inventory.content[sourceSlot]
            val previousOffhand = inventory.offhand
            inventory.moveItem(sourceSlot, PlayerInventory.SLOT_OFFHAND, inventory, session)
            // moveItem only sends the request; mirror the swap locally (same as AutoTotem).
            inventory.offhand = sourceItem
            inventory.content[sourceSlot] = previousOffhand

            pendingTicks = 10 // ~0.5 s at 20 TPS
            pendingSlot = sourceSlot
            pendingId = wanted
        } catch (e: Exception) {
            Log.w("OffhandModule", "Failed to swap offhand", e)
        }
    }

    private fun ItemData.id(): String? =
        if (this == ItemData.AIR) null else definition?.identifier

    private companion object {
        const val TOTEM = "minecraft:totem_of_undying"
        const val SHIELD = "minecraft:shield"
    }
}
