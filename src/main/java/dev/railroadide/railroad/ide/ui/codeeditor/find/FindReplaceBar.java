package dev.railroadide.railroad.ide.ui.codeeditor.find;

import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.TextRange;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.plugin.spi.editor.EditResult;
import dev.railroadide.railroad.plugin.spi.editor.TextEditorAccess;
import dev.railroadide.railroad.plugin.spi.editor.TextReplacement;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.localized.LocalizedTextProperty;
import dev.railroadide.railroad.ui.localized.LocalizedTooltip;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import javafx.beans.InvalidationListener;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.IndexRange;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.reactfx.Subscription;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.regex.PatternSyntaxException;

/**
 * Finds and replaces text in one editor, highlighting matches while open.
 * Searching follows the editor's text and selection; all methods must be called on the JavaFX thread.
 */
public final class FindReplaceBar extends VBox {
    private static final int MATCH_LIMIT = 100_000;
    private static final int MAX_PREFILL_LENGTH = 1_000;
    private static final Duration REFRESH_DELAY = Duration.ofMillis(100);
    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");

    private final TextField searchField = new TextField();
    private final TextField replaceField = new TextField();
    private final ToggleButton matchCase = option("Cc", "railroad.editor.find.match_case");
    private final ToggleButton wholeWords = option("W", "railroad.editor.find.whole_words");
    private final ToggleButton regex = option(".*", "railroad.editor.find.regex");
    private final Label status = new Label();
    private final Tooltip statusTooltip = new Tooltip();
    private final RRButton replaceToggle = toolButton("railroad.editor.find.toggle_replace",
        FontAwesomeSolid.CHEVRON_RIGHT);
    private final Button replaceButton = actionButton("railroad.editor.find.replace");
    private final Button replaceAllButton = actionButton("railroad.editor.find.replace_all");
    private final HBox replaceRow;
    private final InvalidationListener selectionListener = _ -> selectionChanged();

    private TextEditorPane editor;
    private FindMatchHighlighter highlighter;
    private Subscription textSubscription;
    private List<TextRange> matches = List.of();
    private int currentIndex = -1;
    private int searchOrigin;
    private boolean limitReached;
    private String error;
    private boolean patternInvalid;
    private boolean selecting;

    /** Creates a closed bar without a target editor. */
    public FindReplaceBar() {
        getStyleClass().add("find-replace-bar");
        setVisible(false);
        setManaged(false);

        searchField.getStyleClass().add("find-replace-field");
        searchField.promptTextProperty().bind(new LocalizedTextProperty(searchField, "promptText",
            "railroad.editor.find.search_prompt"));
        replaceField.getStyleClass().add("find-replace-field");
        replaceField.promptTextProperty().bind(new LocalizedTextProperty(replaceField, "promptText",
            "railroad.editor.find.replace_prompt"));
        HBox.setHgrow(searchField, Priority.ALWAYS);
        HBox.setHgrow(replaceField, Priority.ALWAYS);

        status.getStyleClass().add("find-replace-status");
        status.setMinWidth(Region.USE_PREF_SIZE);

        RRButton previous = toolButton("railroad.editor.find.previous", FontAwesomeSolid.ARROW_UP);
        previous.setOnAction(_ -> findPrevious());
        RRButton next = toolButton("railroad.editor.find.next", FontAwesomeSolid.ARROW_DOWN);
        next.setOnAction(_ -> findNext());
        RRButton close = toolButton("railroad.editor.find.close", FontAwesomeSolid.TIMES);
        close.setOnAction(_ -> close());
        replaceToggle.setOnAction(_ -> setReplaceVisible(!isReplaceVisible()));
        replaceButton.setOnAction(_ -> replaceCurrent());
        replaceAllButton.setOnAction(_ -> replaceAll());

        var options = new HBox(matchCase, wholeWords, regex);
        options.getStyleClass().add("find-replace-options");
        options.setAlignment(Pos.CENTER_LEFT);
        var searchRow = new HBox(replaceToggle, searchField, options, status, previous, next, close);
        searchRow.getStyleClass().add("find-replace-row");
        searchRow.setAlignment(Pos.CENTER_LEFT);

        var replaceIndent = new Region();
        replaceIndent.minWidthProperty().bind(replaceToggle.widthProperty());
        replaceRow = new HBox(replaceIndent, replaceField, replaceButton, replaceAllButton);
        replaceRow.getStyleClass().add("find-replace-row");
        replaceRow.setAlignment(Pos.CENTER_LEFT);
        setReplaceVisible(false);
        getChildren().addAll(searchRow, replaceRow);

        searchField.textProperty().addListener(_ -> refresh(true));
        matchCase.selectedProperty().addListener(_ -> refresh(true));
        wholeWords.selectedProperty().addListener(_ -> refresh(true));
        regex.selectedProperty().addListener(_ -> refresh(true));
        searchField.addEventHandler(KeyEvent.KEY_PRESSED, this::handleSearchKey);
        replaceField.addEventHandler(KeyEvent.KEY_PRESSED, this::handleReplaceKey);
        addEventFilter(KeyEvent.KEY_PRESSED, this::handleOptionShortcut);
    }

