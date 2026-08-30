package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.railroadide.railroad.ide.diagnostics.inspections.JavaInspectionTestSupport.runProvider;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreInitializationSafetyInspectionsTest {
    @Test
    void coreInitializationRuleEmitsDiagnosticForImplicitThisCall() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInitializationInspection(), """
            class Example {
                void configure() {}

                Example() {
                    configure();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_OVERRIDABLE_METHOD_DURING_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreInitializationRuleEmitsDiagnosticForExplicitThisCall() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInitializationInspection(), """
            class Example {
                void configure() {}

                Example() {
                    this.configure();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_OVERRIDABLE_METHOD_DURING_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreInitializationRuleDoesNotEmitDiagnosticForOtherReceiver() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInitializationInspection(), """
            class Example {
                void configure() {}

                Example(Example other) {
                    other.configure();
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_OVERRIDABLE_METHOD_DURING_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreInitializationRuleDoesNotEmitDiagnosticForSuperCall() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInitializationInspection(), """
            class Base {
                void configure() {}
            }

            class Example extends Base {
                Example() {
                    super.configure();
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_OVERRIDABLE_METHOD_DURING_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreInitializationRuleDoesNotEmitDiagnosticForFinalOrStaticTarget() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInitializationInspection(), """
            class Example {
                final void configureFinal() {}
                static void configureStatic() {}

                Example() {
                    configureFinal();
                    configureStatic();
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_OVERRIDABLE_METHOD_DURING_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreInitializationRuleEmitsOverriddenDiagnosticWhenSubclassOverrides() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInitializationInspection(), """
            class Base {
                void configure() {}

                Base() {
                    configure();
                }
            }

            class Derived extends Base {
                @Override
                void configure() {
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_OVERRIDDEN_METHOD_DURING_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreInitializationRuleDoesNotEmitOverriddenDiagnosticWhenNoSubclass() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreInitializationInspection(), """
            class Example {
                void configure() {}

                Example() {
                    configure();
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_OVERRIDDEN_METHOD_DURING_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreThisReferenceEscapedRuleEmitsDiagnosticForPassingThisToCollectionPublisher() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreThisReferenceEscapedObjectConstructionInspection(),
            """
                import java.util.ArrayList;
                import java.util.List;

                class Example {
                    private final List<Object> items = new ArrayList<>();

                    Example() {
                        items.add(this);
                    }
                }
                """);

        assertTrue(
            diagnostics.stream().anyMatch(d -> "SEM_THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreThisReferenceEscapedRuleEmitsDiagnosticForPassingThisToPublishingMethod() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreThisReferenceEscapedObjectConstructionInspection(),
            """
                class Example {
                    void register(Object value) {
                    }

                    Example() {
                        register(this);
                    }
                }
                """);

        assertTrue(
            diagnostics.stream().anyMatch(d -> "SEM_THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreThisReferenceEscapedRuleEmitsDiagnosticForLambdaPassedToPublishingMethod() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreThisReferenceEscapedObjectConstructionInspection(),
            """
                class Example {
                    void execute(Runnable runnable) {
                    }

                    Example() {
                        execute(() -> System.out.println(this));
                    }
                }
                """);

        assertTrue(
            diagnostics.stream().anyMatch(d -> "SEM_THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreThisReferenceEscapedRuleEmitsDiagnosticForLambdaPassedToThreadConstructor() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreThisReferenceEscapedObjectConstructionInspection(),
            """
                class Example {
                    Example() {
                        new Thread(() -> System.out.println(this));
                    }
                }
                """);

        assertTrue(
            diagnostics.stream().anyMatch(d -> "SEM_THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreThisReferenceEscapedRuleEmitsDiagnosticForThisAssignedToField() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreThisReferenceEscapedObjectConstructionInspection(),
            """
                class Example {
                    private static Example leaked;

                    Example() {
                        leaked = this;
                    }
                }
                """);

        assertTrue(
            diagnostics.stream().anyMatch(d -> "SEM_THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreThisReferenceEscapedRuleDoesNotEmitDiagnosticForPlainThisUse() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreThisReferenceEscapedObjectConstructionInspection(),
            """
                class Example {
                    Example() {
                        this.hashCode();
                    }
                }
                """);

        assertFalse(
            diagnostics.stream().anyMatch(d -> "SEM_THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreThisReferenceEscapedRuleDoesNotEmitDiagnosticForLocalVariableInitialization() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreThisReferenceEscapedObjectConstructionInspection(),
            """
                class Example {
                    Example() {
                        Object local = this;
                    }
                }
                """);

        assertFalse(
            diagnostics.stream().anyMatch(d -> "SEM_THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreThisReferenceEscapedRuleDoesNotEmitDiagnosticForLocalMethodCall() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreThisReferenceEscapedObjectConstructionInspection(),
            """
                class Example {
                    void use(Object value) {
                    }

                    Example() {
                        use(this);
                    }
                }
                """);

        assertFalse(
            diagnostics.stream().anyMatch(d -> "SEM_THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreThisReferenceEscapedRuleDoesNotEmitDiagnosticForNestedLambdaThatDoesNotEscape() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreThisReferenceEscapedObjectConstructionInspection(),
            """
                class Example {
                    Example() {
                        Runnable outer = () -> {
                            Runnable inner = () -> System.out.println(this);
                        };
                    }
                }
                """);

        assertFalse(
            diagnostics.stream().anyMatch(d -> "SEM_THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION".equals(d.code())));
    }

    @Test
    void coreFieldCanBeLocalVariableRuleEmitsDiagnosticForPrivateFieldUsedOnlyInOneMethod() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFieldCanBeLocalVariableInspection(), """
            class Example {
                private int field = 1;

                void method() {
                    System.out.println(field);
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_FIELD_CAN_BE_LOCAL_VARIABLE".equals(d.code())));
    }

    @Test
    void coreFieldCanBeLocalVariableRuleDoesNotEmitDiagnosticForFieldUsedInConstructor() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFieldCanBeLocalVariableInspection(), """
            class Example {
                private int field = 1;

                Example() {
                    field = 2;
                }

                void method() {
                    System.out.println(field);
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_FIELD_CAN_BE_LOCAL_VARIABLE".equals(d.code())));
    }

    @Test
    void coreFieldCanBeLocalVariableRuleDoesNotEmitDiagnosticForFieldUsedInMultipleMethods() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFieldCanBeLocalVariableInspection(), """
            class Example {
                private int field = 1;

                void method1() {
                    System.out.println(field);
                }

                void method2() {
                    field = 2;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_FIELD_CAN_BE_LOCAL_VARIABLE".equals(d.code())));
    }

    @Test
    void coreFieldCanBeLocalVariableRuleEmitsDiagnosticForFieldOnlyReadInsideLambda() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFieldCanBeLocalVariableInspection(), """
            class Example {
                private int field = 1;

                void method() {
                    Runnable task = () -> System.out.println(field);
                    task.run();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_FIELD_CAN_BE_LOCAL_VARIABLE".equals(d.code())));
    }

    @Test
    void coreFieldCanBeLocalVariableRuleEmitsDiagnosticForFieldReadInMethodAndLambdaWithinSameMethod() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFieldCanBeLocalVariableInspection(), """
            class Example {
                private int field = 1;

                void method() {
                    System.out.println(field);
                    Runnable task = () -> System.out.println(field);
                    task.run();
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_FIELD_CAN_BE_LOCAL_VARIABLE".equals(d.code())));
    }

    @Test
    void coreFieldCanBeLocalVariableRuleDoesNotEmitDiagnosticForFieldAssignedInsideLambda() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFieldCanBeLocalVariableInspection(), """
            class Example {
                private int field = 1;

                void method() {
                    Runnable task = () -> field = 2;
                    task.run();
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_FIELD_CAN_BE_LOCAL_VARIABLE".equals(d.code())));
    }

    @Test
    void coreFieldCanBeLocalVariableRuleDoesNotEmitDiagnosticForFieldIncrementedInsideLambda() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreFieldCanBeLocalVariableInspection(), """
            class Example {
                private int field = 1;

                void method() {
                    Runnable task = () -> this.field++;
                    task.run();
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_FIELD_CAN_BE_LOCAL_VARIABLE".equals(d.code())));
    }

}
