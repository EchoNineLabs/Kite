package dev.echonine.kite.util.extensions

import org.bukkit.Server

fun Server.syncCommands() {
    this.onlinePlayers.forEach {
        it.updateCommands()
    }
}