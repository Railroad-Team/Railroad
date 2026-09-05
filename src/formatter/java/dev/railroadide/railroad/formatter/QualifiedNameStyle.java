package dev.railroadide.railroad.formatter;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.ModuleTree;
import com.sun.source.tree.PackageTree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Shortens resolved, package-qualified types without changing existing simple-name bindings. */
public final class QualifiedNameStyle extends TreePathScanner<Void, Void> {
    private final CompilationUnitTree unit;
    private final Trees trees;
    private final String source;
    private final List<TreePath> candidates = new ArrayList<>();
    private final Map<String, List<TreePath>> identifiers = new HashMap<>();
    private final Set<String> declarations = new HashSet<>();
    private final Map<String, String> imports = new HashMap<>();

    public QualifiedNameStyle(CompilationUnitTree unit, Trees trees, String source) {
        this.unit = unit;
        this.trees = trees;
        this.source = source;
        for (ImportTree importTree : unit.getImports()) {
            if (importTree.getQualifiedIdentifier() instanceof MemberSelectTree select) {
                imports.put(select.getIdentifier().toString(), select.toString());
            }
        }
        scan(unit, null);
    }

    public boolean hasCandidates() {
        return !candidates.isEmpty();
    }

    @Override
    public Void visitImport(ImportTree tree, Void unused) {
        return null;
    }

    @Override
    public Void visitPackage(PackageTree tree, Void unused) {
        return null;
    }

    @Override
    public Void visitModule(ModuleTree tree, Void unused) {
        return null;
    }

    @Override
    public Void visitMemberSelect(MemberSelectTree tree, Void unused) {
        candidates.add(getCurrentPath());
        return super.visitMemberSelect(tree, unused);
    }

    @Override
    public Void visitIdentifier(IdentifierTree tree, Void unused) {
        identifiers.computeIfAbsent(tree.getName().toString(), _ -> new ArrayList<>()).add(getCurrentPath());
        return null;
    }

    @Override
    public Void visitClass(ClassTree tree, Void unused) {
        declarations.add(tree.getSimpleName().toString());
        return super.visitClass(tree, unused);
    }

    @Override
    public Void visitTypeParameter(TypeParameterTree tree, Void unused) {
        declarations.add(tree.getName().toString());
        return super.visitTypeParameter(tree, unused);
    }

    @Override
    public Void visitVariable(VariableTree tree, Void unused) {
        declarations.add(tree.getName().toString());
        return super.visitVariable(tree, unused);
    }

    public void addEdits(Elements elements, List<RailroadJavaStyle.TextEdit> edits, List<Integer> changedLines) {
        Map<String, Set<String>> qualifiedTypes = new HashMap<>();
        Map<TreePath, TypeElement> resolved = new HashMap<>();
        for (TreePath path : candidates) {
            var select = (MemberSelectTree) path.getLeaf();
            Element element = trees.getElement(path);
            if (!(element instanceof TypeElement type) || type.asType().getKind() == TypeKind.ERROR
                || !(trees.getElement(new TreePath(path, select.getExpression())) instanceof PackageElement))
                continue;

            resolved.put(path, type);
            qualifiedTypes.computeIfAbsent(type.getSimpleName().toString(), _ -> new HashSet<>())
                .add(type.getQualifiedName().toString());
        }

        Set<String> addedImports = new TreeSet<>();
        for (TreePath path : candidates) {
            TypeElement type = resolved.get(path);
            if (type == null)
                continue;

            String simple = type.getSimpleName().toString();
            String qualified = type.getQualifiedName().toString();
            String existingImport = imports.get(simple);
            if (declarations.contains(simple)
                || (existingImport != null && !existingImport.equals(qualified))
                || (existingImport == null && qualifiedTypes.get(simple).size() > 1)
                || hasConflictingIdentifier(simple, type)
                || hasScopeConflict(path, type, elements))
                continue;

            int start = Math.toIntExact(trees.getSourcePositions().getStartPosition(unit, path.getLeaf()));
            int end = Math.toIntExact(trees.getSourcePositions().getEndPosition(unit, path.getLeaf()));
            if (start < 0 || end <= start)
                continue;
            String original = source.substring(start, end);
            if (original.contains("/*") || original.contains("//") || original.contains("\\"))
                continue;
            // A structural rule may already replace this type with var.
            if (edits.stream().anyMatch(edit -> edit.start() < end && edit.end() > start))
                continue;

            edits.add(new RailroadJavaStyle.TextEdit(start, end, simple));
            changedLines.add(Math.toIntExact(unit.getLineMap().getLineNumber(start)));
            String packageName = elements.getPackageOf(type).getQualifiedName().toString();
            String currentPackage = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            if (existingImport == null && !packageName.equals("java.lang") && !packageName.equals(currentPackage)) {
                addedImports.add(qualified);
            }
        }

        if (!addedImports.isEmpty()) {
            addImports(addedImports, edits);
        }
    }

