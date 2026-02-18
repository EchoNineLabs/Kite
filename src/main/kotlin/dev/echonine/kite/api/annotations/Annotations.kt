package dev.echonine.kite.api.annotations

/**
 * Configures paths of other scripts to import and include in the main script classpath.
 * Paths are relative to the script file's directory.
 *
 * This is a top-level script annotation.
 *
 * Example:
 * ```
 * @file:Import("extra.kite.kts")
 * ```
 */
@Repeatable
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class Import(vararg val paths: String)

/**
 * Compiler options that will be applied on script compilation
 */
@Repeatable
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class CompilerOptions(vararg val options: String)

/**
 * Configures a Maven-compatible repository to download [Dependency] from.
 *
 * This is a top-level script annotation.
 *
 * Example:
 * ```
 * @file:Repository("https://maven-central.storage-download.googleapis.com/maven2")
 * ```
 *
 * @see Dependency
 * @see Relocation
 */
@Repeatable
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.RUNTIME)
annotation class Repository(val repository: String)

/**
 * Configures a Maven-compatible dependency to download from any specified [Repository], and include in the script classpath.
 *
 * This is a top-level script annotation.
 *
 * Example:
 * ```
 * @file:Dependency("com.github.ben-manes.caffeine:caffeine:3.2.3")
 * ```
 *
 * @see Repository
 * @see Relocation
 */
@Repeatable
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.RUNTIME)
annotation class Dependency(val dependency: String)

/**
 * Configures a relocation rule to apply on a matching [Dependency].
 *
 * This is a top-level script annotation.
 *
 * @see Repository
 * @see Dependency
 */
@Repeatable
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.RUNTIME)
annotation class Relocation(val pattern: String, val newPattern: String)