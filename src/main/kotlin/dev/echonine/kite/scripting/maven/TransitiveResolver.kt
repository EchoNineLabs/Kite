package dev.echonine.kite.scripting.maven

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import revxrsal.zapper.Dependency
import revxrsal.zapper.repository.Repository
import java.util.concurrent.ConcurrentHashMap

class TransitiveResolver(
    val repositories: List<Repository>,
    val isRecursive: Boolean = true
) {

    fun resolve(dependency: Dependency): List<Dependency> = runBlocking(Dispatchers.IO) {
        val visited = ConcurrentHashMap.newKeySet<String>()
        // Resolving and returning the result.
        return@runBlocking resolve(dependency, visited)
    }

    private suspend fun resolve(dependency: Dependency, visited: MutableSet<String>): List<Dependency> {
        for (repository in repositories) {
            try {
                val resolvedDependencies = mutableListOf<Dependency>()
                val resolvedRepositories = LinkedHashSet<Repository>(repositories)
                // Opening the pom.xml stream asynchronously.
                val stream = withContext(Dispatchers.IO) {
                    repository.resolvePom(dependency).openStream()
                }
                stream.use {
                    // Parsing the POM file.
                    val pom = Jsoup.parse(stream, "UTF-8", "", Parser.xmlParser())
                    // Appending repositories to the list.
                    if (isRecursive) {
                        pom.select("repositories > repository > url").forEach { url ->
                            resolvedRepositories += Repository.maven(url.text())
                        }
                    }
                    // Collecting all dependencies declared within this POM.
                    pom.select("dependencies > dependency").forEach { resolvedDependency ->
                        if (resolvedDependency.selectFirst("optional")?.text() == "true")
                            return@forEach
                        val scope = resolvedDependency.selectFirst("scope")?.text()
                        if (scope == null || scope != "compile")
                            return@forEach
                        // Getting the 'groupId' property. Skipping if non-existent or blank.
                        val groupId = resolvedDependency.selectFirst("groupId")?.text()
                            ?.replace($$"${project.groupId}", dependency.groupId) ?: return@forEach
                        // Getting the 'artifactId' property. Skipping if non-existent or blank.
                        val artifactId = resolvedDependency.selectFirst("artifactId")?.text() ?: return@forEach
                        // Skipping 'kotlin-stdlib' as we're already providing an up-to-date version.
                        if (artifactId.startsWith("kotlin-stdlib") == true)
                            return@forEach
                        // Getting the 'version' property. Skipping if non-existent or blank.
                        val version = resolvedDependency.selectFirst("version")?.text()
                            ?.replace($$"${project.version}", dependency.version)
                            ?.takeIf { it.isNotBlank() } ?: return@forEach
                        // Adding to the set, skipping if already there.
                        if (visited.add("$groupId:$artifactId:$version") == true)
                            resolvedDependencies += Dependency(groupId, artifactId, version)
                    }
                }
                // Recursively resolving all collected dependencies outside the original stream.
                if (isRecursive) {
                    val newResolver = TransitiveResolver(resolvedRepositories.toList(), true)
                    resolvedDependencies += coroutineScope {
                        resolvedDependencies.toList().map {
                            async { newResolver.resolve(it, visited) }
                        }.awaitAll().flatten()
                    }
                }
                return resolvedDependencies
            } catch (_: java.io.FileNotFoundException) {
                // Probably not worth logging that.
            }
        }
        return listOf()
    }
}