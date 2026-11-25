package dev.railroadide.railroad.ide.signature;

import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.language.impl.index.JavaWorkspaceAnalysis;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxParser;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import dev.railroadide.railroad.ide.sst.project.JavaLibrarySources;
import dev.railroadide.railroad.ide.sst.request.api.*;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.Map;
import java.util.List;
import java.util.Set;

/**
 * Java parameter information from the SST argument list, semantic resolver and captured symbol index.
 *
 * @param analysis supplies the Java workspace analysis for a request
 */
public record JavaSignatureHelpProvider(Function<RequestContext, JavaWorkspaceAnalysis> analysis)
    implements SignatureHelpProvider {
    /**
     * Uses the shared Java workspace analysis.
     */
    public JavaSignatureHelpProvider() {
        this(JavaLanguageSupport.analysisContextProvider()::index);
    }

    @Override
    public @Nullable SignatureHelp compute(DocumentSnapshot snapshot, int caret) {
        var workspace = new WorkspaceSnapshot("standalone-java-signature", Map.of(snapshot.id(), snapshot),
            new WorkspaceConfigurationSnapshot(0, Set.of()), 0);
        return compute(workspace, snapshot.id(), caret);
    }

    @Override
    public @Nullable SignatureHelp compute(WorkspaceSnapshot workspace, DocumentId id, int caret) {
        return compute(new FeatureRequestContext(workspace, workspace.document(id).orElseThrow(),
            CancellationToken.none(), Deadline.none()), caret).value();
    }

    @Override
    public FeatureResult<SignatureHelp> compute(FeatureRequestContext context, int caret) {
        FeatureResult<List<SignatureHelp>> result = compute(context, caret, false);
        return new FeatureResult<>(context, result.value().isEmpty() ? null : result.value().getFirst());
    }

    @Override
    public FeatureResult<List<SignatureHelp>> compute(
        FeatureRequestContext context,
        int caret,
        boolean includeOverloads
    ) {
        context.checkActive();
        if (!(context.documentSnapshot() instanceof TextDocumentSnapshot text) || !"java".equals(text.languageId())
            || caret <= 0 || caret > text.text().length())
            return new FeatureResult<>(context, List.of());

        var tree = JavaSyntaxParser.parse(context);
        SyntaxNode arguments = argumentListAt(tree.root(), caret);
        if (arguments == null)
            return new FeatureResult<>(context, List.of());

        JavaWorkspaceAnalysis workspaceAnalysis = analysis.apply(context);
        List<SignatureHelp> result = JavaSemanticAnalyzer.signatureHelp(tree, arguments, caret, workspaceAnalysis,
            includeOverloads,
            workspaceAnalysis.attachedSignatures(JavaLibrarySources.shared(), context::checkActive),
            context::checkActive);
        context.checkActive();
        return new FeatureResult<>(context, result);
    }

    private static @Nullable SyntaxNode argumentListAt(SyntaxNode root, int caret) {
        SyntaxNode arguments = null;
        SyntaxNode node = root;
        while (node != null) {
            if (node.kind().equals(JavaSyntaxKinds.ARGUMENT_LIST)) {
                boolean inside = false;
                for (SyntaxNode child : node.children()) {
                    if (child.start() >= caret)
                        break;

                    if (child.kind().equals(JavaSyntaxKinds.tokenKind(JavaTokenType.OPEN_PAREN))) {
                        inside = true;
                    }

                    if (child.kind().equals(JavaSyntaxKinds.tokenKind(JavaTokenType.CLOSE_PAREN))) {
                        inside = false;
                    }
                }

                if (inside) {
                    arguments = node;
                }
            }

            SyntaxNode next = null;
            for (SyntaxNode child : node.children()) {
                if (child.start() < caret && caret <= child.end()) {
                    next = child;
                    break;
                }
            }

            node = next;
        }

        return arguments;
    }
}
