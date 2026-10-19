package com.retrivedmods.wclient.game.module.motion

import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket
import org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket

/**
 * Port of Gato Client (PC) AutoSneak.
 *
 * RealSilent: the server is told you are sneaking while the client moves normally.
 * AutoSprint: sends sprinting while there is movement input (only forward unless
 * Omnidirectional is on), and never while using an item.
 * AntiBug: skips the silent sneak while using an item or breaking a block.
 *
 * Difference from PC: "using an item" and "breaking" are tracked from the packets the
 * client sends (use/release, block actions) instead of reading game memory.
 */
class AutoSneakModule : Module("AutoSneak", ModuleCategory.Motion) {

    private var realSilent by boolValue("RealSilent", false)
    private var autoSprint by boolValue("AutoSprint", false)
    private var omni by boolValue("Omnidirectional", true)
    private var antiBug by boolValue("AntiBug", true)

    private var usingItem = false
    private var breaking = false

    override fun onEnabled() {
        super.onEnabled()
        usingItem = false
        breaking = false
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || !interceptablePacket.isServerBound) return

        when (val packet = interceptablePacket.packet) {
            is InventoryTransactionPacket -> when (packet.transactionType) {
                InventoryTransactionType.ITEM_USE -> if (packet.actionType == 1) usingItem = true
                InventoryTransactionType.ITEM_RELEASE -> usingItem = false
                else -> {}
            }

            is MobEquipmentPacket -> usingItem = false

            is PlayerAuthInputPacket -> onTick(packet)
        }
    }

    private fun onTick(packet: PlayerAuthInputPacket) {
        packet.playerActions.forEach {
            when (it.action) {
                PlayerActionType.START_BREAK, PlayerActionType.CONTINUE_BREAK,
                PlayerActionType.BLOCK_CONTINUE_DESTROY -> breaking = true

                PlayerActionType.STOP_BREAK, PlayerActionType.ABORT_BREAK,
                PlayerActionType.BLOCK_PREDICT_DESTROY -> breaking = false

                else -> {}
            }
        }

        if (realSilent && !(antiBug && (usingItem || breaking))) {
            packet.inputData.remove(PlayerAuthInputData.STOP_SNEAKING)
            packet.inputData.add(PlayerAuthInputData.START_SNEAKING)
        }

        if (autoSprint && !usingItem) {
            val move = packet.motion
            val forward = move.y > 0f
            val anyMove = move.length() > 0f
            if ((!omni && forward) || (omni && anyMove)) {
                packet.inputData.add(PlayerAuthInputData.SPRINTING)
                packet.inputData.add(PlayerAuthInputData.START_SPRINTING)
            }
        }
    }
}
