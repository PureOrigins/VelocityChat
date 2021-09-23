package it.pureorigins.velocitychat.party

import com.mojang.brigadier.arguments.StringArgumentType.getString
import com.mojang.brigadier.arguments.StringArgumentType.word
import com.velocitypowered.api.event.EventTask.async
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.player.PlayerChatEvent
import com.velocitypowered.api.proxy.Player
import it.pureorigins.velocitychat.*
import it.pureorigins.velocityconfiguration.sendMessage
import it.pureorigins.velocityconfiguration.templateComponent
import it.pureotigins.velocityfriends.VelocityFriends
import kotlinx.serialization.Serializable
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import java.util.concurrent.TimeUnit

class Parties(
    private val plugin: VelocityChat,
    private val friends: VelocityFriends?,
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
            party.owner != sender -> return sender.sendMessage(config.invite.notAnOwner?.templateComponent("player" to player, "owner" to party.owner))
            sender == player -> return sender.sendMessage(config.invite.cannotInviteSelf?.templateComponent())
            friends != null && !friends.isFriend(sender, player) -> return sender.sendMessage(config.invite.notFriend?.templateComponent("player" to player))
            player in party.members -> return sender.sendMessage(config.invite.alreadyInParty?.templateComponent("player" to player))
            player in party.requests -> return sender.sendMessage(config.invite.alreadyRequested?.templateComponent("player" to player))
            else -> {
                if (notRegistered) {
                    registerParty(party)
                    sender.sendMessage(config.invite.partyCreated?.templateComponent())
                }
                party.invite(player)
                requests.putIfAbsent(player, mutableSetOf())
                requests[player]!! += party
                player.sendMessage(config.invite.request?.templateComponent("player" to sender))
                sender.sendMessage(config.invite.requestSent?.templateComponent("player" to player))
                if (config.invite.requestSound != null) {
                    player.playSound(Sound.sound(Key.key(config.invite.requestSound.key), Sound.Source.MASTER, config.invite.requestSound.volume, config.invite.requestSound.pitch), Sound.Emitter.self())
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
            player.sendMessage(config.invite.inviteExpired?.templateComponent("player" to sender))
        }
    }
    
    fun accept(sender: Player, player: Player) {
        val party = fromMember(player)
        when {
            party == null || sender !in party.requests -> return sender.sendMessage(config.accept.notInvited?.templateComponent("player" to player))
            else -> {
                left(sender)
                sender.sendMessage(config.accept.inviteAccepted?.templateComponent("player" to player)) // sender won't see newMember message
                party.sendMessage(config.accept.newMember?.templateComponent("player" to sender))
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
        player.sendMessage(config.leave.left?.templateComponent())
        party.sendMessage(config.leave.playerLeft?.templateComponent("player" to player))
        party.requests.forEach { it.sendMessage(config.invite.inviteExpired?.templateComponent("player" to player)) }
        if (wasOwner) party.sendMessage(config.leave.newOwner?.templateComponent("oldOwner" to player, "newOwner" to party.owner))
    }
    
    fun kick(sender: Player, player: Player) {
        val party = fromMember(sender)
        when {
            party == null -> return sender.sendMessage(config.kick.notInParty?.templateComponent())
            sender == player -> return leave(sender)
            party.owner != sender -> return sender.sendMessage(config.kick.notAnOwner?.templateComponent("player" to player, "owner" to party.owner))
            player !in party.members && player !in party.requests -> return sender.sendMessage(config.kick.playerNotInParty?.templateComponent("player" to player))
            player in party.requests -> {
                cancelInvite(sender, player)
                sender.sendMessage(config.kick.requestCanceled?.templateComponent("player" to player))
            }
            else -> {
                party.remove(player)
                parties.remove(player)
                player.sendMessage(config.kick.kicked?.templateComponent("player" to sender))
                party.sendMessage(config.kick.memberKicked?.templateComponent("player" to player, "sender" to sender))
            }
        }
    }
    
    fun leave(player: Player) {
        val party = fromMember(player) ?: return player.sendMessage(config.leave.notInParty?.templateComponent())
        val wasOwner = party.owner == player
        party.remove(player)
        parties.remove(player)
        player.sendMessage(config.leave.left?.templateComponent())
        party.sendMessage(config.leave.playerLeft?.templateComponent("player" to player))
        party.requests.forEach { it.sendMessage(config.invite.inviteExpired?.templateComponent("player" to player)) }
        if (wasOwner) party.sendMessage(config.leave.newOwner?.templateComponent("oldOwner" to player, "newOwner" to party.owner))
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
                party.sendMessage(config.chatFormat.templateComponent("player" to player, "message" to message.substring(config.chatPrefix.length)))
                PlayerChatEvent.ChatResult.denied()
            } else {
                PlayerChatEvent.ChatResult.allowed()
            }
        } else {
            if (message.startsWith(config.chatPrefix)) {
                PlayerChatEvent.ChatResult.message(message.substring(config.chatPrefix.length))
            } else {
                party.sendMessage(config.chatFormat.templateComponent("player" to player, "message" to message))
                PlayerChatEvent.ChatResult.denied()
            }
        }
    }
    
    override val command get() = literal(config.commandName) {
        requires {
            it is Player && it.hasPermission("chat.party")
        }
        success {
            it.source.sendMessage(config.commandUsage?.templateComponent())
        }
        then(inviteCommand)
        then(acceptCommand)
        then(leaveCommand)
        then(kickCommand)
        then(infoCommand)
    }
    
    private val inviteCommand get() = literal(config.invite.commandName) {
        requires {
            it.hasPermission("chat.party.invite")
        }
        success {
            it.source.sendMessage(config.invite.commandUsage?.templateComponent())
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
                    ?: return@success context.source.sendMessage(config.invite.playerNotFound?.templateComponent("player" to playerName))
                val sender = context.source as? Player ?: return@success
                invite(sender, player)
            }
        })
    }
    
    private val acceptCommand get() = literal(config.accept.commandName) {
        requires {
            it.hasPermission("chat.party.accept")
        }
        success {
            it.source.sendMessage(config.accept.commandUsage?.templateComponent())
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
                    ?: return@success context.source.sendMessage(config.accept.playerNotFound?.templateComponent("player" to playerName))
                val sender = context.source as? Player ?: return@success
                accept(sender, player)
            }
        })
    }
    
    private val leaveCommand get() = literal(config.leave.commandName) {
        requires {
            it.hasPermission("chat.party.leave")
        }
        success { context ->
            val sender = context.source as? Player ?: return@success
            leave(sender)
        }
    }
    
    private val kickCommand get() = literal(config.kick.commandName) {
        requires {
            it.hasPermission("chat.party.kick")
        }
        success {
            it.source.sendMessage(config.kick.commandUsage?.templateComponent())
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
                    ?: return@success context.source.sendMessage(config.kick.playerNotFound?.templateComponent("player" to playerName))
                val sender = context.source as? Player ?: return@success
                kick(sender, player)
            }
        })
    }
    
    private val infoCommand get() = literal(config.info.commandName) {
        requires {
            it.hasPermission("chat.party.info")
        }
        success { context ->
            val sender = context.source as? Player ?: return@success
            val party = fromMember(sender)
            if (party != null) {
                sender.sendMessage(config.info.info?.templateComponent("party" to party))
            } else {
                sender.sendMessage(config.info.notInParty?.templateComponent())
            }
        }
    }
    
    @Serializable
    data class Config(
        val chatFormat: String = "[{\"text\": \"\${player.username}\", \"color\": \"aqua\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}, {\"text\": \" » \${message}\", \"color\": \"aqua\"}]",
        val chatPrefix: String = "!",
        val useChatPrefixForPartyChat: Boolean = false,
        val requestExpirationTime: Long = 60,
        val commandName: String = "party",
        val commandUsage: String? = "[{\"text\": \"Usage: \", \"color\": \"dark_gray\"}, {\"text\": \"/party <invite | accept | leave | remove | info>\", \"color\": \"gray\"}]",
        val invite: Invite = Invite(),
        val accept: Accept = Accept(),
        val kick: Kick = Kick(),
        val leave: Leave = Leave(),
        val info: Info = Info()
    ) {
        @Serializable
        data class Sound(
            val key: String,
            val volume: Float,
            val pitch: Float
        )
        
        @Serializable
        data class Invite(
            val commandName: String = "invite",
            val commandUsage: String? = "[{\"text\": \"Usage: \", \"color\": \"dark_gray\"}, {\"text\": \"/party invite <player>\", \"color\": \"gray\"}]",
            val playerNotFound: String? = "[{\"text\": \"\${player}\", \"color\": \"gold\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}, {\"text\": \" is not online.\", \"color\": \"dark_gray\"}]",
            val cannotInviteSelf: String? = "{\"text\": \"You cannot invite yourself.\", \"color\": \"dark_gray\"}",
            val notFriend: String? = "[{\"text\": \"Only friends can invite \", \"color\": \"dark_gray\"}, {\"text\": \"\${player.username}\", \"color\": \"gold\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}, {\"text\": \".\", \"color\": \"dark_gray\"}]",
            val notAnOwner: String? = "{\"text\":\"You must be the party owner to invite players. \",\"color\":\"dark_gray\"},{\"text\":\"Ask \",\"italic\":true,\"color\":\"dark_gray\"},{\"text\": \"\${owner.username}\", \"color\": \"gold\", \"italic\": true \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${owner.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}},{\"text\":\" to invite players.\",\"italic\":true,\"color\":\"dark_gray\"}",
            val alreadyInParty: String? = "[{\"text\": \"\${player.username}\", \"color\": \"gold\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}, {\"text\": \" is already in the party.\", \"color\": \"dark_gray\"}]",
            val alreadyRequested: String? = "[{\"text\": \"You have already invited \", \"color\": \"dark_gray\"}, {\"text\": \"\${player.username}\", \"color\": \"gold\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}, {\"text\": \".\", \"color\": \"dark_gray\"}]",
            val partyCreated: String? = "[{\"text\": \"Party created. \", \"color\": \"gray\"}, {\"text\": \"To send a message in the global chat start your message with (!).\", \"color\": \"gray\", \"italic\": true}]",
            val request: String? = "[{\"text\": \"\${player.username}\", \"color\": \"gold\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}, {\"text\": \" invited you to his party. \", \"color\": \"gray\"}, {\"text\": \"[ACCEPT]\", \"color\": \"green\", \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/party accept \${player.username}\"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"accept\"}}]",
            val requestSent: String? = "[{\"text\": \"Party invite sent to\", \"color\": \"gray\"}, {\"text\": \"\${player.username}\", \"color\": \"gold\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}, {\"text\": \".\", \"color\": \"gray\"}]",
            val inviteExpired: String? = "[{\"text\": \"Party invite of \", \"color\": \"dark_gray\"}, {\"text\": \"\${player.username}\", \"color\": \"gold\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}, {\"text\": \" expired.\", \"color\": \"dark_gray\"}]",
            val requestSound: Sound? = Sound("block.note_block.bell", 1f, 1f)
        )
        
        @Serializable
        data class Accept(
            val commandName: String = "accept",
            val commandUsage: String? = "[{\"text\": \"Usage: \", \"color\": \"dark_gray\"}, {\"text\": \"/party accept <player>\", \"color\": \"gray\"}]",
            val playerNotFound: String? = "[{\"text\": \"\${player}\", \"color\": \"gold\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}, {\"text\": \" is not online.\", \"color\": \"dark_gray\"}]",
            val notInvited: String? = "[{\"text\": \"\${player.username}\", \"color\": \"gold\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}, {\"text\": \" has not invited you.\", \"color\": \"dark_gray\"}]",
            val inviteAccepted: String? = "[{\"text\": \"You are joining \", \"color\": \"gray\"}, {\"text\": \"\${player.username}\", \"color\": \"gold\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}, {\"text\": \" party.\", \"color\": \"gray\"}]",
            val newMember: String? = "[{\"text\":\"[\",\"color\":\"dark_aqua\"},{\"text\":\"+\",\"color\":\"green\"},{\"text\":\"] \",\"color\":\"dark_aqua\"},{\"text\": \"\${player.username}\", \"color\": \"aqua\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}, {\"text\": \" joined the party.\", \"color\": \"dark_aqua\"}]"
        )
        
        @Serializable
        data class Kick(
            val commandName: String = "kick",
            val commandUsage: String? = "[{\"text\": \"Usage: \", \"color\": \"dark_gray\"}, {\"text\": \"/party kick <player>\", \"color\": \"gray\"}]",
            val playerNotFound: String? = "[{\"text\": \"\${player}\", \"color\": \"gold\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}, {\"text\": \" is not online.\", \"color\": \"dark_gray\"}]",
            val notInParty: String? = "{\"text\":\"You are not in a party.\", \"color\":\"dark_gray\"}",
            val notAnOwner: String? = "{\"text\":\"You must be the party owner to kick players. \",\"color\":\"dark_gray\"},{\"text\":\"Ask \",\"italic\":true,\"color\":\"dark_gray\"},{\"text\": \"\${owner.username}\", \"color\": \"gold\", \"italic\": true \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${owner.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}},{\"text\":\" to kick players.\",\"italic\":true,\"color\":\"dark_gray\"}",
            val playerNotInParty: String? = "[{\"text\": \"\${player.username}\", \"color\": \"gold\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}, {\"text\": \" is not in the party.\", \"color\": \"dark_gray\"}]",
            val requestCanceled: String? = "{\"text\":\"Request canceled.\", \"color\":\"gray\"}",
            val kicked: String? = "[{\"text\": \"\${player.username}\", \"color\": \"gold\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}, {\"text\": \" kicked you from the party.\", \"color\": \"dark_gray\"}]",
            val memberKicked: String? = "[{\"text\":\"[\",\"color\":\"dark_aqua\"},{\"text\":\"-\",\"color\":\"red\"},{\"text\":\"] \",\"color\":\"dark_aqua\"},{\"text\": \"\${player.username}\", \"color\": \"aqua\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}, {\"text\": \" kicked from \", \"color\": \"dark_aqua\"}, {\"text\": \"\${sender.username}\", \"color\": \"aqua\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${sender.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}, {\"text\": \".\", \"color\": \"dark_aqua\"}]"
        )
        
        @Serializable
        data class Leave(
            val commandName: String = "leave",
            val notInParty: String? = "{\"text\":\"You are not in a party.\", \"color\":\"dark_gray\"}",
            val left: String? = "{\"text\": \"You left the party.\", \"color\": \"gray\"}",
            val playerLeft: String? = "[{\"text\":\"[\",\"color\":\"dark_aqua\"},{\"text\":\"-\",\"color\":\"red\"},{\"text\":\"] \",\"color\":\"dark_aqua\"},{\"text\": \"\${player.username}\", \"color\": \"aqua\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${player.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}, {\"text\": \" left the party.\", \"color\": \"dark_aqua\"}]",
            val newOwner: String? = "[{\"text\":\"The new party owner is \",\"color\":\"dark_aqua\"},{\"text\": \"\${newOwner.username}\", \"color\": \"aqua\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${newOwner.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}},{\"text\":\".\",\"color\":\"dark_aqua\"}]"
        )
        
        @Serializable
        data class Info(
            val commandName: String = "info",
            val notInParty: String? = "{\"text\":\"You are not in a party.\", \"color\":\"gray\"}",
            val info: String? = "[{\"text\": \"Party of \", \"color\": \"gray\"}, {\"text\": \"\${party.owner.username}\", \"color\": \"gold\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${party.owner.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}},{\"text\":\"\\n\"},{\"text\": \"\${party.members?size} Member<#if (party.members?size > 1)>s</#if>: \", \"color\": \"gray\"}, <#list party.members as member>{\"text\": \"\${member.username}\", \"color\": \"gold\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${member.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}<#sep>,{\"text\":\", \", \"color\":\"gray\"},</#list><#if (party.requests?size > 0)>,{\"text\":\"\\n\"},{\"text\": \"Pending requests: \", \"color\": \"gray\"}, <#list party.requests as request>{\"text\": \"\${request.username}\", \"color\": \"gold\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/msg \${request.username} \"}, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"send message\"}}<#sep>,{\"text\":\", \", \"color\":\"gray\"},</#list></#if>]"
        )
    }
}
