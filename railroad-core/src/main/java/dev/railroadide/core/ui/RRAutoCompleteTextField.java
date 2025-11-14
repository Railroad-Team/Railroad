package dev.railroadide.core.ui;

import javafx.application.Platform;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * {@link RRTextField} variant that can display auto-complete suggestions beneath the field.
 */
public class RRAutoCompleteTextField extends RRTextField {
    private final ContextMenu suggestionsPopup = new ContextMenu();
    private AutoCompleteOptions autoCompleteOptions = new AutoCompleteOptions();

    public RRAutoCompleteTextField() {
        this(null);
    }

    public RRAutoCompleteTextField(String localizationKey, Object... args) {
        super(localizationKey, args);
        initialize();
    }

    private void initialize() {
        suggestionsPopup.setAutoHide(true);
        setPrefWidth(240);

        textProperty().addListener((obs, oldValue, newValue) -> handleTextChanged(newValue));
        focusedProperty().addListener((obs, oldValue, newValue) -> {
            if (!newValue) {
                suggestionsPopup.hide();
            } else if (autoCompleteOptions.isShowSuggestionsOnEmpty()) {
                handleTextChanged(getText());
            }
        });

        addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPress);
    }

    private void handleKeyPress(KeyEvent event) {
        if (event.getCode() == KeyCode.ESCAPE && suggestionsPopup.isShowing()) {
            suggestionsPopup.hide();
            event.consume();
        } else if (event.getCode() == KeyCode.DOWN && !suggestionsPopup.isShowing()) {
            handleTextChanged(getText());
        }
    }

    private void handleTextChanged(@Nullable String newValue) {
        if (!autoCompleteOptions.isConfigured())
            return;

        String query = newValue == null ? "" : newValue;
        if (!shouldQuery(query)) {
            suggestionsPopup.hide();
            return;
        }

        List<String> suggestions = fetchSuggestions(query);
        if (suggestions.isEmpty()) {
            suggestionsPopup.hide();
            return;
        }

        populatePopup(suggestions);
    }

    private boolean shouldQuery(String query) {
        if ((query.isBlank() && !autoCompleteOptions.isShowSuggestionsOnEmpty()))
            return false;

        return query.isBlank() || query.length() >= autoCompleteOptions.getMinQueryLength();
    }

    private List<String> fetchSuggestions(String query) {
        Collection<String> source = autoCompleteOptions.getSuggestionsProvider() != null ?
            autoCompleteOptions.getSuggestionsProvider().apply(query) :
            resolveStaticSuggestions();

        if (source == null || source.isEmpty())
            return List.of();

        if (autoCompleteOptions.getSuggestionsProvider() != null) {
            return source.stream()
                .filter(Objects::nonNull)
                .limit(autoCompleteOptions.getMaxSuggestions())
                .collect(Collectors.toList());
        }

        final String normalizedQuery = autoCompleteOptions.isCaseSensitive() ? query : query.toLowerCase(Locale.ROOT);
        final boolean emptyQuery = normalizedQuery.isBlank();

        return source.stream()
            .filter(Objects::nonNull)
            .filter(candidate -> emptyQuery || matches(candidate, normalizedQuery))
            .limit(autoCompleteOptions.getMaxSuggestions())
            .collect(Collectors.toList());
    }

    private Collection<String> resolveStaticSuggestions() {
        return autoCompleteOptions.getSuggestionsSupplier() != null ?
            autoCompleteOptions.getSuggestionsSupplier().get() :
            List.of();
    }

    private boolean matches(String candidate, String normalizedQuery) {
        String value = autoCompleteOptions.isCaseSensitive() ? candidate : candidate.toLowerCase(Locale.ROOT);
        return value.contains(normalizedQuery);
    }

    private void populatePopup(List<String> suggestions) {
        List<CustomMenuItem> menuItems = new ArrayList<>(suggestions.size());
        for (String suggestion : suggestions) {
            var entry = new Label(suggestion);
            var item = new CustomMenuItem(entry, true);
            item.setOnAction(event -> {
                setText(suggestion);
                positionCaret(suggestion.length());
                suggestionsPopup.hide();
            });
            menuItems.add(item);
        }

        suggestionsPopup.getItems().setAll(menuItems);
        if (!suggestionsPopup.isShowing()) {
            Platform.runLater(() -> suggestionsPopup.show(this, Side.BOTTOM, 0, 0));
        }
    }

    /**
     * Applies the provided auto-complete options to this text field.
     */
    public void applyOptions(AutoCompleteOptions options) {
        if (options == null)
            throw new IllegalArgumentException("AutoCompleteOptions cannot be null");

        this.autoCompleteOptions = options;
    }
}
