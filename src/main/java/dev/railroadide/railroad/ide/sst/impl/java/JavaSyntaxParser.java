package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.document.api.DocumentVersion;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.lexer.Lexer;
import dev.railroadide.railroad.ide.sst.syntax.api.*;
import dev.railroadide.railroad.ide.sst.syntax.internal.GreenElement;
import dev.railroadide.railroad.ide.sst.syntax.internal.GreenNode;
import dev.railroadide.railroad.ide.sst.syntax.internal.SyntaxInternalFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Creates Java syntax trees and recovery diagnostics from source snapshots or lexers.
 * Incremental parsing reuses unaffected top-level prefixes when possible and falls
 * back to parsing the whole document when a suitable reparse boundary is unavailable.
 */
public final class JavaSyntaxParser {
    private static final Set<String> INCREMENTAL_ANCHOR_KIND_IDS = Set.of(
        JavaSyntaxKinds.TYPE_DECLARATION.id(),
        JavaSyntaxKinds.CLASS_DECLARATION.id(),
        JavaSyntaxKinds.INTERFACE_DECLARATION.id(),
        JavaSyntaxKinds.ENUM_DECLARATION.id(),
        JavaSyntaxKinds.ANNOTATION_TYPE_DECLARATION.id(),
        JavaSyntaxKinds.RECORD_DECLARATION.id(),
        JavaSyntaxKinds.EMPTY_TYPE_DECLARATION.id());
    private static final String EOF_KIND_ID = JavaSyntaxKinds.tokenKind(JavaTokenType.EOF).id();
    private static final String MISSING_TOKEN_KIND_ID = SyntaxKind.MISSING_TOKEN.id();
    private static final String MISSING_TOKEN_PREFIX = "JAVA_MISSING_";
    private static final String ERROR_NODE_KIND_ID = JavaSyntaxKinds.ERROR.id();

    private JavaSyntaxParser() {
    }

    /**
     * Parses Java source text.
     * A fresh in-memory document identity and initial version are assigned.
     *
     * @param source Java source text to parse
     * @return parsed Java syntax tree
     */
    public static SyntaxTree parse(CharSequence source) {
        return parse(DocumentId.create(), source);
    }

    /**
     * Parses Java source text.
     * The document uses an in-memory URI and its initial version.
     *
     * @param documentId identity of the logical document
     * @param source Java source text to parse
     * @return parsed Java syntax tree
     */
    public static SyntaxTree parse(DocumentId documentId, CharSequence source) {
        return parse(documentId, DocumentUri.inMemory(documentId), source);
    }

    /**
     * Parses Java source text.
     * The document uses its initial version.
     *
     * @param documentId identity of the logical document
     * @param documentUri location associated with the document
     * @param source Java source text to parse
     * @return parsed Java syntax tree
     */
    public static SyntaxTree parse(DocumentId documentId, DocumentUri documentUri, CharSequence source) {
        return parse(documentId, documentUri, DocumentVersion.initial(), source);
    }

    /**
     * Parses Java source text.
     *
     * @param documentId identity of the logical document
     * @param documentUri location associated with the document
     * @param documentVersion version represented by the source text
     * @param source Java source text to parse
     * @return parsed Java syntax tree
     */
    public static SyntaxTree parse(
        DocumentId documentId,
        DocumentUri documentUri,
        DocumentVersion documentVersion,
        CharSequence source
    ) {
        return parse(new TextDocumentSnapshot(
            documentId,
            documentUri,
            documentVersion,
            "java",
            source,
            StandardCharsets.UTF_8));
    }

    /**
     * Parses the supplied Java snapshot.
     *
     * @param documentSnapshot immutable Java snapshot whose text and metadata the tree represents
     * @return parsed Java syntax tree
     * @throws IllegalArgumentException if the snapshot language is not Java
     */
    public static SyntaxTree parse(TextDocumentSnapshot documentSnapshot) {
        documentSnapshot = requireJavaSnapshot(documentSnapshot);
        try (var lexer = new JavaLexer(documentSnapshot.text())) {
            return parse(documentSnapshot, lexer);
        }
    }

    /**
     * Parses Java tokens from the supplied lexer.
     * A fresh in-memory document identity and initial version are assigned.
     * The snapshot text is reconstructed from the parsed tokens.
     *
     * @param lexer Java token source to consume; the caller retains responsibility for closing it
     * @return parsed Java syntax tree
     */
    public static SyntaxTree parse(Lexer<JavaTokenType> lexer) {
        return parse(DocumentId.create(), lexer);
    }

