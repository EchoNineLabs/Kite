package dev.echonine.kite.util.extensions

import java.io.File

val File.nameWithoutExtensions: String
    get() = this.name.substringBefore(".")
