package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static dev.railroadide.railroad.ide.diagnostics.inspections.JavaInspectionTestSupport.runProvider;
import static org.junit.jupiter.api.Assertions.*;

class CoreDefiniteAssignmentInspectionTest {
    @Test
    void coreDefiniteAssignmentRuleEmitsUnassignedAndIllegalFinalAssignmentDiagnostics() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDefiniteAssignmentInspection(), """
            class Example {
                void run(boolean flag, final int parameter) {
                    int value;
                    if (flag) {
                        value = 1;
                    }

                    final int once;
                    once = 1;
                    once = 2;
                    parameter = 3;
                    System.out.println(value);
                }

                void ok(boolean flag) {
                    final int assignedInBranches;
                    if (flag) {
                        assignedInBranches = 1;
                    } else {
                        assignedInBranches = 2;
                    }
                    System.out.println(assignedInBranches);
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_UNASSIGNED_VARIABLE".equals(d.code()) && d.message().contains("value")));
        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_ILLEGAL_FINAL_ASSIGNMENT".equals(d.code()) && d.message().contains("once")));
        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_ILLEGAL_FINAL_ASSIGNMENT".equals(d.code()) && d.message().contains("parameter")));
        assertFalse(diagnostics.stream().anyMatch(d -> d.message().contains("assignedInBranches")));
    }

    @Test
    void coreDefiniteAssignmentRuleAllowsReassigningNonFinalParameters() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDefiniteAssignmentInspection(), """
            class Example {
                String normalize(String value) {
                    value = value.trim();
                    return value;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_ILLEGAL_FINAL_ASSIGNMENT".equals(diagnostic.code())));
    }

    @Test
    void coreDefiniteAssignmentRuleRecognizesQualifiedFieldWritesAndConstructorDelegation() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDefiniteAssignmentInspection(), """
            class Example {
                private final String name;
                private final int count;

                Example(String name) {
                    this(name, 1);
                }

                Example(String name, int count) {
                    this.name = name;
                    this.count = count;
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_UNINITIALIZED_FINAL_FIELD".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void coreDefiniteAssignmentRuleRecognizesLombokGeneratedConstructors() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDefiniteAssignmentInspection(), """
            @AllArgsConstructor
            final class Example {
                private final String name;
                private final int count;
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(diagnostic ->
            "SEM_UNINITIALIZED_FINAL_FIELD".equals(diagnostic.code())),
            () -> diagnostics.stream().map(SemanticDiagnostic::message).collect(Collectors.joining("\n")));
    }

    @Test
    void realDelegatingAndLombokConstructorsInitializeFinalFields() throws Exception {
        List<Path> sourceFiles = List.of(
            Path.of("src/main/java/dev/railroadide/railroad/project/RailroadProject.java"),
            Path.of("src/main/java/dev/railroadide/railroad/ide/sst/impl/java/JavaLexer.java"),
            Path.of("src/main/java/dev/railroadide/railroad/project/License.java"),
            Path.of("src/main/java/dev/railroadide/railroad/form/FormTransformer.java")
        );
        List<String> errors = new ArrayList<>();
        for (Path sourceFile : sourceFiles) {
            runProvider(new CoreDefiniteAssignmentInspection(), sourceFile, Files.readString(sourceFile)).stream()
                .filter(diagnostic -> "SEM_UNINITIALIZED_FINAL_FIELD".equals(diagnostic.code()))
                .map(diagnostic -> sourceFile + ": " + diagnostic.message())
                .forEach(errors::add);
        }

        assertTrue(errors.isEmpty(), () -> String.join("\n", errors));
    }

    @Test
    void realLoopTryAndSwitchAssignmentsDoNotReportUnassignedVariables() throws Exception {
        List<Path> sourceFiles = List.of(
            Path.of("src/main/java/dev/railroadide/railroad/ide/sst/impl/java/JavaLexer.java"),
            Path.of("src/main/java/dev/railroadide/railroad/ide/projectexplorer/task/WatchTask.java"),
            Path.of("src/main/java/dev/railroadide/railroad/config/ConfigHandler.java")
        );
        List<String> errors = new ArrayList<>();
        for (Path sourceFile : sourceFiles) {
            String source = Files.readString(sourceFile);
            runProvider(new CoreDefiniteAssignmentInspection(), sourceFile, source).stream()
                .filter(diagnostic -> "SEM_UNASSIGNED_VARIABLE".equals(diagnostic.code()))
                .map(diagnostic -> sourceFile + ": " + diagnostic.message())
                .forEach(errors::add);
        }

        assertTrue(errors.isEmpty(), () -> String.join("\n", errors));
    }

