package it.pureorigins.velocitychat.messages

import com.mojang.brigadier.arguments.StringArgumentType.*
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import it.pureorigins.velocitychat.*
import it.pureorigins.velocityconfiguration.templateComponent
import kotlinx.serialization.Serializable

class PrivateMessageCommand(private val server: ProxyServer, private val config: Config) : VelocityCommand {
    override val command get() = literal(config.name) {
        requires {
            it.hasPermission("chat.msg")
        }
        success { context ->
            context.source.sendMessage(config.usage.templateComponent())
        }
        then(playerArgument)
    }
    
    private val playerArgument get() = argument("player", string()) {
        suggests { _ ->
            server.allPlayers.forEach {
                val username = it.username
                if (username.startsWith(remaining, ignoreCase = true)) suggest(username)
            }
        }
        success { context ->
            context.source.sendMessage(config.usage.templateComponent())
        }
        then(messageArgument)
    }
    
    private val messageArgument get() = argument("message", greedyString()) {
        success { context ->
            val playerName = getString(context, "player")
            val player = server.getPlayer(playerName).orElse(null)
            val message = getString(context, "message")
            if (player == null) {
                context.source.sendMessage(config.invalidPlayer.templateComponent("player" to playerName))
                return@success
            }
            val sender = context.source as? Player
            player.sendMessage(config.message.templateComponent("sender" to sender, "message" to message))
            context.source.sendMessage(config.messageSent.templateComponent("player" to player, "message" to message))
        }
    }
    
    @Serializable
    data class Config(
        val name: String = "msg",
        val usage: String = "Usage: /msg <player> <message>",
        val invalidPlayer: String = "\${player} is not online.",
        val messageSent: String = "Message sent.",
        val message: String = "Message received from <#if sender??>\${sender.username}<#else>Server</#if>: \${message}"
    )
}
