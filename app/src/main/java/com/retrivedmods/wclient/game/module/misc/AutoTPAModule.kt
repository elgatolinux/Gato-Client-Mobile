package com.retrivedmods.wclient.game.module.misc

import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import com.retrivedmods.wclient.game.entity.Player
import com.retrivedmods.wclient.game.friend.FriendManager
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket

/**
 * Port of Gato Client (PC) AutoTPA: sends /tpa (you go to them) or /tpahere (they come
 * to you) every `Delay` seconds, to the nearest player or to a fixed name.
 */
class AutoTPAModule : Module("AutoTPA", ModuleCategory.Misc) {

    enum class Mode { TPA, TPAHERE }
    enum class Target { NEAREST, NAME }

    private var mode by enumValue("Mode", Mode.TPA, Mode::class.java)
    private var target by enumValue("Target", Target.NEAREST, Target::class.java)
    private var playerName by stringValue("Player", "", null)
    private var delay by floatValue("Delay", 5f, 0.5f..60f)

    private var tickCounter = 0
    private var warnedNoTarget = false

    override fun onEnabled() {
        super.onEnabled()
        tickCounter = (delay * 20).toInt() // first request goes out on the next tick
        warnedNoTarget = false
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || !interceptablePacket.isServerBound) return
        if (interceptablePacket.packet !is PlayerAuthInputPacket) return

        if (tickCounter < (delay * 20).toInt()) {
            tickCounter++
            return
        }
        tickCounter = 0

        val name = resolveTargetName()
        if (name.isEmpty()) {
            if (!warnedNoTarget) {
                warnedNoTarget = true
                val why = if (target == Target.NAME) "escribe el nombre" else "sin jugadores cerca"
                session.displayClientMessage("§cAutoTPA: no hay target ($why)")
            }
            return
        }

        val command = if (mode == Mode.TPA) "/tpa" else "/tpahere"
        session.sendCommand("$command $name")
        warnedNoTarget = false
    }

    private fun resolveTargetName(): String {
        if (target == Target.NAME) return playerName.trim().trimStart('§')

        val localPlayer = session.localPlayer
        val nearest = session.level.entityMap.values
            .filterIsInstance<Player>()
            .filter { it !== localPlayer && !FriendManager.isFriend(it.uuid) }
            .minByOrNull { it.distance(localPlayer) }
            ?: return ""

        // The name can carry color codes (§x); keep only the visible name.
        return nearest.username.replace(Regex("§."), "")
    }
}
