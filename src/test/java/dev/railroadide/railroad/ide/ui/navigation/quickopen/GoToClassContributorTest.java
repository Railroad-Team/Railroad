package dev.railroadide.railroad.ide.ui.navigation.quickopen;

import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolKind;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndexer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GoToClassContributorTest {
    @TempDir
    public Path root;

    @Test
    public void typesListEveryTypeDeclarationButNotMembers() throws IOException {
        Path shapes = write("dev/Shapes.java", """
            package dev;

            public class Shapes {
                private int count;

                void draw() {
                }

                interface Shape {
                }

                enum Kind { ROUND }

                record Point(int x, int y) {
                }

                @interface Marker {
                }
            }
            """);
        JavaProjectSemanticIndex index = new JavaProjectSemanticIndexer().build(List.of(shapes));

        List<GoToClassContributor.TypeEntry> types = GoToClassContributor.types(index, () -> {
        }).stream().sorted(Comparator.comparing(GoToClassContributor.TypeEntry::qualifiedName)).toList();

        assertEquals(List.of("dev.Shapes", "dev.Shapes.Kind", "dev.Shapes.Marker", "dev.Shapes.Point",
            "dev.Shapes.Shape"), types.stream().map(GoToClassContributor.TypeEntry::qualifiedName).toList());
        assertEquals(List.of(JavaSymbolKind.CLASS, JavaSymbolKind.ENUM, JavaSymbolKind.ANNOTATION,
            JavaSymbolKind.RECORD, JavaSymbolKind.INTERFACE),
            types.stream().map(GoToClassContributor.TypeEntry::kind).toList());
        assertTrue(types.stream().allMatch(type -> type.file().equals(shapes.toAbsolutePath().normalize())));
    }

    @Test
    public void indexedOffsetsPointAtTheDeclaredName() throws IOException {
        String source = "package dev;\n\npublic final class Widget {\n}\n";
        JavaProjectSemanticIndex index = new JavaProjectSemanticIndexer().build(List.of(write("dev/Widget.java",
            source)));

        GoToClassContributor.TypeEntry widget = GoToClassContributor.types(index, () -> {
        }).getFirst();

        assertEquals(source.indexOf("Widget"), widget.nameOffset());
        assertEquals(source.indexOf("Widget"),
            GoToClassContributor.declarationOffset(source, "Widget", widget.nameOffset()));
    }

    @Test
    public void entriesShowTheirNameAndContainer() {
        var contributor = new GoToClassContributor();
        var nested = new GoToClassContributor.TypeEntry("Inner", "dev.Outer.Inner", JavaSymbolKind.CLASS,
            root.resolve("Outer.java"), -1);
        var unnamedPackage = new GoToClassContributor.TypeEntry("Main", "Main", JavaSymbolKind.CLASS,
            root.resolve("Main.java"), -1);

        assertEquals("Inner", contributor.name(nested));
        assertEquals("dev.Outer.Inner", contributor.path(nested));
        assertEquals("dev.Outer", contributor.detail(nested));
        assertEquals("", contributor.detail(unnamedPackage));
    }

    @Test
    public void declarationOffsetUsesTheIndexedOffsetWhileItStillDeclaresTheType() {
        String text = "class A { class Node {} }\nclass B { class Node {} }";
        int second = text.lastIndexOf("Node");

        assertEquals(second, GoToClassContributor.declarationOffset(text, "Node", second));
        assertEquals(text.indexOf("Node"), GoToClassContributor.declarationOffset(text, "Node", second + 1));
    }

    @Test
    public void declarationOffsetSearchesWhenTheIndexedOffsetIsStale() {
        String text = "// Target is documented here\nclass TargetHelper {}\npublic record Target(int x) {}";
        int declaration = text.indexOf("Target(int");

        assertEquals(declaration, GoToClassContributor.declarationOffset(text, "Target", 3));
        assertEquals(declaration, GoToClassContributor.declarationOffset(text, "Target", -1));
        assertEquals(declaration, GoToClassContributor.declarationOffset(text, "Target", 999));
    }

    @Test
    public void declarationOffsetFindsAnnotationTypes() {
        String text = "public @interface Marker {}";

        assertEquals(text.indexOf("Marker"), GoToClassContributor.declarationOffset(text, "Marker", -1));
    }

    @Test
    public void declarationOffsetIsMissingWithoutADeclaration() {
        assertEquals(-1, GoToClassContributor.declarationOffset("new Target();", "Target", -1));
    }

    private Path write(String relativePath, String source) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        return file;
    }
}
