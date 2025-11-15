package dev.railroadide.core.ui;

import javafx.application.Platform;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * {@link RRTextField} variant that can display auto-complete suggestions beneath the field.
 */
public class RRAutoCompleteTextField extends RRTextField {
    private static final double MAX_POPUP_HEIGHT = 200;
    private static final double MIN_SUGGESTION_HEIGHT = 28;
    private final ContextMenu suggestionsPopup = new ContextMenu();
    private final ListView<String> suggestionsListView = new RRListView<>();
    private final CustomMenuItem suggestionsContainer = new CustomMenuItem(suggestionsListView, false);
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
        suggestionsContainer.setHideOnClick(false);
        setPrefWidth(240);
        suggestionsPopup.getItems().add(suggestionsContainer);

        suggestionsListView.setFocusTraversable(false);
        suggestionsListView.setOnMouseClicked(event -> applySelectedSuggestion());
        suggestionsListView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                applySelectedSuggestion();
                event.consume();
            }
        });

        applyListViewOptions();

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
            List<String> provided = source.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            return applySortingAndLimit(query, provided);
        }

        final String normalizedQuery = autoCompleteOptions.isCaseSensitive() ? query : query.toLowerCase(Locale.ROOT);
        final boolean emptyQuery = normalizedQuery.isBlank();

        List<String> filtered = source.stream()
            .filter(Objects::nonNull)
            .filter(candidate -> emptyQuery || matches(candidate, normalizedQuery))
            .collect(Collectors.toList());

        return applySortingAndLimit(query, filtered);
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

    private List<String> applySortingAndLimit(String query, Collection<String> suggestions) {
        String normalizedQuery = autoCompleteOptions.isCaseSensitive() ? query : query.toLowerCase(Locale.ROOT);

        return suggestions.stream()
            .sorted(createSuggestionComparator(normalizedQuery))
            .limit(autoCompleteOptions.getMaxSuggestions())
            .collect(Collectors.toList());
    }

    private Comparator<String> createSuggestionComparator(String normalizedQuery) {
        return Comparator
            .<String>comparingInt(candidate -> matchScore(candidate, normalizedQuery))
            .thenComparing(Comparator.naturalOrder());
    }

    private int matchScore(String candidate, String normalizedQuery) {
        if (normalizedQuery.isBlank())
            return 0;

        String value = autoCompleteOptions.isCaseSensitive() ? candidate : candidate.toLowerCase(Locale.ROOT);
        if (value.startsWith(normalizedQuery))
            return 0;

        int index = value.indexOf(normalizedQuery);
        if (index >= 0)
            return 1 + index;

        return Integer.MAX_VALUE;
    }

    private void populatePopup(List<String> suggestions) {
        suggestionsListView.getItems().setAll(suggestions);
        suggestionsListView.getSelectionModel().selectFirst();
        adjustPopupSize();

        if (!suggestionsPopup.isShowing()) {
            Platform.runLater(() -> suggestionsPopup.show(this, Side.BOTTOM, 0, 0));
        }
    }

    private void adjustPopupSize() {
        Platform.runLater(() -> {
            double prefHeight = suggestionsListView.prefHeight(-1);
            if (prefHeight <= 0 || Double.isNaN(prefHeight)) {
                prefHeight = Math.max(MIN_SUGGESTION_HEIGHT, suggestionsListView.getItems().size() * MIN_SUGGESTION_HEIGHT);
            }

            double clampedHeight = Math.min(MAX_POPUP_HEIGHT, prefHeight);
            suggestionsListView.setMinHeight(clampedHeight);
            suggestionsListView.setPrefHeight(clampedHeight);
            suggestionsListView.setMaxHeight(MAX_POPUP_HEIGHT);

            double prefWidth = Math.max(getWidth(), getPrefWidth());
            suggestionsListView.setPrefWidth(prefWidth);
        });
    }

    private void applySelectedSuggestion() {
        String suggestion = suggestionsListView.getSelectionModel().getSelectedItem();
        if (suggestion == null)
            return;

        setText(suggestion);
        positionCaret(suggestion.length());
        suggestionsPopup.hide();
    }

    /**
     * Applies the provided auto-complete options to this text field.
     */
    public void applyOptions(AutoCompleteOptions options) {
        if (options == null)
            throw new IllegalArgumentException("AutoCompleteOptions cannot be null");

        this.autoCompleteOptions = options;
        applyListViewOptions();
    }

    private void applyListViewOptions() {
        suggestionsListView.setCellFactory(autoCompleteOptions.getSuggestionCellFactory());
    }
}
