package com.retrivedmods.wclient.game.module.misc

import android.util.Log
import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import com.retrivedmods.wclient.game.Value
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket

/**
 * Port of Gato Client (PC) AutoHotBar: keeps chosen items in fixed hotbar slots,
 * refilling them from the main inventory (slots 9-35).
 *
 * Differences from PC: items are matched by name instead of PC's numeric item ids
 * (potions by their potion type), and it does at most one swap per tick so it never
 * floods the server with transactions.
 */
class AutoHotbarModule : Module("AutoHotBar", ModuleCategory.Misc) {

    private class Entry(
        val enabled: Value<Boolean>,
        val slot: Value<Int>,
        val matches: (ItemData) -> Boolean
    )

    private val entries = ArrayList<Entry>()

    private fun entry(name: String, enabled: Boolean, slot: Int, matches: (ItemData) -> Boolean) {
        entries.add(Entry(boolValue(name, enabled), intValue("$name Slot", slot, 1..9), matches))
    }

    private fun id(identifier: String): (ItemData) -> Boolean = { it.identifier() == identifier }

    private fun potion(vararg types: Int): (ItemData) -> Boolean = {
        it.identifier() in POTIONS && it.damage in types
    }

    init {
        entry("Sword", false, 1, id("minecraft:diamond_sword"))
        entry("Pickaxe", false, 2, id("minecraft:diamond_pickaxe"))
        entry("Obsidian", false, 8, id("minecraft:obsidian"))
        entry("Crystal", false, 4, id("minecraft:end_crystal"))
        entry("Potions", false, 5, potion(31, 32, 33))  // Strength
        entry("Harming", false, 5, potion(23, 24))      // Harming
        entry("Beds", false, 9, id("minecraft:bed"))
        entry("Anvil", false, 6, id("minecraft:anvil"))
        entry("TNT", true, 7, id("minecraft:tnt"))
        entry("Gapple", false, 3, id("minecraft:golden_apple"))
        entry("Netherite", false, 1, id("minecraft:netherite_block"))
        entry("Piston", false, 1, id("minecraft:piston"))
        entry("Redstone", false, 1, id("minecraft:redstone_block"))
        entry("Glowstone", false, 1, id("minecraft:glowstone"))
        entry("Anchor", false, 1, id("minecraft:respawn_anchor"))
        entry("Pearl", false, 1, id("minecraft:ender_pearl"))
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || !interceptablePacket.isServerBound) return
        if (interceptablePacket.packet !is PlayerAuthInputPacket) return

        val inventory = session.localPlayer.inventory
        for (entry in entries) {
            if (!entry.enabled.value) continue
            val target = entry.slot.value - 1
            if (entry.matches(inventory.content[target])) continue

            val source = (9 until 36).firstOrNull { entry.matches(inventory.content[it]) } ?: continue
            try {
                val sourceItem = inventory.content[source]
                val targetItem = inventory.content[target]
                inventory.moveItem(source, target, inventory, session)
                // moveItem only sends the request; mirror the swap locally (same as AutoTotem).
                inventory.content[target] = sourceItem
                inventory.content[source] = targetItem
            } catch (e: Exception) {
                Log.w("AutoHotbarModule", "Failed to refill slot ${target + 1}", e)
            }
            return
        }
    }

    private fun ItemData.identifier(): String? =
        if (this == ItemData.AIR) null else definition?.identifier

    private companion object {
        val POTIONS = setOf("minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion")
    }
}
