package dev.echonine.kite;

import com.google.gson.Gson;
import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

// Downloading libraries directly from Maven Central may be considered a violation of their Terms of Service.
// Usage of PaperMC repository is highly discouraged and this leaves Google's mirror as the most reliable option.
@SuppressWarnings("UnstableApiUsage")
public final class PluginLibrariesLoader implements io.papermc.paper.plugin.loader.PluginLoader {

    @Override
    public void classloader(final @NotNull PluginClasspathBuilder classpathBuilder) throws IllegalStateException {
        final MavenLibraryResolver resolver = new MavenLibraryResolver();
        // Parsing the file.
        try (final InputStream in = getClass().getResourceAsStream("/paper-libraries.json")) {
            final PluginLibraries libraries = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), PluginLibraries.class);
            // Adding repositories to the maven library resolver.
            libraries.asRepositories().forEach(resolver::addRepository);
            // Adding dependencies to the maven library resolver.
            libraries.asDependencies().forEach(resolver::addDependency);
            // Adding library resolver to the classpath builder.
            classpathBuilder.addLibrary(resolver);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private record PluginLibraries(Map<String, String> repositories, List<String> dependencies) {

        public Stream<RemoteRepository> asRepositories() {
            return repositories.entrySet().stream().map(entry -> {
                try {
                    final String MAVEN_CENTRAL_DEFAULT_MIRROR = (String) MavenLibraryResolver.class.getField("MAVEN_CENTRAL_DEFAULT_MIRROR").get(null);
                    // Replacing Maven Central repository with a pre-configured mirror.
                    // See: https://docs.papermc.io/paper/dev/getting-started/paper-plugins/#loaders
                    if (entry.getValue().contains("maven.org") == true || entry.getValue().contains("maven.apache.org") == true) {
                        return new RemoteRepository.Builder(entry.getKey(), "default", MAVEN_CENTRAL_DEFAULT_MIRROR).build();
                    }
                    return new RemoteRepository.Builder(entry.getKey(), "default", entry.getValue()).build();
                } catch (final NoSuchFieldError | NoSuchFieldException | IllegalAccessException e) {
                    return new RemoteRepository.Builder(entry.getKey(), "default", "https://maven-central.storage-download.googleapis.com/maven2").build();
                }
            });
        }

        public Stream<Dependency> asDependencies() {
            return dependencies.stream().map(value -> new Dependency(new DefaultArtifact(value), null));
        }
    }
}