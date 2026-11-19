package dev.railroadide.railroad.gradle.ui.task;

import dev.railroadide.railroad.gradle.ui.tree.GradleTreeElement;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroadplugin.dto.RailroadGradleTask;
import dev.railroadide.railroadplugin.dto.RailroadModule;
import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GradleTaskTreeBuilderTest {
    @Test
    public void subprojectsNestUnderParentsThatAlsoHaveTasks() {
        RailroadModule root = module(":", "Industria");
        RailroadModule common = module(":common", "common");
        RailroadModule nested = module(":common:api", "api");
        List<RailroadGradleTask> tasks = List.of(task(nested, "jar"), task(common, "build"), task(root, "help"));

        TreeItem<GradleTreeElement> tree = new GradleTaskTreeBuilder().buildTree(proxy(Project.class, Map.of()), tasks);

        TreeItem<GradleTreeElement> rootNode = child(tree, "Industria");
        TreeItem<GradleTreeElement> commonNode = child(rootNode, "common");
        child(commonNode, "api");
        assertEquals(1, tree.getChildren().size());
    }

    private static TreeItem<GradleTreeElement> child(TreeItem<GradleTreeElement> parent, String name) {
        return parent.getChildren().stream().filter(item -> name.equals(item.getValue().getName())).findFirst()
            .orElseThrow(() -> new AssertionError("Missing " + name + " under " + parent.getValue()));
    }

    private static RailroadModule module(String path, String name) {
        return proxy(RailroadModule.class, Map.of("getPath", path, "getName", name));
    }

    private static RailroadGradleTask task(RailroadModule module, String name) {
        return proxy(RailroadGradleTask.class, Map.of("module", module, "getName", name, "getGroup", "build"));
    }

    private static <T> T proxy(Class<T> type, Map<String, Object> values) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (self, method, args) -> switch (method.getName()) {
                case "equals" -> self == args[0];
                case "hashCode" -> System.identityHashCode(self);
                case "toString" -> type.getSimpleName() + values;
                default -> values.get(method.getName());
            }));
    }
}
