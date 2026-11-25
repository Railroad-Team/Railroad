package dev.railroadide.railroad.ide.projectexplorer;

import dev.railroadide.railroad.ide.projectexplorer.ProjectStructure.Role;
import dev.railroadide.railroadplugin.dto.RailroadContentRoot;
import dev.railroadide.railroadplugin.dto.RailroadSourceDirectory;
import dev.railroadide.railroadplugin.dto.impl.BasicRailroadContentRoot;
import dev.railroadide.railroadplugin.dto.impl.BasicRailroadSourceDirectory;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectStructureTest {
    private final Path root = Path.of("project").toAbsolutePath();

    @Test
    public void infersSourceSetsFromSourceDirectoriesInsideTheirModule() {
        Path library = root.resolve("library");
        ProjectStructure structure = new ProjectStructure.Builder()
            .module(root)
            .module(library)
            .sourceDirectory(library, library.resolve("src/main/java"), Role.SOURCES)
            .sourceDirectory(library, library.resolve("src/main/resources"), Role.RESOURCES)
            .sourceDirectory(library, library.resolve("src/test/java"), Role.TEST_SOURCES)
            .sourceDirectory(library, library.resolve("src/test/resources"), Role.TEST_RESOURCES)
            .excluded(library.resolve("build"))
            .build();

        assertEquals(Role.MODULE, structure.roleOf(root));
        assertEquals(Role.MODULE, structure.roleOf(library));
        assertEquals(Role.SOURCE_SET, structure.roleOf(library.resolve("src/main")));
        assertEquals(Role.SOURCE_SET, structure.roleOf(library.resolve("src/test")));
        assertEquals(Role.SOURCES, structure.roleOf(library.resolve("src/main/java")));
        assertEquals(Role.RESOURCES, structure.roleOf(library.resolve("src/main/resources")));
        assertEquals(Role.TEST_SOURCES, structure.roleOf(library.resolve("src/test/java")));
        assertEquals(Role.TEST_RESOURCES, structure.roleOf(library.resolve("src/test/resources")));
        assertEquals(Role.EXCLUDED, structure.roleOf(library.resolve("build")));
        assertNull(structure.roleOf(library.resolve("src")));
        assertNull(structure.roleOf(library.resolve("src/main/java/dev")));
        assertNull(structure.roleOf(null));
    }

    @Test
    public void doesNotTreatModulesOrGeneratedDirectoryParentsAsSourceSets() {
        Path generated = root.resolve("build/generated/sources/annotationProcessor/java/main");
        ProjectStructure structure = new ProjectStructure.Builder()
            .module(root)
            .sourceDirectory(root, root.resolve("src"), Role.SOURCES)
            .sourceDirectory(root, generated, Role.GENERATED_SOURCES)
            .excluded(root.resolve("build"))
            .build();

        assertEquals(Role.MODULE, structure.roleOf(root));
        assertEquals(Role.SOURCES, structure.roleOf(root.resolve("src")));
        assertEquals(Role.GENERATED_SOURCES, structure.roleOf(generated));
        assertNull(structure.roleOf(generated.getParent()));
    }

    @Test
    public void modulesTakePrecedenceOverSourceSetsAndLookupsAreNormalized() {
        Path nested = root.resolve("src/main");
        ProjectStructure structure = new ProjectStructure.Builder()
            .module(root)
            .module(nested)
            .sourceDirectory(root, nested.resolve("java"), Role.SOURCES)
            .build();

        assertEquals(Role.MODULE, structure.roleOf(nested));
        assertEquals(Role.SOURCES, structure.roleOf(root.resolve("src/test/../main/java")));
    }

    @Test
    public void gradleModulesPreferResourcesAndRecogniseTestSourceSets() {
        Path module = root.resolve("common");
        File mainJava = module.resolve("src/main/java").toFile();
        File mainResources = module.resolve("src/main/resources").toFile();
        File testJava = module.resolve("qa/custom/java").toFile();
        File testResources = module.resolve("qa/custom/resources").toFile();
        File generated = module.resolve("build/generated/sources/annotationProcessor/java/main").toFile();
        ProjectStructure structure = new ProjectStructure.Builder()
            .gradleModule(root, List.of())
            .gradleModule(module, List.of(
                contentRoot(module, "main", List.of(mainJava, generated),
                    List.of(mainResources)),
                contentRoot(module, "test", List.of(testJava), List.of(testResources))))
            .build();

        assertEquals(Role.MODULE, structure.roleOf(module));
        assertEquals(Role.SOURCES, structure.roleOf(mainJava.toPath()));
        assertEquals(Role.RESOURCES, structure.roleOf(mainResources.toPath()));
        assertEquals(Role.TEST_SOURCES, structure.roleOf(testJava.toPath()));
        assertEquals(Role.TEST_RESOURCES, structure.roleOf(testResources.toPath()));
        assertEquals(Role.GENERATED_SOURCES, structure.roleOf(generated.toPath()));
        assertEquals(Role.SOURCE_SET, structure.roleOf(module.resolve("qa/custom")));
        assertEquals(Role.EXCLUDED, structure.roleOf(module.resolve("build")));
        assertEquals(Role.EXCLUDED, structure.roleOf(root.resolve("build")));
        assertEquals(Role.EXCLUDED, structure.roleOf(root.resolve(".gradle")));
    }

    private static RailroadContentRoot contentRoot(
        Path module,
        String name,
        List<File> java,
        List<File> resources
    ) {
        boolean test = name.equals("test");
        return new BasicRailroadContentRoot(module.toFile(), name, List.of(), List.of(), null,
            test ? List.of() : directories(java, "source"), test ? directories(java, "testSource") : List.of(),
            test ? List.of() : directories(resources, "resource"),
            test ? directories(resources, "testResource") : List.of(),
            Set.of(module.resolve("build").toFile()));
    }

    private static List<RailroadSourceDirectory> directories(List<File> files, String type) {
        return files.stream()
            .<RailroadSourceDirectory>map(file -> new BasicRailroadSourceDirectory(file.getPath().contains("build"),
                file, type))
            .toList();
    }

    @Test
    public void conventionalLayoutMarksStandardSourceSets() {
        ProjectStructure structure = ProjectStructure.conventional(root);

        assertEquals(Role.MODULE, structure.roleOf(root));
        assertEquals(Role.SOURCE_SET, structure.roleOf(root.resolve("src/main")));
        assertEquals(Role.SOURCES, structure.roleOf(root.resolve("src/main/java")));
        assertEquals(Role.TEST_RESOURCES, structure.roleOf(root.resolve("src/test/resources")));
    }
}