    private boolean hasConflictingIdentifier(String name, TypeElement type) {
        for (TreePath identifier : identifiers.getOrDefault(name, List.of())) {
            if (!type.equals(trees.getElement(identifier)))
                return true;
        }
        return false;
    }

    private boolean hasScopeConflict(TreePath path, TypeElement type, Elements elements) {
        String name = type.getSimpleName().toString();
        for (var scope = trees.getScope(path); scope != null; scope = scope.getEnclosingScope()) {
            for (Element local : scope.getLocalElements()) {
                if (local.getSimpleName().contentEquals(name) && !local.equals(type))
                    return true;
            }
            TypeElement enclosingClass = scope.getEnclosingClass();
            if (enclosingClass == null)
                continue;
            // An unresolved parent may contribute an unknown member with the same name.
            if (enclosingClass.getSuperclass().getKind() == TypeKind.ERROR
                || enclosingClass.getInterfaces().stream().anyMatch(parent -> parent.getKind() == TypeKind.ERROR))
                return true;
            for (Element member : elements.getAllMembers(enclosingClass)) {
                if ((member instanceof TypeElement || member.getKind().isField())
                    && member.getSimpleName().contentEquals(name) && !member.equals(type))
                    return true;
            }
        }
        return false;
    }

    private void addImports(Set<String> addedImports, List<RailroadJavaStyle.TextEdit> edits) {
        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        String block = String.join(newline, addedImports.stream().map(name -> "import " + name + ";").toList());
        List<? extends ImportTree> regularImports = unit.getImports().stream().filter(tree -> !tree.isStatic())
            .toList();
        if (regularImports.isEmpty() && !unit.getImports().isEmpty()) {
            int offset = Math
                .toIntExact(trees.getSourcePositions().getStartPosition(unit, unit.getImports().getFirst()));
            edits.add(new RailroadJavaStyle.TextEdit(offset, offset, block + newline + newline));
            return;
        }
        boolean hasImports = !regularImports.isEmpty();
        if (!hasImports && unit.getPackage() == null) {
            edits.add(new RailroadJavaStyle.TextEdit(0, 0, block + newline + newline));
            return;
        }

        int offset = Math.toIntExact(trees.getSourcePositions().getEndPosition(unit,
            hasImports ? regularImports.getLast() : unit.getPackage()));
        // Keep a trailing line comment attached to its original package/import declaration.
        int lineEnd = source.indexOf('\n', offset);
        if (lineEnd >= 0) {
            String tail = source.substring(offset, lineEnd).strip();
            if (tail.isEmpty() || tail.startsWith("//")) {
                offset = lineEnd + 1;
                edits.add(new RailroadJavaStyle.TextEdit(offset, offset,
                    (hasImports ? "" : newline) + block + newline));
                return;
            }
        }
        edits.add(new RailroadJavaStyle.TextEdit(offset, offset, newline + block + newline));
    }
}
