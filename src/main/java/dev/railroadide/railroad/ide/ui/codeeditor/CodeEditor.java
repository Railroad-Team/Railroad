package dev.railroadide.railroad.ide.ui.codeeditor;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.*;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

public class CodeEditor extends Region {
    private static final Text LINE_HEIGHT_TEST_TEXT = new Text("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");

    private final DocumentModel document;
    private final ListView<String> virtualFlow;
    private final ScrollPane scrollPane;
    private final CaretManager caretManager;

    private final Rectangle caretShape;
    private final Timeline caretBlinkTimeline;

    private final ObjectProperty<Font> editorFont = new SimpleObjectProperty<>(Font.font("Consolas", 14));
    private final DoubleProperty lineHeight = new SimpleDoubleProperty(0.0);
    private final DoubleProperty lineSpacing = new SimpleDoubleProperty(0);
    private final DoubleProperty lineScrollSpeed = new SimpleDoubleProperty(3.0); // Lines to scroll per scroll event
    private final IntegerProperty tabSize = new SimpleIntegerProperty(4);
    private final BooleanProperty convertTabsToSpaces = new SimpleBooleanProperty(true);

    public CodeEditor() {
        this.document = new DocumentModel();
        this.caretManager = new CaretManager(document);

        updateFontMetrics();
        this.caretShape = new Rectangle(1, lineHeight.get(), Color.RED);
        this.caretShape.heightProperty().bind(lineHeight);
        this.caretBlinkTimeline = new Timeline(
                new KeyFrame(Duration.seconds(0.5), e -> caretShape.setVisible(true)),
                new KeyFrame(Duration.seconds(1.0), e -> caretShape.setVisible(false))
        );
        this.caretBlinkTimeline.setCycleCount(Animation.INDEFINITE);

        this.virtualFlow = new ListView<>(document.getLines());
        this.scrollPane = new ScrollPane();
        this.scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        this.scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        this.scrollPane.setContent(virtualFlow);

        setupVirtualFlow();
        setupEventHandlers();
        setupStyling();

        getChildren().addAll(this.scrollPane, this.caretShape);
        requestFocus();
    }

    /**
     * Updates the font metrics to ensure the caret and text lines are rendered correctly.
     */
    private void updateFontMetrics() {
        LINE_HEIGHT_TEST_TEXT.fontProperty().bind(editorFont);
        lineHeight.bind(LINE_HEIGHT_TEST_TEXT.layoutBoundsProperty().map(Bounds::getHeight));
    }

    /**
     * Sets up the virtual flow for displaying text lines.
     */
    private void setupVirtualFlow() {
        this.scrollPane.prefWidthProperty().bind(widthProperty());
        this.scrollPane.prefHeightProperty().bind(heightProperty());
        this.scrollPane.setFitToWidth(true);
        this.scrollPane.setFitToHeight(true);
        this.virtualFlow.setPadding(new Insets(10));
        this.virtualFlow.setOnMouseClicked($ -> requestFocus());
        this.virtualFlow.fixedCellSizeProperty().bind(lineHeight.add(lineSpacing));
        this.virtualFlow.setCellFactory(ignored -> new TextLineCell());
    }

    /**
     * Sets up event handlers for keyboard and mouse interactions.
     */
    private void setupEventHandlers() {
        setFocusTraversable(true);
        setOnKeyPressed(this::handleKeyPress);
        setOnKeyTyped(this::handleKeyTyped);
        this.virtualFlow.setOnMouseClicked(this::handleMouseClick);

        focusedProperty().addListener(
                ($, $1, newValue) -> updateCaretState(newValue));
        this.caretManager.addLineListener(this::handleCaretLineChange);
        this.caretManager.addColumnListener(this::handleCaretColumnChange);

        this.scrollPane.vvalueProperty().addListener(
                ($, $1, $2) -> updateCaretPosition());
        this.scrollPane.setOnScroll(this::handleScroll);
    }

