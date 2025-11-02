package dev.echonine.kite.extensions

import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import net.kyori.adventure.text.minimessage.MiniMessage

fun ComponentLogger.infoRich(message: String) {
    this.info(MiniMessage.miniMessage().deserialize(message))
}

fun ComponentLogger.warnRich(message: String) {
    this.warn(MiniMessage.miniMessage().deserialize("<yellow>$message)"))
}

fun ComponentLogger.errorRich(message: String) {
    this.error(MiniMessage.miniMessage().deserialize("<red>$message"))
}

fun ComponentLogger.debugRich(message: String) {
    this.debug(MiniMessage.miniMessage().deserialize(message))
}

fun ComponentLogger.traceRich(message: String) {
    this.trace(MiniMessage.miniMessage().deserialize(message))
}