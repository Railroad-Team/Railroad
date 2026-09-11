package dev.railroadide.railroad.ide.projectexplorer;

import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class PathTreeItemTest {
    @TempDir
    private Path project;

    @Test
    public void compactsPackagesAndRevealsFilesAndIntermediateDirectories() throws IOException {
        Path file = createFile("src/main/java/dev/railroadide/railroad/RailroadLauncher.java");
        var root = new PathTreeItem(new PathItem(project));
        var packageItem = (PathTreeItem) PathTreeItem.find(root, file.getParent());

        assertEquals("dev.railroadide.railroad", packageItem.getDisplayName());
        assertEquals(file.getParent(), packageItem.getValue().getPath());
        assertSame(packageItem, PathTreeItem.find(root, project.resolve("src/main/java/dev")));
        assertSame(packageItem, PathTreeItem.find(root, project.resolve("src/main/java/dev/railroadide")));
        assertEquals(file, PathTreeItem.find(root, file).getValue().getPath());
        assertEquals("RailroadLauncher.java", ((PathTreeItem) PathTreeItem.find(root, file)).getDisplayName());
        assertNull(PathTreeItem.find(root, project.resolve("outside.java")));
    }

    @Test
    public void keepsSourceFoldersResourcesAndBranchingPackagesSeparate() throws IOException {
        Path first = createFile("src/main/java/dev/railroadide/railroad/AppResources.java");
        createFile("src/main/java/dev/other/Other.java");
        Path resource = createFile("src/main/resources/assets/railroad/lang/en_us.lang");
        var root = new PathTreeItem(new PathItem(project));

        var dev = (PathTreeItem) PathTreeItem.find(root, project.resolve("src/main/java/dev"));
        assertEquals("dev", dev.getDisplayName());
        assertEquals(2, dev.getChildren().size());
        assertEquals("railroadide.railroad",
            ((PathTreeItem) PathTreeItem.find(root, first.getParent())).getDisplayName());
        assertEquals("java", ((PathTreeItem) dev.getParent()).getDisplayName());
        TreeItem<PathItem> language = PathTreeItem.find(root, resource.getParent());
        assertEquals("lang", ((PathTreeItem) language).getDisplayName());
        assertEquals("railroad", ((PathTreeItem) language.getParent()).getDisplayName());
    }

    @Test
    public void doesNotHideFilesOrInvalidPackageNames() throws IOException {
        createFile("src/test/java/dev/example/Test.java");
        createFile("src/test/java/dev/package-info.java");
        createFile("src/test/java/invalid-name/example/Test.java");
        var root = new PathTreeItem(new PathItem(project.resolve("src/test/java")));

        assertEquals("dev", ((PathTreeItem) root.getChildren().getFirst()).getDisplayName());
        assertEquals("invalid-name", ((PathTreeItem) root.getChildren().get(1)).getDisplayName());
    }

    @Test
    public void filesystemChangesSplitAndRejoinPackagesWithoutReplacingUnrelatedRows() throws IOException {
        Path file = createFile("src/main/java/dev/railroadide/railroad/AppResources.java");
        Path other = createFile("src/main/java/org/example/Other.java");
        var root = new PathTreeItem(new PathItem(project.resolve("src/main/java")));
        TreeItem<PathItem> otherItem = PathTreeItem.find(root, other.getParent());
        otherItem.setExpanded(true);
        otherItem.getValue().setCut(true);
        assertEquals("dev.railroadide.railroad",
            ((PathTreeItem) PathTreeItem.find(root, file.getParent())).getDisplayName());

        Path sibling = createFile("src/main/java/dev/another/Sibling.java");
        root.refresh(sibling.getParent());
        var dev = (PathTreeItem) PathTreeItem.find(root, project.resolve("src/main/java/dev"));
        assertEquals("dev", dev.getDisplayName());
        assertNotNull(PathTreeItem.find(root, sibling));
        assertNotNull(PathTreeItem.find(root, file));
        assertSame(otherItem, PathTreeItem.find(root, other.getParent()));
        assertTrue(otherItem.isExpanded());
        assertTrue(otherItem.getValue().isCut());

        Files.delete(sibling);
        Files.delete(sibling.getParent());
        root.refresh(sibling.getParent());
        assertEquals("dev.railroadide.railroad",
            ((PathTreeItem) PathTreeItem.find(root, file.getParent())).getDisplayName());
        assertNull(PathTreeItem.find(root, sibling));
    }

    @Test
    public void refreshesFilesAtTheEndOfACompactPackage() throws IOException {
        Path file = createFile("src/main/java/dev/example/First.java");
        var root = new PathTreeItem(new PathItem(project.resolve("src/main/java")));
        assertNotNull(PathTreeItem.find(root, file));
        Path added = createFile("src/main/java/dev/example/Second.java");
        root.refresh(added);
        assertNotNull(PathTreeItem.find(root, added));
        Files.delete(file);
        root.refresh(file);
        assertNull(PathTreeItem.find(root, file));
        assertNotNull(PathTreeItem.find(root, added));
    }

    @Test
    public void disabledCompactionKeepsEachPackageDirectoryAfterFilesystemChanges() throws IOException {
        Path file = createFile("src/main/java/dev/railroadide/railroad/First.java");
        var root = new PathTreeItem(new PathItem(project), false);
        TreeItem<PathItem> fileItem = PathTreeItem.find(root, file);
        TreeItem<PathItem> railroad = fileItem.getParent();
        TreeItem<PathItem> railroadide = railroad.getParent();
        TreeItem<PathItem> dev = railroadide.getParent();
        assertEquals("railroad", ((PathTreeItem) railroad).getDisplayName());
        assertEquals("railroadide", ((PathTreeItem) railroadide).getDisplayName());
        assertEquals("dev", ((PathTreeItem) dev).getDisplayName());

        Path added = createFile("src/main/java/dev/railroadide/railroad/Second.java");
        root.refresh(added);
        assertSame(railroad, PathTreeItem.find(root, added).getParent());
        Files.delete(file);
        root.refresh(file);
        assertNull(PathTreeItem.find(root, file));
        assertSame(dev, PathTreeItem.find(root, dev.getValue().getPath()));
        assertEquals("dev", ((PathTreeItem) dev).getDisplayName());
    }

    @Test
    public void filteredTreeCompactsWithoutLoadingNonmatchingFiles() throws IOException {
        Path file = createFile("src/main/java/dev/example/Match.java");
        createFile("src/main/java/dev/example/Other.java");
        var root = PathTreeItem.filtered(project.resolve("src/main/java"));
        var dev = PathTreeItem.filtered(root.getValue().getPath().resolve("dev"));
        var example = PathTreeItem.filtered(file.getParent());
        root.getChildren().add(dev);
        dev.getChildren().add(example);
        example.getChildren().add(PathTreeItem.filtered(file));
        PathTreeItem.compactFilteredPackages(root, false);
        assertEquals("dev", dev.getDisplayName());
        assertSame(example, dev.getChildren().getFirst());
        assertEquals(1, example.getChildren().size());
        assertEquals(file, example.getChildren().getFirst().getValue().getPath());
        PathTreeItem.compactFilteredPackages(root, true);

        assertEquals("dev.example", dev.getDisplayName());
        assertEquals(1, dev.getChildren().size());
        assertEquals(file, dev.getChildren().getFirst().getValue().getPath());
        assertSame(dev, dev.getChildren().getFirst().getParent());
    }

    private Path createFile(String relative) throws IOException {
        Path path = project.resolve(relative);
        Files.createDirectories(path.getParent());
        return Files.createFile(path);
    }
}