    /**
     * Sets up the styling for the code editor component.
     */
    private void setupStyling() {
        setBackground(new Background(new BackgroundFill(
                Color.rgb(30, 30, 30), CornerRadii.EMPTY, Insets.EMPTY)));
    }

    /**
     * Handles key press events for navigation and text input.
     *
     * @param event The key event to handle.
     */
    private void handleKeyPress(KeyEvent event) {
        caretBlinkTimeline.stop();
        caretShape.setVisible(true);

        switch (event.getCode()) {
            case UP -> caretManager.moveUp();
            case DOWN -> caretManager.moveDown();
            case LEFT -> caretManager.moveLeft();
            case RIGHT -> caretManager.moveRight();
            case ENTER -> insertNewLine();
            case BACK_SPACE -> handleBackspace();
            case DELETE -> handleDelete();
            default -> {
                // Do nothing for other keys in KEY_PRESSED.
                // Character input is handled by handleKeyTyped.
            }
        }

        caretBlinkTimeline.playFromStart();
        event.consume();
    }

    /**
     * Handles key typed events for character input.
     *
     * @param event The key event to handle.
     */
    private void handleKeyTyped(KeyEvent event) {
        caretBlinkTimeline.stop();
        caretShape.setVisible(true);

        String character = event.getCharacter();
        if (character != null && !character.isEmpty() && !character.equals("\r") && !character.equals("\b") && !character.equals("\u007F")) {
            insertText(character);
        }

        caretBlinkTimeline.playFromStart();
        event.consume();
    }

    /**
     * Handles mouse click events to place the caret at the clicked position.
     *
     * @param event The mouse event to handle.
     */
    private void handleMouseClick(MouseEvent event) {
        caretBlinkTimeline.stop();
        caretShape.setVisible(true);

        double clickX = event.getX() - virtualFlow.getPadding().getLeft();
        double clickY = event.getY() - virtualFlow.getPadding().getTop();

        int lineIndex = getLineIndex(clickY);
        int column = getColumn(lineIndex, clickX);

        caretManager.setPosition(lineIndex, column);
        updateCaretPosition();

        requestFocus();
        event.consume();
    }

    /**
     * Calculates the line index based on the Y position of the mouse click.
     *
     * @param yPos The Y position of the click.
     * @return The index of the line at the clicked position.
     */
    private int getLineIndex(double yPos) {
        double fullLineHeight = getFullLineHeight();
        double totalContentHeight = document.getLineCount() * fullLineHeight +
                virtualFlow.getPadding().getTop() + virtualFlow.getPadding().getBottom();
        double viewportHeight = scrollPane.getHeight();
        double scrollOffset = totalContentHeight > viewportHeight ?
                scrollPane.getVvalue() * (totalContentHeight - viewportHeight) :
                0;
        yPos += scrollOffset;

        int lineIndex = Math.clamp((int) (yPos / fullLineHeight),
                0, document.getLineCount() - 1);
        System.out.println("Click Y: " + yPos + ", Line Index: " + lineIndex);
        return lineIndex;
    }

    /**
     * Calculates the column index based on the X position of the mouse click.
     *
     * @param lineIndex The index of the line where the click occurred.
     * @param xPos      The X position of the click.
     * @return The index of the column at the clicked position.
     */
    private int getColumn(int lineIndex, double xPos) {
        String line = document.getLine(lineIndex);

        MEASURING_TEXT.setFont(editorFont.get());

        int column = 0;
        double minDistance = Double.MAX_VALUE;

        // Iterate through the characters of the line to find the closest column
        // considering the visual width of tabs.
        int currentVisualLength = 0;
        for (int i = 0; i <= line.length(); i++) {
            String subString = line.substring(0, i);
            String displayedSubString = subString.replace("\t", " ".repeat(Math.max(0, tabSize.get())));
            MEASURING_TEXT.setText(displayedSubString);
            double charPos = MEASURING_TEXT.getLayoutBounds().getWidth();
            double distance = Math.abs(xPos - charPos);
            if (distance < minDistance) {
                minDistance = distance;
                column = i;
            }
        }

        System.out.println("Click X: " + xPos + ", Line: " + lineIndex + ", Column: " + column);

        return column;
    }

