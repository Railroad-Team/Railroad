package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.railroadide.railroad.ide.diagnostics.inspections.JavaInspectionTestSupport.runProvider;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreAutoCloseableInspectionTest {
    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleEmitsDiagnosticForDirectConstructorAcquisition() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                static final class Resource implements AutoCloseable {
                    public void close() {
                    }
                }

                void run() {
                    Resource resource = new Resource();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }

    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleEmitsDiagnosticForVarConstructorAcquisition() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                static final class Resource implements AutoCloseable {
                    public void close() {
                    }
                }

                void run() {
                    var resource = new Resource();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }

    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleDoesNotEmitDiagnosticInsideTryWithResources() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                static final class Resource implements AutoCloseable {
                    public void close() {
                    }
                }

                void run() {
                    try (Resource resource = new Resource()) {
                        System.out.println(resource);
                    }
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }

    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleDoesNotEmitDiagnosticForDeclarationWithoutInitializer() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                static final class Resource implements AutoCloseable {
                    public void close() {
                    }
                }

                void run() {
                    Resource resource;
                    resource = new Resource();
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }

    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleDoesNotEmitDiagnosticForFieldDeclaration() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                static final class Resource implements AutoCloseable {
                    public void close() {
                    }
                }

                private final Resource resource = new Resource();
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }

    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleDoesNotEmitDiagnosticForAliasInitializer() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                static final class Resource implements AutoCloseable {
                    public void close() {
                    }
                }

                void run(Resource existing) {
                    Resource alias = existing;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }

    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleDoesNotEmitDiagnosticForFieldAccessInitializer() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                static final class Resource implements AutoCloseable {
                    public void close() {
                    }
                }

                private final Resource shared = null;

                void run() {
                    Resource alias = this.shared;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }

    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleEmitsDiagnosticForOpenMethodFactory() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                static final class Resource implements AutoCloseable {
                    public void close() {
                    }
                }

                Resource openResource() {
                    return new Resource();
                }

                void run() {
                    Resource resource = openResource();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }

    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleDoesNotEmitDiagnosticForMethodFactoryOutsideHeuristic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                static final class Resource implements AutoCloseable {
                    public void close() {
                    }
                }

                Resource buildResource() {
                    return new Resource();
                }

                void run() {
                    Resource resource = buildResource();
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }

    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleDoesNotEmitDiagnosticForOpenMethodReturningNonCloseable() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                Object openValue() {
                    return new Object();
                }

                void run() {
                    Object value = openValue();
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }

    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleEmitsDiagnosticForConditionalFactoryBranch() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                static final class Resource implements AutoCloseable {
                    public void close() {
                    }
                }

                Resource openResource() {
                    return new Resource();
                }

                void run(boolean flag, Resource existing) {
                    Resource resource = flag ? existing : openResource();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }

    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleDoesNotEmitDiagnosticForConditionalAliases() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                static final class Resource implements AutoCloseable {
                    public void close() {
                    }
                }

                void run(boolean flag, Resource left, Resource right) {
                    Resource resource = flag ? left : right;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }

    @Test
    void coreAutoCloseableWithoutTryWithResourcesRuleEmitsDiagnosticForCastWrappedConstructorAcquisition() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreAutoCloseableWithoutTryWithResourcesInspection(), """
            class Example {
                static final class Resource implements AutoCloseable {
                    public void close() {
                    }
                }

                void run() {
                    Resource resource = (Resource) new Resource();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES".equals(d.code())));
    }


}
