package dev.railroadide.railroad.project;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.events.ProjectEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectManagerTransitionTest {
    @Test
    public void replacementEventIdentifiesNewProjectAndCloseIdentifiesPreviousProject() {
        List<ProjectEvent> events = new ArrayList<>();
        AtomicReference<ProjectManager> owner = new AtomicReference<>();
        var manager = new ProjectManager(() -> {
        }, event -> {
            events.add(event);
            assertSame(event.isClosed() ? null : event.project(), owner.get().getOpenProject());
        });
        owner.set(manager);
        Project first = project();
        Project second = project();

        manager.setCurrentProject(first);
        manager.setCurrentProject(second);
        manager.setCurrentProject(null);

        assertEquals(3, events.size());
        assertTrue(events.get(0).isOpened());
        assertSame(first, events.get(0).project());
        assertTrue(events.get(1).isOpened());
        assertSame(second, events.get(1).project());
        assertTrue(events.get(2).isClosed());
        assertSame(second, events.get(2).project());
    }

    @Test
    public void repeatingCurrentIdentityDoesNotPublishAnotherTransition() {
        List<ProjectEvent> events = new ArrayList<>();
        var manager = new ProjectManager(() -> {
        }, events::add);
        Project project = project();

        manager.setCurrentProject(null);
        manager.setCurrentProject(project);
        manager.setCurrentProject(project);
        manager.setCurrentProject(null);
        manager.setCurrentProject(null);

        assertEquals(2, events.size());
        assertTrue(events.get(0).isOpened());
        assertTrue(events.get(1).isClosed());
    }

    private static Project project() {
        return (Project) Proxy.newProxyInstance(Project.class.getClassLoader(), new Class<?>[]{Project.class},
            (proxy, method, args) -> {
                throw new AssertionError("Project transition should only use identity: " + method.getName());
            });
    }
}
