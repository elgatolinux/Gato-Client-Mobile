package com.retrivedmods.wclient.game.module.misc

import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import org.cloudburstmc.protocol.bedrock.packet.TextPacket

/**
 * Port of Gato Client (PC) Chat (ChatSuffix): decorates the chat messages you send
 * with green text, random tags, the client name or small-caps text. Commands (/...)
 * are left untouched.
 */
class ChatSuffixModule : Module("ChatSuffix", ModuleCategory.Misc) {

    private var greenText by boolValue("Green Text", false)
    private var suffix by boolValue("Suffix", false)
    private var fancyText by boolValue("Fancy Text", false)
    private var prefixTag by boolValue("Prefix Tag", false)
    private var prefixLength by intValue("Prefix Length", 8, 1..32)
    private var prefixUpper by boolValue("Prefix Upper", true)
    private var prefixLower by boolValue("Prefix Lower", true)
    private var prefixNums by boolValue("Prefix Nums", false)
    private var suffixTag by boolValue("Suffix Tag", false)
    private var suffixLength by intValue("Suffix Length", 8, 1..32)
    private var suffixUpper by boolValue("Suffix Upper", true)
    private var suffixLower by boolValue("Suffix Lower", true)
    private var suffixNums by boolValue("Suffix Nums", true)
    private var fakeName by boolValue("Fake Name", false)
    private var fakeNameValue by stringValue("Fake Name Value", "", null)
    private var customClientName by stringValue("Custom Client Name", "", null)

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || !interceptablePacket.isServerBound) return
        val packet = interceptablePacket.packet as? TextPacket ?: return
        if (packet.type != TextPacket.Type.CHAT) return

        val message = packet.message ?: return
        if (message.startsWith("/")) return

        val result = StringBuilder()
        if (greenText) result.append("> ")
        if (prefixTag) {
            val tag = randomTag(prefixLength, prefixUpper, prefixLower, prefixNums)
            if (tag.isNotEmpty()) result.append(tag).append(" | ")
        }
        result.append(message)
        if (suffix) {
            val client = customClientName.ifEmpty { CLIENT_NAME }
            if (!result.contains(client)) result.append(" | ").append(client)
        }
        if (suffixTag) {
            val tag = randomTag(suffixLength, suffixUpper, suffixLower, suffixNums)
            if (tag.isNotEmpty()) result.append(" | ").append(tag)
        }

        var finalMessage = result.toString()
        if (fancyText) finalMessage = toFancy(finalMessage)

        packet.message = finalMessage
        if (fakeName && fakeNameValue.isNotEmpty()) packet.sourceName = fakeNameValue
    }

    private fun randomTag(length: Int, upper: Boolean, lower: Boolean, nums: Boolean): String {
        val chars = buildString {
            if (upper) append("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
            if (lower) append("abcdefghijklmnopqrstuvwxyz")
            if (nums) append("0123456789")
        }
        if (chars.isEmpty()) return ""
        return (1..length.coerceIn(1, 32)).map { chars.random() }.joinToString("")
    }

    private fun toFancy(text: String): String = buildString {
        for (c in text) append(FANCY[c.uppercaseChar()] ?: c)
    }

    private companion object {
        const val CLIENT_NAME = "Gato Client"

        val FANCY = mapOf(
            'A' to 'ᴀ', 'B' to 'ʙ', 'C' to 'ᴄ', 'D' to 'ᴅ', 'E' to 'ᴇ', 'F' to 'ꜰ', 'G' to 'ɢ',
            'H' to 'ʜ', 'I' to 'ɪ', 'J' to 'ᴊ', 'K' to 'ᴋ', 'L' to 'ʟ', 'M' to 'ᴍ', 'N' to 'ɴ',
            'O' to 'ᴏ', 'P' to 'ᴘ', 'Q' to 'ǫ', 'R' to 'ʀ', 'S' to 'ꜱ', 'T' to 'ᴛ', 'U' to 'ᴜ',
            'V' to 'ᴠ', 'W' to 'ᴡ', 'X' to 'x', 'Y' to 'ʏ', 'Z' to 'ᴢ',
            '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄', '5' to '₅',
            '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉', '0' to '₀'
        )
    }
}