    /**
     * Inserts text at the current caret position.
     *
     * @param text The text to insert.
     */
    private void insertText(String text) {
        int line = caretManager.getLine();
        int column = caretManager.getColumn();

        String textToInsert = text;
        if (convertTabsToSpaces.get()) {
            textToInsert = text.replace("\t", " ".repeat(tabSize.get()));
        }

        document.insertText(line, column, textToInsert);
        caretManager.moveRightBy(textToInsert.length());
    }

    /**
     * Inserts a new line at the current caret position.
     */
    private void insertNewLine() {
        int line = caretManager.getLine();
        int column = caretManager.getColumn();

        document.insertNewLine(line, column);
        caretManager.setPosition(line + 1, 0);
    }

    /**
     * Handles backspace key press to delete text or join lines.
     */
    private void handleBackspace() {
        int line = caretManager.getLine();
        int column = caretManager.getColumn();
        if (column > 0) {
            document.deleteText(line, column - 1, 1);
            caretManager.moveLeft();
        } else if (line > 0) {
            int prevLineLength = document.getLine(line - 1).length();
            document.joinLines(line - 1, line);
            caretManager.setPosition(line - 1, prevLineLength);
        }
    }

    /**
     * Handles delete key press to delete text or join lines.
     */
    private void handleDelete() {
        int line = caretManager.getLine();
        int column = caretManager.getColumn();
        if (column < document.getLine(line).length()) {
            document.deleteText(line, column, 1);
        } else if (line < document.getLineCount() - 1) {
            document.joinLines(line, line + 1);
        }
    }

    /**
     * Requests a layout update and repositions the caret.
     */
    private void redraw() {
        virtualFlow.refresh();
        updateCaretPosition();
    }

    /**
     * Updates the caret position based on the current caret's state.
     */
    private void updateCaretPosition() {
        int lineIndex = caretManager.getLine();
        int columnIndex = caretManager.getColumn();

        double caretX = getCaretX(lineIndex, columnIndex);
        double caretY = getCaretY(lineIndex);

        caretShape.setTranslateX(caretX);
        caretShape.setTranslateY(caretY);

        caretShape.setVisible(true);
        caretBlinkTimeline.playFromStart();
    }

    private static final Text MEASURING_TEXT = new Text();

    /**
     * Calculates the X position of the caret based on the line and column indices.
     *
     * @param lineIndex   The index of the line where the caret is located.
     * @param columnIndex The index of the column where the caret is located.
     * @return The X position of the caret.
     */
    private double getCaretX(int lineIndex, int columnIndex) {
        String currentLineText = document.getLine(lineIndex);

        var tempText = new Text();
        tempText.setFont(editorFont.get());

        String textBeforeCaret = (columnIndex <= currentLineText.length()) ?
                currentLineText.substring(0, columnIndex) : currentLineText;
        String displayedTextBeforeCaret = textBeforeCaret.replace("\t", " ".repeat(Math.max(0, tabSize.get())));
        MEASURING_TEXT.setText(displayedTextBeforeCaret);
        double caretX = MEASURING_TEXT.getLayoutBounds().getWidth() + virtualFlow.getPadding().getLeft();

        double hScrollValue = scrollPane.getHvalue();
        double contentWidth = virtualFlow.getBoundsInLocal().getWidth();
        double viewportWidth = scrollPane.getWidth();
        double hScrollOffset = Math.max(0, contentWidth - viewportWidth) * hScrollValue;

        return caretX - hScrollOffset;
    }

