package it.pureorigins.velocitychat

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.Dependency
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import it.pureorigins.velocitychat.chat.Chat
import it.pureorigins.velocitychat.messages.PrivateMessageCommand
import it.pureorigins.velocitychat.party.Parties
import it.pureorigins.velocityconfiguration.json
import it.pureorigins.velocityconfiguration.readFileAs
import it.pureotigins.velocityfriends.VelocityFriends
import kotlinx.serialization.Serializable
import org.slf4j.Logger
import java.nio.file.Path


@Plugin(id = "velocity-chat", name = "Chat", version = "1.0.0", url = "https://github.com/PureOrigins/VelocityChat", description = "Chat utilities", dependencies = [Dependency(id = "velocity-language-kotlin"), Dependency(id = "velocity-configuration"), Dependency(id = "velocity-friends", optional = true)], authors = ["AgeOfWar"])
class VelocityChat @Inject constructor(
    val server: ProxyServer,
    val logger: Logger,
    @DataDirectory private val dataDirectory: Path
) {
    private val commands get() = server.commandManager
    private val events get() = server.eventManager
    
    @Subscribe
    fun onInit(event: ProxyInitializeEvent) {
        val (msg, chat, party) = json.readFileAs(dataDirectory.resolve("velocity_chat.json"), Config())
        if (party.invite.requestSound != null) logger.warn("party.inviteSound feature is not supported yet")
        val friends = server.pluginManager.getPlugin("velocity-friends").orElse(null)?.instance?.orElse(null) as? VelocityFriends
        val parties = Parties(this, friends, party)
        val chats = Chat(this, friends, chat)
        events.register(this, parties)
        events.register(this, chats)
        commands.register(PrivateMessageCommand(this, friends, msg))
        commands.register(parties)
    }
    
    @Serializable
    data class Config(
        val msg: PrivateMessageCommand.Config = PrivateMessageCommand.Config(),
        val chat: Chat.Config = Chat.Config(),
        val party: Parties.Config = Parties.Config()
    )
}