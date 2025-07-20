package io.github.railroad.plugin;

import coursierapi.Dependency;
import coursierapi.Fetch;
import coursierapi.MavenRepository;
import coursierapi.Repository;
import coursierapi.error.CoursierError;
import io.github.railroad.railroadpluginapi.deps.MavenDeps;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;

/**
 * A custom class loader for loading plugin JAR files and their dependencies.
 * This class extends {@link URLClassLoader} to allow dynamic loading of classes from JAR files.
 */
public class PluginClassLoader extends URLClassLoader {
    public PluginClassLoader(@NotNull Path jarPath, @NotNull MavenDeps deps) throws IOException {
        super(new URL[]{jarPath.toUri().toURL()}, PluginManager.class.getClassLoader());

        addDependenciesToClasspath(deps);
    }

    private void addDependenciesToClasspath(@NotNull MavenDeps deps) throws MalformedURLException {
        List<MavenRepository> repositories = deps.repositories().stream()
                .map(mavenRepo -> MavenRepository.of(mavenRepo.url()))
                .toList();
        Fetch fetch = Fetch.create()
                .addRepositories(repositories.toArray(new MavenRepository[0]))
                .addRepositories(Repository.central())
                .addDependencies(
                        deps.artifacts().stream()
                                .map(mavenDep -> Dependency.of(
                                        mavenDep.groupId(),
                                        mavenDep.artifactId(),
                                        mavenDep.version()))
                                .toArray(Dependency[]::new));
        try {
            List<File> jars = fetch.fetch();
            for (File jar : jars) {
                addURL(jar.toURI().toURL());
            }
        } catch (CoursierError error) {
            throw new RuntimeException(error);
        }
    }
}
