package dev.echonine.kite.extensions

import org.bukkit.Server


fun Server.syncCommands() {
    this.onlinePlayers.forEach {
        it.updateCommands()
    }
}