package dev.echonine.kite.extensions

import kotlin.script.experimental.api.SourceCode

fun SourceCode.Location.toRich(): String {
    // Convert the location to a MiniMessage rich text format
    val start = this.start
    val end = this.end
    return when {
        start != null && end != null -> "<white>line ${start.line}, col ${start.col} to line ${end.line}, col ${end.col}</white>"
        start != null -> "<white>line ${start.line}, col ${start.col}</white>"
        else -> "<white>unknown location</white>"
    }
}