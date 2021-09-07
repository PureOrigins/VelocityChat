package it.pureorigins.velocitychat.party

import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.text.Component

data class Party(
    var owner: Player,
    val members: MutableSet<Player> = mutableSetOf(owner),
    val requests: MutableSet<Player> = mutableSetOf()
) {
    fun invite(player: Player) {
        check(player !in members) { "$player is already a member" }
        check(player !in requests) { "$player is already invited" }
        requests += player
    }
    
    fun cancelInvite(player: Player) {
        check(player in requests) { "$player was not invited" }
        requests -= player
    }
    
    fun accept(player: Player) {
        check(player in requests) { "$player is not invited" }
        requests -= player
        members += player
    }
    
    fun remove(player: Player) {
        check(player in members) { "$player is not a member of the party" }
        members -= player
        if (members.isNotEmpty() && owner == player) {
            owner = members.random()
        }
    }
}

fun Party.sendMessage(message: Component) {
    members.forEach {
        it.sendMessage(message)
    }
}
