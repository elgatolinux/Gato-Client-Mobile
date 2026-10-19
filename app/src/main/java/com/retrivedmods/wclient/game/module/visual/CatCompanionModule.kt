package com.retrivedmods.wclient.game.module.visual

import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import com.retrivedmods.wclient.overlay.hud.CatCompanionOverlay
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket

class CatCompanionModule : Module("CatCompanion", ModuleCategory.Visual) {

    enum class CompanionMode {
        DVD_BOUNCER,
        PVP_BUDDY
    }

    private val mode by enumValue("Mode", CompanionMode.DVD_BOUNCER, CompanionMode::class.java)
    private val bounceSpeed by floatValue("Speed", 3.5f, 1.0f..10.0f)

    override fun onEnabled() {
        super.onEnabled()
        if (!isSessionCreated) return
        CatCompanionOverlay.updateConfig(mode, bounceSpeed)
        CatCompanionOverlay.setOverlayEnabled(true)
    }

    override fun onDisabled() {
        super.onDisabled()
        CatCompanionOverlay.setOverlayEnabled(false)
    }

    override fun onDisconnect(reason: String) {
        CatCompanionOverlay.setOverlayEnabled(false)
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled) return
        CatCompanionOverlay.updateConfig(mode, bounceSpeed)

        val packet = interceptablePacket.packet
        if (packet is InventoryTransactionPacket &&
            packet.transactionType == InventoryTransactionType.ITEM_USE_ON_ENTITY &&
            packet.actionType == 1
        ) {
            CatCompanionOverlay.notifyAttack()
        } else if (packet is AnimatePacket &&
            interceptablePacket.isServerBound &&
            packet.action == AnimatePacket.Action.SWING_ARM
        ) {
            CatCompanionOverlay.notifyAttack()
        }
    }
}
