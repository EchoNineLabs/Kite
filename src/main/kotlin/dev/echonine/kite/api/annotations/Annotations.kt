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

@Target(AnnotationTarget.FILE)
@Repeatable
@Retention(AnnotationRetention.RUNTIME)
annotation class Dependency(val dependency: String)

@Target(AnnotationTarget.FILE)
@Repeatable
@Retention(AnnotationRetention.RUNTIME)
annotation class Repository(val repository: String)

@Target(AnnotationTarget.FILE)
@Repeatable
@Retention(AnnotationRetention.RUNTIME)
annotation class Relocation(val pattern: String, val newPattern: String)