    /**
     * Targets another editor, moving an open search to it.
     *
     * @param editor editor to search, or {@code null} to close the bar
     */
    public void setEditor(TextEditorPane editor) {
        if (this.editor == editor)
            return;

        boolean open = isOpen();
        detach();
        this.editor = editor;
        if (editor == null) {
            hide();
        } else if (open) {
            attach();
            searchOrigin = editor.getSelection().getStart();
            refresh(false);
        }
    }

    /**
     * Opens the bar on its editor and focuses the search field, seeding it with a selected single line.
     *
     * @param withReplace whether the replace row is shown
     */
    public void open(boolean withReplace) {
        if (editor == null)
            return;

        setReplaceVisible(withReplace);
        searchOrigin = editor.getSelection().getStart();
        String selected = editor.getSelectedText();
        boolean show = !isOpen();
        if (show) {
            setVisible(true);
            setManaged(true);
            attach();
        }
        if (!selected.isEmpty() && selected.length() <= MAX_PREFILL_LENGTH && selected.indexOf('\n') < 0) {
            String text = regex.isSelected() ? escapeRegex(selected) : selected;
            if (!text.equals(searchField.getText())) {
                searchField.setText(text);
            } else if (show) {
                refresh(true);
            }
        } else if (show) {
            refresh(true);
        }
        searchField.requestFocus();
        searchField.selectAll();
    }

    /** Closes the bar, removes its highlights and returns focus to the editor. */
    public void close() {
        boolean focused = isFocusWithin();
        hide();
        if (editor != null && focused) {
            editor.requestFocus();
        }
    }

    /**
     * Reports whether the bar is showing.
     *
     * @return whether the bar is open
     */
    public boolean isOpen() {
        return isVisible();
    }

    /** Selects the first match after the editor selection, wrapping to the start of the document. */
    public void findNext() {
        if (!prepareNavigation())
            return;

        int index = firstStartingAtOrAfter(editor.getSelection().getEnd());
        navigateTo(index == matches.size() ? 0 : index);
    }

    /** Selects the last match before the editor selection, wrapping to the end of the document. */
    public void findPrevious() {
        if (!prepareNavigation())
            return;

        int index = firstStartingAtOrAfter(editor.getSelection().getStart()) - 1;
        navigateTo(index < 0 ? matches.size() - 1 : index);
    }

    /** Replaces the selected match and selects the following one, or finds the next match if none is selected. */
    public void replaceCurrent() {
        if (!prepareNavigation())
            return;

        int index = indexOfSelection();
        if (index < 0) {
            findNext();
            return;
        }

        TextRange match = matches.get(index);
        String replacement = replaceField.getText();
        List<TextReplacement> applied = apply(text -> {
            TextReplacement edit = query().replace(text, match, replacement);
            return edit == null ? List.of() : List.of(edit);
        });
        if (applied == null || applied.isEmpty())
            return;

        refresh(false);
        if (!matches.isEmpty()) {
            int next = firstStartingAtOrAfter(match.start + applied.getFirst().text().length());
            navigateTo(next == matches.size() ? 0 : next);
        }
    }

    /** Replaces every match as a single undoable edit. */
    public void replaceAll() {
        if (editor == null)
            return;

        String replacement = replaceField.getText();
        if (apply(text -> query().replaceAll(text, replacement)) != null) {
            refresh(false);
        }
    }

    /**
     * Returns the query described by the bar's current inputs.
     *
     * @return current query
     */
    public FindQuery query() {
        return new FindQuery(searchField.getText(), matchCase.isSelected(), wholeWords.isSelected(),
            regex.isSelected());
    }

    /**
     * Returns the field holding the search text or pattern.
     *
     * @return search field
     */
    public TextField searchField() {
        return searchField;
    }

    /**
     * Returns the field holding the replacement text or template.
     *
     * @return replacement field
     */
    public TextField replaceField() {
        return replaceField;
    }

    /**
     * Returns the toggle that makes matching case-sensitive.
     *
     * @return match-case toggle
     */
    public ToggleButton matchCaseToggle() {
        return matchCase;
    }

