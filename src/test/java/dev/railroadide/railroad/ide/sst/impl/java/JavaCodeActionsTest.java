package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.diagnostics.DiagnosticOrigin;
import dev.railroadide.railroad.ide.diagnostics.DiagnosticSeverity;
import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic.TextEditorDiagnostic;
import dev.railroadide.railroad.ide.diagnostics.JavaDiagnosticsProvider;
import dev.railroadide.railroad.ide.language.LanguageSupports;
import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceChange;
import dev.railroadide.railroad.ide.sst.provider.api.CodeAction;
import dev.railroadide.railroad.ide.sst.provider.api.CodeActionRequest;
import dev.railroadide.railroad.ide.sst.request.api.CancellationToken;
import dev.railroadide.railroad.ide.sst.request.api.CancellationTokenSource;
import dev.railroadide.railroad.ide.sst.request.api.Deadline;
import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.plugin.spi.editor.TextReplacement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class JavaCodeActionsTest {
    @TempDir
    public static Path directory;
    private static JavaSstServices services;
    private static final AtomicInteger WORKSPACES = new AtomicInteger();
    private final String root = "w" + WORKSPACES.incrementAndGet() + "/";
    private final Map<String, TextDocumentSnapshot> documents = new LinkedHashMap<>();
    private DefaultDocumentStore store;

    @BeforeAll
    public static void startServices() {
        LanguageSupports.initialize();
        services = new JavaSstServices();
    }

    @AfterAll
    public static void closeServices() {
        services.close();
    }

    @Test
    public void addsTheOnlyMatchingTypeLazilyInSortedPosition() {
        source("p/A.java", """
            package p;

            import java.util.Map;

            class A {
                ArrayList<String> names;
                Map<String, String> values;
            }
            """);
        CodeAction action = single(actionsAt("p/A.java", "ArrayList<"));
        assertEquals("Import 'java.util.ArrayList'", action.title());
        assertTrue(action.available());
        assertTrue(action.preferred());
        assertNull(action.edit(), "Discovery must not compute the edit");

        CodeAction resolved = resolve("p/A.java", action);
        assertTrue(resolved.isResolved());
        assertEquals("""
            package p;

            import java.util.ArrayList;
            import java.util.Map;

            class A {
                ArrayList<String> names;
                Map<String, String> values;
            }
            """, applied("p/A.java", resolved));
    }

    @Test
    public void addsImportsAfterThePackageOrAtTheTopWhenThereAreNone() {
        source("p/A.java", """
            package p;

            class A { ArrayList<String> names; }
            """);
        assertEquals("""
            package p;

            import java.util.ArrayList;

            class A { ArrayList<String> names; }
            """, applied("p/A.java", resolve("p/A.java", single(actionsAt("p/A.java", "ArrayList")))));

        source("B.java", """
            // header
            class B { ArrayList<String> names; }
            """);
        assertEquals("""
            // header
            import java.util.ArrayList;

            class B { ArrayList<String> names; }
            """, applied("B.java", resolve("B.java", single(actionsAt("B.java", "ArrayList")))));
    }

    @Test
    public void importsQualifiersOfMemberAccessesAndMethodReferences() {
        source("p/A.java", """
            package p;
            class A {
                Object empty = Collections.emptyList();
                Runnable sort = Arrays::asList;
            }
            """);
        assertEquals("Import 'java.util.Collections'", single(actionsAt("p/A.java", "Collections.")).title());
        assertEquals("Import 'java.util.Arrays'", single(actionsAt("p/A.java", "Arrays::")).title());
        assertTrue(actionsAt("p/A.java", "emptyList").isEmpty());
    }

    @Test
    public void ambiguousNamesAreExplainedButNeverApplied() {
        source("p/A.java", """
            package p;
            class A { List<String> names; }
            """);
        CodeAction action = single(actionsAt("p/A.java", "List<"));
        assertFalse(action.available());
        assertNull(action.edit());
        assertTrue(action.unavailableReason().contains("java.util.List"), action.unavailableReason());
        assertTrue(action.unavailableReason().contains("java.awt.List"), action.unavailableReason());
        assertSame(action, resolve("p/A.java", action));
    }

    @Test
    public void offersNothingWhenTheNameMayAlreadyResolve() {
        source("p/Helper.java", """
            package p;
            public class Helper {}
            """);
        source("p/A.java", """
            package p;
            import java.util.*;
            import q.ArrayDeque;
            class A<Deque> {
                String text;
                Helper helper;
                ArrayList<String> fromWildcard;
                ArrayDeque<String> imported;
                Deque typeParameter;
                java.util.ArrayList<String> qualified;
                class Collections {}
                Collections local;
            }
            """);
        for (String marker : List.of("String text", "Helper helper", "ArrayList<String> from", "ArrayDeque<",
            "Deque typeParameter", "java.util", "Collections local")) {
            assertEquals(List.of(), actionsAt("p/A.java", marker), marker);
        }
    }

    @Test
    public void inheritedMemberTypesBlockImportsOfNestedNames() {
        source("p/A.java", """
            package p;
            class A extends java.util.HashMap<String, String> {
                Entry<String, String> first;
                ArrayList<String> names;
            }
            """);
        assertEquals(List.of(), actionsAt("p/A.java", "Entry<"));
        assertEquals("Import 'java.util.ArrayList'", single(actionsAt("p/A.java", "ArrayList")).title());
    }

    @Test
    public void importsProjectTypesAndExplainsInaccessibleOnes() {
        source("q/Widget.java", """
            package q;
            public class Widget {}
            """);
        source("q/Hidden.java", """
            package q;
            class Hidden {}
            """);
        source("q/Marker.java", """
            package q;
            public @interface Marker {}
            """);
        source("r/Marker.java", """
            package r;
            public class Marker {}
            """);
        source("p/A.java", """
            package p;
            @Marker
            class A { Widget widget; Hidden hidden; }
            """);
        CodeAction widget = single(actionsAt("p/A.java", "Widget widget"));
        assertEquals("Import 'q.Widget'", widget.title(), widget::toString);
        CodeAction hidden = single(actionsAt("p/A.java", "Hidden hidden"));
        assertFalse(hidden.available());
        assertTrue(hidden.unavailableReason().contains("not accessible"), hidden.unavailableReason());
        CodeAction marker = single(actionsAt("p/A.java", "Marker"));
        assertEquals("Import 'q.Marker'", marker.title());
        assertTrue(applied("p/A.java", resolve("p/A.java", marker)).startsWith("package p;\n\nimport q.Marker;\n"));
    }

    @Test
    public void removesUnusedSingleAndStaticImportsButNotUsedOnes() {
        source("p/A.java", """
            package p;

            import java.util.Map;
            import java.util.List;
            import java.util.Set;
            import java.io.*;
            import static java.util.Collections.emptyList;
            import static java.util.Collections.singleton;

            /** Holds a {@link Set}. */
            class A {
                List<String> names = emptyList();
            }
            """);
        CodeAction map = single(actionsAt("p/A.java", "java.util.Map"));
        assertEquals("Remove unused import 'java.util.Map'", map.title());
        assertNull(map.edit());
        assertEquals("""
            package p;

            import java.util.List;
            import java.util.Set;
            import java.io.*;
            import static java.util.Collections.emptyList;
            import static java.util.Collections.singleton;

            /** Holds a {@link Set}. */
            class A {
                List<String> names = emptyList();
            }
            """, applied("p/A.java", resolve("p/A.java", map)));
        assertEquals("Remove unused import 'static java.util.Collections.singleton'",
            single(actionsAt("p/A.java", "singleton")).title());
        for (String used : List.of("java.util.List", "java.util.Set", "java.io", "emptyList;")) {
            assertEquals(List.of(), actionsAt("p/A.java", used), used);
        }
    }

    @Test
    public void importsUsedThroughUnicodeEscapesAreKept() {
        source("p/A.java", """
            package p;

            import java.util.List;
            import java.util.Map;
            import java.util.Set;
            import java.util.\\u0041rrayList;

            class A {
                \\u004Cist<String> names;
                S\\u0065t<String> unique;
                String text = "\\\\u004Dap";
            }
            """);
        for (String used : List.of("java.util.List", "java.util.Set")) {
            assertEquals(List.of(), actionsAt("p/A.java", used), used);
        }
        CodeAction map = single(actionsAt("p/A.java", "java.util.Map"));
        assertEquals("Remove unused import 'java.util.Map'", map.title());
        assertEquals(text("p/A.java").replace("import java.util.Map;\n", ""),
            applied("p/A.java", resolve("p/A.java", map)));
        CodeAction escaped = single(actionsAt("p/A.java", "java.util.\\u0041"));
        assertEquals("Remove unused import 'java.util.ArrayList'", escaped.title());
        assertEquals(text("p/A.java").replace("import java.util.\\u0041rrayList;\n", ""),
            applied("p/A.java", resolve("p/A.java", escaped)));
        assertEquals(List.of("java.util.Map", "java.util.\\u0041rrayList"), unusedImportDiagnostics("p/A.java"),
            "An escaped backslash in a literal is not an escape");

        source("p/B.java", """
            package p;
            import java.util.\\u004Cist;
            import java.util.List;
            class B { Object names = List.of(); }
            """);
        assertEquals(List.of(), actionsAt("p/B.java", "List.of"), "An escaped import already makes the name resolve");
        assertEquals("Remove duplicate import 'java.util.List'",
            single(actionsAt("p/B.java", "import java.util.List")).title());
    }

    @Test
    public void importsLinkedOnlyFromDocumentationCommentsAreKept() {
        source("p/A.java", """
            package p;

            import java.util.List;
            import java.util.Map;
            import java.util.Optional;
            import java.util.Set;

            /// Holds a [List] and refers to {@link Optional}.
            class A {
                /** Keyed by {@linkplain Set sets}. */
                int size;
            }
            """);
        for (String used : List.of("java.util.List", "java.util.Optional", "java.util.Set")) {
            assertEquals(List.of(), actionsAt("p/A.java", used), used);
        }
        assertEquals("Remove unused import 'java.util.Map'", single(actionsAt("p/A.java", "java.util.Map")).title());
        assertEquals(List.of("java.util.Map"), unusedImportDiagnostics("p/A.java"));

        source("p/B.java", """
            package p;
            import java.util.List;
            // Not documentation: [List]
            //////// [List] ////////
            class B {}
            """);
        assertEquals("Remove unused import 'java.util.List'", single(actionsAt("p/B.java", "java.util.List")).title());
    }

    @Test
    public void inaccessibleTypesNeverMakeAnImportAmbiguous() {
        source("q/Widget.java", """
            package q;
            public class Widget {}
            """);
        source("r/Widget.java", """
            package r;
            class Widget {}
            """);
        source("s/Gadget.java", """
            package s;
            class Gadget {}
            """);
        source("t/Gadget.java", """
            package t;
            class Gadget {}
            """);
        source("p/A.java", """
            package p;
            class A { Widget widget; Gadget gadget; }
            """);
        CodeAction widget = single(actionsAt("p/A.java", "Widget widget"));
        assertEquals("Import 'q.Widget'", widget.title(), widget::toString);
        assertTrue(widget.available());
        assertEquals("package p;\n\nimport q.Widget;\nclass A { Widget widget; Gadget gadget; }\n",
            applied("p/A.java", resolve("p/A.java", widget)));

        CodeAction gadget = single(actionsAt("p/A.java", "Gadget gadget"));
        assertFalse(gadget.available());
        assertEquals("No 'Gadget' is accessible from this file: s.Gadget, t.Gadget", gadget.unavailableReason());
    }

    @Test
    public void removesOnlyTheRepeatedCopyOfADuplicateImport() {
        source("p/A.java", """
            package p;
            import java.util.List;
            import java.util.List; import java.util.Set;
            class A { List<String> names; Set<String> set; }
            """);
        int second = text("p/A.java").indexOf("import java.util.List", 20);
        assertEquals(List.of(), actionsAt("p/A.java", "java.util.List"));
        CodeAction duplicate = single(actions("p/A.java", CodeActionRequest.at(second + 2)));
        assertEquals("Remove duplicate import 'java.util.List'", duplicate.title());
        assertEquals("""
            package p;
            import java.util.List;
            import java.util.Set;
            class A { List<String> names; Set<String> set; }
            """, applied("p/A.java", resolve("p/A.java", duplicate)));
    }

    @Test
    public void currentDiagnosticsWidenDiscoveryButStaleOnesAreIgnored() {
        source("p/A.java", """
            package p;
            import java.util.Map;
            class A {}
            """);
        TextDocumentSnapshot saved = documents.get("p/A.java");
        int target = saved.text().indexOf("java.util.Map");
        var current = diagnostic(saved, target, target + "java.util.Map".length(), JavaCodeActions.UNUSED_IMPORT);
        var away = new CodeActionRequest(new TextRange(saved.text().length() - 2,
            saved.text().length() - 2), List.of(current));
        CodeAction action = single(actions("p/A.java", away));
        assertEquals(List.of(current), action.diagnostics());

        TextDocumentSnapshot edited = overlay("p/A.java", saved.text() + "\n");
        var stale = new CodeActionRequest(new TextRange(edited.text().length() - 1,
            edited.text().length() - 1), List.of(current));
        assertEquals(List.of(), actions("p/A.java", stale));
    }

    @Test
    public void unsavedEditsDecideWhetherAnImportIsUsed() {
        source("p/A.java", """
            package p;
            import java.util.Map;
            class A {}
            """);
        assertEquals(1, actionsAt("p/A.java", "java.util.Map").size());
        overlay("p/A.java", """
            package p;
            import java.util.Map;
            class A { Map<String, String> values; }
            """);
        assertEquals(List.of(), actionsAt("p/A.java", "java.util.Map"));
    }

    @Test
    public void resolutionRechecksTheDocumentAndRejectsFixesThatNoLongerApply() {
        source("p/A.java", """
            package p;
            import java.util.Map;
            class A { ArrayList<String> names; }
            """);
        CodeAction removal = single(actionsAt("p/A.java", "java.util.Map"));
        CodeAction addition = single(actionsAt("p/A.java", "ArrayList"));
        overlay("p/A.java", """
            package p;
            import java.util.Map;
            import java.util.ArrayList;
            class A { ArrayList<String> names; Map<String, String> values; }
            """);
        assertFalse(resolve("p/A.java", removal).available());
        assertFalse(resolve("p/A.java", addition).available());
        assertFalse(resolve("p/A.java", CodeAction.deferred("Mystery", "java:unknown")).available());
    }

    @Test
    public void cancelledRequestsStopBeforeProducingActions() {
        source("p/A.java", """
            package p;
            import java.util.Map;
            class A {}
            """);
        var cancellation = new CancellationTokenSource();
        cancellation.cancel();
        var context = new FeatureRequestContext(store().capture(), documents.get("p/A.java"),
            cancellation.token(), Deadline.none());
        assertThrows(CancellationException.class, () -> services.actions(context, CodeActionRequest.at(20)));
        assertThrows(CancellationException.class,
            () -> services.resolve(context, CodeAction.deferred("Remove", "java:remove-unused-import:11")));
    }

    @Test
    public void unusedImportInspectionReportsOnlyProvablyUnusedImports() {
        source("p/A.java", """
            package p;
            import java.util.Map;
            import java.util.List;
            import java.util.Set;
            import java.io.*;
            /** {@link Set} */
            class A { List<String> names; }
            """);
        List<TextEditorDiagnostic> unused = new JavaDiagnosticsProvider(null, null, services::capture)
            .compute(context("p/A.java")).value().stream()
            .filter(diagnostic -> JavaCodeActions.UNUSED_IMPORT.equals(diagnostic.code()))
            .toList();
        assertEquals(1, unused.size(), unused::toString);
        TextRange range = unused.getFirst().location().range();
        assertEquals("java.util.Map", text("p/A.java").substring(range.start, range.end));
    }

    @Test
    public void importsUsedOnlyByPackageAnnotationsAreKept() {
        source("q/Marker.java", """
            package q;
            public @interface Marker {}
            """);
        source("p/package-info.java", """
            @Marker
            package p;

            import q.Marker;
            """);
        assertEquals(List.of(), actionsAt("p/package-info.java", "q.Marker"));
        assertTrue(new JavaDiagnosticsProvider(null, null, services::capture).compute(context("p/package-info.java"))
            .value().stream().noneMatch(diagnostic -> JavaCodeActions.UNUSED_IMPORT.equals(diagnostic.code())));
    }

    @Test
    public void insertsAfterTrailingCommentsBeforeStaticImportsAndAboveTypeDocumentation() {
        source("p/A.java", """
            package p;
            import java.util.Map; // values
            class A { ArrayList<String> a; Map<String, String> m; Set<String> s; }
            """);
        assertEquals("""
            package p;
            import java.util.Map; // values
            import java.util.Set;
            class A { ArrayList<String> a; Map<String, String> m; Set<String> s; }
            """, applied("p/A.java", resolve("p/A.java", single(actionsAt("p/A.java", "Set<")))));

        source("p/B.java", """
            package p;
            import static java.util.Collections.emptyList;
            class B { ArrayList<String> a = new ArrayList<>(emptyList()); }
            """);
        assertEquals("""
            package p;
            import java.util.ArrayList;
            import static java.util.Collections.emptyList;
            class B { ArrayList<String> a = new ArrayList<>(emptyList()); }
            """, applied("p/B.java", resolve("p/B.java", single(actionsAt("p/B.java", "ArrayList<")))));

        source("C.java", """
            /** Documents C. */
            public class C { ArrayList<String> a; }
            """);
        assertEquals("""
            import java.util.ArrayList;

            /** Documents C. */
            public class C { ArrayList<String> a; }
            """, applied("C.java", resolve("C.java", single(actionsAt("C.java", "ArrayList<")))));
    }

    @Test
    public void keepsWindowsLineEndingsAndRemovesAFinalImportWithoutNewline() {
        source("p/A.java", "package p;\r\nimport java.util.Map;\r\nclass A { ArrayList<String> a; }\r\n");
        assertEquals("package p;\r\nimport java.util.ArrayList;\r\nimport java.util.Map;\r\n"
            + "class A { ArrayList<String> a; }\r\n",
            applied("p/A.java", resolve("p/A.java", single(actionsAt("p/A.java", "ArrayList<")))));
        assertEquals("package p;\r\nclass A { ArrayList<String> a; }\r\n",
            applied("p/A.java", resolve("p/A.java", single(actionsAt("p/A.java", "java.util.Map")))));

        source("p/B.java", "package p;\nimport java.util.Map;");
        assertEquals("package p;\n",
            applied("p/B.java", resolve("p/B.java", single(actionsAt("p/B.java", "java.util.Map")))));
    }

    @Test
    public void unknownOnDemandImportsAndHiddenJdkPackagesNeverYieldGuesses() {
        source("p/A.java", """
            package p;
            import missing.pkg.*;
            class A { ArrayList<String> a; }
            """);
        assertEquals(List.of(), actionsAt("p/A.java", "ArrayList<"));

        source("p/B.java", """
            package p;
            class B { Unsafe unsafe; }
            """);
        assertEquals("Import 'sun.misc.Unsafe'", single(actionsAt("p/B.java", "Unsafe unsafe")).title(),
            "jdk.internal.misc.Unsafe is not exported, so sun.misc.Unsafe is the only candidate");
    }

    @Test
    public void listsAtMostFourAmbiguousCandidates() {
        for (String owner : List.of("a", "b", "c", "d", "e")) {
            source(owner + "/Thing.java", "package " + owner + ";\npublic class Thing {}\n");
        }
        source("p/A.java", """
            package p;
            class A { Thing thing; }
            """);
        CodeAction action = single(actionsAt("p/A.java", "Thing thing"));
        assertEquals("'Thing' is ambiguous: a.Thing, b.Thing, c.Thing, d.Thing and 1 more",
            action.unavailableReason());
    }

    @Test
    public void unresolvedTypeDiagnosticsOfferImportsAwayFromTheCaret() {
        source("p/A.java", """
            package p;
            class A {
                ArrayList<String> names;
            }
            """);
        TextDocumentSnapshot document = documents.get("p/A.java");
        int start = document.text().indexOf("ArrayList");
        var unresolved = diagnostic(document, start, start + "ArrayList<String>".length(), "SEM_UNRESOLVED_TYPE");
        CodeAction action = single(actions("p/A.java", new CodeActionRequest(new TextRange(0, 0),
            List.of(unresolved))));
        assertEquals("Import 'java.util.ArrayList'", action.title());
        assertEquals(List.of(unresolved), action.diagnostics());
    }

    @Test
    public void cancellationDuringAnalysisStopsDiscovery() {
        source("p/A.java", """
            package p;
            class A { ArrayList<String> names; }
            """);
        var cancellation = new CancellationTokenSource();
        var context = new FeatureRequestContext(store().capture(), documents.get("p/A.java"), cancellation.token(),
            Deadline.none());
        var actions = new JavaCodeActions(context, captured -> {
            cancellation.cancel();
            return services.capture(new FeatureRequestContext(captured.workspaceSnapshot(),
                captured.documentSnapshot(), CancellationToken.none(), Deadline.none()));
        });
        int offset = text("p/A.java").indexOf("ArrayList") + 1;
        assertThrows(CancellationException.class, () -> actions.discover(CodeActionRequest.at(offset)));
    }

    private static CodeAction single(List<CodeAction> actions) {
        assertEquals(1, actions.size(), actions::toString);
        return actions.getFirst();
    }

    private List<CodeAction> actionsAt(String file, String marker) {
        int offset = text(file).indexOf(marker);
        assertTrue(offset >= 0, marker);
        return actions(file, CodeActionRequest.at(offset + 1));
    }

    private List<CodeAction> actions(String file, CodeActionRequest request) {
        return services.actions(context(file), request).value();
    }

    private CodeAction resolve(String file, CodeAction action) {
        return services.resolve(context(file), action).value();
    }

    private String applied(String file, CodeAction action) {
        assertNotNull(action.edit(), () -> String.valueOf(action.unavailableReason()));
        var change = (WorkspaceChange.TextEdit) action.edit().changes().getFirst();
        assertEquals(1, action.edit().changes().size());
        assertSame(documents.get(file), change.expected());
        var builder = new StringBuilder(change.expected().text());
        change.replacements().stream()
            .sorted(Comparator.comparingInt((TextReplacement replacement) -> replacement.range().start).reversed())
            .forEach(replacement -> builder.replace(replacement.range().start, replacement.range().end,
                replacement.text()));
        return builder.toString();
    }

    private static TextEditorDiagnostic diagnostic(TextDocumentSnapshot snapshot, int start, int end, String code) {
        return new TextEditorDiagnostic(Location.TextLocation.from(snapshot, start, end), DiagnosticSeverity.WARNING,
            code, "message", "railroad:test", DiagnosticOrigin.INSPECTION, Set.of(), List.of(), null);
    }

    private FeatureRequestContext context(String file) {
        return new FeatureRequestContext(store().capture(), documents.get(file), CancellationToken.none(),
            Deadline.none());
    }

    private String text(String file) {
        return documents.get(file).text();
    }

    private List<String> unusedImportDiagnostics(String file) {
        return new JavaDiagnosticsProvider(null, null, services::capture).compute(context(file)).value().stream()
            .filter(diagnostic -> JavaCodeActions.UNUSED_IMPORT.equals(diagnostic.code()))
            .map(diagnostic -> text(file).substring(diagnostic.location().range().start,
                diagnostic.location().range().end))
            .toList();
    }

    private void source(String file, String text) {
        var uri = DocumentUri.fromPath(directory.resolve(root + file));
        var document = new TextDocumentSnapshot(DocumentId.create(), uri, DocumentVersion.initial(), "java",
            DocumentOrigin.original(uri), text, StandardCharsets.UTF_8);
        documents.put(file, document);
        store().publishBacking(document);
    }

    private TextDocumentSnapshot overlay(String file, String text) {
        TextDocumentSnapshot saved = documents.get(file);
        var document = new TextDocumentSnapshot(saved.id(), saved.uri(), saved.version().next(), "java",
            saved.origin(), text, StandardCharsets.UTF_8);
        documents.put(file, document);
        store().publishOverlay(document);
        return document;
    }

    private DefaultDocumentStore store() {
        if (store == null) {
            var configuration = new WorkspaceConfigurationSnapshot(0, Set.of("main"),
                Map.of("module.main.sources", directory.resolve(root).toString()));
            store = new DefaultDocumentStore("code-actions", configuration);
        }
        return store;
    }
}
