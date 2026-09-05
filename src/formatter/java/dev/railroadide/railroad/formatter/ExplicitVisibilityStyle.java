package dev.railroadide.railroad.formatter;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePathScanner;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Reports package-private declarations without choosing a different access level for them. */
public final class ExplicitVisibilityStyle extends TreePathScanner<Void, Void> {
    private final CompilationUnitTree unit;
    private final SourcePositions positions;
    private final List<Violation> violations = new ArrayList<>();

    private ExplicitVisibilityStyle(CompilationUnitTree unit, SourcePositions positions) {
        this.unit = unit;
        this.positions = positions;
    }

    /** Inspect the parsed tree before attribution adds implicit constructors and members. */
    public static List<Violation> inspect(CompilationUnitTree unit, SourcePositions positions) {
        var scanner = new ExplicitVisibilityStyle(unit, positions);
        scanner.scan(unit, null);
        return List.copyOf(scanner.violations);
    }

    @Override
    public Void visitClass(ClassTree tree, Void unused) {
        Tree parent = getCurrentPath().getParentPath().getLeaf();
        // Local and anonymous classes cannot declare an access modifier.
        if (!tree.getSimpleName().isEmpty()
            && (parent instanceof CompilationUnitTree || parent instanceof ClassTree)
            && !isInterface(parent)) {
            inspect(tree, tree.getModifiers(), "type '" + tree.getSimpleName() + "'");
        }
        return super.visitClass(tree, unused);
    }

    @Override
    public Void visitMethod(MethodTree tree, Void unused) {
        Tree parent = getCurrentPath().getParentPath().getLeaf();
        boolean constructor = tree.getReturnType() == null;
        // Interface methods are implicitly public; enum constructors are implicitly private.
        if (parent instanceof ClassTree owner && !isInterface(owner)
            && !(constructor && owner.getKind() == Tree.Kind.ENUM)) {
            String description = constructor
                ? "constructor '" + owner.getSimpleName() + "'"
                : "method '" + tree.getName() + "'";
            inspect(tree, tree.getModifiers(), description);
        }
        return super.visitMethod(tree, unused);
    }

    @Override
    public Void visitVariable(VariableTree tree, Void unused) {
        Tree parent = getCurrentPath().getParentPath().getLeaf();
        // Parameters, locals, and resources have no member visibility. Interface fields, enum constants,
        // and record component fields already have public/private access in the parsed tree.
        if (parent instanceof ClassTree && !isInterface(parent)) {
            inspect(tree, tree.getModifiers(), "field '" + tree.getName() + "'");
        }
        return super.visitVariable(tree, unused);
    }

    private void inspect(Tree tree, ModifiersTree modifiers, String description) {
        if (modifiers.getFlags().contains(Modifier.PUBLIC)
            || modifiers.getFlags().contains(Modifier.PROTECTED)
            || modifiers.getFlags().contains(Modifier.PRIVATE))
            return;

        long start = positions.getStartPosition(unit, tree);
        if (start >= 0) {
            violations.add(new Violation(Math.toIntExact(unit.getLineMap().getLineNumber(start)),
                "Package-private " + description + " is not allowed; choose an explicit access modifier"));
        }
    }

    private static boolean isInterface(Tree tree) {
        return tree.getKind() == Tree.Kind.INTERFACE || tree.getKind() == Tree.Kind.ANNOTATION_TYPE;
    }

    public record Violation(int line, String message) {
    }
}
