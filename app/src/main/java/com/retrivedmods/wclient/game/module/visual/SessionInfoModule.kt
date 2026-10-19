package com.retrivedmods.wclient.game.module.visual

import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import com.retrivedmods.wclient.overlay.hud.SessionInfoOverlay
import kotlinx.coroutines.*
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.sqrt

class SessionInfoModule : Module("SessionInfo", ModuleCategory.Visual) {

    private val showCps by boolValue("Show CPS", true)
    private val showBps by boolValue("Show BPS", true)
    private val showHits by boolValue("Show Hits", true)
    private val showTime by boolValue("Show Playtime", true)
    private val compactMode by boolValue("Compact Mode", false)
    private val bgOpacity by floatValue("BG Opacity", 0.8f, 0.2f..1.0f)

    private var scope: CoroutineScope? = null
    private val swingTimestamps = ConcurrentLinkedQueue<Long>()
    private var totalHits = 0
    private var sessionStartMs = System.currentTimeMillis()
    private var currentBps = 0f

    override fun onEnabled() {
        super.onEnabled()
        if (!isSessionCreated) return

        sessionStartMs = System.currentTimeMillis()
        SessionInfoOverlay.setOverlayEnabled(true)
        pushSettings()

        scope?.cancel()
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob()).apply {
            launch {
                while (isActive && isEnabled && isSessionCreated) {
                    val now = System.currentTimeMillis()
                    while (true) {
                        val head = swingTimestamps.peek() ?: break
                        if (now - head > 1000L) swingTimestamps.poll() else break
                    }
                    val elapsedSec = (now - sessionStartMs) / 1000L
                    pushSettings()
                    SessionInfoOverlay.updateStats(
                        cps = swingTimestamps.size,
                        bps = currentBps,
                        hits = totalHits,
                        sessionSeconds = elapsedSec
                    )
                    delay(100L)
                }
            }
        }
    }

    override fun onDisabled() {
        super.onDisabled()
        scope?.cancel()
        scope = null
        SessionInfoOverlay.setOverlayEnabled(false)
    }

    override fun onDisconnect(reason: String) {
        scope?.cancel()
        scope = null
        SessionInfoOverlay.setOverlayEnabled(false)
    }

    private fun pushSettings() {
        SessionInfoOverlay.updateConfig(
            showCps = showCps,
            showBps = showBps,
            showHits = showHits,
            showTime = showTime,
            compact = compactMode,
            bgAlpha = bgOpacity
        )
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || !isSessionCreated) return

        when (val packet = interceptablePacket.packet) {
            is AnimatePacket -> {
                if (interceptablePacket.isServerBound && packet.action == AnimatePacket.Action.SWING_ARM) {
                    swingTimestamps.add(System.currentTimeMillis())
                }
            }
            is InventoryTransactionPacket -> {
                if (interceptablePacket.isServerBound &&
                    packet.transactionType == InventoryTransactionType.ITEM_USE_ON_ENTITY &&
                    packet.actionType == 1
                ) {
                    totalHits++
                    swingTimestamps.add(System.currentTimeMillis())
                }
            }
            is PlayerAuthInputPacket -> {
                val mx = session.localPlayer.motionX
                val mz = session.localPlayer.motionZ
                currentBps = sqrt(mx * mx + mz * mz) * 20f
            }
        }
    }
}
