package dev.railroadide.railroad.ide.sst.project;

import dev.railroadide.railroad.ide.classparser.Type;
import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.sst.impl.java.JavaImports;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxParser;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

/** Recovers shaded package names from matching declarations in a library and its source attachment. */
public final class JavaSourceRelocations {
    private JavaSourceRelocations() {
    }

    /**
     * Matches source field and supertype declarations to the owning archive's compiled signatures, then resolves
     * unambiguous original names for classes bundled under those packages.
     *
     * @param archive binary archive that owns the source
     * @param origins winning classpath entry for each binary type
     * @param stubs captured binary declarations
     * @param check cancellation check
     * @return source names mapped to physical class names
     * @throws IOException if the source attachment cannot be read
     */
    public static Map<String, String> aliases(
        Path archive,
        Map<String, Path> origins,
        Map<String, ClassStub> stubs,
        Runnable check
    ) throws IOException {
        List<Path> attachments = JavaLibrarySources.sourceArchives(archive);
        if (attachments.isEmpty())
            return Map.of();
        try (var sources = new JarFile(attachments.getFirst().toFile())) {
            List<ClassStub> consumers = new ArrayList<>();
            Set<String> bundled = new HashSet<>();
            for (var entry : origins.entrySet()) {
                check.run();
                if (!archive.equals(entry.getValue()))
                    continue;
                String name = entry.getKey();
                String outer = topLevel(name);
                if (sources.getJarEntry(sourcePath(outer)) == null) {
                    bundled.add(name);
                } else if (name.equals(outer)) {
                    consumers.add(stubs.get(name));
                }
            }
            if (bundled.isEmpty())
                return Map.of();

            Set<PackagePair> pairs = new HashSet<>();
            for (var consumer : consumers) {
                check.run();
                List<String> supers = new ArrayList<>();
                addBundled(supers, consumer.superClass(), bundled);
                consumer.interfaces().forEach(type -> addBundled(supers, type, bundled));
                Map<String, String> fields = new HashMap<>();
                consumer.fields().forEach(field -> {
                    if (field.type() instanceof Type.ClassType type && bundled.contains(type.name())) {
                        fields.put(field.name(), type.name());
                    }
                });
                if (fields.isEmpty() && supers.isEmpty())
                    continue;
                try (var input = sources.getInputStream(sources.getJarEntry(sourcePath(consumer.getFullName())))) {
                    var tree = JavaSyntaxParser.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8), check);
                    collectPairs(consumer, tree.root(), fields, supers, stubs.keySet(), pairs);
                }
            }

