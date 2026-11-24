package dev.railroadide.railroad.ide.ui.console;

import dev.railroadide.railroad.command.CommandButtons;
import dev.railroadide.railroad.command.CommandContext;
import dev.railroadide.railroad.command.RunCommands;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.session.RunOutput;
import dev.railroadide.railroad.ide.runconfig.session.RunSession;
import dev.railroadide.railroad.ide.runconfig.session.RunSessionListener;
import dev.railroadide.railroad.ide.runconfig.session.RunSessionState;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.RRToggleButton;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.ui.localized.LocalizedTooltip;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import dev.railroadide.railroad.utility.javafx.JavaFXUtils;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.fxmisc.richtext.model.TwoDimensional;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shows the output of one run session and lets the user rerun, stop or type input to it.
 * <p>
 * Output reaches the view on producer threads and is queued; the JavaFX thread drains the queue at most once per
 * pulse, so a process that writes quickly cannot flood the event queue. The view can be rebound to the next session of
 * the same configuration when it is rerun. Everything except the session listener runs on the JavaFX thread.
 */
public final class RunSessionView extends BorderPane implements AutoCloseable {
    private static final int MAX_DISPLAYED_CHARS = RunSession.DEFAULT_RETAINED_CHARS;
    private static final int TRIMMED_DISPLAYED_CHARS = MAX_DISPLAYED_CHARS - MAX_DISPLAYED_CHARS / 10;

    private final Project project;
    private final StyleClassedTextArea output = new StyleClassedTextArea();
    private final TextField input = new TextField();
    private final RRButton rerun = toolButton(new RRButton(), "railroad.ide.console.rerun", FontAwesomeSolid.REDO);
    private final RRButton stop = toolButton(new RRButton(), "railroad.ide.console.stop", FontAwesomeSolid.STOP);
    private final RRToggleButton follow = toolButton(new RRToggleButton(), "railroad.ide.console.follow",
        FontAwesomeSolid.ANGLE_DOUBLE_DOWN);
    private final RRToggleButton wrap = toolButton(new RRToggleButton(), "railroad.ide.console.wrap",
        FontAwesomeSolid.PARAGRAPH);
    private final ReadOnlyBooleanWrapper ended = new ReadOnlyBooleanWrapper(this, "ended");
    private final Queue<Pending> pending = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean drainScheduled = new AtomicBoolean();
    private final Map<RunOutput.Kind, AnsiParser> parsers = new EnumMap<>(RunOutput.Kind.class);
    private final List<Segment> batch = new ArrayList<>();
    private RunSession session;
    private Runnable unsubscribe = () -> {
    };
    private boolean closed;

    /**
     * Creates a view showing the session.
     *
     * @param project the project the session was launched in
     * @param session the session to show
     */
    public RunSessionView(Project project, RunSession session) {
        this.project = Objects.requireNonNull(project, "project");
        getStyleClass().add("run-session-view");
        for (RunOutput.Kind kind : RunOutput.Kind.values()) {
            parsers.put(kind, new AnsiParser());
        }

        output.setEditable(false);
        output.getStyleClass().add("console-output");
        var scroll = new VirtualizedScrollPane<>(output);
        follow.setSelected(true);
        wrap.selectedProperty().addListener((_, _, wrapped) -> output.setWrapText(wrapped));
        var clear = toolButton(new RRButton(), "railroad.ide.console.clear", FontAwesomeSolid.TRASH_ALT);
        clear.setOnAction(_ -> output.clear());

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var toolbar = new HBox(rerun, stop, spacer, follow, wrap, clear);
        toolbar.getStyleClass().add("console-toolbar");

        input.getStyleClass().add("console-input");
        input.promptTextProperty().bind(new LocalizedLabel("railroad.ide.console.input.prompt").textProperty());
        input.accessibleTextProperty().bind(input.promptTextProperty());
        input.setOnAction(_ -> sendInput());
        input.disableProperty().bind(ended);
        stop.disableProperty().bind(ended);

        setTop(toolbar);
        setCenter(scroll);
        setBottom(input);
        bind(session);
    }

    /**
     * Returns the session currently shown.
     *
     * @return the shown session
     */
    public RunSession session() {
        return session;
    }

    /**
     * Reports whether the shown session has ended.
     *
     * @return a property that is true once the shown session has ended
     */
    public ReadOnlyBooleanProperty endedProperty() {
        return ended.getReadOnlyProperty();
    }

    /**
     * Replaces the shown session, clearing the previous session's output.
     *
     * @param next the session to show
     */
    public void bind(RunSession next) {
        Objects.requireNonNull(next, "next");
        unsubscribe.run();
        pending.clear();
        batch.clear();
        parsers.values().forEach(AnsiParser::reset);
        output.clear();
        session = next;
        ended.set(false);

        RunConfiguration<?> configuration = next.configuration();
        CommandButtons.bind(rerun, next.isDebug() ? RunCommands.DEBUG : RunCommands.RUN,
            () -> CommandContext.withArgument(project, this, configuration));
        CommandButtons.bind(stop, RunCommands.STOP, () -> CommandContext.withArgument(project, this, configuration));
        unsubscribe = next.subscribe(new RunSessionListener() {
            @Override
            public void outputAppended(RunOutput chunk) {
                enqueue(new Pending(next, chunk, null));
            }

            @Override
            public void sessionEnded(RunSessionState state) {
                enqueue(new Pending(next, null, state));
            }
        });
    }

