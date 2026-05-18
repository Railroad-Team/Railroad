package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.RegisteredInspection;
import dev.railroadide.railroad.ide.diagnostics.rules.java.JavaSemanticRules;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxKinds;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.util.*;

@RegisteredInspection
public class CoreRedundantInterfaceDeclarationInspection implements JavaInspectionRuleProvider {
    public static final String ID = "railroad:core-redundant-interface-declaration";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<JavaInspectionRule> rules() {
        return List.of(
            new SimpleJavaInspectionRule(
                JavaSemanticRules.REDUNDANT_INTERFACE_DECLARATION.id(),
                JavaSemanticRules.REDUNDANT_INTERFACE_DECLARATION.defaultSeverity(),
                JavaSemanticRules.REDUNDANT_INTERFACE_DECLARATION.messageTemplate(),
                Set.of("core", "code-quality"),
                CoreRedundantInterfaceDeclarationInspection::reportRedundantInterfaceDeclaration
            )
        );
    }

    private static void reportRedundantInterfaceDeclaration(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
        for (Map.Entry<String, SyntaxNode> entry : context.localTypeDeclarations().entrySet()) {
            String ownerQualifiedName = entry.getKey();
            SyntaxNode declarationNode = entry.getValue();
            List<InterfaceRef> directInterfaces = directDeclaredInterfaces(context, declarationNode);
            if (directInterfaces.isEmpty())
                continue;

            String directSuperclass = directSuperclass(context, declarationNode);
            Set<String> seenQualifiedNames = new HashSet<>();
            for (int i = 0; i < directInterfaces.size(); i++) {
                InterfaceRef candidate = directInterfaces.get(i);
                String candidateQualifiedName = candidate.qualifiedName();
                if (!seenQualifiedNames.add(candidateQualifiedName)) {
                    reporter.report(candidate.node(), context.simpleTypeName(ownerQualifiedName), context.simpleTypeName(candidateQualifiedName));
                    continue;
                }

                if (directSuperclass != null && context.isSubtype(directSuperclass, candidateQualifiedName)) {
                    reporter.report(candidate.node(), context.simpleTypeName(ownerQualifiedName), context.simpleTypeName(candidateQualifiedName));
                    continue;
                }

                for (int j = 0; j < directInterfaces.size(); j++) {
                    if (i == j)
                        continue;

                    InterfaceRef other = directInterfaces.get(j);
                    String otherQualifiedName = other.qualifiedName();
                    if (Objects.equals(candidateQualifiedName, otherQualifiedName))
                        continue;

                    if (context.isSubtype(otherQualifiedName, candidateQualifiedName)) {
                        reporter.report(candidate.node(), context.simpleTypeName(ownerQualifiedName), context.simpleTypeName(candidateQualifiedName));
                        break;
                    }
                }
            }
        }
    }

    private static String directSuperclass(JavaRuleContext context, SyntaxNode declarationNode) {
        String kindId = declarationNode.kind().id();
        if (!Objects.equals(JavaSyntaxKinds.CLASS_DECLARATION.id(), kindId) && !Objects.equals(JavaSyntaxKinds.ENUM_DECLARATION.id(), kindId) && !Objects.equals(JavaSyntaxKinds.RECORD_DECLARATION.id(), kindId))
            return null;

        SyntaxNode clauseNode = context.directChild(declarationNode, JavaSyntaxKinds.EXTENDS_CLAUSE.id());
        if (clauseNode == null)
            return null;

        for (SyntaxNode child : clauseNode.children()) {
            if (!Objects.equals(JavaSyntaxKinds.TYPE_REFERENCE.id(), child.kind().id()))
                continue;

            String qualifiedName = context.resolveQualifiedTypeName(child);
            if (qualifiedName == null || qualifiedName.isBlank())
                continue;

            return qualifiedName;
        }

        return null;
    }

    private static List<InterfaceRef> directDeclaredInterfaces(JavaRuleContext context, SyntaxNode declarationNode) {
        String kindId = declarationNode.kind().id();

        SyntaxNode clauseNode;
        if (Objects.equals(JavaSyntaxKinds.INTERFACE_DECLARATION.id(), kindId) || Objects.equals(JavaSyntaxKinds.ANNOTATION_TYPE_DECLARATION.id(), kindId)) {
            clauseNode = context.directChild(declarationNode, JavaSyntaxKinds.EXTENDS_CLAUSE.id());
        } else if (Objects.equals(JavaSyntaxKinds.CLASS_DECLARATION.id(), kindId)
            || Objects.equals(JavaSyntaxKinds.ENUM_DECLARATION.id(), kindId)
            || Objects.equals(JavaSyntaxKinds.RECORD_DECLARATION.id(), kindId)) {
            clauseNode = context.directChild(declarationNode, JavaSyntaxKinds.IMPLEMENTS_CLAUSE.id());
        } else {
            return List.of();
        }

        if (clauseNode == null)
            return List.of();

        List<InterfaceRef> interfaces = new ArrayList<>();
        for (SyntaxNode child : clauseNode.children()) {
            if (Objects.equals(JavaSyntaxKinds.TYPE_REFERENCE.id(), child.kind().id())) {
                String qualifiedName = context.resolveQualifiedTypeName(child);
                if (qualifiedName == null || qualifiedName.isBlank())
                    continue;

                if (!context.isInterfaceType(qualifiedName))
                    continue;

                interfaces.add(new InterfaceRef(qualifiedName, child));
            }
        }

        return List.copyOf(interfaces);
    }

    private record InterfaceRef(String qualifiedName, SyntaxNode node) {
    }
}