            return expand(pairs, bundled, stubs.keySet(), sources, check);
        }
    }

    private static Map<String, String> expand(
        Set<PackagePair> pairs,
        Set<String> bundled,
        Set<String> known,
        JarFile sources,
        Runnable check
    ) {
        if (pairs.isEmpty())
            return Map.of();
        Set<String> ambiguous = new HashSet<>();
        Map<String, String> targets = new HashMap<>();
        for (var pair : pairs) {
            String previous = targets.putIfAbsent(pair.original, pair.binary);
            if (previous != null && !previous.equals(pair.binary)) {
                ambiguous.add(pair.original);
            }
        }
        Map<String, String> aliases = new HashMap<>();
        for (String binary : bundled) {
            check.run();
            String original = null;
            for (var pair : pairs) {
                if (!binary.startsWith(pair.binary))
                    continue;
                String candidate = pair.original + binary.substring(pair.binary.length());
                if (ambiguous.contains(pair.original) || original != null && !original.equals(candidate)) {
                    original = null;
                    break;
                }
                original = candidate;
            }
            if (original != null && !known.contains(original)
                && sources.getJarEntry(sourcePath(topLevel(original))) == null) {
                aliases.merge(original, binary, (left, right) -> left.equals(right) ? left : "");
            }
        }
        aliases.values().removeIf(String::isEmpty);
        return Map.copyOf(aliases);
    }

    private static String topLevel(String name) {
        int nested = name.indexOf('$');
        return nested < 0 ? name : name.substring(0, nested);
    }

    private static String sourcePath(String type) {
        return type.replace('.', '/') + ".java";
    }

    private static void addBundled(List<String> types, Type type, Set<String> bundled) {
        if (type instanceof Type.ClassType declared && bundled.contains(declared.name())) {
            types.add(declared.name());
        }
    }

    private static void collectPairs(
        ClassStub consumer,
        SyntaxNode root,
        Map<String, String> fields,
        List<String> supers,
        Set<String> known,
        Set<PackagePair> pairs
    ) {
        Map<String, String> imports = new HashMap<>();
        for (var entry : JavaImports.imports(root)) {
            if (!entry.isWildcard() && !entry.isStatic()) {
                imports.merge(entry.importedName(), entry.target(), (left, right) -> left.equals(right) ? left : "");
            }
        }
        for (var declaration : root.children()) {
            if (!declaration.kind().id().endsWith("_DECLARATION")
                || !JavaSourceSymbolIds.declarationName(declaration).equals(consumer.name()))
                continue;
            for (var child : declaration.children()) {
                switch (child.kind().id()) {
                    case "JAVA_EXTENDS_CLAUSE", "JAVA_IMPLEMENTS_CLAUSE" -> {
                        for (var ref : child.children()) {
                            if (ref.kind().id().equals("JAVA_TYPE_REFERENCE")) {
                                for (String binary : supers) {
                                    match(ref, binary, imports, known, pairs);
                                }
                            }
                        }
                    }
                    case "JAVA_CLASS_BODY", "JAVA_INTERFACE_BODY", "JAVA_ENUM_BODY", "JAVA_RECORD_BODY" ->
                        matchFields(child, fields, imports, known, pairs);
                }
            }
        }
    }

    private static void matchFields(
        SyntaxNode body,
        Map<String, String> fields,
        Map<String, String> imports,
        Set<String> known,
        Set<PackagePair> pairs
    ) {
        for (var field : body.children()) {
            if (!field.kind().id().equals("JAVA_FIELD_DECLARATION"))
                continue;
            var ref = JavaSourceSymbolIds.child(field, "JAVA_TYPE_REFERENCE");
            if (ref == null)
                continue;
            for (var variable : field.children()) {
                if (variable.kind().id().equals("JAVA_VARIABLE_DECLARATOR")) {
                    match(ref, fields.get(JavaSourceSymbolIds.declarationName(variable)), imports, known, pairs);
                }
            }
        }
    }

    private static void match(
        SyntaxNode ref,
        String binary,
        Map<String, String> imports,
        Set<String> known,
        Set<PackagePair> pairs
    ) {
        if (binary == null)
            return;
        String simple = JavaSemanticAnalyzer.eraseTypeArguments(JavaSemanticAnalyzer.canonicalTypeText(ref));
        String original = imports.get(simple);
        if (original == null || original.indexOf('.') < 0 || known.contains(original) || !binary.endsWith("." + simple))
            return;
        String sourcePackage = original.substring(0, original.lastIndexOf('.'));
        String binaryPackage = binary.substring(0, binary.lastIndexOf('.'));
        // Keep the first shared package segment as the anchor, e.g. org.lib.api -> internal.lib.api.
        while (true) {
            int src = sourcePackage.lastIndexOf('.');
            int bin = binaryPackage.lastIndexOf('.');
            if (src < 0 || bin < 0 || !sourcePackage.substring(src).equals(binaryPackage.substring(bin)))
                break;
            String sourceParent = sourcePackage.substring(0, src);
            String binaryParent = binaryPackage.substring(0, bin);
            if (sourceParent.indexOf('.') < 0 || !sourceParent.substring(sourceParent.lastIndexOf('.') + 1)
                .equals(binaryParent.substring(binaryParent.lastIndexOf('.') + 1)))
                break;
            sourcePackage = sourceParent;
            binaryPackage = binaryParent;
        }
        pairs.add(new PackagePair(sourcePackage + ".", binaryPackage + "."));
    }

    private record PackagePair(String original, String binary) {
    }
}
