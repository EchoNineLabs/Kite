package dev.echonine.kite.api.annotations

/**
 * Import other script(s)
 */
@Target(AnnotationTarget.FILE)
@Repeatable
@Retention(AnnotationRetention.SOURCE)
annotation class Import(vararg val paths: String)

/**
 * Compiler options that will be applied on script compilation
 */
@Target(AnnotationTarget.FILE)
@Repeatable
@Retention(AnnotationRetention.SOURCE)
annotation class CompilerOptions(vararg val options: String)
