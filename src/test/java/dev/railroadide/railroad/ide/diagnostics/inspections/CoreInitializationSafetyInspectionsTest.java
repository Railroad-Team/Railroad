package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.railroadide.railroad.ide.diagnostics.inspections.JavaInspectionTestSupport.runProvider;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CoreInitializationSafetyInspectionsTest {
    @Test
    public void coreInitializationRuleEmitsDiagnosticForImplicitThisCall() {
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
    public void coreInitializationRuleEmitsDiagnosticForExplicitThisCall() {
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
    public void coreInitializationRuleDoesNotEmitDiagnosticForOtherReceiver() {
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
    public void coreInitializationRuleDoesNotEmitDiagnosticForSuperCall() {
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
    public void coreInitializationRuleDoesNotEmitDiagnosticForFinalOrStaticTarget() {
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
    public void coreInitializationRuleEmitsOverriddenDiagnosticWhenSubclassOverrides() {
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
    public void coreInitializationRuleDoesNotEmitOverriddenDiagnosticWhenNoSubclass() {
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
    public void coreThisReferenceEscapedRuleEmitsDiagnosticForPassingThisToCollectionPublisher() {
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
    public void coreThisReferenceEscapedRuleEmitsDiagnosticForPassingThisToPublishingMethod() {
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
    public void coreThisReferenceEscapedRuleEmitsDiagnosticForLambdaPassedToPublishingMethod() {
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
    public void coreThisReferenceEscapedRuleEmitsDiagnosticForLambdaPassedToThreadConstructor() {
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
    public void coreThisReferenceEscapedRuleEmitsDiagnosticForThisAssignedToField() {
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
    public void coreThisReferenceEscapedRuleDoesNotEmitDiagnosticForPlainThisUse() {
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
    public void coreThisReferenceEscapedRuleDoesNotEmitDiagnosticForLocalVariableInitialization() {
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
    public void coreThisReferenceEscapedRuleDoesNotEmitDiagnosticForLocalMethodCall() {
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
    public void coreThisReferenceEscapedRuleDoesNotEmitDiagnosticForNestedLambdaThatDoesNotEscape() {
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
    public void coreFieldCanBeLocalVariableRuleEmitsDiagnosticForPrivateFieldUsedOnlyInOneMethod() {
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
    public void coreFieldCanBeLocalVariableRuleDoesNotEmitDiagnosticForFieldUsedInConstructor() {
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
    public void coreFieldCanBeLocalVariableRuleDoesNotEmitDiagnosticForFieldUsedInMultipleMethods() {
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
    public void coreFieldCanBeLocalVariableRuleEmitsDiagnosticForFieldOnlyReadInsideLambda() {
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
    public void coreFieldCanBeLocalVariableRuleEmitsDiagnosticForFieldReadInMethodAndLambdaWithinSameMethod() {
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
    public void coreFieldCanBeLocalVariableRuleDoesNotEmitDiagnosticForFieldAssignedInsideLambda() {
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
    public void coreFieldCanBeLocalVariableRuleDoesNotEmitDiagnosticForFieldIncrementedInsideLambda() {
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
