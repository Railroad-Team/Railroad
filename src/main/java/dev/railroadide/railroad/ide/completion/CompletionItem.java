package dev.railroadide.railroad.ide.completion;

import org.jetbrains.annotations.NotNull;

/**
 * Immutable model representing a single completion entry.
 *
 * @param insertText text used to match the prefix and replace it, before any call parentheses
 * @param label main text shown in the completion list
 * @param suffix signature text following the label, such as method parameters
 * @param detail supplementary text shown at the trailing edge, such as a return type
 * @param kind symbol kind used to identify the suggestion visually
 * @param hasParameters whether the selected method declares any parameters
 */
public record CompletionItem(
    String insertText,
    String label,
    String suffix,
    String detail,
    Kind kind,
    boolean hasParameters
) {
    /** Symbol categories displayed by the completion popup. */
    public enum Kind {
        TEXT,
        METHOD,
        FIELD,
        TYPE
    }

    /**
     * Creates a plain suggestion preserving the provider's complete display text.
     *
     * @param insertText text to insert
     * @param displayText provider-supplied label
     */
    public CompletionItem(String insertText, String displayText) {
        this(insertText, displayText, "", "", Kind.TEXT, false);
    }

    /**
     * @param name method name to insert
     * @param parameters formatted parameter list without parentheses
     * @param returnType return type displayed beside the signature
     * @param isStatic whether the method is static
     * @return a method suggestion
     */
    public static CompletionItem method(String name, String parameters, String returnType, boolean isStatic) {
        return new CompletionItem(name, label(name, isStatic), "(" + parameters + ")", returnType, Kind.METHOD,
            !parameters.isBlank());
    }

    /**
     * @param name field name to insert
     * @param type declared field type
     * @param isStatic whether the field is static
     * @return a field suggestion
     */
    public static CompletionItem field(String name, String type, boolean isStatic) {
        return new CompletionItem(name, label(name, isStatic), "", type, Kind.FIELD, false);
    }

    /**
     * @param name type name to insert
     * @param isStatic whether the type is static
     * @return a type suggestion
     */
    public static CompletionItem type(String name, boolean isStatic) {
        return new CompletionItem(name, label(name, isStatic), "", "", Kind.TYPE, false);
    }

    private static String label(String name, boolean isStatic) {
        return isStatic ? "static " + name : name;
    }

    /** @return complete label for accessibility, tooltips and plain-text consumers */
    public String displayText() {
        if (kind == Kind.TYPE)
            return label + " (type)";
        return label + suffix + (detail.isEmpty() ? "" : " : " + detail);
    }

    @Override
    public @NotNull String toString() {
        return displayText();
    }
}
