package dev.echonine.kite.extensions

fun String.withoutExtensions(): String {
    val firstDotIndex = this.indexOf('.')
    return if (firstDotIndex != -1) {
        this.substring(0, firstDotIndex)
    } else {
        this
    }
}
