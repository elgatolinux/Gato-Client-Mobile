package com.retrivedmods.wclient.game.module.combat

import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import com.retrivedmods.wclient.game.ModuleManager
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket

/**
 * Port of GatoClient (PC) RotCycle module adapted for Mobile:
 * Tapping this module (or its floating Shortcut button on screen) cycles the
 * active rotation of GatoAuraX, +999aura and/or GatoAura without opening the ClickGUI.
 * Also supports an optional automatic timed rotation cycle during combat.
 */
class RotCycleModule : Module("RotCycle", ModuleCategory.Combat) {

    enum class TargetAura {
        ALL, GATO_AURA_X, PLUS_999_AURA, GATO_AURA
    }

    private val targetAura by enumValue("Aura", TargetAura.ALL, TargetAura::class.java)
    private val autoCycle by boolValue("Auto Cycle", false)
    private val cycleEveryTicks by intValue("Cycle Ticks", 40, 5..200)

    private var tickCounter = 0

    override fun onEnabled() {
        if (!autoCycle) {
            cycleSelectedAuras()
            isEnabled = false
            return
        }
        super.onEnabled()
        tickCounter = 0
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || !autoCycle || !isSessionCreated) return
        val packet = interceptablePacket.packet
        if (packet is PlayerAuthInputPacket && interceptablePacket.isServerBound) {
            tickCounter++
            if (tickCounter >= cycleEveryTicks) {
                tickCounter = 0
                cycleSelectedAuras()
            }
        }
    }

    fun cycleSelectedAuras() {
        val gatoX = ModuleManager.modules.filterIsInstance<GatoAuraXModule>().firstOrNull()
        val plus999 = ModuleManager.modules.filterIsInstance<Plus999AuraModule>().firstOrNull()
        val gato = ModuleManager.modules.filterIsInstance<GatoAuraModule>().firstOrNull()

        val doX = (targetAura == TargetAura.ALL || targetAura == TargetAura.GATO_AURA_X) && gatoX != null
        val do999 = (targetAura == TargetAura.ALL || targetAura == TargetAura.PLUS_999_AURA) && plus999 != null
        val doGato = (targetAura == TargetAura.ALL || targetAura == TargetAura.GATO_AURA) && gato != null

        val parts = ArrayList<String>()
        if (doX) parts.add("§fGatoAuraX: §a${gatoX!!.cycleRotMode()}")
        if (do999) parts.add("§f+999aura: §a${plus999!!.cycleRotMode()}")
        if (doGato) parts.add("§fGatoAura: §a${gato!!.cycleRotMode()}")

        if (parts.isNotEmpty() && isSessionCreated) {
            session.displayClientMessage("§l§d[RotCycle] §r" + parts.joinToString(" §7-> "))
        }
    }
}
