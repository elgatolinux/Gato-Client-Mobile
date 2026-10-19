package com.retrivedmods.wclient.game.module.visual

import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import com.retrivedmods.wclient.overlay.hud.NotificationsOverlay

class NotificationsModule : Module("NotificationsHUD", ModuleCategory.Visual, defaultEnabled = true) {

    private val durationSeconds by floatValue("Duration (s)", 2.2f, 1.0f..5.0f)

    override fun onEnabled() {
        super.onEnabled()
        NotificationsOverlay.durationMs = (durationSeconds * 1000f).toLong()
        if (isSessionCreated) {
            NotificationsOverlay.setOverlayEnabled(true)
        }
    }

    override fun onDisabled() {
        super.onDisabled()
        NotificationsOverlay.setOverlayEnabled(false)
    }

    override fun onDisconnect(reason: String) {
        NotificationsOverlay.setOverlayEnabled(false)
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (isEnabled) {
            NotificationsOverlay.durationMs = (durationSeconds * 1000f).toLong()
        }
    }
}
