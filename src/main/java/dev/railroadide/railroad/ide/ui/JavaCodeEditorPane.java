package dev.railroadide.railroad.ide.ui;

import dev.railroadide.core.ui.RRListView;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.signature.JdtSignatureHelpProvider;
import dev.railroadide.railroad.ide.signature.SignatureHelp;
import dev.railroadide.railroad.ide.signature.SignatureHelp.ParameterInfo;
import dev.railroadide.railroad.ide.signature.SignatureHelpProvider;
import dev.railroadide.railroad.ide.syntaxhighlighting.TreeSitterJavaSyntaxHighlighting;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.utility.ShutdownHooks;
import io.github.palexdev.mfxresources.fonts.MFXFontIcon;
import io.github.palexdev.mfxresources.fonts.fontawesome.FontAwesomeSolid;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.stage.Popup;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.*;
import org.fxmisc.richtext.event.MouseOverTextEvent;
import org.fxmisc.richtext.model.PlainTextChange;
import org.fxmisc.richtext.model.StyleSpans;
import org.jetbrains.annotations.NotNull;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JavaCodeEditorPane extends TextEditorPane {
    private static final Duration HIGHLIGHT_DEBOUNCE = Duration.ofMillis(120);
    private static final Duration DIAGNOSTIC_DEBOUNCE = Duration.ofMillis(300);

    private static final Map<Character, Character> OPENING_BRACKETS = Map.of('(', ')', '{', '}', '[', ']');
    private static final Map<Character, Character> CLOSING_BRACKETS = Map.of(')', '(', '}', '{', ']', '[');

    private static final String[] SYSTEM_MODULE_PATHS = resolveSystemModules();

    private final ExecutorService worker = Executors.newFixedThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
        namedThreadFactory("railroad-java-editor-"));

    private final Project project;

    private final AtomicInteger highlightGeneration = new AtomicInteger();
    private final AtomicInteger diagnosticsGeneration = new AtomicInteger();
    private final AtomicInteger completionGeneration = new AtomicInteger();

    private volatile StyleSpans<Collection<String>> lastHighlight =
        TreeSitterJavaSyntaxHighlighting.computeHighlighting("");
    private volatile List<ProblemDiagnostic> visibleDiagnostics = List.of();
    private final Map<Integer, Diagnostic.Kind> lineSeverity = new ConcurrentHashMap<>();

    private final Popup diagnosticPopup = new Popup();
    private final AtomicReference<Popup> activeCompletionPopup = new AtomicReference<>(null);
    private final AtomicReference<RRListView<CompletionItem>> activeCompletionList = new AtomicReference<>(null);
    private ChangeListener<String> completionFilterListener;
    private final List<CompletionItem> completionCandidates = new ArrayList<>();
    private volatile int completionDotIndex = -1;

    private final Popup signaturePopup = new Popup();
    private final TextFlow signatureTextFlow = new TextFlow();
    private final AtomicInteger signatureGeneration = new AtomicInteger();
    private final AtomicReference<SignatureHelp> activeSignatureHelp = new AtomicReference<>(null);
    private final SignatureHelpProvider signatureHelpProvider;

    private int[] bracketHighlightRange;

    public JavaCodeEditorPane(Project project, Path item) {
        super(item);
        this.project = Objects.requireNonNull(project, "project");
        this.signatureHelpProvider = new JdtSignatureHelpProvider(filePath, SYSTEM_MODULE_PATHS);

        diagnosticPopup.setAutoHide(true);
        signaturePopup.setAutoHide(false);
        signaturePopup.setAutoFix(true);
        signaturePopup.setHideOnEscape(true);
        signatureTextFlow.getStyleClass().add("signature-help-text");
        var signaturePopupContainer = new StackPane();
        signaturePopupContainer.getStyleClass().add("signature-help-container");
        signaturePopupContainer.getChildren().add(signatureTextFlow);
        signaturePopup.getContent().add(signaturePopupContainer);

        configureParagraphGraphics();
        installSyntaxHighlighting();
        installDiagnostics();
        installCompletion();
        installSignatureHelp();
        installBracketHighlighting();
        installDiagnosticPopupHandlers();

        ShutdownHooks.addHook(worker::shutdownNow);
    }

    private void installSignatureHelp() {
        caretPositionProperty().addListener((obs, oldPos, newPos) -> requestSignatureHelp());

        plainTextChanges()
            .successionEnds(Duration.ofMillis(120))
            .subscribe(change -> requestSignatureHelp());

        focusedProperty().addListener((obs, oldValue, newValue) -> {
            if (!newValue) {
                hideSignatureHelp();
            }
        });
    }

    private void requestSignatureHelp() {
        String snapshot = getText();
        int caret = getCaretPosition();
        int generation = signatureGeneration.incrementAndGet();

        CompletableFuture.supplyAsync(() -> signatureHelpProvider.compute(snapshot, caret), worker)
            .thenAccept(help -> Platform.runLater(() -> applySignatureHelp(generation, help)))
            .exceptionally(throwable -> {
                Railroad.LOGGER.error("Failed to compute signature help", throwable);
                return null;
            });
    }

    private void applySignatureHelp(int generation, SignatureHelp help) {
        if (signatureGeneration.get() != generation)
            return;

        if (help == null) {
            hideSignatureHelp();
            return;
        }

        SignatureHelp previous = activeSignatureHelp.get();
        if (previous != null && previous.equals(help)) {
            positionSignaturePopup();
            return;
        }

        activeSignatureHelp.set(help);
        showSignatureHelp(help);
    }

    private void showSignatureHelp(SignatureHelp help) {
        signatureTextFlow.getChildren().clear();

        String owner = help.ownerQualified().isBlank() ? help.ownerDisplay() : help.ownerQualified();
        StringBuilder headerBuilder = new StringBuilder();
        if (help.constructor()) {
            headerBuilder.append("new ");
            if (!owner.isBlank()) {
                headerBuilder.append(owner);
            }
        } else {
            if (!owner.isBlank()) {
                headerBuilder.append(owner).append(".");
            }

            headerBuilder.append(help.methodName());
        }

        headerBuilder.append("(");

        var header = new Text(headerBuilder.toString());
        signatureTextFlow.getChildren().add(header);

        List<ParameterInfo> parameters = help.parameters();
        int parameterCount = parameters.size();
        int highlightIndex = help.activeParameter();

        for (int i = 0; i < parameterCount; i++) {
            ParameterInfo parameter = parameters.get(i);
            boolean highlight = highlightIndex == i ||
                (help.varargs() && i == parameterCount - 1 && highlightIndex >= parameterCount - 1 && highlightIndex >= 0);

            String paramLabel = parameter.type() + (parameter.name().isBlank() ? "" : " " + parameter.name());
            var paramText = new Text(paramLabel);
            if (highlight) {
                paramText.getStyleClass().add("signature-param-active");
            } else {
                paramText.getStyleClass().add("signature-param");
            }

            signatureTextFlow.getChildren().add(paramText);

            if (i < parameterCount - 1) {
                signatureTextFlow.getChildren().add(new Text(", "));
            }
        }

        var closing = new Text(")");
        signatureTextFlow.getChildren().add(closing);

        if (!help.constructor()) {
            signatureTextFlow.getChildren().add(new Text(" : " + help.returnType()));
        }

        positionSignaturePopup();
    }

    private void positionSignaturePopup() {
        Optional<Bounds> caretBounds = getCaretBounds();
        double x;
        double y;
        if (caretBounds.isPresent()) {
            Bounds bounds = caretBounds.get();
            x = bounds.getMinX();
            y = bounds.getMaxY() + 6;
        } else {
            Point2D screen = localToScreen(0, 0);
            x = screen.getX();
            y = screen.getY();
        }

        if (!signaturePopup.isShowing()) {
            signaturePopup.show(this, x, y);
        } else {
            signaturePopup.setX(x);
            signaturePopup.setY(y);
        }
    }

    private void hideSignatureHelp() {
        activeSignatureHelp.set(null);
        if (signaturePopup.isShowing()) {
            signaturePopup.hide();
        }
    }

    private static String[] resolveSystemModules() {
        try {
            Path javaHome = Path.of(System.getProperty("java.home"));
            Path jmods = javaHome.resolve("jmods");
            if (Files.isDirectory(jmods)) {
                try (Stream<Path> stream = Files.list(jmods)) {
                    return stream
                        .map(Path::toString)
                        .toArray(String[]::new);
                }
            }
        } catch (Exception exception) {
            Railroad.LOGGER.warn("Unable to resolve system modules for Java analysis", exception);
        }

        return new String[0];
    }

    private void configureParagraphGraphics() {
        setParagraphGraphicFactory(this::createParagraphGraphic);
    }

    private Node createParagraphGraphic(int line) {
        var grid = new GridPane();
        grid.setHgap(5);
        grid.getStyleClass().add("ide-java-code-editor-grid");

        var numberColumn = new ColumnConstraints();
        numberColumn.setHgrow(Priority.ALWAYS);

        var iconColumn = new ColumnConstraints();
        iconColumn.setPrefWidth(12);
        iconColumn.setHgrow(Priority.NEVER);

        grid.getColumnConstraints().addAll(numberColumn, iconColumn);

        var label = new Label(String.format("%4d", line + 1));
        label.setTextAlignment(TextAlignment.RIGHT);
        label.setTextFill(Color.LIGHTGRAY);
        grid.add(label, 0, 0);

        Diagnostic.Kind severity = lineSeverity.get(line + 1);
        if (severity != null) {
            FontAwesomeSolid iconType = severity == Diagnostic.Kind.ERROR ?
                FontAwesomeSolid.CIRCLE_EXCLAMATION :
                FontAwesomeSolid.TRIANGLE_EXCLAMATION;
            Color color = severity == Diagnostic.Kind.ERROR ? Color.RED : Color.YELLOW;

            var icon = new MFXFontIcon(iconType, 12, color);
            grid.add(icon, 1, 0);

            String tooltipText = visibleDiagnostics.stream()
                .filter(diagnostic -> diagnostic.line() == line + 1)
                .map(ProblemDiagnostic::message)
                .findFirst()
                .orElse(severity == Diagnostic.Kind.ERROR ? "Error" : "Warning");
            Tooltip.install(icon, new Tooltip(tooltipText));
        }

        return grid;
    }

    private void installSyntaxHighlighting() {
        requestHighlight(getText());
        multiPlainChanges()
            .successionEnds(HIGHLIGHT_DEBOUNCE)
            .subscribe(changes -> requestHighlight(getText()));
    }

    private void requestHighlight(String snapshot) {
        int generation = highlightGeneration.incrementAndGet();
        CompletableFuture
            .supplyAsync(() -> TreeSitterJavaSyntaxHighlighting.computeHighlighting(snapshot), worker)
            .thenAccept(spans -> Platform.runLater(() -> applyHighlightIfLatest(generation, spans)))
            .exceptionally(throwable -> {
                Railroad.LOGGER.error("Failed to compute Java syntax highlighting", throwable);
                return null;
            });
    }

    private void applyHighlightIfLatest(int generation, StyleSpans<Collection<String>> spans) {
        if (highlightGeneration.get() != generation)
            return;

        lastHighlight = spans;
        setStyleSpans(0, spans);
        overlayDiagnostics();
        restoreBracketHighlight();
    }

    private void installDiagnostics() {
        requestDiagnostics(getText());
        multiPlainChanges()
            .successionEnds(DIAGNOSTIC_DEBOUNCE)
            .subscribe(changes -> requestDiagnostics(getText()));
    }

    private void requestDiagnostics(String snapshot) {
        int generation = diagnosticsGeneration.incrementAndGet();
        CompletableFuture
            .supplyAsync(() -> analyseDiagnostics(snapshot), worker)
            .thenAccept(result -> Platform.runLater(() -> applyDiagnosticsIfLatest(generation, result)))
            .exceptionally(throwable -> {
                Railroad.LOGGER.error("Failed to analyse Java diagnostics", throwable);
                return null;
            });
    }

    private List<ProblemDiagnostic> analyseDiagnostics(String text) {
        char[] source = text.toCharArray();
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(false);
        parser.setBindingsRecovery(false);
        parser.setStatementsRecovery(true);
        parser.setSource(source);
        parser.setUnitName(filePath.getFileName().toString());

        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
        parser.setCompilerOptions(options);

        CompilationUnit unit = (CompilationUnit) parser.createAST(null);
        return getProblemDiagnostics(unit, source);
    }

    private static @NotNull List<ProblemDiagnostic> getProblemDiagnostics(CompilationUnit unit, char[] source) {
        IProblem[] problems = unit.getProblems();

        List<ProblemDiagnostic> diagnostics = new ArrayList<>(problems.length);
        for (IProblem problem : problems) {
            Diagnostic.Kind kind = problem.isError()
                ? Diagnostic.Kind.ERROR
                : (problem.isWarning() ? Diagnostic.Kind.WARNING : Diagnostic.Kind.OTHER);
            if (kind == Diagnostic.Kind.OTHER)
                continue;

            int start = Math.max(0, problem.getSourceStart());
            int end = Math.min(source.length, problem.getSourceEnd() + 1);
            long line = problem.getSourceLineNumber();
            long column = computeColumn(source, start);
            String message = problem.getMessage();
            diagnostics.add(new ProblemDiagnostic(kind, start, end, line, column, message));
        }

        return diagnostics;
    }

    private void applyDiagnosticsIfLatest(int generation, List<ProblemDiagnostic> diagnostics) {
        if (diagnosticsGeneration.get() != generation)
            return;

        diagnosticPopup.hide();

        visibleDiagnostics = diagnostics;
        recomputeLineSeverity();

        if (lastHighlight != null) {
            setStyleSpans(0, lastHighlight);
        }

        overlayDiagnostics();
        restoreBracketHighlight();
        requestLayout();
    }

    private void recomputeLineSeverity() {
        lineSeverity.clear();
        for (ProblemDiagnostic diagnostic : visibleDiagnostics) {
            int line = (int) diagnostic.line();
            Diagnostic.Kind kind = diagnostic.kind();
            if (kind == Diagnostic.Kind.ERROR) {
                lineSeverity.put(line, Diagnostic.Kind.ERROR);
            } else if (!lineSeverity.containsKey(line) || lineSeverity.get(line) == Diagnostic.Kind.WARNING) {
                lineSeverity.put(line, Diagnostic.Kind.WARNING);
            }
        }
    }

    private void overlayDiagnostics() {
        for (ProblemDiagnostic diagnostic : visibleDiagnostics) {
            String style = diagnostic.kind() == Diagnostic.Kind.ERROR ? "error" : "warning";
            try {
                setStyleClass(diagnostic.start(), diagnostic.end(), style);
            } catch (IndexOutOfBoundsException ignored) {
                // Ignore invalid ranges caused by parser desync
            }
        }
    }

    private void installDiagnosticPopupHandlers() {
        addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_BEGIN, this::handleMouseOverText);
        addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_END, event -> diagnosticPopup.hide());
        addEventHandler(MouseEvent.MOUSE_MOVED, this::handleMouseMoved);
    }

    private ProblemDiagnostic findDiagnosticAt(int index) {
        if (index < 0)
            return null;

        for (ProblemDiagnostic diagnostic : visibleDiagnostics) {
            if (index >= diagnostic.start() && index <= diagnostic.end()) {
                return diagnostic;
            }
        }

        return null;
    }

    private void showDiagnosticPopup(ProblemDiagnostic diagnostic, double screenX, double screenY) {
        diagnosticPopup.getContent().clear();
        diagnosticPopup.getContent().add(new DiagnosticPane(diagnostic));
        diagnosticPopup.show(this, screenX, screenY);
    }

    private void installCompletion() {
        plainTextChanges().subscribe(this::showAutocomplete);

        setOnMouseClicked(event -> hideAutoComplete());
        focusedProperty().addListener((obs, oldValue, newValue) -> {
            if (!newValue) {
                hideAutoComplete();
            }
        });

        caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            if (completionDotIndex >= 0 && newPos <= completionDotIndex) {
                hideAutoComplete();
            }
        });

        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                hideAutoComplete();
            }
        });
    }

    private void triggerCompletion(int dotIndex) {
        String snapshot = getText();
        int generation = completionGeneration.incrementAndGet();
        CompletableFuture.supplyAsync(() -> computeCompletion(snapshot, dotIndex), worker)
            .thenAccept(result -> Platform.runLater(() -> {
                if (completionGeneration.get() == generation) {
                    handleCompletionResult(result);
                }
            }))
            .exceptionally(throwable -> {
                Railroad.LOGGER.error("Failed to compute Java code completion", throwable);
                return null;
            });
    }

    private CompletionResult computeCompletion(String text, int dotIndex) {
        if (dotIndex < 0 || dotIndex >= text.length() || text.charAt(dotIndex) != '.')
            return CompletionResult.empty();

        Pair range = findIdentifierRangeBeforeDot(text, dotIndex);
        if (range == null)
            return CompletionResult.empty();

        ASTParser parser = ASTParser.newParser(AST.JLS21); // TODO: Detect version from project settings
        parser.setSource(text.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);
        parser.setUnitName(filePath.getFileName().toString());

        // TODO: Set classpath and sourcepath from project settings
        parser.setEnvironment(SYSTEM_MODULE_PATHS, null, null, false);

        CompilationUnit unit = (CompilationUnit) parser.createAST(null);
        var finder = new NodeFinder(unit, range.start(), Math.max(1, range.length()));
        ASTNode node = finder.getCoveredNode();
        if (node == null) {
            node = finder.getCoveringNode();
        }

        CompletionTarget target = null;
        ASTNode cursor = node;
        while (cursor != null && target == null) {
            if (cursor instanceof ExpressionStatement statement) {
                cursor = statement.getExpression();
            }

            target = resolveCompletionTarget(cursor);
            cursor = cursor != null ? cursor.getParent() : null;
        }

        if (target == null || target.type() == null) {
            return CompletionResult.empty();
        }

        List<CompletionItem> suggestions = collectCompletionSuggestions(unit, target);
        if (suggestions.isEmpty())
            return CompletionResult.empty();

        return new CompletionResult(dotIndex, suggestions);
    }

    private CompletionTarget resolveCompletionTarget(ASTNode node) {
        if (node == null)
            return null;

        if (node instanceof Name name) {
            IBinding binding = name.resolveBinding();
            if (binding instanceof ITypeBinding typeBinding)
                return new CompletionTarget(normalizeType(typeBinding), true);

            if (binding instanceof IVariableBinding variableBinding) {
                ITypeBinding type = normalizeType(variableBinding.getType());
                if (type != null)
                    return new CompletionTarget(type, false);
            }

            if (binding instanceof IMethodBinding methodBinding) {
                ITypeBinding type = normalizeType(methodBinding.getReturnType());
                if (type != null)
                    return new CompletionTarget(type, false);
            }
        }

        if (node instanceof Expression expression) {
            ITypeBinding type = normalizeType(expression.resolveTypeBinding());
            if (type != null)
                return new CompletionTarget(type, false);
        }

        return null;
    }

    private ITypeBinding normalizeType(ITypeBinding binding) {
        if (binding == null)
            return null;

        if (binding.isAnonymous()) {
            binding = binding.getSuperclass();
        }

        if (binding == null)
            return null;

        if (binding.isArray() || binding.isPrimitive() || binding.isNullType())
            return binding;

        if (binding.isTypeVariable()) {
            ITypeBinding erasure = binding.getErasure();
            return erasure != null ? erasure : binding;
        }

        ITypeBinding declaration = binding.getTypeDeclaration();
        return declaration != null ? declaration : binding;
    }

    private List<CompletionItem> collectCompletionSuggestions(CompilationUnit unit, CompletionTarget target) {
        LinkedHashSet<CompletionItem> results = new LinkedHashSet<>();
        String currentPackage = extractPackageName(unit);
        Set<String> visited = new HashSet<>();
        addMembers(target.type(), target.staticContext(), currentPackage, results, visited);

        return new ArrayList<>(results);
    }

    private String extractPackageName(CompilationUnit unit) {
        if (unit == null)
            return "";

        PackageDeclaration declaration = unit.getPackage();
        if (declaration == null || declaration.getName() == null)
            return "";

        return declaration.getName().getFullyQualifiedName();
    }

    private void addMembers(ITypeBinding type, boolean staticContext, String currentPackage,
                            LinkedHashSet<CompletionItem> results, Set<String> visited) {
        if (type == null)
            return;

        ITypeBinding working = type;
        if (!working.isArray() && !working.isPrimitive()) {
            working = working.getErasure();
        }

        String key = working.getKey();
        if (key == null) {
            key = working.getQualifiedName();
        }

        if (key == null || !visited.add(key))
            return;

        if (working.isArray()) {
            results.add(new CompletionItem("length", "length : int"));
            addMembers(working.getSuperclass(), staticContext, currentPackage, results, visited);
            return;
        }

        if (working.isPrimitive())
            return;

        for (IVariableBinding field : working.getDeclaredFields()) {
            if (field.isSynthetic())
                continue;

            int modifiers = field.getModifiers();
            boolean isStatic = Modifier.isStatic(modifiers);
            if (staticContext && !isStatic)
                continue;
            else if (isStatic)
                continue;

            if (!isAccessible(modifiers, field.getDeclaringClass(), currentPackage))
                continue;

            results.add(createFieldItem(field));
        }

        for (IMethodBinding method : working.getDeclaredMethods()) {
            if (method.isConstructor() || method.isSynthetic())
                continue;

            int modifiers = method.getModifiers();
            boolean isStatic = Modifier.isStatic(modifiers);
            if (staticContext && !isStatic)
                continue;
            else if (isStatic)
                continue;

            if (!isAccessible(modifiers, method.getDeclaringClass(), currentPackage))
                continue;

            results.add(createMethodItem(method));
        }

        if (staticContext) {
            for (ITypeBinding nested : working.getDeclaredTypes()) {
                if (nested == null || nested.getName() == null || nested.getName().isBlank())
                    continue;

                int modifiers = nested.getModifiers();
                if (!Modifier.isStatic(modifiers))
                    continue;

                if (!isAccessible(modifiers, nested, currentPackage))
                    continue;

                results.add(createTypeItem(nested));
            }
        }

        addMembers(working.getSuperclass(), staticContext, currentPackage, results, visited);
        for (ITypeBinding iface : working.getInterfaces()) {
            addMembers(iface, staticContext, currentPackage, results, visited);
        }
    }

    private CompletionItem createFieldItem(IVariableBinding field) {
        String name = field.getName();
        String type = renderType(field.getType());
        String prefix = Modifier.isStatic(field.getModifiers()) ? "static " : "";
        return new CompletionItem(name, prefix + name + " : " + type);
    }

    private CompletionItem createMethodItem(IMethodBinding method) {
        String name = method.getName();
        String prefix = Modifier.isStatic(method.getModifiers()) ? "static " : "";
        String parameters = Arrays.stream(method.getParameterTypes())
            .map(this::renderType)
            .collect(Collectors.joining(", "));
        String returnType = renderType(method.getReturnType());
        String display = prefix + name + "(" + parameters + ") : " + returnType;
        return new CompletionItem(name, display);
    }

    private CompletionItem createTypeItem(ITypeBinding type) {
        String name = type.getName();
        String prefix = Modifier.isStatic(type.getModifiers()) ? "static " : "";
        return new CompletionItem(name, prefix + name + " (type)");
    }

    private String renderType(ITypeBinding type) {
        if (type == null)
            return "?";

        if (type.isArray())
            return renderType(type.getComponentType()) + "[]";

        if (type.isPrimitive() || type.isNullType())
            return type.getName();

        ITypeBinding erasure = type.getErasure();
        String qualified = erasure != null ? erasure.getQualifiedName() : type.getQualifiedName();
        if (qualified != null && !qualified.isBlank())
            return qualified;

        String name = type.getName();
        return name != null && !name.isBlank() ? name : "?";
    }

    private boolean isAccessible(int modifiers, ITypeBinding declaringType, String currentPackage) {
        if (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers))
            return true;

        if (Modifier.isPrivate(modifiers))
            return false;

        String declaringPackage = declaringType != null ? packageName(declaringType) : "";
        return Objects.equals(declaringPackage, currentPackage);
    }

    private String packageName(ITypeBinding type) {
        if (type == null)
            return "";

        IPackageBinding pkg = type.getPackage();
        if (pkg == null)
            return "";

        String name = pkg.getName();
        return name != null ? name : "";
    }

    private void handleCompletionResult(CompletionResult result) {
        if (result.items().isEmpty()) {
            hideAutoComplete();
            return;
        }

        completionCandidates.clear();
        completionCandidates.addAll(result.items());
        completionDotIndex = result.dotIndex();
        showAutoComplete();
    }

    private void showAutoComplete() {
        hideAutoComplete(false);

        RRListView<CompletionItem> listView = new RRListView<>();
        listView.setCellFactory(view -> new CompletionItemListCell());
        listView.getItems().setAll(completionCandidates);
        listView.getSelectionModel().selectFirst();
        listView.setOnMouseClicked(event -> completeFromSelection(listView));

        var popup = new Popup();
        popup.setAutoHide(true);
        popup.getContent().add(listView);

        activeCompletionList.set(listView);
        activeCompletionPopup.set(popup);

        completionFilterListener = (obs, oldText, newText) -> filterAutoComplete(newText);
        textProperty().addListener(completionFilterListener);

        Optional<Bounds> caretBounds = getCaretBounds();
        if (caretBounds.isPresent()) {
            Bounds bounds = caretBounds.get();
            popup.show(this, bounds.getMaxX(), bounds.getMaxY());
        } else {
            Point2D screen = localToScreen(0, 0);
            popup.show(this, screen.getX(), screen.getY());
        }
    }

    private void completeFromSelection(RRListView<CompletionItem> listView) {
        CompletionItem selected = listView.getSelectionModel().getSelectedItem();
        if (selected == null)
            return;

        int caret = getCaretPosition();
        int start = Math.min(completionDotIndex + 1, caret);
        String insert = selected.insertText();
        replaceText(start, caret, insert);
        moveTo(start + insert.length());
        hideAutoComplete();
    }

    private void filterAutoComplete(String text) {
        Popup popup = activeCompletionPopup.get();
        RRListView<CompletionItem> listView = activeCompletionList.get();
        if (popup == null || listView == null || !popup.isShowing())
            return;

        int caret = getCaretPosition();
        if (completionDotIndex < 0 || completionDotIndex >= text.length() || text.charAt(completionDotIndex) != '.') {
            hideAutoComplete();
            return;
        }

        if (caret <= completionDotIndex) {
            hideAutoComplete();
            return;
        }

        String prefix = text.substring(completionDotIndex + 1, Math.min(caret, text.length()));
        List<CompletionItem> filtered = completionCandidates.stream()
            .filter(item -> item.insertText().startsWith(prefix))
            .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            hideAutoComplete();
        } else {
            listView.getItems().setAll(filtered);
            listView.getSelectionModel().selectFirst();
        }
    }

    private void hideAutoComplete() {
        hideAutoComplete(true);
    }

    private void hideAutoComplete(boolean clearState) {
        Popup popup = activeCompletionPopup.getAndSet(null);
        if (popup != null) {
            popup.hide();
        }

        activeCompletionList.set(null);

        if (completionFilterListener != null) {
            textProperty().removeListener(completionFilterListener);
            completionFilterListener = null;
        }

        if (clearState) {
            completionCandidates.clear();
            completionDotIndex = -1;
        }
    }

    private void installBracketHighlighting() {
        caretPositionProperty().addListener(
            (obs, oldPos, newPos) -> updateBracketHighlight(newPos));
    }

    private void updateBracketHighlight(int caretPosition) {
        String text = getText();
        if (text.isEmpty()) {
            clearBracketHighlight();
            return;
        }

        int index = caretPosition - 1;
        boolean lookForward;
        if (index >= 0 && index < text.length() && OPENING_BRACKETS.containsKey(text.charAt(index))) {
            lookForward = true;
        } else if (caretPosition < text.length() && OPENING_BRACKETS.containsKey(text.charAt(caretPosition))) {
            index = caretPosition;
            lookForward = true;
        } else if (index >= 0 && index < text.length() && CLOSING_BRACKETS.containsKey(text.charAt(index))) {
            lookForward = false;
        } else if (caretPosition < text.length() && CLOSING_BRACKETS.containsKey(text.charAt(caretPosition))) {
            index = caretPosition;
            lookForward = false;
        } else {
            clearBracketHighlight();
            return;
        }

        Character bracket = text.charAt(index);
        int match = lookForward
            ? findMatchingForward(text, index, OPENING_BRACKETS.getOrDefault(bracket, bracket))
            : findMatchingBackward(text, index, CLOSING_BRACKETS.getOrDefault(bracket, bracket));

        if (match == -1) {
            clearBracketHighlight();
            return;
        }

        applyBracketHighlight(index, match);
    }

    private int findMatchingForward(String text, int start, char target) {
        char opening = text.charAt(start);
        int balance = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == opening) {
                balance++;
            } else if (c == target) {
                balance--;
                if (balance == 0)
                    return i;
            }
        }

        return -1;
    }

    private int findMatchingBackward(String text, int start, char target) {
        char closing = text.charAt(start);
        int balance = 0;
        for (int i = start; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == closing) {
                balance++;
            } else if (c == target) {
                balance--;
                if (balance == 0)
                    return i;
            }
        }

        return -1;
    }

    private void applyBracketHighlight(int first, int second) {
        clearBracketHighlight();
        bracketHighlightRange = new int[]{first, second};
        addBracketStyle(first);
        addBracketStyle(second);
    }

    private void clearBracketHighlight() {
        if (bracketHighlightRange == null)
            return;

        removeBracketStyle(bracketHighlightRange[0]);
        removeBracketStyle(bracketHighlightRange[1]);
        bracketHighlightRange = null;
    }

    private void restoreBracketHighlight() {
        if (bracketHighlightRange == null)
            return;

        addBracketStyle(bracketHighlightRange[0]);
        addBracketStyle(bracketHighlightRange[1]);
    }

    private void addBracketStyle(int position) {
        if (position < 0 || position >= getLength())
            return;

        List<String> styles = new ArrayList<>(getStyleAtPosition(position));
        if (!styles.contains("bracket-highlight")) {
            styles.add("bracket-highlight");
        }

        setStyle(position, position + 1, styles);
    }

    private void removeBracketStyle(int position) {
        if (position < 0 || position >= getLength())
            return;

        List<String> styles = new ArrayList<>(getStyleAtPosition(position));
        if (styles.remove("bracket-highlight")) {
            setStyle(position, position + 1, styles);
        }
    }

    private static long computeColumn(char[] source, int position) {
        int column = 1;
        for (int i = position - 1; i >= 0; i--) {
            char c = source[i];
            if (c == '\n' || c == '\r')
                break;

            column++;
        }

        return column;
    }

    private static Pair findIdentifierRangeBeforeDot(String text, int dotPosition) {
        int start = dotPosition - 1;
        while (start >= 0 && Character.isJavaIdentifierPart(text.charAt(start))) {
            start--;
        }

        start++;
        if (start > dotPosition)
            return null;

        return new Pair(start, dotPosition - start);
    }

    public String getLanguageId() {
        return "java";
    }

    private void handleMouseOverText(MouseOverTextEvent event) {
        int index = event.getCharacterIndex();
        ProblemDiagnostic diagnostic = findDiagnosticAt(index);
        if (diagnostic == null)
            return;

        Point2D position = event.getScreenPosition();
        showDiagnosticPopup(diagnostic, position.getX(), position.getY() + 6);
    }

    private void handleMouseMoved(MouseEvent event) {
        if (!diagnosticPopup.isShowing())
            return;

        int index = hit(event.getX(), event.getY())
            .getCharacterIndex()
            .orElse(-1);
        if (index < 0 || findDiagnosticAt(index) == null) {
            diagnosticPopup.hide();
        }
    }

    private void showAutocomplete(PlainTextChange change) {
        String inserted = change.getInserted();
        if (inserted == null || inserted.isEmpty())
            return;

        if (inserted.endsWith(".")) {
            int dotIndex = change.getPosition() + inserted.length() - 1;
            triggerCompletion(dotIndex);
        }
    }

    private record ProblemDiagnostic(
        Diagnostic.Kind kind,
        int start,
        int end,
        long line,
        long column,
        String message
    ) implements Diagnostic<JavaFileObject> {
        @Override
        public Diagnostic.Kind getKind() {
            return kind;
        }

        @Override
        public JavaFileObject getSource() {
            return null;
        }

        @Override
        public long getPosition() {
            return start;
        }

        @Override
        public long getStartPosition() {
            return start;
        }

        @Override
        public long getEndPosition() {
            return end;
        }

        @Override
        public long getLineNumber() {
            return line;
        }

        @Override
        public long getColumnNumber() {
            return column;
        }

        @Override
        public String getCode() {
            return null;
        }

        @Override
        public String getMessage(Locale locale) {
            return message;
        }
    }

    private record CompletionTarget(ITypeBinding type, boolean staticContext) {
    }

    private record CompletionResult(int dotIndex, List<CompletionItem> items) {
        static CompletionResult empty() {
            return new CompletionResult(-1, Collections.emptyList());
        }
    }

    private record CompletionItem(String insertText, String displayText) {
        @Override
        public @NotNull String toString() {
            return displayText;
        }
    }

    private record Pair(int start, int length) {
    }

    private static final class CompletionItemListCell extends ListCell<CompletionItem> {
        @Override
        protected void updateItem(CompletionItem item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item.displayText());
        }
    }
}
