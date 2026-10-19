package com.retrivedmods.wclient.game.module.combat

import android.util.Log
import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import com.retrivedmods.wclient.game.inventory.PlayerInventory
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket

/**
 * Port of Gato Client (PC) AutoArmor.
 *
 * Every `Delay` ticks it checks one armor slot (round-robin, so it never sends four
 * transactions in the same tick). If that slot is empty or holds something that is not
 * that piece, it equips the best matching piece from the inventory. A slot that already
 * has the piece or an elytra is left alone, like on PC.
 *
 * Difference from PC: the best piece is picked by material only (netherite > diamond >
 * iron > chain > gold > leather/turtle), since the relay has no armor points per item.
 */
class AutoArmorModule : Module("AutoArmor", ModuleCategory.Combat) {

    private var delay by intValue("Delay", 2, 0..20)

    private var tickCounter = 0
    private var pieceIndex = 0

    private val pieces = listOf(
        PlayerInventory.SLOT_HELMET to "helmet",
        PlayerInventory.SLOT_CHESTPLATE to "chestplate",
        PlayerInventory.SLOT_LEGGINGS to "leggings",
        PlayerInventory.SLOT_BOOTS to "boots"
    )

    override fun onEnabled() {
        super.onEnabled()
        tickCounter = 0
        pieceIndex = 0
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || !interceptablePacket.isServerBound) return
        if (interceptablePacket.packet !is PlayerAuthInputPacket) return

        if (tickCounter < delay) {
            tickCounter++
            return
        }
        tickCounter = 0

        val (armorSlot, piece) = pieces[pieceIndex]
        pieceIndex = (pieceIndex + 1) % pieces.size
        equipPiece(armorSlot, piece)
    }

    private fun equipPiece(armorSlot: Int, piece: String) {
        val inventory = session.localPlayer.inventory
        val current = inventory.content[armorSlot]
        val currentId = current.id()
        if (currentId != null && (piece in currentId || "elytra" in currentId)) return

        var bestSlot = -1
        var bestRank = -1
        for (i in 0 until 36) {
            val id = inventory.content[i].id() ?: continue
            if (piece !in id) continue
            val rank = materialRank(id)
            if (rank > bestRank) {
                bestRank = rank
                bestSlot = i
            }
        }
        if (bestSlot == -1) return

        try {
            val sourceItem = inventory.content[bestSlot]
            inventory.moveItem(bestSlot, armorSlot, inventory, session)
            // moveItem only sends the request; mirror the swap locally (same as AutoTotem).
            inventory.content[armorSlot] = sourceItem
            inventory.content[bestSlot] = current
        } catch (e: Exception) {
            Log.w("AutoArmorModule", "Failed to equip $piece", e)
        }
    }

    private fun materialRank(id: String): Int = when {
        "netherite" in id -> 6
        "diamond" in id -> 5
        "iron" in id -> 4
        "chainmail" in id -> 3
        "golden" in id -> 2
        "leather" in id || "turtle" in id -> 1
        else -> 0
    }

    private fun ItemData.id(): String? =
        if (this == ItemData.AIR) null else definition?.identifier
}