    /**
     * Calculates the Y position of the caret based on the line index.
     *
     * @param lineIndex The index of the line where the caret is located.
     * @return The Y position of the caret.
     */
    private double getCaretY(int lineIndex) {
        double fullLineHeight = getFullLineHeight();
        double lineTopY = lineIndex * fullLineHeight;
        double caretY = lineTopY + virtualFlow.getPadding().getTop();

        // Account for the scroll position of the scrollPane
        double totalContentHeight = document.getLineCount() * fullLineHeight +
                virtualFlow.getPadding().getTop() + virtualFlow.getPadding().getBottom();
        double viewportHeight = scrollPane.getHeight();
        double scrollOffset = 0;
        if (totalContentHeight > viewportHeight) {
            scrollOffset = scrollPane.getVvalue() * (totalContentHeight - viewportHeight);
        }

        caretY -= scrollOffset;
        return caretY;
    }

    /**
     * Calculates the visual length of a string, expanding tabs to spaces based on tabSize.
     * This is used for accurate cursor positioning and rendering.
     *
     * @param text The input string.
     * @return The visual length of the string.
     */
    private int getVisualLength(String text) {
        int visualLength = 0;
        for (char c : text.toCharArray()) {
            if (c == '\t') {
                visualLength += tabSize.get();
            } else {
                visualLength++;
            }
        }
        return visualLength;
    }

    /**
     * Sets the text content of the editor, replacing any existing content.
     *
     * @param text The text to set in the editor.
     */
    public void setText(String text) {
        document.setText(text);
        caretManager.setPosition(0, 0);
        redraw();
    }

    /**
     * Gets the current text content of the editor.
     *
     * @return The text currently in the editor.
     */
    public String getText() {
        return document.getText();
    }

    /**
     * Sets the font used in the code editor.
     *
     * @param font The font to use for the editor text.
     */
    public void setFont(Font font) {
        this.editorFont.set(font);
        redraw();
    }

    /**
     * Gets the current font used in the code editor.
     *
     * @return The font currently used in the editor.
     */
    public Font getFont() {
        return editorFont.get();
    }

    public int getTabSize() {
        return tabSize.get();
    }

    public void setTabSize(int tabSize) {
        this.tabSize.set(tabSize);
        redraw();
    }

    public boolean isConvertTabsToSpaces() {
        return convertTabsToSpaces.get();
    }

    public void setConvertTabsToSpaces(boolean convertTabsToSpaces) {
        this.convertTabsToSpaces.set(convertTabsToSpaces);
        redraw();
    }

    /**
     * Handles changes in the caret line index, adjusting the scroll position if necessary.
     *
     * @param oldValue The previous line index.
     * @param newValue The new line index.
     */
    private void handleCaretLineChange(Integer oldValue, Integer newValue) {
        if (newValue < 0 || newValue >= document.getLineCount()) return;

        double fullLineHeight = getFullLineHeight();
        double totalHeight = fullLineHeight * document.getLineCount();
        double visibleHeight = scrollPane.getHeight();

        double scrollY = scrollPane.getVvalue();
        double scrollOffset = scrollY * (totalHeight - visibleHeight);

        int firstVisibleLine = (int) (scrollOffset / fullLineHeight);
        int visibleLines = (int) (visibleHeight / fullLineHeight);
        int lastVisibleLine = firstVisibleLine + visibleLines - 1;

        double newScrollY = scrollY;
        if (newValue >= lastVisibleLine - 1) {
            newScrollY = (scrollOffset + fullLineHeight) / (totalHeight - visibleHeight);
        } else if (newValue < firstVisibleLine) {
            newScrollY = (newValue * fullLineHeight) / (totalHeight - visibleHeight);
        }

        scrollPane.setVvalue(newScrollY);
        updateCaretPosition();
    }

