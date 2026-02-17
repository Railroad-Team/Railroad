package dev.railroadide.railroad.ide.ui.git.diff;

import dev.railroadide.core.ui.RRBorderPane;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.localized.LocalizedText;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.syntaxhighlighting.JsonSyntaxHighlighting;
import dev.railroadide.railroad.ide.syntaxhighlighting.TreeSitterJavaSyntaxHighlighting;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.utility.ShutdownHooks;
import dev.railroadide.railroad.vcs.git.GitManager;
import dev.railroadide.railroad.vcs.git.diff.*;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class GitDiffPane extends RRBorderPane {
    private static final String DEFAULT_TITLE = "Git Diff";
    private static final String PLACEHOLDER_NO_FILE_KEY = "railroad.git.diff.placeholder.no_file";
    private static final String PLACEHOLDER_NO_REPO_KEY = "railroad.git.diff.placeholder.no_repo";
    private static final String PLACEHOLDER_NO_CHANGES_KEY = "railroad.git.diff.placeholder.no_changes";
    private static final String PLACEHOLDER_LOADING_KEY = "railroad.git.diff.placeholder.loading";
    private static final String PLACEHOLDER_FAILED_KEY = "railroad.git.diff.placeholder.failed";

    private final ObjectProperty<Path> filePath = new SimpleObjectProperty<>();
    private final GitManager gitManager;
    private final CodeArea diffArea = new CodeArea();
    private final LocalizedText placeholderText = new LocalizedText(PLACEHOLDER_NO_FILE_KEY);
    private final StringProperty title = new SimpleStringProperty(DEFAULT_TITLE);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        var thread = new Thread(runnable, "GitDiffPane-Worker");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger generation = new AtomicInteger();
    private List<RenderLine> renderLines = List.of();
    private int oldNumberDigits = 1;
    private int newNumberDigits = 1;

    public GitDiffPane(Project project, Path filePath) {
        this.gitManager = project.getGitManager();

        getStyleClass().add("git-diff-pane-root");
        diffArea.getStyleClass().add("git-diff-code-area");
        placeholderText.getStyleClass().add("git-diff-placeholder");
        diffArea.setEditable(false);
        diffArea.setParagraphGraphicFactory(this::createGutterNode);

        this.filePath.set(filePath);
        title.set(resolveTitle(this.filePath.get()));
        if (this.filePath.get() != null) {
            requestDiff(this.filePath.get());
        } else {
            showPlaceholder(PLACEHOLDER_NO_FILE_KEY);
        }

        this.filePath.addListener((obs, oldPath, newPath) -> {
            title.set(resolveTitle(newPath));
            requestDiff(newPath);
        });

        ShutdownHooks.addHook(executor::shutdownNow);
    }

    public GitDiffPane(Project project) {
        this(project, null);
    }

    public ObjectProperty<Path> filePathProperty() {
        return filePath;
    }

    public Path getFilePath() {
        return filePath.get();
    }

    public void setFilePath(Path filePath) {
        this.filePath.set(filePath);
    }

    public void setExternalDiff(String displayTitle, String diffText) {
        int requestId = generation.incrementAndGet();
        if (displayTitle != null && !displayTitle.isBlank()) {
            title.set(displayTitle);
        }

        DiffResult result;
        if (diffText == null) {
            result = DiffResult.placeholder(PLACEHOLDER_FAILED_KEY);
        } else if (diffText.isBlank()) {
            result = DiffResult.placeholder(PLACEHOLDER_NO_CHANGES_KEY);
        } else {
            result = renderDiff(diffText);
        }

        applyDiffResult(requestId, result);
    }

    public StringProperty titleProperty() {
        return title;
    }

    public String getTitle() {
        return title.get();
    }

    private void requestDiff(Path path) {
        int requestId = generation.incrementAndGet();
        if (path == null) {
            showPlaceholder(PLACEHOLDER_NO_FILE_KEY);
            return;
        }

        showPlaceholder(PLACEHOLDER_LOADING_KEY);
        executor.submit(() -> {
            DiffResult result = loadDiff(path);
            Platform.runLater(() -> applyDiffResult(requestId, result));
        });
    }

    private DiffResult loadDiff(Path path) {
        Optional<String> diffTextOpt;
        try {
            diffTextOpt = gitManager.getUnstagedDiff(path);
        } catch (Exception exception) {
            Railroad.LOGGER.error("Failed to load git diff for {}", path, exception);
            return DiffResult.placeholder(PLACEHOLDER_FAILED_KEY);
        }

        if (diffTextOpt.isEmpty()) {
            return DiffResult.placeholder(gitManager.getGitRepository() == null
                ? PLACEHOLDER_NO_REPO_KEY
                : PLACEHOLDER_FAILED_KEY);
        }

        String diffText = diffTextOpt.orElse("");
        if (diffText.isBlank())
            return DiffResult.placeholder(PLACEHOLDER_NO_CHANGES_KEY);

        return renderDiff(diffText);
    }

    private void applyDiffResult(int requestId, DiffResult result) {
        if (generation.get() != requestId)
            return;

        if (result.diffText() == null) {
            showPlaceholder(result.placeholderText());
            return;
        }

        diffArea.replaceText(result.diffText());
        renderLines = result.lines();
        updateGutterMetrics();
        diffArea.setParagraphGraphicFactory(this::createGutterNode);
        applyDiffStyles(result.diffText(), renderLines);
        diffArea.moveTo(0);
        diffArea.scrollToPixel(0, 0);
        setCenter(diffArea);
    }

    private void showPlaceholder(String message) {
        placeholderText.setKey(message);
        setCenter(placeholderText);
    }

    private void applyDiffStyles(String diffText, List<RenderLine> lines) {
        if (diffText == null || diffText.isEmpty()) {
            diffArea.clear();
            return;
        }

        StyleSpans<Collection<String>> spans = buildStyles(diffText, lines);
        diffArea.setStyleSpans(0, spans);
    }

    private static StyleSpans<Collection<String>> buildStyles(String diffText, List<RenderLine> lines) {
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        int lineCount = lines.size();
        for (int i = 0; i < lineCount; i++) {
            RenderLine line = lines.get(i);
            boolean hasNewline = i < lineCount - 1;
            appendLineStyles(builder, line, hasNewline);
        }

        return builder.create();
    }

    private static void appendLineStyles(StyleSpansBuilder<Collection<String>> builder, RenderLine line, boolean hasNewline) {
        String text = line.text();
        if (text.isEmpty()) {
            builder.add(styleForKind(line.kind()), hasNewline ? 1 : 0);
            return;
        }

        if (line.kind().isCodeLine()) {
            String diffClass = styleClassForKind(line.kind());
            List<String> prefixStyle = List.of(diffClass, "git-diff-line-prefix");
            builder.add(prefixStyle, 1);

            String content = text.length() > 1 ? text.substring(1) : "";
            if (!content.isEmpty()) {
                StyleSpans<Collection<String>> syntaxSpans = computeSyntaxHighlight(line.languageId(), content);
                if (syntaxSpans != null) {
                    for (StyleSpan<Collection<String>> span : syntaxSpans) {
                        builder.add(mergeStyles(diffClass, span.getStyle()), span.getLength());
                    }
                } else {
                    builder.add(List.of(diffClass), content.length());
                }
            }

            if (hasNewline) {
                builder.add(List.of(diffClass), 1);
            }
        } else {
            Collection<String> styles = styleForKind(line.kind());
            builder.add(styles, text.length() + (hasNewline ? 1 : 0));
        }
    }

    private static Collection<String> mergeStyles(String diffClass, Collection<String> syntaxStyles) {
        if (syntaxStyles == null || syntaxStyles.isEmpty())
            return List.of(diffClass);

        List<String> merged = new ArrayList<>(syntaxStyles.size() + 1);
        merged.add(diffClass);
        merged.addAll(syntaxStyles);
        return merged;
    }

    private static Collection<String> styleForKind(LineKind kind) {
        return List.of(styleClassForKind(kind));
    }

    private static String styleClassForKind(LineKind kind) {
        return switch (kind) {
            case FILE_HEADER -> "git-diff-line-header";
            case HUNK_HEADER -> "git-diff-line-hunk";
            case META -> "git-diff-line-meta";
            case ADDITION -> "git-diff-line-addition";
            case DELETION -> "git-diff-line-deletion";
            case CONTEXT -> "git-diff-line-context";
        };
    }

    private static StyleSpans<Collection<String>> computeSyntaxHighlight(String languageId, String content) {
        if (languageId == null || content == null || content.isEmpty())
            return null;

        return switch (languageId) {
            case "java" -> TreeSitterJavaSyntaxHighlighting.computeHighlighting(content);
            case "json" -> JsonSyntaxHighlighting.computeHighlighting(content);
            default -> null;
        };
    }

    private DiffResult renderDiff(String diffText) {
        DiffBlob blob = DiffParser.parseDiff(diffText);
        if (blob.files().isEmpty())
            return DiffResult.placeholder(PLACEHOLDER_NO_CHANGES_KEY);

        List<RenderLine> lines = new ArrayList<>();

        for (DiffFile file : blob.files()) {
            String languageId = resolveLanguageId(file);
            lines.add(new RenderLine(formatFileHeader(file), LineKind.FILE_HEADER, null, null, languageId));

            if (file.isBinary()) {
                String binaryLine = file.headers().stream()
                    .filter(header -> header.startsWith("Binary files"))
                    .findFirst()
                    .orElse("Binary file differs");
                lines.add(new RenderLine(binaryLine, LineKind.META, null, null, languageId));
                lines.add(new RenderLine("", LineKind.META, null, null, languageId));
                continue;
            }

            for (DiffHunk hunk : file.hunks()) {
                lines.add(new RenderLine(formatHunkHeader(hunk), LineKind.HUNK_HEADER, null, null, languageId));

                for (DiffHunkLine hunkLine : hunk.lines()) {
                    LineKind kind = switch (hunkLine.type()) {
                        case CONTEXT -> LineKind.CONTEXT;
                        case ADDITION -> LineKind.ADDITION;
                        case DELETION -> LineKind.DELETION;
                    };
                    String text = prefixForKind(kind) + hunkLine.content();
                    lines.add(new RenderLine(text, kind, hunkLine.oldLineNumber(), hunkLine.newLineNumber(), languageId));
                }
            }

            lines.add(new RenderLine("", LineKind.META, null, null, languageId));
        }

        String text = linesToText(lines);
        return DiffResult.content(text, lines);
    }

    private static String linesToText(List<RenderLine> lines) {
        var builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            builder.append(lines.get(i).text());
            if (i < lines.size() - 1) {
                builder.append('\n');
            }
        }

        return builder.toString();
    }

    private static String formatFileHeader(DiffFile file) {
        String oldPath = file.oldPath() != null ? file.oldPath().toString() : null;
        String newPath = file.newPath() != null ? file.newPath().toString() : null;
        if (oldPath == null)
            return "File: " + newPath + " (new)";

        if (newPath == null)
            return "File: " + oldPath + " (deleted)";

        if (!oldPath.equals(newPath))
            return "File: " + newPath + " (renamed from " + oldPath + ")";

        return "File: " + newPath;
    }

    private static String formatHunkHeader(DiffHunk hunk) {
        String header = "@@ -%d,%d +%d,%d @@".formatted(hunk.oldStart(), hunk.oldCount(), hunk.newStart(), hunk.newCount());
        if (hunk.sectionHeader() != null && !hunk.sectionHeader().isBlank()) {
            header = header + " " + hunk.sectionHeader();
        }

        return header;
    }

    private static String prefixForKind(LineKind kind) {
        return switch (kind) {
            case ADDITION -> "+";
            case DELETION -> "-";
            case CONTEXT -> " ";
            default -> "";
        };
    }

    private static String resolveLanguageId(DiffFile file) {
        Path path = file.newPath() != null ? file.newPath() : file.oldPath();
        if (path == null)
            return null;

        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".java"))
            return "java";

        if (name.endsWith(".json"))
            return "json";

        return null;
    }

    private void updateGutterMetrics() {
        int maxOld = 0;
        int maxNew = 0;
        for (RenderLine line : renderLines) {
            if (line.oldLineNumber() != null) {
                maxOld = Math.max(maxOld, line.oldLineNumber());
            }

            if (line.newLineNumber() != null) {
                maxNew = Math.max(maxNew, line.newLineNumber());
            }
        }

        oldNumberDigits = Math.max(1, String.valueOf(maxOld).length());
        newNumberDigits = Math.max(1, String.valueOf(maxNew).length());
    }

    private Node createGutterNode(int lineIndex) {
        if (lineIndex < 0 || lineIndex >= renderLines.size())
            return new RRHBox();

        RenderLine line = renderLines.get(lineIndex);
        String oldLabelText = formatLineNumber(line.oldLineNumber(), oldNumberDigits);
        String newLabelText = formatLineNumber(line.newLineNumber(), newNumberDigits);

        var oldLabel = new Label(oldLabelText);
        var newLabel = new Label(newLabelText);
        oldLabel.getStyleClass().add("git-diff-gutter-number");
        newLabel.getStyleClass().add("git-diff-gutter-number");
        if (line.oldLineNumber() == null) {
            oldLabel.getStyleClass().add("git-diff-gutter-empty");
        }

        if (line.newLineNumber() == null) {
            newLabel.getStyleClass().add("git-diff-gutter-empty");
        }

        var gutter = new RRHBox(6, oldLabel, newLabel);
        gutter.setAlignment(Pos.CENTER_RIGHT);
        gutter.getStyleClass().add("git-diff-gutter");
        gutter.getStyleClass().add(styleClassForKind(line.kind()));

        return gutter;
    }

    private static String formatLineNumber(Integer number, int width) {
        if (number == null)
            return " ".repeat(Math.max(1, width));

        return String.format("%" + width + "d", number);
    }

    private record DiffResult(String diffText, List<RenderLine> lines, String placeholderText) {
        static DiffResult content(String diffText, List<RenderLine> lines) {
            return new DiffResult(diffText, lines, null);
        }

        static DiffResult placeholder(String placeholder) {
            return new DiffResult(null, List.of(), placeholder);
        }
    }

    private record RenderLine(String text, LineKind kind, Integer oldLineNumber, Integer newLineNumber,
                              String languageId) {
    }

    private static String resolveTitle(Path path) {
        if (path == null)
            return DEFAULT_TITLE;

        Path fileName = path.getFileName();
        return fileName == null ? DEFAULT_TITLE : fileName.toString();
    }

    private enum LineKind {
        FILE_HEADER(false),
        HUNK_HEADER(false),
        META(false),
        CONTEXT(true),
        ADDITION(true),
        DELETION(true);

        private final boolean codeLine;

        LineKind(boolean codeLine) {
            this.codeLine = codeLine;
        }

        boolean isCodeLine() {
            return codeLine;
        }
    }
}
