package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.lexer.Lexer;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxDiagnostic;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxKind;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTree;
import dev.railroadide.railroad.ide.sst.syntax.internal.GreenElement;
import dev.railroadide.railroad.ide.sst.syntax.internal.GreenNode;
import dev.railroadide.railroad.ide.sst.syntax.internal.SyntaxInternalFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class JavaSyntaxParser {
    private static final Set<String> INCREMENTAL_ANCHOR_KIND_IDS = Set.of(
            JavaSyntaxKinds.TYPE_DECLARATION.id(),
            JavaSyntaxKinds.CLASS_DECLARATION.id(),
            JavaSyntaxKinds.INTERFACE_DECLARATION.id(),
            JavaSyntaxKinds.ENUM_DECLARATION.id(),
            JavaSyntaxKinds.ANNOTATION_TYPE_DECLARATION.id(),
            JavaSyntaxKinds.RECORD_DECLARATION.id(),
            JavaSyntaxKinds.EMPTY_TYPE_DECLARATION.id()
    );
    private static final String EOF_KIND_ID = JavaSyntaxKinds.tokenKind(JavaTokenType.EOF).id();
    private static final String MISSING_TOKEN_KIND_ID = SyntaxKind.MISSING_TOKEN.id();
    private static final String MISSING_TOKEN_PREFIX = "JAVA_MISSING_";
    private static final String ERROR_NODE_KIND_ID = JavaSyntaxKinds.ERROR.id();

    private JavaSyntaxParser() {
    }

    public static SyntaxTree parse(CharSequence source) {
        Objects.requireNonNull(source, "source");
        try (var lexer = new JavaLexer(source)) {
            return parse(lexer);
        }
    }

    public static SyntaxTree parse(Lexer<JavaTokenType> lexer) {
        return new JavaGreenParser(Objects.requireNonNull(lexer, "lexer")).parseSyntaxTree();
    }

    public static ParseResult parseWithDiagnostics(CharSequence source) {
        Objects.requireNonNull(source, "source");
        try (var lexer = new JavaLexer(source)) {
            return parseWithDiagnostics(lexer);
        }
    }

    public static ParseResult parseWithDiagnostics(Lexer<JavaTokenType> lexer) {
        SyntaxTree tree = parse(lexer);
        return new ParseResult(tree, collectSyntaxDiagnostics(tree.root()));
    }

    public static IncrementalParseResult parseIncremental(
            SyntaxTree previousTree,
            CharSequence previousSource,
            CharSequence newSource,
            TextEdit edit
    ) {
        Objects.requireNonNull(previousTree, "previousTree");
        Objects.requireNonNull(previousSource, "previousSource");
        Objects.requireNonNull(newSource, "newSource");
        Objects.requireNonNull(edit, "edit");

        int oldLength = previousSource.length();
        int newLength = newSource.length();
        validateEdit(edit, oldLength, newLength);

        ReusePlan fallbackPlan = planReuse(previousTree, previousSource, newSource, edit);
        Optional<TopLevelReparseWindow> incrementalWindow = selectTopLevelWindow(previousTree.root(), edit, oldLength, newLength);
        if (incrementalWindow.isEmpty()) {
            SyntaxTree reparsed = parse(newSource);
            return new IncrementalParseResult(reparsed, fallbackPlan, true);
        }

        try {
            TopLevelReparseWindow window = incrementalWindow.get();
            SyntaxTree incrementalTree = reparseTopLevelTail(previousTree, newSource, window);
            ReusePlan incrementalPlan = buildReusePlan(
                    previousTree.root(),
                    window.oldReparseStart(),
                    window.oldReparseEnd(),
                    edit,
                    oldLength,
                    newLength
            );
            return new IncrementalParseResult(incrementalTree, incrementalPlan, false);
        } catch (RuntimeException ignored) {
            SyntaxTree reparsed = parse(newSource);
            return new IncrementalParseResult(reparsed, fallbackPlan, true);
        }
    }

    public static ReusePlan planReuse(
            SyntaxTree previousTree,
            CharSequence previousSource,
            CharSequence newSource,
            TextEdit edit
    ) {
        Objects.requireNonNull(previousTree, "previousTree");
        Objects.requireNonNull(previousSource, "previousSource");
        Objects.requireNonNull(newSource, "newSource");
        Objects.requireNonNull(edit, "edit");

        int oldLength = previousSource.length();
        int newLength = newSource.length();
        validateEdit(edit, oldLength, newLength);

        int oldEditStart = edit.startOffset();
        int oldEditEnd = oldEditStart + edit.removedLength();

        SyntaxNode root = previousTree.root();
        SyntaxNode coveringNode = findSmallestCoveringNode(root, oldEditStart, oldEditEnd);
        if (coveringNode == null)
            coveringNode = root;

        if (coveringNode instanceof SyntaxToken) {
            Optional<SyntaxNode> parent = coveringNode.parent();
            if (parent.isPresent())
                coveringNode = parent.get();
        }

        int oldReparseStart = clamp(coveringNode.start(), 0, oldLength);
        int oldReparseEnd = clamp(coveringNode.end(), oldReparseStart, oldLength);
        return buildReusePlan(root, oldReparseStart, oldReparseEnd, edit, oldLength, newLength);
    }

    private static ReusePlan buildReusePlan(
            SyntaxNode root,
            int oldReparseStart,
            int oldReparseEnd,
            TextEdit edit,
            int oldLength,
            int newLength
    ) {
        int clampedOldStart = clamp(oldReparseStart, 0, oldLength);
        int clampedOldEnd = clamp(oldReparseEnd, clampedOldStart, oldLength);
        int newReparseStart = clamp(mapOldOffsetToNew(clampedOldStart, edit), 0, newLength);
        int newReparseEnd = clamp(mapOldOffsetToNew(clampedOldEnd, edit), newReparseStart, newLength);
        int delta = edit.lengthDelta();
        List<ReuseCandidate> candidates = new ArrayList<>();
        collectReuseCandidates(root, clampedOldStart, clampedOldEnd, delta, candidates);
        return new ReusePlan(clampedOldStart, clampedOldEnd, newReparseStart, newReparseEnd, delta, List.copyOf(candidates));
    }

    private static void validateEdit(TextEdit edit, int oldLength, int newLength) {
        int startOffset = edit.startOffset();
        int removedLength = edit.removedLength();
        if (startOffset > oldLength)
            throw new IllegalArgumentException("edit startOffset exceeds previous source length");

        if (startOffset + removedLength > oldLength)
            throw new IllegalArgumentException("edit removedLength exceeds previous source length");

        int expectedNewLength = oldLength - removedLength + edit.insertedText().length();
        if (expectedNewLength != newLength) {
            throw new IllegalArgumentException("new source length does not match edit delta: expected " +
                    expectedNewLength + ", got " + newLength);
        }
    }

    private static Optional<TopLevelReparseWindow> selectTopLevelWindow(
            SyntaxNode root,
            TextEdit edit,
            int oldLength,
            int newLength
    ) {
        List<SyntaxNode> topLevelChildren = root.children();
        if (topLevelChildren.isEmpty())
            return Optional.empty();

        int affectedChildIndex = findAffectedTopLevelChild(topLevelChildren, edit.startOffset(), edit.oldEndOffset(), oldLength);
        if (affectedChildIndex < 0)
            return Optional.empty();

        SyntaxNode affected = topLevelChildren.get(affectedChildIndex);
        if (isEofNode(affected)) {
            if (affectedChildIndex == 0)
                return Optional.empty();

            affectedChildIndex--;
            affected = topLevelChildren.get(affectedChildIndex);
        }

        if (!isIncrementalAnchor(affected))
            return Optional.empty();

        int oldReparseStart = clamp(affected.start(), 0, oldLength);
        int oldReparseEnd = oldLength;
        int newReparseStart = clamp(mapOldOffsetToNew(oldReparseStart, edit), 0, newLength);
        int newReparseEnd = newLength;
        if (newReparseStart > newReparseEnd)
            return Optional.empty();

        return Optional.of(new TopLevelReparseWindow(affectedChildIndex, oldReparseStart, oldReparseEnd, newReparseStart, newReparseEnd));
    }

    private static SyntaxTree reparseTopLevelTail(
            SyntaxTree previousTree,
            CharSequence newSource,
            TopLevelReparseWindow window
    ) {
        List<SyntaxNode> previousChildren = previousTree.root().children();
        List<GreenElement> mergedChildren = new ArrayList<>(previousChildren.size());
        for (int index = 0; index < window.startChildIndex(); index++) {
            mergedChildren.add(SyntaxInternalFactory.greenElement(previousChildren.get(index)));
        }

        CharSequence tailSource = newSource.subSequence(window.newReparseStart(), window.newReparseEnd());
        SyntaxTree reparsedTail = parse(tailSource);
        GreenNode reparsedTailRoot = SyntaxInternalFactory.greenRoot(reparsedTail);
        mergedChildren.addAll(reparsedTailRoot.children());

        GreenNode mergedRoot = SyntaxInternalFactory.greenNode(JavaSyntaxKinds.COMPILATION_UNIT, mergedChildren);
        return SyntaxInternalFactory.treeFromGreenRoot(mergedRoot);
    }

    private static int findAffectedTopLevelChild(
            List<SyntaxNode> topLevelChildren,
            int oldEditStart,
            int oldEditEnd,
            int oldLength
    ) {
        int probeStart = oldEditStart;
        int probeEnd = oldEditEnd;
        if (probeStart == probeEnd) {
            probeStart = Math.max(0, oldEditStart - 1);
            probeEnd = Math.min(oldLength, oldEditStart + 1);
        }

        for (int index = 0; index < topLevelChildren.size(); index++) {
            SyntaxNode child = topLevelChildren.get(index);
            if (rangesOverlap(child.start(), child.end(), probeStart, probeEnd))
                return index;
        }

        if (oldEditStart >= oldLength) {
            for (int index = topLevelChildren.size() - 1; index >= 0; index--) {
                if (!isEofNode(topLevelChildren.get(index)))
                    return index;
            }
        }

        return -1;
    }

    private static boolean rangesOverlap(int leftStart, int leftEnd, int rightStart, int rightEnd) {
        return leftStart < rightEnd && rightStart < leftEnd;
    }

    private static boolean isIncrementalAnchor(SyntaxNode node) {
        return INCREMENTAL_ANCHOR_KIND_IDS.contains(node.kind().id());
    }

    private static boolean isEofNode(SyntaxNode node) {
        return EOF_KIND_ID.equals(node.kind().id());
    }

    private static int mapOldOffsetToNew(int oldOffset, TextEdit edit) {
        int oldEditStart = edit.startOffset();
        int oldEditEnd = edit.oldEndOffset();
        if (oldOffset <= oldEditStart)
            return oldOffset;

        if (oldOffset >= oldEditEnd)
            return oldOffset + edit.lengthDelta();

        return oldEditStart + edit.insertedText().length();
    }

    private static SyntaxNode findSmallestCoveringNode(SyntaxNode node, int start, int endExclusive) {
        if (node.start() > start || node.end() < endExclusive)
            return null;

        for (SyntaxNode child : node.children()) {
            SyntaxNode covering = findSmallestCoveringNode(child, start, endExclusive);
            if (covering != null)
                return covering;
        }

        return node;
    }

    private static void collectReuseCandidates(
            SyntaxNode node,
            int oldReparseStart,
            int oldReparseEnd,
            int delta,
            List<ReuseCandidate> candidates
    ) {
        if (node instanceof SyntaxToken || node.width() == 0) {
            for (SyntaxNode child : node.children()) {
                collectReuseCandidates(child, oldReparseStart, oldReparseEnd, delta, candidates);
            }
            return;
        }

        int nodeStart = node.start();
        int nodeEnd = node.end();
        if (nodeEnd <= oldReparseStart || nodeStart >= oldReparseEnd) {
            int newStart = nodeStart >= oldReparseEnd ? nodeStart + delta : nodeStart;
            int newEnd = nodeEnd >= oldReparseEnd ? nodeEnd + delta : nodeEnd;
            candidates.add(new ReuseCandidate(node.kind().id(), nodeStart, nodeEnd, newStart, newEnd));
            return;
        }

        for (SyntaxNode child : node.children()) {
            collectReuseCandidates(child, oldReparseStart, oldReparseEnd, delta, candidates);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static List<SyntaxDiagnostic> collectSyntaxDiagnostics(SyntaxNode root) {
        List<SyntaxDiagnostic> diagnostics = new ArrayList<>();
        ArrayDeque<SyntaxNode> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            SyntaxNode node = stack.pop();
            String kindId = node.kind().id();
            if (ERROR_NODE_KIND_ID.equals(kindId)) {
                diagnostics.add(new SyntaxDiagnostic(
                        SyntaxDiagnostic.Severity.ERROR,
                        "JAVA_ERROR_NODE",
                        "Recovered syntax error node",
                        node.start(),
                        node.end()
                ));
            } else if (node instanceof SyntaxToken && isMissingTokenKind(kindId)) {
                diagnostics.add(new SyntaxDiagnostic(
                        SyntaxDiagnostic.Severity.ERROR,
                        "JAVA_MISSING_TOKEN",
                        "Inserted missing token",
                        node.start(),
                        node.end()
                ));
            }

            for (SyntaxNode child : node.children()) {
                stack.push(child);
            }
        }

        return List.copyOf(diagnostics);
    }

    private static boolean isMissingTokenKind(String kindId) {
        return MISSING_TOKEN_KIND_ID.equals(kindId) || kindId.startsWith(MISSING_TOKEN_PREFIX);
    }

    private record TopLevelReparseWindow(
            int startChildIndex,
            int oldReparseStart,
            int oldReparseEnd,
            int newReparseStart,
            int newReparseEnd
    ) {
    }

    public record TextEdit(
            int startOffset,
            int removedLength,
            String insertedText
    ) {
        public TextEdit {
            if (startOffset < 0)
                throw new IllegalArgumentException("startOffset cannot be negative");
            if (removedLength < 0)
                throw new IllegalArgumentException("removedLength cannot be negative");
            insertedText = Objects.requireNonNull(insertedText, "insertedText");
        }

        public int oldEndOffset() {
            return startOffset + removedLength;
        }

        public int lengthDelta() {
            return insertedText.length() - removedLength;
        }
    }

    public record ReuseCandidate(
            String kindId,
            int oldStartOffset,
            int oldEndOffset,
            int newStartOffset,
            int newEndOffset
    ) {
    }

    public record ReusePlan(
            int oldReparseStart,
            int oldReparseEnd,
            int newReparseStart,
            int newReparseEnd,
            int lengthDelta,
            List<ReuseCandidate> candidates
    ) {
    }

    public record IncrementalParseResult(
            SyntaxTree tree,
            ReusePlan reusePlan,
            boolean fullReparse
    ) {
    }

    public record ParseResult(
            SyntaxTree tree,
            List<SyntaxDiagnostic> diagnostics
    ) {
        public ParseResult {
            tree = Objects.requireNonNull(tree, "tree");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }
}
