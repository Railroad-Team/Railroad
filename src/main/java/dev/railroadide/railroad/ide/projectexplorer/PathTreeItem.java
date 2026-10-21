package dev.railroadide.railroad.ide.projectexplorer;

import dev.railroadide.railroad.Railroad;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import javax.lang.model.SourceVersion;

/**
 * A filesystem tree item that loads directory children on first access.
 */
public class PathTreeItem extends TreeItem<PathItem> {
    private final Path firstPath;
    private final boolean compactMiddlePackages;
    private boolean isLeaf = false;
    private boolean isFirstTimeChildren = true;
    private boolean isFirstTimeLeft = true;

    /**
     * Creates a tree item whose children will be loaded lazily.
     *
     * @param pathItem filesystem tree item
     */
    public PathTreeItem(PathItem pathItem) {
        this(pathItem, true);
    }

    /**
     * Creates a lazy tree item with the selected package presentation for its subtree.
     *
     * @param pathItem filesystem tree item
     * @param compactMiddlePackages whether single-child package chains share one row
     */
    public PathTreeItem(PathItem pathItem, boolean compactMiddlePackages) {
        super(pathItem);
        firstPath = pathItem.getPath();
        this.compactMiddlePackages = compactMiddlePackages;
    }

    /**
     * Creates an initially empty item for a filtered tree without scanning the filesystem.
     *
     * @param path filesystem path represented by the item
     * @return tree item whose children can be populated with filtered results
     */
    public static PathTreeItem filtered(Path path) {
        var item = new PathTreeItem(new PathItem(path));
        item.isFirstTimeChildren = false;
        return item;
    }

    /**
     * Returns the dotted package label, while the value retains the actual operation target.
     *
     * @return dotted path from the first represented directory, or the full path for a filesystem root
     */
    public String getDisplayName() {
        Path path = getValue().getPath();
        return firstPath.getParent() == null
            ? path.toString()
            : firstPath.getParent().relativize(path).toString().replace(path.getFileSystem().getSeparator(), ".");
    }

    /**
     * Checks whether a path is one of the directories represented by this row.
     *
     * @param path path to check, or {@code null}
     * @return whether the normalized path lies within this row's compacted directory chain
     */
    public boolean represents(Path path) {
        if (path == null)
            return false;
        Path target = path.toAbsolutePath().normalize();
        return target.startsWith(firstPath.toAbsolutePath().normalize()) &&
            getValue().getPath().toAbsolutePath().normalize().startsWith(target);
    }

    /**
     * Reloads the affected loaded directory, retaining unrelated rows and their state.
     *
     * @param changedPath path of the filesystem entry that changed
     */
    public void refresh(Path changedPath) {
        if (!areChildrenLoaded())
            return;
        Path directory = changedPath.getParent();
        boolean reload = getValue().getPath().equals(directory) || getLoadedChildren().stream()
            .anyMatch(child -> child instanceof PathTreeItem item && item.represents(directory));
        if (reload) {
            var replacement = buildChildren(this);
            for (int index = 0; index < replacement.size(); index++) {
                var candidate = (PathTreeItem) replacement.get(index);
                for (TreeItem<PathItem> child : getLoadedChildren()) {
                    if (child instanceof PathTreeItem existing && existing.firstPath.equals(candidate.firstPath)
                        && existing.getValue().getPath().equals(candidate.getValue().getPath())) {
                        existing.refresh(changedPath);
                        replacement.set(index, existing);
                        break;
                    }
                }
            }
            super.getChildren().setAll(replacement);
            return;
        }
        for (TreeItem<PathItem> child : getLoadedChildren()) {
            if (child instanceof PathTreeItem item && changedPath.startsWith(item.getValue().getPath())) {
                item.refresh(changedPath);
            }
        }
    }