    /**
     * Parses Java tokens from the supplied lexer.
     * The document uses an in-memory URI and its initial version.
     * The snapshot text is reconstructed from the parsed tokens.
     *
     * @param documentId identity of the logical document
     * @param lexer Java token source to consume; the caller retains responsibility for closing it
     * @return parsed Java syntax tree
     */
    public static SyntaxTree parse(DocumentId documentId, Lexer<JavaTokenType> lexer) {
        return parse(documentId, DocumentUri.inMemory(documentId), lexer);
    }

    /**
     * Parses Java tokens from the supplied lexer.
     * The document uses its initial version.
     * The snapshot text is reconstructed from the parsed tokens.
     *
     * @param documentId identity of the logical document
     * @param documentUri location associated with the document
     * @param lexer Java token source to consume; the caller retains responsibility for closing it
     * @return parsed Java syntax tree
     */
    public static SyntaxTree parse(
        DocumentId documentId,
        DocumentUri documentUri,
        Lexer<JavaTokenType> lexer
    ) {
        return parse(documentId, documentUri, DocumentVersion.initial(), lexer);
    }

    /**
     * Parses Java tokens from the supplied lexer.
     * The snapshot text is reconstructed from the parsed tokens.
     *
     * @param documentId identity of the logical document
     * @param documentUri location associated with the document
     * @param documentVersion version represented by the source text
     * @param lexer Java token source to consume; the caller retains responsibility for closing it
     * @return parsed Java syntax tree
     */
    public static SyntaxTree parse(
        DocumentId documentId,
        DocumentUri documentUri,
        DocumentVersion documentVersion,
        Lexer<JavaTokenType> lexer
    ) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(documentUri, "documentUri");
        Objects.requireNonNull(documentVersion, "documentVersion");
        GreenNode root = new JavaGreenParser(Objects.requireNonNull(lexer, "lexer")).parseGreenTree();
        var documentSnapshot = new TextDocumentSnapshot(
            documentId,
            documentUri,
            documentVersion,
            "java",
            SyntaxInternalFactory.sourceText(root),
            StandardCharsets.UTF_8);
        return SyntaxInternalFactory.treeFromGreenRoot(documentSnapshot, root);
    }

    /**
     * Parses the supplied Java snapshot.
     *
     * @param documentSnapshot immutable Java snapshot whose text must match the supplied lexer input
     * @param lexer Java token source to consume; the caller retains responsibility for closing it
     * @return parsed Java syntax tree
     * @throws IllegalArgumentException if the snapshot language is not Java
     */
    public static SyntaxTree parse(
        TextDocumentSnapshot documentSnapshot,
        Lexer<JavaTokenType> lexer
    ) {
        documentSnapshot = requireJavaSnapshot(documentSnapshot);
        GreenNode root = new JavaGreenParser(Objects.requireNonNull(lexer, "lexer")).parseGreenTree();
        return SyntaxInternalFactory.treeFromGreenRoot(documentSnapshot, root);
    }

    /**
     * Parses Java source text and collects syntax recovery diagnostics.
     * A fresh in-memory document identity and initial version are assigned.
     *
     * @param source Java source text to parse
     * @return parsed tree and diagnostics for error nodes and inserted missing tokens
     */
    public static ParseResult parseWithDiagnostics(CharSequence source) {
        return parseWithDiagnostics(DocumentId.create(), source);
    }

    /**
     * Parses Java source text and collects syntax recovery diagnostics.
     * The document uses an in-memory URI and its initial version.
     *
     * @param documentId identity of the logical document
     * @param source Java source text to parse
     * @return parsed tree and diagnostics for error nodes and inserted missing tokens
     */
    public static ParseResult parseWithDiagnostics(DocumentId documentId, CharSequence source) {
        return parseWithDiagnostics(documentId, DocumentUri.inMemory(documentId), source);
    }

    /**
     * Parses Java source text and collects syntax recovery diagnostics.
     * The document uses its initial version.
     *
     * @param documentId identity of the logical document
     * @param documentUri location associated with the document
     * @param source Java source text to parse
     * @return parsed tree and diagnostics for error nodes and inserted missing tokens
     */
    public static ParseResult parseWithDiagnostics(
        DocumentId documentId,
        DocumentUri documentUri,
        CharSequence source
    ) {
        return parseWithDiagnostics(documentId, documentUri, DocumentVersion.initial(), source);
    }

    /**
     * Parses Java source text and collects syntax recovery diagnostics.
     *
     * @param documentId identity of the logical document
     * @param documentUri location associated with the document
     * @param documentVersion version represented by the source text
     * @param source Java source text to parse
     * @return parsed tree and diagnostics for error nodes and inserted missing tokens
     */
    public static ParseResult parseWithDiagnostics(
        DocumentId documentId,
        DocumentUri documentUri,
        DocumentVersion documentVersion,
        CharSequence source
    ) {
        return parseWithDiagnostics(new TextDocumentSnapshot(
            documentId,
            documentUri,
            documentVersion,
            "java",
            source,
            StandardCharsets.UTF_8));
    }

    /**
     * Parses the supplied Java snapshot and collects syntax recovery diagnostics.
     *
     * @param documentSnapshot immutable Java snapshot whose text and metadata the tree represents
     * @return parsed tree and diagnostics for error nodes and inserted missing tokens
     * @throws IllegalArgumentException if the snapshot language is not Java
     */
    public static ParseResult parseWithDiagnostics(TextDocumentSnapshot documentSnapshot) {
        SyntaxTree tree = parse(documentSnapshot);
        return new ParseResult(tree, collectSyntaxDiagnostics(tree.root()));
    }

    /**
     * Parses Java tokens from the supplied lexer and collects syntax recovery diagnostics.
     * A fresh in-memory document identity and initial version are assigned.
     * The snapshot text is reconstructed from the parsed tokens.
     *
     * @param lexer Java token source to consume; the caller retains responsibility for closing it
     * @return parsed tree and diagnostics for error nodes and inserted missing tokens
     */
    public static ParseResult parseWithDiagnostics(Lexer<JavaTokenType> lexer) {
        return parseWithDiagnostics(DocumentId.create(), lexer);
    }

    /**
     * Parses Java tokens from the supplied lexer and collects syntax recovery diagnostics.
     * The document uses an in-memory URI and its initial version.
     * The snapshot text is reconstructed from the parsed tokens.
     *
     * @param documentId identity of the logical document
     * @param lexer Java token source to consume; the caller retains responsibility for closing it
     * @return parsed tree and diagnostics for error nodes and inserted missing tokens
     */
    public static ParseResult parseWithDiagnostics(DocumentId documentId, Lexer<JavaTokenType> lexer) {
        return parseWithDiagnostics(documentId, DocumentUri.inMemory(documentId), lexer);
    }

    /**
     * Parses Java tokens from the supplied lexer and collects syntax recovery diagnostics.
     * The document uses its initial version.
     * The snapshot text is reconstructed from the parsed tokens.
     *
     * @param documentId identity of the logical document
     * @param documentUri location associated with the document
     * @param lexer Java token source to consume; the caller retains responsibility for closing it
     * @return parsed tree and diagnostics for error nodes and inserted missing tokens
     */
    public static ParseResult parseWithDiagnostics(
        DocumentId documentId,
        DocumentUri documentUri,
        Lexer<JavaTokenType> lexer
    ) {
        return parseWithDiagnostics(documentId, documentUri, DocumentVersion.initial(), lexer);
    }

    /**
     * Parses Java tokens from the supplied lexer and collects syntax recovery diagnostics.
     * The snapshot text is reconstructed from the parsed tokens.
     *
     * @param documentId identity of the logical document
     * @param documentUri location associated with the document
     * @param documentVersion version represented by the source text
     * @param lexer Java token source to consume; the caller retains responsibility for closing it
     * @return parsed tree and diagnostics for error nodes and inserted missing tokens
     */
    public static ParseResult parseWithDiagnostics(
        DocumentId documentId,
        DocumentUri documentUri,
        DocumentVersion documentVersion,
        Lexer<JavaTokenType> lexer
    ) {
        SyntaxTree tree = parse(documentId, documentUri, documentVersion, lexer);
        return new ParseResult(tree, collectSyntaxDiagnostics(tree.root()));
    }

    /**
     * Parses the supplied Java snapshot and collects syntax recovery diagnostics.
     *
     * @param documentSnapshot immutable Java snapshot whose text must match the supplied lexer input
     * @param lexer Java token source to consume; the caller retains responsibility for closing it
     * @return parsed tree and diagnostics for error nodes and inserted missing tokens
     * @throws IllegalArgumentException if the snapshot language is not Java
     */
    public static ParseResult parseWithDiagnostics(
        TextDocumentSnapshot documentSnapshot,
        Lexer<JavaTokenType> lexer
    ) {
        SyntaxTree tree = parse(documentSnapshot, lexer);
        return new ParseResult(tree, collectSyntaxDiagnostics(tree.root()));
    }

    /**
     * Parses an edited document, reusing its unaffected top-level prefix when possible.
     * The document identity, URI, language, and encoding are retained.
     * The snapshot version advances by one.
     *
     * @param previousTree syntax tree for the previous document snapshot
     * @param previousSource source text matching the snapshot carried by the previous tree
     * @param newSource complete source text after applying the edit
     * @param edit replacement describing the change from the previous source to the new source
     * @return updated tree, reuse plan, and whether a full reparse was necessary
     * @throws IllegalArgumentException if the previous source, edit bounds, or version are invalid
     */
    public static IncrementalParseResult parseIncremental(
        SyntaxTree previousTree,
        CharSequence previousSource,
        CharSequence newSource,
        TextEdit edit
    ) {
        Objects.requireNonNull(previousTree, "previousTree");
        verifyPreviousSource(previousTree, previousSource);
        TextDocumentSnapshot previousSnapshot = previousTree.documentSnapshot();
        return parseIncremental(previousTree, new TextDocumentSnapshot(
            previousSnapshot.id(),
            previousSnapshot.uri(),
            previousSnapshot.version().next(),
            previousSnapshot.languageId(),
            newSource,
            previousSnapshot.encoding()), edit);
    }

    /**
     * Parses an edited document, reusing its unaffected top-level prefix when possible.
     * The document identity, URI, language, and encoding are retained.
     *
     * @param previousTree syntax tree for the previous document snapshot
     * @param newVersion document version later than the previous tree version
     * @param previousSource source text matching the snapshot carried by the previous tree
     * @param newSource complete source text after applying the edit
     * @param edit replacement describing the change from the previous source to the new source
     * @return updated tree, reuse plan, and whether a full reparse was necessary
     * @throws IllegalArgumentException if the previous source, edit bounds, or version are invalid
     */
    public static IncrementalParseResult parseIncremental(
        SyntaxTree previousTree,
        DocumentVersion newVersion,
        CharSequence previousSource,
        CharSequence newSource,
        TextEdit edit
    ) {
        Objects.requireNonNull(previousTree, "previousTree");
        verifyPreviousSource(previousTree, previousSource);
        TextDocumentSnapshot previousSnapshot = previousTree.documentSnapshot();
        return parseIncremental(previousTree, new TextDocumentSnapshot(
            previousSnapshot.id(),
            previousSnapshot.uri(),
            Objects.requireNonNull(newVersion, "newVersion"),
            previousSnapshot.languageId(),
            newSource,
            previousSnapshot.encoding()), edit);
    }

    /**
     * Incrementally parses a later immutable snapshot of the same logical document.
     *
     * @param previousTree syntax tree for the preceding snapshot
     * @param newSnapshot complete later snapshot to parse
     * @param edit change transforming the previous snapshot text into the new text
     * @return incremental parse result carrying {@code newSnapshot}
     * @throws IllegalArgumentException if identity or version continuity is invalid
     */
    public static IncrementalParseResult parseIncremental(
        SyntaxTree previousTree,
        TextDocumentSnapshot newSnapshot,
        TextEdit edit
    ) {
        Objects.requireNonNull(previousTree, "previousTree");
        newSnapshot = requireJavaSnapshot(newSnapshot);
        edit = Objects.requireNonNull(edit, "edit");

        TextDocumentSnapshot previousSnapshot = previousTree.documentSnapshot();
        if (!newSnapshot.id().equals(previousSnapshot.id()))
            throw new IllegalArgumentException("newSnapshot must have the same document identity as previousTree");
        if (!newSnapshot.version().isAfter(previousSnapshot.version()))
            throw new IllegalArgumentException(
                "newSnapshot version must be later than previous tree version " + previousSnapshot.version());

        String previousSource = previousSnapshot.text();
        String newSource = newSnapshot.text();

        int oldLength = previousSource.length();
        int newLength = newSource.length();
        validateEdit(edit, oldLength, newLength);

        ReusePlan fallbackPlan = planReuse(previousTree, previousSource, newSource, edit);
        Optional<TopLevelReparseWindow> incrementalWindow = selectTopLevelWindow(previousTree.root(), edit, oldLength,
            newLength);
        if (incrementalWindow.isEmpty()) {
            SyntaxTree reparsed = parse(newSnapshot);
            return new IncrementalParseResult(reparsed, fallbackPlan, true);
        }

        try {
            TopLevelReparseWindow window = incrementalWindow.get();
            SyntaxTree incrementalTree = reparseTopLevelTail(previousTree, newSnapshot, window);
            ReusePlan incrementalPlan = buildReusePlan(
                previousTree.root(),
                window.oldReparseStart(),
                window.oldReparseEnd(),
                edit,
                oldLength,
                newLength);
            return new IncrementalParseResult(incrementalTree, incrementalPlan, false);
        } catch (RuntimeException _) {
            SyntaxTree reparsed = parse(newSnapshot);
            return new IncrementalParseResult(reparsed, fallbackPlan, true);
        }
    }

    /**
     * Plans a reparse range around the smallest syntax node covering the edit.
     * Subtrees outside that range are recorded as potential reuse candidates.
     * This method describes potential reuse without parsing the new source.
     *
     * @param previousTree syntax tree for the previous document snapshot
     * @param previousSource source text matching the snapshot carried by the previous tree
     * @param newSource complete source text after applying the edit
     * @param edit replacement describing the change from the previous source to the new source
     * @return reparse ranges in the previous and new sources, with mapped reuse candidates
     * @throws IllegalArgumentException if the edit is out of bounds or its length change is inconsistent
     */
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
        if (coveringNode == null) {
            coveringNode = root;
        }

        if (coveringNode instanceof SyntaxToken) {
            Optional<SyntaxNode> parent = coveringNode.parent();
            if (parent.isPresent()) {
                coveringNode = parent.get();
            }
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
        return new ReusePlan(clampedOldStart, clampedOldEnd, newReparseStart, newReparseEnd, delta,
            List.copyOf(candidates));
    }

    private static void validateEdit(TextEdit edit, int oldLength, int newLength) {
        int startOffset = edit.startOffset();
        int removedLength = edit.removedLength();
        if (startOffset > oldLength)
            throw new IllegalArgumentException("edit startOffset exceeds previous source length");

        if (startOffset + removedLength > oldLength)
            throw new IllegalArgumentException("edit removedLength exceeds previous source length");

        int expectedNewLength = oldLength - removedLength + edit.insertedText().length();
        if (expectedNewLength != newLength)
            throw new IllegalArgumentException("new source length does not match edit delta: expected " +
                expectedNewLength + ", got " + newLength);
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

        int affectedChildIndex = findAffectedTopLevelChild(topLevelChildren, edit.startOffset(), edit.oldEndOffset(),
            oldLength);
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

        return Optional.of(new TopLevelReparseWindow(affectedChildIndex, oldReparseStart, oldReparseEnd,
            newReparseStart, newReparseEnd));
    }

    private static SyntaxTree reparseTopLevelTail(
        SyntaxTree previousTree,
        TextDocumentSnapshot newSnapshot,
        TopLevelReparseWindow window
    ) {
        List<SyntaxNode> previousChildren = previousTree.root().children();
        List<GreenElement> mergedChildren = new ArrayList<>(previousChildren.size());
        for (int index = 0; index < window.startChildIndex(); index++) {
            mergedChildren.add(SyntaxInternalFactory.greenElement(previousChildren.get(index)));
        }

        CharSequence tailSource = newSnapshot.text().subSequence(window.newReparseStart(), window.newReparseEnd());
        SyntaxTree reparsedTail = parse(tailSource);
        GreenNode reparsedTailRoot = SyntaxInternalFactory.greenRoot(reparsedTail);
        mergedChildren.addAll(reparsedTailRoot.children());

        GreenNode mergedRoot = SyntaxInternalFactory.greenNode(JavaSyntaxKinds.COMPILATION_UNIT, mergedChildren);
        return SyntaxInternalFactory.treeFromGreenRoot(newSnapshot, mergedRoot);
    }

    private static TextDocumentSnapshot requireJavaSnapshot(TextDocumentSnapshot documentSnapshot) {
        documentSnapshot = Objects.requireNonNull(documentSnapshot, "documentSnapshot");
        if (!"java".equalsIgnoreCase(documentSnapshot.languageId()))
            throw new IllegalArgumentException("Java parser requires a snapshot with languageId 'java'");
        return documentSnapshot;
    }

    private static void verifyPreviousSource(SyntaxTree previousTree, CharSequence previousSource) {
        previousSource = Objects.requireNonNull(previousSource, "previousSource");
        if (!previousTree.documentSnapshot().text().contentEquals(previousSource))
            throw new IllegalArgumentException(
                "previousSource must match the immutable snapshot carried by previousTree");
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
                    node.end()));
            } else if (node instanceof SyntaxToken && isMissingTokenKind(kindId)) {
                diagnostics.add(new SyntaxDiagnostic(
                    SyntaxDiagnostic.Severity.ERROR,
                    "JAVA_MISSING_TOKEN",
                    "Inserted missing token",
                    node.start(),
                    node.end()));
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

    /**
     * Describes one replacement in the previous source using UTF-16 offsets.
     *
     * @param startOffset zero-based start of the replaced range in the previous source
     * @param removedLength number of UTF-16 code units removed
     * @param insertedText replacement text, which may be empty
     */
    public record TextEdit(
        int startOffset,
        int removedLength,
        String insertedText
    ) {
        /**
         * Creates a replacement with nonnegative offsets and a non-null replacement.
         *
         * @param startOffset zero-based start of the replaced range
         * @param removedLength number of UTF-16 code units removed
         * @param insertedText text inserted at the start offset
         * @throws IllegalArgumentException if the start offset or removed length is negative
         */
        public TextEdit {
            if (startOffset < 0)
                throw new IllegalArgumentException("startOffset cannot be negative");
            if (removedLength < 0)
                throw new IllegalArgumentException("removedLength cannot be negative");
            insertedText = Objects.requireNonNull(insertedText, "insertedText");
        }

        /**
         * Returns the end of the replaced range in the previous source.
         *
         * @return exclusive UTF-16 end offset before applying the edit
         */
        public int oldEndOffset() {
            return startOffset + removedLength;
        }

        /**
         * Calculates the change in source length caused by this replacement.
         *
         * @return inserted length minus removed length, in UTF-16 code units
         */
        public int lengthDelta() {
            return insertedText.length() - removedLength;
        }
    }

    /**
     * Identifies a syntax subtree outside the planned reparse range.
     * All ranges use zero-based UTF-16 offsets with exclusive end offsets.
     *
     * @param kindId syntax kind identifier of the candidate subtree
     * @param oldStartOffset subtree start in the previous source
     * @param oldEndOffset subtree end in the previous source
     * @param newStartOffset mapped subtree start in the new source
     * @param newEndOffset mapped subtree end in the new source
     */
    public record ReuseCandidate(
        String kindId,
        int oldStartOffset,
        int oldEndOffset,
        int newStartOffset,
        int newEndOffset
    ) {
    }

    /**
     * Describes a reparse range and subtrees that may be reused outside it.
     * Candidates describe potential reuse, including when parsing falls back to a full reparse.
     * All ranges use zero-based UTF-16 offsets with exclusive end offsets.
     *
     * @param oldReparseStart start of the reparse range in the previous source
     * @param oldReparseEnd end of the reparse range in the previous source
     * @param newReparseStart start of the corresponding range in the new source
     * @param newReparseEnd end of the corresponding range in the new source
     * @param lengthDelta change in total source length caused by the edit
     * @param candidates syntax subtrees outside the reparse range
     */
    public record ReusePlan(
        int oldReparseStart,
        int oldReparseEnd,
        int newReparseStart,
        int newReparseEnd,
        int lengthDelta,
        List<ReuseCandidate> candidates
    ) {
    }

    /**
     * Contains the updated syntax tree and the outcome of an incremental parse attempt.
     *
     * @param tree syntax tree for the new document snapshot
     * @param reusePlan planned reparse range and candidate subtrees
     * @param fullReparse whether parsing fell back to rebuilding the entire tree
     */
    public record IncrementalParseResult(
        SyntaxTree tree,
        ReusePlan reusePlan,
        boolean fullReparse
    ) {
    }

    /**
     * Pairs a parsed syntax tree with diagnostics for error nodes and inserted missing tokens.
     *
     * @param tree parsed syntax tree
     * @param diagnostics immutable list of syntax recovery diagnostics
     */
    public record ParseResult(
        SyntaxTree tree,
        List<SyntaxDiagnostic> diagnostics
    ) {
        /**
         * Creates a parse result with a defensive copy of its diagnostics.
         *
         * @param tree parsed syntax tree
         * @param diagnostics syntax recovery diagnostics to copy
         */
        public ParseResult {
            tree = Objects.requireNonNull(tree, "tree");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }
}