    /**
     * Returns the toggle that restricts matches to whole words.
     *
     * @return whole-words toggle
     */
    public ToggleButton wholeWordsToggle() {
        return wholeWords;
    }

    /**
     * Returns the toggle that interprets the search text as a regular expression.
     *
     * @return regex toggle
     */
    public ToggleButton regexToggle() {
        return regex;
    }

    /**
     * Reports whether the replace row is shown.
     *
     * @return whether replacement controls are visible
     */
    public boolean isReplaceVisible() {
        return replaceRow.isVisible();
    }

    /**
     * Returns the matches of the latest search, capped at a fixed limit.
     *
     * @return matches in document order, empty while closed
     */
    public List<TextRange> matches() {
        return matches;
    }

    /**
     * Returns the position of the selected match within {@link #matches()}.
     *
     * @return current match index, or {@code -1} when the selection is not a match
     */
    public int currentIndex() {
        return currentIndex;
    }

    /**
     * Returns the displayed result count or error summary.
     *
     * @return status text, empty when there is no query
     */
    public String statusText() {
        return status.getText();
    }

    /**
     * Returns the highlighter drawing matches on the editor.
     *
     * @return highlighter, or {@code null} while closed
     */
    public FindMatchHighlighter highlighter() {
        return highlighter;
    }

    private boolean prepareNavigation() {
        if (editor == null)
            return false;
        if (!isOpen()) {
            setVisible(true);
            setManaged(true);
            attach();
        }
        refresh(false);
        return !matches.isEmpty();
    }

    private void navigateTo(int index) {
        select(index);
        searchOrigin = matches.get(index).start;
    }

    private void select(int index) {
        currentIndex = index;
        TextRange match = matches.get(index);
        selecting = true;
        try {
            editor.showSelection(match.start, match.end);
        } finally {
            selecting = false;
        }
        keepClearOfBar(match);
        updateView();
    }

    private void keepClearOfBar(TextRange match) {
        Parent host = getParent();
        if (host == null || getScene() == null || editor.getScene() != getScene())
            return;

        // Lay out now so the editor has scrolled to the match before measuring what the bar covers.
        host.layout();
        Bounds bar = localToScreen(getLayoutBounds());
        Bounds selected = editor.getCharacterBoundsOnScreen(match.start, match.end).orElse(null);
        if (bar == null || selected == null || selected.getMinY() >= bar.getMaxY())
            return;

        editor.scrollYBy(selected.getMinY() - bar.getMaxY());
        host.layout();
    }

    private void refresh(boolean navigate) {
        if (editor == null || !isOpen())
            return;

        error = null;
        patternInvalid = false;
        List<TextRange> found;
        try {
            found = query().findAll(editor.getText(), MATCH_LIMIT + 1);
        } catch (PatternSyntaxException exception) {
            found = List.of();
            patternInvalid = true;
            error = exception.getDescription();
        }
        limitReached = found.size() > MATCH_LIMIT;
        matches = limitReached ? List.copyOf(found.subList(0, MATCH_LIMIT)) : List.copyOf(found);

        if (navigate && !matches.isEmpty()) {
            int index = firstStartingAtOrAfter(searchOrigin);
            select(index == matches.size() ? 0 : index);
        } else {
            currentIndex = indexOfSelection();
            updateView();
        }
    }

    private void selectionChanged() {
        if (selecting)
            return;

        currentIndex = indexOfSelection();
        if (currentIndex < 0) {
            searchOrigin = editor.getSelection().getStart();
        }
        updateView();
    }

    private List<TextReplacement> apply(Function<String, List<TextReplacement>> edits) {
        TextEditorAccess access = editor.textEditorAccess();
        EditResult result;
        try {
            TextDocumentSnapshot snapshot = access.snapshot();
            List<TextReplacement> replacements = edits.apply(snapshot.text());
            result = access.replace(snapshot, replacements);
            if (result == EditResult.APPLIED)
                return replacements;
        } catch (PatternSyntaxException exception) {
            showError(exception.getDescription(), true);
            return null;
        } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
            showError(L18n.localize("railroad.editor.find.invalid_replacement", exception.getMessage()), false);
            return null;
        } catch (IllegalStateException exception) {
            result = EditResult.UNAVAILABLE;
        }

