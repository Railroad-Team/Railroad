package dev.railroadide.railroad.plugin.spi.inspection;

import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxParser;
import dev.railroadide.railroad.ide.sst.request.api.CancellationTokenSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LanguageInspectionRuleEngineTest {
    @Test
    public void continuesAfterRuleFailureForVirtualDocument() {
        LanguageRuleContext context = new LanguageRuleContext() {
            @Override
            public String languageId() {
                return "test";
            }

            @Override
            public Path filePath() {
                throw new IllegalStateException("Virtual document has no physical path");
            }

            @Override
            public DocumentUri documentUri() {
                return DocumentUri.virtual("generated", "/demo/Example.java");
            }

            @Override
            public String documentText() {
                return "";
            }
        };
        List<String> executedRules = new ArrayList<>();
        var first = new TestRule("test:failing", _ -> {
            executedRules.add("first");
            throw new IllegalArgumentException("Deliberate inspection failure");
        });
        var second = new TestRule("test:successful", _ -> executedRules.add("second"));
        var provider = new TestProvider(List.of(first, second));
        InspectionSettingsAccess settings = new InspectionSettingsAccess() {
            @Override
            public boolean isEnabled(LanguageInspectionRule<?> rule) {
                return true;
            }

            @Override
            public SemanticDiagnostic.Severity effectiveSeverity(LanguageInspectionRule<?> rule) {
                return rule.defaultSeverity();
            }
        };

        assertDoesNotThrow(() -> LanguageInspectionRuleEngine.runRules(provider, context, diagnostic -> {
        }, settings));
        assertEquals(List.of("first", "second"), executedRules);
    }

    @Test
    public void derivesDocumentUriForExistingPathOnlyContexts() {
        Path path = Path.of("sources", "Example.java");
        LanguageRuleContext context = new PathOnlyContext(path);

        assertEquals(DocumentUri.fromPath(path), context.documentUri());
    }

    @Test
    public void cancellationBetweenRulesStopsLaterRulesAndDiscardsCancelledOutput() {
        var cancellation = new CancellationTokenSource();
        var context = new ControlledContext(cancellation.token()::throwIfCancellationRequested);
        var root = JavaSyntaxParser.parse("class Example {}").root();
        var reported = new ArrayList<SemanticDiagnostic>();
        var executed = new ArrayList<String>();
        var first = new TestRule("first", (_, reporter) -> {
            reporter.reportMessage(root, "discard me");
            executed.add("first");
            cancellation.cancel();
        });
        var second = new TestRule("second", _ -> executed.add("second"));

        assertThrows(CancellationException.class, () -> LanguageInspectionRuleEngine.runRules(
            new TestProvider(List.of(first, second)), context, reported::add, enabledSettings()));
        assertEquals(List.of("first"), executed);
        assertTrue(reported.isEmpty());
    }

    @Test
    public void failedRuleCannotPublishPartialDiagnosticsAndHealthyRuleStillRuns() {
        var root = JavaSyntaxParser.parse("class Example {}").root();
        var failed = new TestRule("failed", (_, reporter) -> {
            reporter.reportMessage(root, "discard me");
            throw new IllegalStateException("failed after reporting");
        });
        var healthy = new TestRule("healthy", (_, reporter) -> reporter.reportMessage(root, "keep me"));
        var reported = LanguageInspectionRuleEngine.collectDiagnostics(new TestProvider(List.of(failed, healthy)),
            new PathOnlyContext(Path.of("Example.java")), enabledSettings());

        assertEquals(1, reported.size());
        assertEquals("keep me", reported.getFirst().message());
    }

    private static InspectionSettingsAccess enabledSettings() {
        return new InspectionSettingsAccess() {
            @Override
            public boolean isEnabled(LanguageInspectionRule<?> rule) {
                return true;
            }

            @Override
            public SemanticDiagnostic.Severity effectiveSeverity(LanguageInspectionRule<?> rule) {
                return rule.defaultSeverity();
            }
        };
    }

    private record ControlledContext(Runnable activity) implements LanguageRuleContext {
        @Override
        public void checkActive() {
            activity.run();
        }

        @Override
        public String languageId() {
            return "test";
        }

        @Override
        public Path filePath() {
            return Path.of("Example.java");
        }

        @Override
        public String documentText() {
            return "";
        }
    }

    private record PathOnlyContext(Path filePath) implements LanguageRuleContext {
        @Override
        public String languageId() {
            return "test";
        }

        @Override
        public String documentText() {
            return "";
        }
    }

    private record TestProvider(List<TestRule> rules) implements LanguageInspectionRuleProvider<LanguageRuleContext> {
        @Override
        public String id() {
            return "test:provider";
        }

        @Override
        public String languageId() {
            return "test";
        }
    }

    private record TestRule(String id, BiConsumer<LanguageRuleContext, LanguageInspectionRuleReporter> evaluator)
        implements
            LanguageInspectionRule<LanguageRuleContext> {
        private TestRule(String id, Consumer<LanguageRuleContext> evaluator) {
            this(id, (context, _) -> evaluator.accept(context));
        }

        @Override
        public SemanticDiagnostic.Severity defaultSeverity() {
            return SemanticDiagnostic.Severity.WARNING;
        }

        @Override
        public String messageTemplate() {
            return "Test diagnostic";
        }

        @Override
        public void evaluate(LanguageRuleContext context, LanguageInspectionRuleReporter reporter) {
            evaluator.accept(context, reporter);
        }
    }
}