    /**
     * Resolves a path, loading only its ancestors and accounting for compact package rows.
     *
     * @param root root of the subtree to search, or {@code null}
     * @param path path to locate, or {@code null}
     * @return row representing the path, or {@code null} if no row matches or either argument is null
     */
    public static TreeItem<PathItem> find(TreeItem<PathItem> root, Path path) {
        if (root == null || path == null)
            return null;
        Path target = path.toAbsolutePath().normalize();
        Path current = root.getValue().getPath().toAbsolutePath().normalize();
        if (current.equals(target) || root instanceof PathTreeItem item && item.represents(path))
            return root;
        if (!target.startsWith(current))
            return null;
        for (TreeItem<PathItem> child : root.getChildren()) {
            TreeItem<PathItem> found = find(child, path);
            if (found != null)
                return found;
        }
        return null;
    }

    private static boolean isPackageDirectory(Path path) {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
            return false;

        for (Path current = path; current != null; current = current.getParent()) {
            String name = current.getFileName() == null ? "" : current.getFileName().toString();
            Path parent = current.getParent();
            if (name.equals("java") && parent != null &&
                (parent.endsWith("src") || parent.getParent() != null && parent.getParent().endsWith("src")))
                return !current.equals(path);
            if (!SourceVersion.isIdentifier(name) || SourceVersion.isKeyword(name))
                return false;
        }
        return false;
    }

    private static Path compactPackagePath(Path path) {
        while (isPackageDirectory(path)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
                var iterator = entries.iterator();
                if (!iterator.hasNext())
                    break;
                Path child = iterator.next();
                if (iterator.hasNext() || !isPackageDirectory(child))
                    break;
                path = child;
            } catch (IOException exception) {
                break;
            }
        }
        return path;
    }

    /**
     * Compacts filtered results only where the real filesystem also has a single package child.
     *
     * @param parent parent whose filtered descendants will be compacted in place
     * @param compactMiddlePackages whether to compact package chains; false leaves the tree unchanged
     */
    public static void compactFilteredPackages(TreeItem<PathItem> parent, boolean compactMiddlePackages) {
        if (!compactMiddlePackages)
            return;
        for (TreeItem<PathItem> child : parent.getChildren()) {
            Path compactPath = compactPackagePath(child.getValue().getPath());
            while (!child.getValue().getPath().equals(compactPath) && child.getChildren().size() == 1) {
                TreeItem<PathItem> descendant = child.getChildren().getFirst();
                child.setValue(descendant.getValue());
                var grandchildren = FXCollections.observableArrayList(descendant.getChildren());
                descendant.getChildren().clear();
                child.getChildren().setAll(grandchildren);
            }
            compactFilteredPackages(child, true);
        }
    }

    @Override
    public ObservableList<TreeItem<PathItem>> getChildren() {
        if (isFirstTimeChildren) {
            isFirstTimeChildren = false;
            super.getChildren().setAll(buildChildren(this));
        }

        return super.getChildren();
    }

    /**
     * Checks whether directory children have already been loaded.
     *
     * @return whether the initial child load has occurred
     */
    public boolean areChildrenLoaded() {
        return !isFirstTimeChildren;
    }

    /**
     * Returns the current child list without triggering directory loading.
     *
     * @return live list of already loaded children
     */
    public ObservableList<TreeItem<PathItem>> getLoadedChildren() {
        return super.getChildren();
    }

    @Override
    public boolean isLeaf() {
        if (this.isFirstTimeLeft) {
            this.isFirstTimeLeft = false;
            Path path = getValue().getPath();
            this.isLeaf = !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
        }

        return this.isLeaf;
    }

    private ObservableList<TreeItem<PathItem>> buildChildren(TreeItem<PathItem> treeItem) {
        Path path = treeItem.getValue().getPath();
        if (path != null && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            ObservableList<TreeItem<PathItem>> children = FXCollections.observableArrayList();
            try (DirectoryStream<Path> directories = Files.newDirectoryStream(path)) {
                for (Path directory : directories) {
                    var child = new PathTreeItem(new PathItem(directory), compactMiddlePackages);
                    if (compactMiddlePackages) {
                        child.setValue(new PathItem(compactPackagePath(directory)));
                    }
                    children.add(child);
                }
                children.sort(new PathTreeItemComparator());
            } catch (IOException exception) {
                Railroad.LOGGER.error("Failed to build children for tree item: {}", treeItem, exception);
            }

            return children;
        }

        return FXCollections.emptyObservableList();
    }
}