    @Override
    public void close() {
        closed = true;
        unsubscribe.run();
        pending.clear();
    }

    private void enqueue(Pending item) {
        pending.add(item);
        if (drainScheduled.compareAndSet(false, true)) {
            JavaFXUtils.runOnApplicationThread(this::drain);
        }
    }

    private void drain() {
        drainScheduled.set(false);
        if (closed)
            return;

        Pending item;
        while ((item = pending.poll()) != null) {
            if (item.session() != session)
                continue;

            if (item.output() != null) {
                appendOutput(item.output());
            } else if (item.state() != null) {
                appendStateLine(item.state());
                ended.set(true);
            }
        }
        flushBatch();
        trim();
        if (follow.isSelected()) {
            output.moveTo(output.getLength());
            output.requestFollowCaret();
        }
    }

    private void appendOutput(RunOutput chunk) {
        List<String> kindClasses = kindClasses(chunk.kind());
        for (AnsiParser.Token token : parsers.get(chunk.kind()).feed(chunk.text())) {
            switch (token) {
                case AnsiParser.Text(String text, AnsiStyle style) -> {
                    if (style.equals(AnsiStyle.PLAIN)) {
                        batch.add(new Segment(text, kindClasses));
                    } else {
                        var classes = new ArrayList<>(kindClasses);
                        classes.addAll(style.styleClasses());
                        batch.add(new Segment(text, classes));
                    }
                }
                case AnsiParser.CarriageReturn _ -> {
                    flushBatch();
                    int lastParagraph = output.getParagraphs().size() - 1;
                    output.deleteText(output.getAbsolutePosition(lastParagraph, 0), output.getLength());
                }
            }
        }
    }

    private void appendStateLine(RunSessionState state) {
        String message = switch (state) {
            case STOPPED -> L18n.localize("railroad.ide.console.stopped");
            case SUCCEEDED, FAILED -> {
                OptionalInt exitCode = session.exitCode();
                Optional<String> failure = session.failureMessage();
                if (exitCode.isPresent())
                    yield L18n.localize("railroad.ide.console.exited", exitCode.getAsInt());
                else if (failure.isPresent())
                    yield L18n.localize("railroad.ide.console.failed", failure.get());
                else
                    yield L18n.localize(state == RunSessionState.SUCCEEDED
                        ? "railroad.ide.console.finished"
                        : "railroad.ide.console.failed_without_reason");
            }
            case RUNNING -> throw new IllegalStateException("A running session has not ended");
        };
        flushBatch();
        int length = output.getLength();
        String separator = length == 0 || output.getText(length - 1, length).equals("\n") ? "" : "\n";
        batch.add(new Segment(separator + message + "\n", kindClasses(RunOutput.Kind.SYSTEM)));
    }

    private void flushBatch() {
        if (batch.isEmpty())
            return;

        var text = new StringBuilder();
        var spans = new StyleSpansBuilder<Collection<String>>(batch.size());
        for (Segment segment : batch) {
            text.append(segment.text());
            spans.add(segment.styleClasses(), segment.text().length());
        }
        batch.clear();
        int start = output.getLength();
        output.appendText(text.toString());
        output.setStyleSpans(start, spans.create());
    }

    private void trim() {
        int length = output.getLength();
        if (length <= MAX_DISPLAYED_CHARS)
            return;

        int excess = length - TRIMMED_DISPLAYED_CHARS;
        int paragraph = output.offsetToPosition(excess, TwoDimensional.Bias.Forward)
            .getMajor();
        int cut = paragraph + 1 < output.getParagraphs().size()
            ? output.getAbsolutePosition(paragraph + 1, 0)
            : excess;
        output.deleteText(0, cut);
    }

    private void sendInput() {
        if (session.sendInput(input.getText() + "\n")) {
            input.clear();
        }
    }

    private static List<String> kindClasses(RunOutput.Kind kind) {
        return switch (kind) {
            case STDOUT -> List.of();
            case STDERR -> List.of("console-stderr");
            case SYSTEM -> List.of("console-system");
            case INPUT -> List.of("console-user-input");
        };
    }

    private static <B extends ButtonBase> B toolButton(B button, String key, Ikon icon) {
        button.setGraphic(new FontIcon(icon));
        button.getStyleClass().add("toolbar-button");
        var tooltip = new LocalizedTooltip(key);
        button.setTooltip(tooltip);
        button.accessibleTextProperty().bind(tooltip.textProperty());
        if (button instanceof RRButton rrButton) {
            rrButton.setVariant(ButtonVariant.GHOST);
        } else if (button instanceof RRToggleButton toggle) {
            toggle.setVariant(ButtonVariant.GHOST);
        }
        return button;
    }

    private record Pending(RunSession session, @Nullable RunOutput output, @Nullable RunSessionState state) {
    }

    private record Segment(String text, Collection<String> styleClasses) {
    }
}
