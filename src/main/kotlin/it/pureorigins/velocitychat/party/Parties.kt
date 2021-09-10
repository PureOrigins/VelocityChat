package it.pureorigins.velocitychat.party

import com.mojang.brigadier.arguments.StringArgumentType.getString
import com.mojang.brigadier.arguments.StringArgumentType.word
import com.velocitypowered.api.event.EventTask.async
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.player.PlayerChatEvent
import com.velocitypowered.api.proxy.Player
import it.pureorigins.velocitychat.*
import it.pureorigins.velocityconfiguration.templateComponent
import kotlinx.serialization.Serializable
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import java.util.concurrent.TimeUnit

class Parties(
    private val plugin: VelocityChat,
    private val config: Config,
    private val parties: MutableMap<Player, Party> = mutableMapOf(),
    private val requests: MutableMap<Player, MutableSet<Party>> = mutableMapOf()
) : VelocityCommand {
    fun fromMember(player: Player) = parties[player]
    fun fromInvitedPlayer(player: Player) = requests[player] ?: emptySet()
    
    fun registerParty(party: Party): Party {
        parties[party.owner] = party
        return party
    }
    
    fun invite(sender: Player, player: Player) {
        val registeredParty = fromMember(sender)
        val notRegistered = registeredParty == null
        val party = registeredParty ?: Party(sender)
        when {
            party.owner != sender -> return sender.sendMessage(config.notAnOwner.templateComponent("player" to player, "owner" to party.owner))
            sender == player -> return sender.sendMessage(config.cannotInviteSelf.templateComponent())
            player in party.members -> return sender.sendMessage(config.alreadyInParty.templateComponent("player" to player))
            player in party.requests -> return sender.sendMessage(config.alreadyRequested.templateComponent("player" to player))
            else -> {
                if (notRegistered) {
                    registerParty(party)
                    sender.sendMessage(config.partyCreated.templateComponent())
                }
                party.invite(player)
                requests.putIfAbsent(player, mutableSetOf())
                requests[player]!! += party
                player.sendMessage(config.request.templateComponent("player" to sender))
                sender.sendMessage(config.requestSent.templateComponent("player" to player))
                if (config.inviteSound != null) {
                    player.playSound(Sound.sound(Key.key(config.inviteSound.key), Sound.Source.MASTER, config.inviteSound.volume, config.inviteSound.pitch), Sound.Emitter.self())
                }
                plugin.scheduleAfter(config.requestExpirationTime, TimeUnit.SECONDS) {
                    if (player in party.requests) {
                        cancelInvite(sender, player)
                    }
                }
            }
        }
    }
    
    private fun cancelInvite(sender: Player, player: Player) {
        val party = fromMember(sender)
        if (party != null) {
            party.cancelInvite(player)
            requests[player]!! -= party
            player.sendMessage(config.inviteExpired.templateComponent("player" to sender))
        }
    }
    
    fun accept(sender: Player, player: Player) {
        val party = fromMember(player)
        when {
            party == null || sender !in party.requests -> return sender.sendMessage(config.notInvited.templateComponent("player" to player))
            else -> {
                left(sender)
                sender.sendMessage(config.inviteAccepted.templateComponent("player" to player)) // sender won't see newMember message
                party.sendMessage(config.newMember.templateComponent("player" to sender))
                party.accept(sender)
                requests[sender]!! -= party
                parties[sender] = party
            }
        }
    }
    
    fun left(player: Player) {
        val party = fromMember(player) ?: return
        val wasOwner = party.owner == player
        party.remove(player)
        parties.remove(player)
        player.sendMessage(config.left.templateComponent())
        party.sendMessage(config.playerLeft.templateComponent("player" to player))
        if (wasOwner) party.sendMessage(config.newOwner.templateComponent("oldOwner" to player, "newOwner" to party.owner))
    }
    
    fun kick(sender: Player, player: Player) {
        val party = fromMember(sender)
        when {
            party == null -> return sender.sendMessage(config.notInParty.templateComponent())
            sender == player -> return leave(sender)
            party.owner != sender -> return sender.sendMessage(config.notAnOwner.templateComponent("player" to player, "owner" to party.owner))
            player !in party.members && player !in party.requests -> return sender.sendMessage(config.playerNotInParty.templateComponent("player" to player))
            player in party.requests -> {
                cancelInvite(sender, player)
                sender.sendMessage(config.requestCanceled.templateComponent("player" to player))
            }
            else -> {
                party.remove(player)
                parties.remove(player)
                player.sendMessage(config.kicked.templateComponent("player" to sender))
                party.sendMessage(config.memberKicked.templateComponent("player" to player, "sender" to sender))
            }
        }
    }
    
    fun leave(player: Player) {
        val party = fromMember(player) ?: return player.sendMessage(config.notInParty.templateComponent())
        val wasOwner = party.owner == player
        party.remove(player)
        parties.remove(player)
        player.sendMessage(config.left.templateComponent())
        party.sendMessage(config.playerLeft.templateComponent("player" to player))
        if (wasOwner) party.sendMessage(config.newOwner.templateComponent("oldOwner" to player, "newOwner" to party.owner))
    }
    
    @Subscribe
    fun onPlayerQuit(event: DisconnectEvent) = async {
        left(event.player)
    }
    
    @Subscribe
    fun onPlayerChat(event: PlayerChatEvent) = async {
        val message = event.message
        val player = event.player
        val party = fromMember(player)
        if (party == null) return@async
        event.result = if (config.useChatPrefixForPartyChat) {
            if (message.startsWith(config.chatPrefix)) {
                party.sendMessage(config.format.templateComponent("player" to player, "message" to message.substring(config.chatPrefix.length)))
                PlayerChatEvent.ChatResult.denied()
            } else {
                PlayerChatEvent.ChatResult.allowed()
            }
        } else {
            if (message.startsWith(config.chatPrefix)) {
                PlayerChatEvent.ChatResult.message(message.substring(config.chatPrefix.length))
            } else {
                party.sendMessage(config.format.templateComponent("player" to player, "message" to message))
                PlayerChatEvent.ChatResult.denied()
            }
        }
    }
    
    override val command get() = literal(config.commandName) {
        requires {
            it is Player && it.hasPermission("chat.party")
        }
        success {
            it.source.sendMessage(config.commandUsage.templateComponent())
        }
        then(inviteCommand)
        then(acceptCommand)
        then(leaveCommand)
        then(kickCommand)
        then(infoCommand)
    }
    
    val inviteCommand get() = literal(config.inviteCommandName) {
        requires {
            it.hasPermission("chat.party.invite")
        }
        success {
            it.source.sendMessage(config.inviteCommandUsage.templateComponent())
        }
        then(argument("player", word()) {
            suggests { context ->
                val player = context.source as? Player ?: return@suggests
                val party = fromMember(player)
                if (party == null) {
                    plugin.server.allPlayers.forEach {
                        if (it != player) {
                            suggest(it.username)
                        }
                    }
                } else {
                    plugin.server.allPlayers.forEach {
                        if (it !in party.members && it !in party.requests) {
                            suggest(it.username)
                        }
                    }
                }
            }
            success { context ->
                val playerName = getString(context, "player")
                val player = plugin.server.getPlayer(playerName).orElse(null)
                    ?: return@success context.source.sendMessage(config.invalidPlayer.templateComponent("player" to playerName))
                val sender = context.source as? Player ?: return@success
                invite(sender, player)
            }
        })
    }
    
    val acceptCommand get() = literal(config.acceptCommandName) {
        requires {
            it.hasPermission("chat.party.accept")
        }
        success {
            it.source.sendMessage(config.acceptCommandUsage.templateComponent())
        }
        then(argument("player", word()) {
            suggests { context ->
                val player = context.source as? Player ?: return@suggests
                requests[player]?.forEach {
                    suggest(it.owner.username)
                }
            }
            success { context ->
                val playerName = getString(context, "player")
                val player = plugin.server.getPlayer(playerName).orElse(null)
                    ?: return@success context.source.sendMessage(config.invalidPlayer.templateComponent("player" to playerName))
                val sender = context.source as? Player ?: return@success
                accept(sender, player)
            }
        })
    }
    
    val leaveCommand get() = literal(config.leaveCommandName) {
        requires {
            it.hasPermission("chat.party.leave")
        }
        success { context ->
            val sender = context.source as? Player ?: return@success
            leave(sender)
        }
    }
    
    val kickCommand get() = literal(config.kickCommandName) {
        requires {
            it.hasPermission("chat.party.kick")
        }
        success {
            it.source.sendMessage(config.kickCommandUsage.templateComponent())
        }
        then(argument("player", word()) {
            suggests { context ->
                val player = context.source as? Player ?: return@suggests
                val party = fromMember(player) ?: return@suggests
                if (party.owner != player) return@suggests
                party.members.forEach {
                    suggest(it.username)
                }
                party.requests.forEach {
                    suggest(it.username)
                }
            }
            success { context ->
                val playerName = getString(context, "player")
                val player = plugin.server.getPlayer(playerName).orElse(null)
                    ?: return@success context.source.sendMessage(config.invalidPlayer.templateComponent("player" to playerName))
                val sender = context.source as? Player ?: return@success
                kick(sender, player)
            }
        })
    }
    
    val infoCommand get() = literal(config.infoCommandName) {
        requires {
            it.hasPermission("chat.party.info")
        }
        success { context ->
            val sender = context.source as? Player ?: return@success
            val party = fromMember(sender)
            if (party != null) {
                sender.sendMessage(config.partyInfo.templateComponent("party" to party))
            } else {
                sender.sendMessage(config.notInParty.templateComponent())
            }
        }
    }
    
    @Serializable
    data class Config(
        val format: String = "[{\"text\": \"\${player.username}\", \"color\": \"aqua\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}, {\"text\": \" » \${message}\", \"color\": \"aqua\"}]",
        val chatPrefix: String = "!",
        val useChatPrefixForPartyChat: Boolean = false,
        val requestExpirationTime: Long = 60,
        val request: String = "\${player.username} invited you to his party.",
        val requestSent: String = "Request sent.",
        val partyCreated: String = "Party created.",
        val left: String = "You left the party.",
        val playerLeft: String = "\${player.username} left the party.",
        val newOwner: String = "The new owner is \${newOwner.username}.",
        val notAnOwner: String = "You need to be the owner of the party to perform this command.",
        val notInParty: String = "You are not in a party.",
        val notInvited: String = "\${player.username} did not invite you.",
        val requestCanceled: String = "Request cancelled.",
        val inviteExpired: String = "\${player.username} invite expired.",
        val inviteAccepted: String = "You joined \${player.username} party.",
        val newMember: String = "\${player.username} joined the party.",
        val memberKicked: String = "\${player.username} kicked from the party.",
        val kicked: String = "\${player.username} kicked you from the party.",
        val invalidPlayer: String = "\${player} is not online.",
        val playerNotInParty: String = "\${player.username} is not in the party.",
        val alreadyInParty: String = "\${player.username} is already in the party.",
        val alreadyRequested: String = "You have already invited \${player.username}.",
        val cannotInviteSelf: String = "You cannot invite yourself.",
        val partyInfo: String = "\${party}",
        val commandName: String = "party",
        val commandUsage: String = "Usage: /party <invite | accept | leave | kick | info>",
        val inviteCommandName: String = "invite",
        val inviteCommandUsage: String = "Usage: /party invite <player>",
        val acceptCommandName: String = "accept",
        val acceptCommandUsage: String = "Usage: /party accept <player>",
        val leaveCommandName: String = "leave",
        val kickCommandName: String = "kick",
        val kickCommandUsage: String = "Usage: /party kick <player>",
        val infoCommandName: String = "info",
        val inviteSound: Sound? = Sound()
    ) {
        @Serializable
        data class Sound(
            val key: String = "block.note_block.bell",
            val volume: Float = 1f,
            val pitch: Float = 1f
        )
    }
}