    @Test
    void coreDefiniteAssignmentRuleHandlesLoopBreakAndContinueExits() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDefiniteAssignmentInspection(), """
            class Example {
                void run() {
                    int fromWhile;
                    while (true) {
                        fromWhile = 1;
                        break;
                    }
                    System.out.println(fromWhile);
                }

                void alsoRun() {
                    int fromDoWhile;
                    do {
                        fromDoWhile = 1;
                        continue;
                    } while (false);
                    System.out.println(fromDoWhile);
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_UNASSIGNED_VARIABLE".equals(d.code()) && d.message().contains("fromWhile")));
        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_UNASSIGNED_VARIABLE".equals(d.code()) && d.message().contains("fromDoWhile")));
    }

    @Test
    void coreDefiniteAssignmentRuleHandlesLabeledBreakAndContinueExits() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDefiniteAssignmentInspection(), """
            class Example {
                void run() {
                    int fromLabeledBlock;
                    outer: {
                        fromLabeledBlock = 1;
                        break outer;
                    }
                    System.out.println(fromLabeledBlock);
                }

                void loop() {
                    int fromLabeledLoop;
                    outer:
                    do {
                        fromLabeledLoop = 1;
                        continue outer;
                    } while (false);
                    System.out.println(fromLabeledLoop);
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_UNASSIGNED_VARIABLE".equals(d.code()) && d.message().contains("fromLabeledBlock")));
        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_UNASSIGNED_VARIABLE".equals(d.code()) && d.message().contains("fromLabeledLoop")));
    }

    @Test
    void coreDefiniteAssignmentRuleHandlesSwitchFallthroughAndMissingDefaultExits() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDefiniteAssignmentInspection(), """
            class Example {
                void assigned(int mode) {
                    int assignedValue;
                    switch (mode) {
                        case 0:
                        case 1:
                            assignedValue = 1;
                            break;
                        default:
                            assignedValue = 2;
                    }
                    System.out.println(assignedValue);
                }

                void fallthroughAssigned(int mode) {
                    int fallthroughValue;
                    switch (mode) {
                        case 0:
                            fallthroughValue = 1;
                        case 1:
                            fallthroughValue = 2;
                            break;
                        default:
                            fallthroughValue = 3;
                    }
                    System.out.println(fallthroughValue);
                }

                void missingDefault(int mode) {
                    int missingDefaultValue;
                    switch (mode) {
                        case 0:
                            missingDefaultValue = 1;
                            break;
                    }
                    System.out.println(missingDefaultValue);
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_UNASSIGNED_VARIABLE".equals(d.code()) && d.message().contains("assignedValue")));
        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_UNASSIGNED_VARIABLE".equals(d.code()) && d.message().contains("fallthroughValue")));
        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_UNASSIGNED_VARIABLE".equals(d.code()) && d.message().contains("missingDefaultValue")));
    }

    @Test
    void coreDefiniteAssignmentRuleDoesNotTreatForUpdateAsPreLoopAssignment() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDefiniteAssignmentInspection(), """
            class Example {
                void run() {
                    int updatedValue;
                    for (;; updatedValue = 1) {
                        break;
                    }
                    System.out.println(updatedValue);
                }

                void ok() {
                    int initializedValue;
                    for (initializedValue = 1;;) {
                        break;
                    }
                    System.out.println(initializedValue);
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_UNASSIGNED_VARIABLE".equals(d.code()) && d.message().contains("updatedValue")));
        assertEquals(1, diagnostics.stream()
            .filter(d -> "SEM_UNASSIGNED_VARIABLE".equals(d.code()) && d.message().contains("updatedValue"))
            .count());
    }

    @Test
    void coreDefiniteAssignmentRuleEmitsUninitializedFinalFieldDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDefiniteAssignmentInspection(), """
            class Example {
                final int value;
                final int initialized;

                {
                    initialized = 1;
                }

                Example(boolean flag) {
                    if (flag) {
                        value = 1;
                    }
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_UNINITIALIZED_FINAL_FIELD".equals(d.code()) && d.message().contains("value")));
        assertFalse(diagnostics.stream().anyMatch(d -> d.message().contains("'initialized'")));
    }

    @Test
    void coreDefiniteAssignmentRuleEmitsIllegalFinalFieldAssignmentDiagnostic() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDefiniteAssignmentInspection(), """
            class Example {
                final int value = 1;

                Example() {
                    value = 2;
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d -> "SEM_ILLEGAL_FINAL_ASSIGNMENT".equals(d.code()) && d.message().contains("value")));
    }

    @Test
    void coreDefiniteAssignmentRuleHandlesFinalFieldInitializationThroughSwitchFallthrough() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreDefiniteAssignmentInspection(), """
            class Example {
                final int value;

                Example(int mode) {
                    switch (mode) {
                        case 0:
                        case 1:
                            value = 1;
                            break;
                        default:
                            value = 2;
                    }
                }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d -> "SEM_UNINITIALIZED_FINAL_FIELD".equals(d.code()) && d.message().contains("value")));
    }


}
