package com.retrivedmods.wclient.game.module.combat

import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import com.retrivedmods.wclient.game.entity.Player
import com.retrivedmods.wclient.game.friend.FriendManager
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket

/**
 * Port of Gato Client (PC) Criticals.
 *
 * Packet modes walk the reported Y position down a little every tick while a target
 * is in range and the player is on the ground, so the server sees a "falling" hit.
 * Jump mode makes the client jump for real instead.
 *
 * Differences from PC: there is no liquid check (the relay does not track blocks), and
 * Geyser Strict uses "the client attacked in the last 500 ms" instead of reading the
 * left mouse button.
 */
class CriticalsModule : Module("Criticals", ModuleCategory.Combat) {

    enum class Mode { NUKKIT, GEYSER, GEYSER_STRICT, JUMP, PACKET }

    private var mode by enumValue("Mode", Mode.PACKET, Mode::class.java)

    private val range = 4.5f
    private var dip = 0f
    private var lastAttackTime = 0L

    override fun onDisabled() {
        super.onDisabled()
        dip = 0f
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || !interceptablePacket.isServerBound) return

        when (val packet = interceptablePacket.packet) {
            is InventoryTransactionPacket -> {
                if (packet.transactionType == InventoryTransactionType.ITEM_USE_ON_ENTITY && packet.actionType == 1) {
                    lastAttackTime = System.currentTimeMillis()
                }
            }

            is PlayerAuthInputPacket -> onTick(packet)
        }
    }

    private fun onTick(packet: PlayerAuthInputPacket) {
        val onGround = packet.inputData.contains(PlayerAuthInputData.VERTICAL_COLLISION) &&
                (packet.delta?.y ?: 0f) <= 0f

        if (!hasTarget() || !onGround) {
            dip = 0f
            return
        }

        if (mode == Mode.JUMP) {
            val delta = packet.delta ?: Vector3f.ZERO
            session.clientBound(SetEntityMotionPacket().apply {
                runtimeEntityId = session.localPlayer.runtimeEntityId
                motion = Vector3f.from(delta.x, 0.42f, delta.z)
            })
            return
        }

        if (mode == Mode.GEYSER_STRICT && System.currentTimeMillis() - lastAttackTime > 500) {
            dip = 0f
            return
        }

        val (step, maxDip) = when (mode) {
            Mode.NUKKIT -> 0.05f to 0.3f
            Mode.GEYSER, Mode.GEYSER_STRICT -> 0.008f to 0.12f
            else -> 0.012f to 0.24f
        }

        packet.position = packet.position.sub(0f, dip, 0f)
        dip += step
        if (dip >= maxDip) dip = 0f
    }

    private fun hasTarget(): Boolean {
        val localPlayer = session.localPlayer
        return session.level.entityMap.values.any {
            it is Player && it !== localPlayer &&
                    !FriendManager.isFriend(it.uuid) &&
                    it.distance(localPlayer) <= range
        }
    }
}
