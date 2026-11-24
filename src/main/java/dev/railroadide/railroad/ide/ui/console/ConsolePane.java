package dev.railroadide.railroad.ide.ui.console;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.session.RunSession;
import dev.railroadide.railroad.ide.runconfig.session.RunSessionManager;
import dev.railroadide.railroad.ide.ui.IDEDockItem;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.ui.id.UIIds;
import dev.railroadide.railroad.utility.javafx.JavaFXUtils;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.HashMap;
import java.util.Map;

/**
 * Shows the project's run sessions, one tab per session.
 * <p>
 * Rerunning a configuration reuses the tab of its previous, ended session. A tab can be closed once its session has
 * ended, which also removes the session from the project. When a session starts for a configuration that asks for the
 * console, the console dock item is brought to the front.
 */
public final class ConsolePane extends BorderPane implements AutoCloseable {
    private final Project project;
    private final TabPane tabs = new TabPane();
    private final LocalizedLabel empty = new LocalizedLabel("railroad.ide.console.empty");
    private final Map<RunSession, Tab> tabsBySession = new HashMap<>();
    private final Runnable unsubscribe;
    private volatile boolean replaying = true;
    private boolean closed;

    /**
     * Creates a console showing the project's sessions.
     *
     * @param project the project whose run sessions are shown
     */
    public ConsolePane(Project project) {
        this.project = project;
        getStyleClass().add("console-pane");
        empty.getStyleClass().add("console-empty");
        tabs.getStyleClass().add("console-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        setCenter(empty);

        unsubscribe = project.getRunSessions().subscribe(new RunSessionManager.Listener() {
            @Override
            public void sessionStarted(RunSession session, @Nullable RunSession replaced) {
                boolean reveal = !replaying;
                JavaFXUtils.runOnApplicationThread(() -> showSession(session, replaced, reveal));
            }

            @Override
            public void sessionRemoved(RunSession session) {
                JavaFXUtils.runOnApplicationThread(() -> removeSession(session));
            }
        });
        replaying = false;
    }

    @Override
    public void close() {
        closed = true;
        unsubscribe.run();
        tabsBySession.values().forEach(tab -> view(tab).close());
        tabsBySession.clear();
        tabs.getTabs().clear();
    }

    private void showSession(RunSession session, @Nullable RunSession replaced, boolean reveal) {
        if (closed || tabsBySession.containsKey(session))
            return;

        Tab tab = replaced == null ? null : tabsBySession.remove(replaced);
        if (tab == null) {
            tab = createTab(session);
            tabs.getTabs().add(tab);
        } else {
            view(tab).bind(session);
        }
        tabsBySession.put(session, tab);
        tabs.getSelectionModel().select(tab);
        setCenter(tabs);

        if (reveal && session.configuration().data().isShowConsoleOnRun()) {
            Services.UI_MANAGER.lookup(UIIds.IDE.IDE).ifPresent(ide -> ide.revealDockItem(IDEDockItem.CONSOLE));
        }
    }

    private void removeSession(RunSession session) {
        Tab tab = tabsBySession.remove(session);
        if (tab == null)
            return;

        view(tab).close();
        tabs.getTabs().remove(tab);
        if (tabs.getTabs().isEmpty()) {
            setCenter(empty);
        }
    }

    private Tab createTab(RunSession session) {
        RunConfiguration<?> configuration = session.configuration();
        var view = new RunSessionView(project, session);
        var icon = new FontIcon(configuration.type().getIcon());
        icon.setIconColor(configuration.type().getIconColor());
        var tab = new Tab(configuration.data().getName(), view);
        tab.setGraphic(icon);
        tab.closableProperty().bind(view.endedProperty());
        tab.setOnClosed(_ -> {
            RunSession shown = view.session();
            tabsBySession.remove(shown);
            view.close();
            project.getRunSessions().remove(shown);
            if (tabs.getTabs().isEmpty()) {
                setCenter(empty);
            }
        });
        return tab;
    }

    private static RunSessionView view(Tab tab) {
        return (RunSessionView) tab.getContent();
    }
}
