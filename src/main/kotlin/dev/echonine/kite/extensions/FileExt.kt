package dev.echonine.kite.extensions

import java.io.File

val File.nameWithoutExtensions: String
    get() = this.name.substringBefore(".")
