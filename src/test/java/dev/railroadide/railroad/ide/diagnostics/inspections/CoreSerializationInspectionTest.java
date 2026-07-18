package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.railroadide.railroad.ide.diagnostics.inspections.JavaInspectionTestSupport.runProvider;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreSerializationInspectionTest {
    @Test
    void coreSerializationRuleEmitsDiagnosticForDirectAncestorWithoutNoArgConstructor() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreSerializableClassWithUnconstructableAncestorInspection(), """
            import java.io.Serializable;

            class Base {
                Base(int value) {}
            }

            class Child extends Base implements Serializable {
                Child() { super(1); }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_SERIALIZABLE_CLASS_WITH_UNCONSTRUCTABLE_ANCESTOR".equals(d.code())));
    }

    @Test
    void coreSerializationRuleEmitsDiagnosticForIndirectNonSerializableAncestor() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreSerializableClassWithUnconstructableAncestorInspection(), """
            import java.io.Serializable;

            class Base {
                Base(String value) {}
            }

            class Mid extends Base implements Serializable {
                Mid() { super("x"); }
            }

            class Leaf extends Mid implements Serializable {
                Leaf() {}
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_SERIALIZABLE_CLASS_WITH_UNCONSTRUCTABLE_ANCESTOR".equals(d.code())));
    }

    @Test
    void coreSerializationRuleEmitsDiagnosticForPrivateNoArgConstructor() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreSerializableClassWithUnconstructableAncestorInspection(), """
            import java.io.Serializable;

            class Base {
                private Base() {}
            }

            class Child extends Base implements Serializable {
                Child() { super(); }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(d ->
            "SEM_SERIALIZABLE_CLASS_WITH_UNCONSTRUCTABLE_ANCESTOR".equals(d.code())));
    }

    @Test
    void coreSerializationRuleDoesNotEmitDiagnosticForImplicitDefaultConstructor() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreSerializableClassWithUnconstructableAncestorInspection(), """
            import java.io.Serializable;

            class Base {
            }

            class Child extends Base implements Serializable {
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_SERIALIZABLE_CLASS_WITH_UNCONSTRUCTABLE_ANCESTOR".equals(d.code())));
    }

    @Test
    void coreSerializationRuleDoesNotEmitDiagnosticForAccessibleProtectedNoArgConstructor() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreSerializableClassWithUnconstructableAncestorInspection(), """
            import java.io.Serializable;

            class Base {
                protected Base() {}
            }

            class Child extends Base implements Serializable {
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_SERIALIZABLE_CLASS_WITH_UNCONSTRUCTABLE_ANCESTOR".equals(d.code())));
    }

    @Test
    void coreSerializationRuleDoesNotEmitDiagnosticForNestedAncestorImplicitPackagePrivateNoArgConstructor() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreSerializableClassWithUnconstructableAncestorInspection(), """
            import java.io.Serializable;

            class Outer {
                static class Base {
                }
            }

            class Child extends Outer.Base implements Serializable {
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_SERIALIZABLE_CLASS_WITH_UNCONSTRUCTABLE_ANCESTOR".equals(d.code())));
    }

    @Test
    void coreSerializationRuleDoesNotEmitDiagnosticForNonSerializableClass() {
        List<SemanticDiagnostic> diagnostics = runProvider(new CoreSerializableClassWithUnconstructableAncestorInspection(), """
            class Base {
                Base(int value) {}
            }

            class Child extends Base {
                Child() { super(1); }
            }
            """);

        assertFalse(diagnostics.stream().anyMatch(d ->
            "SEM_SERIALIZABLE_CLASS_WITH_UNCONSTRUCTABLE_ANCESTOR".equals(d.code())));
    }


}
