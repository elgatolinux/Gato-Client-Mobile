package com.retrivedmods.wclient.game

import com.retrivedmods.wrelay.listener.WRelayPacketListener
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket

interface ComposedPacketHandler : WRelayPacketListener {

    fun beforePacketBound(packet: BedrockPacket, isServerBound: Boolean): Boolean

    fun afterPacketBound(packet: BedrockPacket) {}

    override fun beforeClientBound(packet: BedrockPacket): Boolean {
        return beforePacketBound(packet, false)
    }

    override fun beforeServerBound(packet: BedrockPacket): Boolean {
        return beforePacketBound(packet, true)
    }

    override fun afterClientBound(packet: BedrockPacket) {
        afterPacketBound(packet)
    }

    override fun afterServerBound(packet: BedrockPacket) {
        afterPacketBound(packet)
    }

}