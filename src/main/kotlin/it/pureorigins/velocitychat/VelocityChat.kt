package it.pureorigins.velocitychat

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.Dependency
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import it.pureorigins.velocitychat.messages.PrivateMessageCommand
import it.pureorigins.velocitychat.party.Parties
import it.pureorigins.velocityconfiguration.json
import it.pureorigins.velocityconfiguration.readFileAs
import kotlinx.serialization.Serializable
import java.nio.file.Path


@Plugin(id = "velocity-chat", name = "Chat", version = "1.0.0", url = "https://github.com/PureOrigins/VelocityChat",
    description = "Chat utilities", dependencies = [Dependency(id = "velocity-language-kotlin"), Dependency(id = "velocity-configuration")], authors = ["AgeOfWar", "ekardnamm"])
class VelocityChat @Inject constructor(
    val server: ProxyServer,
    @DataDirectory private val dataDirectory: Path
) {
    val commands get() = server.commandManager
    val events get() = server.eventManager
    val scheduler get() = server.scheduler
    
    @Subscribe
    fun onInit(event: ProxyInitializeEvent) {
        val (msg, party) = json.readFileAs(dataDirectory.resolve("velocity_chat.json"), Config())
        val parties = Parties(this, party)
        events.register(this, parties)
        commands.register(PrivateMessageCommand(server, msg))
        commands.register(parties)
    }
    
    @Serializable
    data class Config(
        val msg: PrivateMessageCommand.Config = PrivateMessageCommand.Config(),
        val party: Parties.Config = Parties.Config()
    )
}