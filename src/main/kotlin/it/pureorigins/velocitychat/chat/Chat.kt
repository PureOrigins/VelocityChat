package it.pureorigins.velocitychat.chat

import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.event.player.PlayerChatEvent
import com.velocitypowered.api.event.player.ServerConnectedEvent
import it.pureorigins.velocitychat.VelocityChat
import it.pureorigins.velocityconfiguration.sendMessage
import it.pureorigins.velocityconfiguration.templateComponent
import it.pureotigins.velocityfriends.VelocityFriends
import kotlinx.serialization.Serializable
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.audience.MessageType
import net.kyori.adventure.text.Component

class Chat(private val plugin: VelocityChat, private val friends: VelocityFriends?, private val config: Config) {
    @Subscribe(order = PostOrder.LATE)
    fun onPlayerChat(event: PlayerChatEvent) = EventTask.async {
        val player = event.player
        player.currentServer.ifPresent {
            it.server.sendMessage(config.format?.templateComponent("player" to player, "message" to event.message), MessageType.CHAT)
        }
        event.result = PlayerChatEvent.ChatResult.denied()
    }
    
    @Subscribe
    fun onPlayerLogin(event: PostLoginEvent) = EventTask.async {
        val player = event.player
        friends?.getFriends(player)?.forEach {
            plugin.server.getPlayer(it).orElse(null)?.sendMessage(config.friendJoinFormat?.templateComponent("player" to player))
        }
    }
    
    @Subscribe
    fun onPlayerDisconnect(event: DisconnectEvent) = EventTask.async {
        if (event.loginStatus != DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN) return@async
        val player = event.player
        friends?.getFriends(player)?.forEach {
            plugin.server.getPlayer(it).orElse(null)?.sendMessage(config.friendQuitFormat?.templateComponent("player" to player))
        }
        player.currentServer.ifPresent { it.server.sendMessage(config.quitFormat?.templateComponent("player" to player), config.joinAndQuitMessageType) }
    }
    
    @Subscribe
    fun onPlayerJoinServer(event: ServerConnectedEvent) = EventTask.async {
        val player = event.player
        event.server.sendMessage(config.joinFormat?.templateComponent("player" to player), config.joinAndQuitMessageType)
        event.previousServer.ifPresent { it.sendMessage(config.quitFormat?.templateComponent("player" to player), config.joinAndQuitMessageType) }
    }
    
    @Serializable
    data class Config(
        val joinAndQuitMessageType: MessageType = MessageType.CHAT,
        val format: String? = "[{\"text\":\"\${player.username}\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}, {\"text\": \" » \", \"color\": \"white\"}, {\"text\": \"\${message}\"}]",
        val joinFormat: String? = "[{\"text\": \"[\", \"color\": \"gray\"}, {\"text\": \"+\", \"color\": \"green\"}, {\"text\": \"]\", \"color\": \"gray\"}, {\"text\":\"\${player.username}\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}]",
        val quitFormat: String? = "[{\"text\": \"[\", \"color\": \"gray\"}, {\"text\": \"-\", \"color\": \"red\"}, {\"text\": \"]\", \"color\": \"gray\"}, {\"text\":\"\${player.username}\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}]",
        val friendJoinFormat: String? = "[{\"text\": \"[\", \"color\": \"dark_aqua\"}, {\"text\": \"+\", \"color\": \"green\"}, {\"text\": \"]\", \"color\": \"dark_aqua\"}, {\"text\":\"\${player.username}\", \"color\": \"aqua\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}, {\"text\": \" is now online\", \"color\": \"dark_aqua\"}]",
        val friendQuitFormat: String? = "[{\"text\": \"[\", \"color\": \"dark_aqua\"}, {\"text\": \"-\", \"color\": \"red\"}, {\"text\": \"]\", \"color\": \"dark_aqua\"}, {\"text\":\"\${player.username}\", \"color\": \"aqua\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}, {\"text\": \" is now offline\", \"color\": \"dark_aqua\"}]"
    )
}

fun Audience.sendMessage(component: Component?, type: MessageType) {
    if (component != null) {
        sendMessage(component, type)
    }
}