package it.pureorigins.velocitychat.messages

import com.mojang.brigadier.arguments.StringArgumentType.*
import com.velocitypowered.api.proxy.Player
import it.pureorigins.velocitychat.*
import it.pureorigins.velocityconfiguration.sendMessage
import it.pureorigins.velocityconfiguration.templateComponent
import it.pureotigins.velocityfriends.VelocityFriends
import kotlinx.serialization.Serializable

class PrivateMessageCommand(private val plugin: VelocityChat, private val friends: VelocityFriends?, private val config: Config) : VelocityCommand {
    override val command get() = literal(config.commandName) {
        requires {
            it is Player && it.hasPermission("chat.msg")
        }
        success { context ->
            context.source.sendMessage(config.usage?.templateComponent())
        }
        then(playerArgument)
    }
    
    private val playerArgument get() = argument("player", string()) {
        suggests { context ->
            val player = context.source as? Player ?: return@suggests
            val blocked = if (friends != null) friends.getBlockedPlayers(player) + friends.getWhoBlockedPlayer(player) else emptySet()
            plugin.server.allPlayers.forEach {
                if (it.uniqueId !in blocked) suggest(it.username)
            }
        }
        success { context ->
            context.source.sendMessage(config.usage?.templateComponent())
        }
        then(messageArgument)
    }
    
    private val messageArgument get() = argument("message", greedyString()) {
        success { context ->
            val playerName = getString(context, "player")
            val player = plugin.server.getPlayer(playerName).orElse(null)
            val message = getString(context, "message")
            val sender = context.source as? Player ?: return@success
            if (player == null) {
                return@success sender.sendMessage(config.playerNotFound?.templateComponent("player" to playerName))
            }
            if (friends != null && friends.isBlocked(sender, player)) {
                return@success sender.sendMessage(config.blocked?.templateComponent("player" to player))
            }
            if (friends != null && friends.isBlocked(player, sender)) {
                return@success sender.sendMessage(config.playerBlocked?.templateComponent("player" to player))
            }
            player.sendMessage(config.message?.templateComponent("sender" to sender, "message" to message))
            context.source.sendMessage(config.messageSent?.templateComponent("player" to player, "message" to message))
        }
    }
    
    @Serializable
    data class Config(
        val commandName: String = "msg",
        val usage: String? = "[{\"text\": \"Usage: \", \"color\": \"dark_gray\"}, {\"text\": \"/msg <player> <message>\", \"color\": \"gray\"}]",
        val playerNotFound: String? = "[{\"text\": \"\${player}\", \"color\": \"gold\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}, {\"text\": \" is not online.\", \"color\": \"dark_gray\"}]",
        val blocked: String? = "[{\"text\": \"\${player.username}\", \"color\": \"gold\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}, {\"text\": \" blocked you.\", \"color\": \"dark_gray\"}]",
        val playerBlocked: String? = "[{\"text\": \"You blocked \", \"color\": \"dark_gray\"}, {\"text\": \"\${player.username}\", \"color\": \"gold\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}, {\"text\": \".\", \"color\": \"dark_gray\"}]",
        val messageSent: String? = "{\"text\": \"Message sent to \${player.username} » \${message}\", \"color\": \"gray\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"reply\"}}}",
        val message: String? = "{\"text\": \"Message received from \${sender.username} » \${message}\", \"color\": \"gray\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${sender.name} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"reply\"}}}"
    )
}
