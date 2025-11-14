package dev.railroadide.core.ui;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Configuration holder for auto-complete capable text fields.
 * Provides the data sources and runtime behaviour flags for suggestion lookups.
 */
public class AutoCompleteOptions {
    private Supplier<? extends Collection<String>> suggestionsSupplier;
    private Function<String, ? extends Collection<String>> suggestionsProvider;
    @Setter
    @Getter
    private boolean caseSensitive;
    @Setter
    @Getter
    private boolean showSuggestionsOnEmpty;
    @Getter
    private int maxSuggestions = 5;
    @Getter
    private int minQueryLength = 1;

    public boolean isConfigured() {
        return suggestionsProvider != null || suggestionsSupplier != null;
    }

    public @Nullable Supplier<? extends Collection<String>> getSuggestionsSupplier() {
        return suggestionsSupplier;
    }

    public void setSuggestionsSupplier(@Nullable Supplier<? extends Collection<String>> suggestionsSupplier) {
        this.suggestionsSupplier = suggestionsSupplier;
    }

    public @Nullable Function<String, ? extends Collection<String>> getSuggestionsProvider() {
        return suggestionsProvider;
    }

    public void setSuggestionsProvider(@Nullable Function<String, ? extends Collection<String>> suggestionsProvider) {
        this.suggestionsProvider = suggestionsProvider;
    }

    public void setMaxSuggestions(int maxSuggestions) {
        if (maxSuggestions <= 0)
            throw new IllegalArgumentException("maxSuggestions must be greater than 0");

        this.maxSuggestions = maxSuggestions;
    }

    public void setMinQueryLength(int minQueryLength) {
        if (minQueryLength < 0)
            throw new IllegalArgumentException("minQueryLength must be >= 0");

        this.minQueryLength = minQueryLength;
    }
}
