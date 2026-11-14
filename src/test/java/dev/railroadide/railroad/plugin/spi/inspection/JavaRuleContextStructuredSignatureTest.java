package dev.railroadide.railroad.plugin.spi.inspection;

import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolIds;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolKind;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import dev.railroadide.railroad.ide.sst.semantic.api.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class JavaRuleContextStructuredSignatureTest {
    @Test
    public void indexedNestedGenericParameterRemainsOneArgument() {
        var context = context("Api.accept(null); Api.accept(null, null);", false);
        var calls = context.nodesOfKind("JAVA_METHOD_INVOCATION_EXPRESSION");
        assertEquals(2, calls.size());
        assertTrue(context.resolvedSymbol(calls.getFirst()).isEmpty());
        assertTrue(context.canResolveMethodInvocation(calls.getFirst()));
        assertFalse(context.canResolveMethodInvocation(calls.getLast()));
    }

    @Test
    public void indexedStructuredVarargsAcceptZeroAndMultipleArguments() {
        var context = context("Api.accept(); Api.accept(null, null);", true);
        for (var call : context.nodesOfKind("JAVA_METHOD_INVOCATION_EXPRESSION")) {
            assertTrue(context.resolvedSymbol(call).isEmpty());
            assertTrue(context.canResolveMethodInvocation(call));
        }
    }

    private static JavaRuleContext context(String calls, boolean varargs) {
        var owner = JavaSymbolIds.type("sample.Api");
        var string = new TypeReference("java.lang.String");
        var strings = new TypeReference("java.util.List", List.of(string), 0, null);
        var mapping = new TypeReference("java.util.Map", List.of(string, strings), 0, null);
        var function = new TypeReference("java.util.function.Function", List.of(
            new TypeReference("? super", List.of(strings), 0, null),
            new TypeReference("? extends", List.of(mapping), 0, null)), 0, null);
        var method = new SymbolDescriptor(
            JavaSymbolIds.method(owner, "accept", List.of("java.util.function.Function" + (varargs ? "[]" : ""))),
            SymbolKindId.from(JavaSymbolKind.METHOD), "accept", "sample.Api#accept", owner,
            new SymbolSignature(List.of(), List.of(new SymbolSignature.Parameter("mapper", function, varargs)),
                new TypeReference("void"), List.of()),
            null, new SymbolMetadata(Set.of("public", "static"), "public", false, List.of(), Map.of()), List.of());
        Path binary = Path.of("missing-api.class");
        var index = JavaProjectSemanticIndex.builder().putFile(new JavaProjectSemanticIndex.SourceFileIndex(binary,
            "sample", List.of(), List.of(
                new JavaProjectSemanticIndex.SymbolDescriptor(JavaSymbolKind.CLASS, "Api", "sample.Api", null,
                    null, binary, false, true),
                new JavaProjectSemanticIndex.SymbolDescriptor(JavaSymbolKind.METHOD, "accept", "sample.Api#accept",
                    "sample.Api", null, binary, true, false, method))))
            .build();
        String source = "import sample.Api; class Example { void run() { " + calls + " } }";
        return new JavaRuleContext(Path.of("Example.java"), source, JavaSemanticAnalyzer.analyzeFacts(source), index);
    }
}