    /**
     * Handles changes in the caret column index, adjusting the scroll position if necessary.
     *
     * @param oldValue The previous column index.
     * @param newValue The new column index.
     */
    private void handleCaretColumnChange(Integer oldValue, Integer newValue) {
        double contentWidth = virtualFlow.getBoundsInLocal().getWidth();
        double viewportWidth = scrollPane.getWidth();

        if (contentWidth <= viewportWidth) {
            updateCaretPosition();
            return;
        }

        double caretX = getCaretX(caretManager.getLine(), newValue);
        double hScrollValue = scrollPane.getHvalue();
        double hScrollOffset = Math.max(0, contentWidth - viewportWidth) * hScrollValue;

        double visibleCaretX = caretX - hScrollOffset;

        if (visibleCaretX < 0) {
            scrollPane.setHvalue(caretX / (contentWidth - viewportWidth));
        } else if (visibleCaretX > viewportWidth) {
            scrollPane.setHvalue((caretX - viewportWidth) / (contentWidth - viewportWidth));
        }

        updateCaretPosition();
    }

    /**
     * Handles scroll events to scroll by a full line height * lineScrollSpeed.
     *
     * @param event The scroll event to handle.
     */
    private void handleScroll(ScrollEvent event) {
        double scrollDelta = event.getDeltaY();

        double fullLineHeight = getFullLineHeight();
        double totalContentHeight = document.getLineCount() * fullLineHeight +
                virtualFlow.getPadding().getTop() + virtualFlow.getPadding().getBottom();
        double viewportHeight = scrollPane.getHeight();

        if (totalContentHeight <= viewportHeight) {
            event.consume();
            return;
        }

        double scrollableHeight = totalContentHeight - viewportHeight;
        double currentScrollOffsetPixels = scrollPane.getVvalue() * scrollableHeight;
        double pixelScrollAmount = lineScrollSpeed.get() * fullLineHeight;
        double newScrollOffsetPixels = Math.clamp(
                (scrollDelta < 0 ?
                        currentScrollOffsetPixels + pixelScrollAmount :
                        currentScrollOffsetPixels - pixelScrollAmount),
                0, scrollableHeight);

        scrollPane.setVvalue(newScrollOffsetPixels / scrollableHeight);
        event.consume();
    }

    /**
     * Gets the full height of a line, including line spacing.
     *
     * @return The full height of a line in pixels.
     */
    private double getFullLineHeight() {
        return lineHeight.get() + lineSpacing.get();
    }

    private void updateCaretState(boolean isFocused) {
        if (isFocused) {
            caretBlinkTimeline.playFromStart();
            caretShape.setVisible(true);
        } else {
            caretBlinkTimeline.stop();
            caretShape.setVisible(false);
        }

        updateCaretPosition();
    }

    private class TextLineCell extends ListCell<String> {
        private final TextFlow textFlow;
        private final Text textNode;
        private final IntegerProperty index = new SimpleIntegerProperty(this, "index", -1);

        public TextLineCell() {
            this.textNode = new Text();

            this.textFlow = new TextFlow(this.textNode);
            this.textFlow.setLineSpacing(0);
            this.textFlow.setPadding(Insets.EMPTY);
            this.textFlow.setMaxWidth(Double.MAX_VALUE);
            this.textFlow.minHeightProperty().set(0);
            this.textFlow.prefHeightProperty().bind(lineHeight.add(lineSpacing));

            this.textNode.fontProperty().bind(editorFont);
            this.textNode.setFill(Color.WHITE);
            this.textNode.setBoundsType(TextBoundsType.VISUAL);

            setFocusTraversable(true);
        }

        @Override
        public void updateIndex(int newIndex) {
            super.updateIndex(newIndex);
            this.index.set(newIndex);
        }

        @Override
        public void updateItem(String line, boolean empty) {
            super.updateItem(line, empty);

            if (line != null && !empty) {
                String displayedLine = line.replace("\t", " ".repeat(tabSize.get())).replace("\r", "");
                textNode.setText(displayedLine);
                setGraphic(this.textFlow);
            } else {
                textNode.setText("");
                setGraphic(null);
            }
        }
    }
}
