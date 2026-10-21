package dev.railroadide.railroad.ui;

import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;
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
    private Callback<ListView<String>, ListCell<String>> suggestionCellFactory;
    /**
     * Whether supplier filtering and match ranking distinguish letter case; false by default.
     *
     * @param caseSensitive true to match and rank using case-sensitive comparisons
     * @return true if comparisons distinguish letter case
     */
    @Setter
    @Getter
    private boolean caseSensitive;
    /**
     * Whether blank queries may display suggestions regardless of minimum query length; false by default.
     *
     * @param showSuggestionsOnEmpty true to allow suggestions for blank queries
     * @return true if blank queries may display suggestions
     */
    @Setter
    @Getter
    private boolean showSuggestionsOnEmpty;
    /**
     * Maximum number of sorted suggestions shown in the popup; defaults to 50.
     *
     * @return the positive suggestion limit
     */
    @Getter
    private int maxSuggestions = 50;
    /**
     * Minimum length of a nonblank query required to fetch suggestions; defaults to one.
     *
     * @return the nonnegative minimum query length
     */
    @Getter
    private int minQueryLength = 1;

    /**
     * Creates unconfigured options with case-insensitive matching, no suggestions for empty queries,
     * a maximum of 50 suggestions, and a minimum query length of one.
     */
    public AutoCompleteOptions() {
    }

    /**
     * Reports whether a suggestion source has been supplied.
     *
     * @return true if either a query provider or a collection supplier is configured
     */
    public boolean isConfigured() {
        return suggestionsProvider != null || suggestionsSupplier != null;
    }

    /**
     * Returns the source filtered by the field when no query provider is configured.
     *
     * @return the suggestion supplier, or null if unset
     */
    public @Nullable Supplier<? extends Collection<String>> getSuggestionsSupplier() {
        return suggestionsSupplier;
    }

    /**
     * Sets a collection source whose non-null entries are filtered by substring and ranked by match position.
     *
     * @param suggestionsSupplier the collection supplier, or null to clear it; ignored while a provider is set
     */
    public void setSuggestionsSupplier(@Nullable Supplier<? extends Collection<String>> suggestionsSupplier) {
        this.suggestionsSupplier = suggestionsSupplier;
    }

    /**
     * Returns the query-specific source, which takes precedence over the collection supplier.
     *
     * @return the query provider, or null if unset
     */
    public @Nullable Function<String, ? extends Collection<String>> getSuggestionsProvider() {
        return suggestionsProvider;
    }

    /**
     * Sets a provider that receives the query and performs its own filtering; results are still ranked and limited.
     *
     * @param suggestionsProvider the query provider, or null to fall back to the collection supplier
     */
    public void setSuggestionsProvider(@Nullable Function<String, ? extends Collection<String>> suggestionsProvider) {
        this.suggestionsProvider = suggestionsProvider;
    }

    /**
     * Returns the cell factory used to display suggestion strings.
     *
     * @return the custom cell factory, or null for the default cells
     */
    public @Nullable Callback<ListView<String>, ListCell<String>> getSuggestionCellFactory() {
        return suggestionCellFactory;
    }

    /**
     * Sets the factory applied to the suggestion list when these options are applied to a field.
     *
     * @param suggestionCellFactory the custom cell factory, or null for default cells
     */
    public void setSuggestionCellFactory(@Nullable Callback<ListView<String>, ListCell<String>> suggestionCellFactory) {
        this.suggestionCellFactory = suggestionCellFactory;
    }

    /**
     * Sets the maximum number of ranked suggestions shown.
     *
     * @param maxSuggestions the positive result limit
     * @throws IllegalArgumentException if maxSuggestions is zero or negative
     */
    public void setMaxSuggestions(int maxSuggestions) {
        if (maxSuggestions <= 0)
            throw new IllegalArgumentException("maxSuggestions must be greater than 0");

        this.maxSuggestions = maxSuggestions;
    }

    /**
     * Sets the minimum length for nonblank queries; blank queries use the show-suggestions-on-empty setting.
     *
     * @param minQueryLength the nonnegative minimum query length
     * @throws IllegalArgumentException if minQueryLength is negative
     */
    public void setMinQueryLength(int minQueryLength) {
        if (minQueryLength < 0)
            throw new IllegalArgumentException("minQueryLength must be >= 0");

        this.minQueryLength = minQueryLength;
    }
}
