package com.retrivedmods.wclient.game

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket

data class InterceptablePacket(
    val packet: BedrockPacket,
    // true = sent by the game client towards the server, false = coming from the server
    val isServerBound: Boolean = false
) {

    var isIntercepted = false
        private set

    fun intercept() {
        isIntercepted = true
    }

}