        showError(L18n.localize(result == EditResult.READ_ONLY
            ? "railroad.editor.find.read_only"
            : "railroad.editor.find.replace_failed"), false);
        return null;
    }

    private void showError(String message, boolean invalidPattern) {
        error = message;
        patternInvalid = invalidPattern;
        updateView();
    }

    private void updateView() {
        if (highlighter != null) {
            highlighter.show(matches, currentIndex < 0 ? null : matches.get(currentIndex));
        }

        searchField.pseudoClassStateChanged(ERROR, patternInvalid);
        status.pseudoClassStateChanged(ERROR, error != null);
        if (error != null) {
            status.setText(L18n.localize(patternInvalid
                ? "railroad.editor.find.invalid_regex"
                : "railroad.editor.find.error"));
            statusTooltip.setText(error);
            status.setTooltip(statusTooltip);
        } else {
            status.setTooltip(null);
            status.setText(countText());
        }

        boolean editable = editor != null && editor.isEditable();
        replaceButton.setDisable(!editable || matches.isEmpty());
        replaceAllButton.setDisable(!editable || matches.isEmpty());
    }

    private String countText() {
        if (searchField.getText().isEmpty())
            return "";
        if (matches.isEmpty())
            return L18n.localize("railroad.editor.find.no_results");

        String total = limitReached ? matches.size() + "+" : Integer.toString(matches.size());
        return currentIndex < 0
            ? L18n.localize("railroad.editor.find.result_count", total)
            : L18n.localize("railroad.editor.find.result_position", currentIndex + 1, total);
    }

    private int indexOfSelection() {
        if (editor == null)
            return -1;

        IndexRange selection = editor.getSelection();
        int index = firstStartingAtOrAfter(selection.getStart());
        if (index < matches.size()) {
            TextRange match = matches.get(index);
            if (match.start == selection.getStart() && match.end == selection.getEnd())
                return index;
        }
        return -1;
    }

    private int firstStartingAtOrAfter(int offset) {
        int low = 0;
        int high = matches.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (matches.get(middle).start < offset) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private void attach() {
        if (editor == null || textSubscription != null)
            return;

        highlighter = new FindMatchHighlighter(editor);
        textSubscription = editor.plainTextChanges()
            .successionEnds(REFRESH_DELAY)
            .subscribe(_ -> refresh(false));
        editor.selectionProperty().addListener(selectionListener);
    }

    private void detach() {
        if (textSubscription != null) {
            textSubscription.unsubscribe();
            textSubscription = null;
            editor.selectionProperty().removeListener(selectionListener);
        }
        if (highlighter != null) {
            highlighter.clear();
            highlighter = null;
        }
        matches = List.of();
        currentIndex = -1;
    }

    private void hide() {
        detach();
        setVisible(false);
        setManaged(false);
    }

    private void setReplaceVisible(boolean visible) {
        replaceRow.setVisible(visible);
        replaceRow.setManaged(visible);
        replaceToggle.setIcon(visible ? FontAwesomeSolid.CHEVRON_DOWN : FontAwesomeSolid.CHEVRON_RIGHT);
    }

    private void handleSearchKey(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            if (event.isShiftDown()) {
                findPrevious();
            } else {
                findNext();
            }
            event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE) {
            close();
            event.consume();
        }
    }

    private void handleReplaceKey(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            replaceCurrent();
            event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE) {
            close();
            event.consume();
        }
    }

    private void handleOptionShortcut(KeyEvent event) {
        if (!event.isAltDown() || event.isControlDown() || event.isMetaDown() || event.isShiftDown())
            return;

        ToggleButton option = switch (event.getCode()) {
            case C -> matchCase;
            case W -> wholeWords;
            case X -> regex;
            default -> null;
        };
        if (option != null) {
            option.setSelected(!option.isSelected());
            event.consume();
        }
    }

    private static String escapeRegex(String text) {
        return text.replaceAll("[\\\\^$.|?*+()\\[\\]{}]", "\\\\$0");
    }

    private static ToggleButton option(String text, String tooltipKey) {
        var toggle = new ToggleButton(text);
        toggle.getStyleClass().add("find-replace-option");
        toggle.setFocusTraversable(false);
        var tooltip = new LocalizedTooltip(tooltipKey);
        toggle.setTooltip(tooltip);
        toggle.accessibleTextProperty().bind(tooltip.textProperty());
        return toggle;
    }

    private static RRButton toolButton(String key, FontAwesomeSolid icon) {
        var button = new RRButton("", icon);
        button.setVariant(ButtonVariant.GHOST);
        button.getStyleClass().add("toolbar-button");
        button.setFocusTraversable(false);
        var tooltip = new LocalizedTooltip(key);
        button.setTooltip(tooltip);
        button.accessibleTextProperty().bind(tooltip.textProperty());
        return button;
    }

    private static Button actionButton(String key) {
        var button = new RRButton(key);
        button.setVariant(ButtonVariant.SECONDARY);
        button.getStyleClass().add("find-replace-action");
        return button;
    }
}
