package dev.railroadide.railroad.ide.diagnostics.inspections;

import org.junit.jupiter.api.Test;
import static dev.railroadide.railroad.ide.diagnostics.inspections.JavaInspectionTestSupport.runProvider;
import static org.junit.jupiter.api.Assertions.*;

public class CoreDuplicateDeclarationInspectionTest {
    @Test
    public void anonymousMembersDoNotCollideWithOuterOrSiblingDeclarations() {
        var diagnostics = runProvider(new CoreDuplicateDeclarationInspection(), """
            class Example {
                int value;
                void run() {}
                Object first = new Object() { int value; void run() {} };
                Object second = new Object() { int value; void run() {} };
            }
            """);
        assertTrue(diagnostics.isEmpty(), () -> diagnostics.toString());
    }

    @Test
    public void duplicateDeclarationsInsideAnAnonymousBodyRemainReported() {
        var diagnostics = runProvider(new CoreDuplicateDeclarationInspection(), """
            class Example {
                Object instance = new Object() { int value; int value; };
            }
            """);
        assertTrue(diagnostics.stream().anyMatch(diagnostic -> "SEM_DUPLICATE_DECLARATION".equals(diagnostic.code())));
    }

    @Test
    public void compactConstructorUsesRecordHeaderToDistinguishOverloads() {
        var diagnostics = runProvider(new CoreDuplicateDeclarationInspection(), """
            record Example(int value) {
                Example {}
                Example() { this(0); }
            }
            """);
        assertTrue(diagnostics.isEmpty(), () -> diagnostics.toString());
    }

    @Test
    public void compactAndExplicitCanonicalConstructorsStillCollide() {
        var diagnostics = runProvider(new CoreDuplicateDeclarationInspection(), """
            record Example(int value) {
                Example {}
                Example(int value) { this.value = value; }
            }
            """);
        assertTrue(diagnostics.stream().anyMatch(diagnostic -> "SEM_DUPLICATE_DECLARATION".equals(diagnostic.code())));
    }
}
