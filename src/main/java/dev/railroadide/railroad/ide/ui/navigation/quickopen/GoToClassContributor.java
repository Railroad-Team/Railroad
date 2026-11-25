package dev.railroadide.railroad.ide.ui.navigation.quickopen;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolKind;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolLocation;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import javafx.scene.Node;
import javafx.scene.control.Label;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lists the Java types declared in the project's sources, as recorded by the project language index.
 */
public final class GoToClassContributor implements GoToContributor<GoToClassContributor.TypeEntry> {
    @Override
    public String titleKey() {
        return "railroad.ide.go_to.class.title";
    }

    @Override
    public List<TypeEntry> collect(Project project, Runnable activityCheck) {
        JavaProjectSemanticIndex index = Services.PROJECT_LANGUAGE_INDEX_SERVICE.currentTyped(project,
            JavaLanguageSupport.LANGUAGE_ID);
        if (index == null) {
            activityCheck.run();
            index = Services.PROJECT_LANGUAGE_INDEX_SERVICE.indexTyped(project, JavaLanguageSupport.LANGUAGE_ID);
        }

        return index == null ? List.of() : types(index, activityCheck);
    }

    /**
     * Collects the type declarations of an index.
     *
     * @param index project index
     * @param activityCheck cancellation check, run for every file
     * @return declared types
     */
    public static List<TypeEntry> types(JavaProjectSemanticIndex index, Runnable activityCheck) {
        List<TypeEntry> types = new ArrayList<>();
        for (JavaProjectSemanticIndex.SourceFileIndex file : index.files().values()) {
            activityCheck.run();
            for (JavaProjectSemanticIndex.SymbolDescriptor symbol : file.declaredSymbols()) {
                if (symbol.kind() instanceof JavaSymbolKind kind && isType(kind) && symbol.qualifiedName() != null) {
                    types.add(new TypeEntry(symbol.simpleName(), symbol.qualifiedName(), kind, file.path(),
                        nameOffset(symbol)));
                }
            }
        }

        return types;
    }

    private static boolean isType(JavaSymbolKind kind) {
        return switch (kind) {
            case CLASS, INTERFACE, ENUM, RECORD, ANNOTATION -> true;
            default -> false;
        };
    }

    private static int nameOffset(JavaProjectSemanticIndex.SymbolDescriptor symbol) {
        if (symbol.descriptor() == null)
            return -1;

        for (SymbolLocation location : symbol.descriptor().locations()) {
            if (location.selection() != null)
                return location.selection().range().start;
        }

        return -1;
    }

    /**
     * Finds the name of a type declaration in source text. The file may have changed since it was indexed, so the
     * indexed offset is only used while a declaration of the name still starts there; otherwise the first declaration
     * of the name is used.
     *
     * @param text current source text
     * @param simpleName type name
     * @param indexedOffset offset recorded by the index, or {@code -1}
     * @return offset of the name, or {@code -1} when no declaration is found
     */
    public static int declarationOffset(String text, String simpleName, int indexedOffset) {
        Matcher matcher = Pattern.compile("(?<![\\w$])(?:class|interface|enum|record)\\s+("
            + Pattern.quote(simpleName) + ")(?![\\w$])").matcher(text);
        int first = -1;
        while (matcher.find()) {
            if (matcher.start(1) == indexedOffset)
                return indexedOffset;

            if (first < 0) {
                first = matcher.start(1);
            }
        }

        return first;
    }

    @Override
    public String name(TypeEntry item) {
        return item.simpleName();
    }

    @Override
    public String path(TypeEntry item) {
        return item.qualifiedName();
    }

    @Override
    public String detail(TypeEntry item) {
        String qualifiedName = item.qualifiedName();
        int separator = qualifiedName.length() - item.simpleName().length() - 1;
        return separator > 0 && qualifiedName.endsWith(item.simpleName()) ? qualifiedName.substring(0, separator) : "";
    }

    @Override
    public char separator() {
        return '.';
    }

    @Override
    public Node icon(TypeEntry item) {
        var badge = new Label(switch (item.kind()) {
            case INTERFACE -> "I";
            case ENUM -> "E";
            case RECORD -> "R";
            case ANNOTATION -> "@";
            default -> "C";
        });

        badge.getStyleClass().addAll("go-to-type-badge",
            "go-to-type-" + item.kind().name().toLowerCase(Locale.ROOT));
        return badge;
    }

    @Override
    public void open(TypeEntry item, GoToSearch.Query query) {
        GoToContributor.openInEditor(item.file(), text -> query.hasLine()
            ? query.offsetIn(text)
            : declarationOffset(text, item.simpleName(), item.nameOffset()));
    }

    /**
     * Declared Java type.
     *
     * @param simpleName type name
     * @param qualifiedName fully qualified name, with nested types separated by dots
     * @param kind declaration kind
     * @param file declaring source file
     * @param nameOffset offset of the name when the file was indexed, or {@code -1}
     */
    public record TypeEntry(
        String simpleName,
        String qualifiedName,
        JavaSymbolKind kind,
        Path file,
        int nameOffset
    ) {
    }
}
