package me.arasple.mc.trchat.module.display

import me.arasple.mc.trchat.api.nms.NMS
import me.arasple.mc.trchat.module.conf.file.Settings
import me.arasple.mc.trchat.module.display.channel.Channel
import me.arasple.mc.trchat.module.display.channel.PrivateChannel
import me.arasple.mc.trchat.util.color.CustomColor
import me.arasple.mc.trchat.util.color.MessageColors
import me.arasple.mc.trchat.util.data
import org.bukkit.entity.Player
import taboolib.expansion.getDataContainer
import taboolib.library.reflex.Reflex.Companion.getProperty
import taboolib.module.chat.ComponentText
import taboolib.module.chat.Components
import taboolib.module.nms.Packet
import taboolib.module.nms.sendPacket
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * @author ItsFlicker
 * @since 2021/12/11 22:44
 */
class ChatSession(val player: Player) {

    val receivedMessages = mutableListOf<ChatMessage>()
    var lastChannel: Channel? = null
    var lastPublicMessage = ""
    var lastPrivateMessage = ""
    var lastPrivateTo = ""
    var cancelChat = false
    @JvmField
    var channel: String? = null

    init {
        val stored = player.data.channel
        fun join(string: String): Boolean {
            val id = Channel.channels.keys.firstOrNull { string.equals(it, ignoreCase = true) } ?: return false
            val channel = Channel.channels[id]!!
            setChannel(channel)
            channel.listeners.add(player.name)
            channel.events.join(player)
            return true
        }
        if (stored == null || !join(stored)) {
            join(Settings.defaultChannel)
        }
    }

    fun getColor(default: CustomColor?): CustomColor {
        val forces = MessageColors.getForceColors(player)
        return if (forces.isNotEmpty()) {
            CustomColor.get(forces[0])
        } else {
            val selectedColor = player.getDataContainer()["color"].takeIf { it != "null" }
            if (selectedColor != null && player.hasPermission(MessageColors.COLOR_PERMISSION_NODE + selectedColor)) {
                CustomColor.get(selectedColor)
            } else {
                default ?: CustomColor(CustomColor.ColorType.NORMAL, "§f")
            }
        }
    }

    fun getChannel(): Channel? {
        channel ?: return null
        return Channel.channels[channel]
    }

    fun setChannel(channel: Channel?) {
        this.channel = channel?.id
        if (channel != null && channel !is PrivateChannel) {
            player.data.setChannel(channel)
        }
    }

    fun addMessage(packet: Packet) {
        try {
            val component = packet.getComponent()
            receivedMessages += ChatMessage(
                packet.source,
                component?.toPlainText()?.replace("\\s".toRegex(), "")?.takeLast(48)
            )
            if (receivedMessages.size > 100) {
                receivedMessages.removeFirstOrNull()
            }
        } catch (_: Throwable) {
        }
    }

    fun removeMessage(message: String) {
        receivedMessages.removeIf { it.message == message }
    }

    fun releaseMessage() {
        val messages = ArrayList(receivedMessages)
        receivedMessages.clear()
        repeat(100) { player.sendMessage("") }
        messages.forEach { player.sendPacket(it.packet) }
    }

    companion object {

        @JvmField
        val sessions = ConcurrentHashMap<UUID, ChatSession>()

        fun getSession(player: Player): ChatSession {
            return sessions.computeIfAbsent(player.uniqueId) { ChatSession(player) }
        }

        fun removeSession(player: Player) {
            Channel.quit(player, hint = false)
            sessions -= player.uniqueId
        }

        private fun Packet.getComponent(): ComponentText? {
            return when (name) {
                "ClientboundSystemChatPacket" -> {
                    val iChat = source.getProperty<Any>("content", findToParent = false) ?: return null
                    Components.parseRaw(NMS.instance.rawMessageFromCraftChatMessage(iChat))
                }
                "PacketPlayOutChat" -> {
                    val iChat = read<Any>("a") ?: return null
                    Components.parseRaw(NMS.instance.rawMessageFromCraftChatMessage(iChat))
                }
                else -> error("Unsupported packet $name")
            }
        }

        data class ChatMessage(val packet: Any, val message: String?)
    }
}