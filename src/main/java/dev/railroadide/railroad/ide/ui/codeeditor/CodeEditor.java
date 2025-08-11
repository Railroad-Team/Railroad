package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.Railroad;
import javafx.beans.property.*;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;
import javafx.scene.text.TextFlow;

import java.util.Objects;

public class CodeEditor extends Region {
    private static final Text LINE_HEIGHT_TEST_TEXT = new Text("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
    private static final Text MEASURING_TEXT = new Text();

    static {
        MEASURING_TEXT.setBoundsType(TextBoundsType.VISUAL);
        LINE_HEIGHT_TEST_TEXT.setBoundsType(TextBoundsType.VISUAL);
    }

    private final DocumentModel document;
    private final ListView<String> virtualFlow;
    private ScrollBar vBar, hBar;
    private final CaretManager caretManager;
    private boolean scrollbarsInitialized = false;

    private final ObjectProperty<Font> editorFont = new SimpleObjectProperty<>(Font.font("Consolas", 14));
    private final DoubleProperty lineHeight = new SimpleDoubleProperty(0.0);
    private final ObjectProperty<Paint> caretColor = new SimpleObjectProperty<>(Color.RED);
    private final DoubleProperty lineSpacing = new SimpleDoubleProperty(0);
    private final DoubleProperty lineScrollSpeed = new SimpleDoubleProperty(3.0); // Lines to scroll per scroll event
    private final IntegerProperty tabSize = new SimpleIntegerProperty(4);
    private final BooleanProperty convertTabsToSpaces = new SimpleBooleanProperty(true);

    public CodeEditor() {
        this.document = new DocumentModel();

        var caret = new Caret(0, 0, lineHeight, caretColor);
        this.caretManager = new CaretManager(document, caret);

        updateFontMetrics();

        this.virtualFlow = new ListView<>(document.getLines());

        setupVirtualFlow();
        setupEventHandlers();
        setupStyling();

        getChildren().addAll(this.virtualFlow, caret.getShape());
        requestFocus();

        // Listen for the skin to be applied to the ListView
        this.virtualFlow.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                this.vBar = findScrollBar(this.virtualFlow, Orientation.VERTICAL);
                this.hBar = findScrollBar(this.virtualFlow, Orientation.HORIZONTAL);

                if (this.vBar != null && this.hBar != null) {
                    onScrollbarsReady();
                } else {
                    Railroad.LOGGER.error("Scroll bars not found in ListView. Ensure the ListView is properly initialized.");
                }
            }
        });
    }

    private void onScrollbarsReady() {
        this.scrollbarsInitialized = true;
        this.vBar.valueProperty().addListener(
                ($, $1, $2) -> updateCaretPosition());
        this.hBar.valueProperty().addListener(
                ($, $1, $2) -> updateCaretPosition());
    }

    private static ScrollBar findScrollBar(ListView<?> listView, Orientation orientation) {
        for (Node node : listView.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar sb && sb.getOrientation() == orientation) {
                return sb;
            }
        }

        return null;
    }

    private void updateFontMetrics() {
        LINE_HEIGHT_TEST_TEXT.fontProperty().bind(editorFont);
        lineHeight.bind(LINE_HEIGHT_TEST_TEXT.layoutBoundsProperty().map(Bounds::getHeight));
    }

    private void setupVirtualFlow() {
        this.virtualFlow.prefWidthProperty().bind(widthProperty());
        this.virtualFlow.prefHeightProperty().bind(heightProperty());
        this.virtualFlow.setPadding(new Insets(10));
        this.virtualFlow.setOnMouseClicked($ -> requestFocus());
        this.virtualFlow.fixedCellSizeProperty().bind(lineHeight.add(lineSpacing));
        this.virtualFlow.setCellFactory(ignored -> new TextLineCell());
        this.virtualFlow.addEventFilter(ScrollEvent.SCROLL, this::handleScroll);
    }

    private void setupEventHandlers() {
        setFocusTraversable(true);
        setOnKeyPressed(this::handleKeyPress);
        setOnKeyTyped(this::handleKeyTyped);

        focusedProperty().addListener(
                ($, oldValue, newValue) -> {
                    if (oldValue == newValue) return;

                    updateCaretState(newValue);
                });
        this.caretManager.addLineListener(this::handleCaretLineChange);
        this.caretManager.addColumnListener(this::handleCaretColumnChange);
    }

    private void setupStyling() {
        setBackground(new Background(new BackgroundFill(
                Color.rgb(30, 30, 30), CornerRadii.EMPTY, Insets.EMPTY)));
    }

    private void handleKeyPress(KeyEvent event) {
        this.caretManager.getCaret().stopAnimation();

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

        this.caretManager.getCaret().startAnimation();
        event.consume();
    }

    private void handleKeyTyped(KeyEvent event) {
        this.caretManager.getCaret().stopAnimation();

        String character = event.getCharacter();
        if (character != null && !character.isEmpty() && !character.equals("\r") && !character.equals("\b") && !character.equals("\u007F")) {
            insertText(character);
        }

        this.caretManager.getCaret().startAnimation();
        event.consume();
    }

    /**
     * Calculates the normalized vertical scroll position based on the current
     * vertical scroll bar value, minimum, and maximum values.
     *
     * @return A value between 0.0 and 1.0 representing the normalized scroll position.
     */
    private double getNormalizedScrollY() {
        double min = this.vBar.getMin();
        double max = this.vBar.getMax();
        double value = this.vBar.getValue();

        return max - min == 0 ?
                0.0 :
                (value - min) / (max - min);

    }

    /**
     * Calculates the normalized horizontal scroll position based on the current
     * horizontal scroll bar value, minimum, and maximum values.
     *
     * @return A value between 0.0 and 1.0 representing the normalized scroll position.
     */
    private double getNormalizedScrollX() {
        double min = this.hBar.getMin();
        double max = this.hBar.getMax();
        double value = this.hBar.getValue();

        return max - min == 0 ?
                0.0 :
                (value - min) / (max - min);
    }

    private int getLineIndex(double yPos) {
        double fullLineHeight = getFullLineHeight();
        double totalContentHeight = getContentHeight();
        double viewportHeight = this.getLayoutBounds().getHeight();
        double scrollOffset = totalContentHeight > viewportHeight ?
                getNormalizedScrollY() * (totalContentHeight - viewportHeight) :
                0;
        yPos += scrollOffset;

        int lineIndex = Math.clamp((int) (yPos / fullLineHeight),
                0, document.getLineCount() - 1);
        System.out.println("Click Y: " + yPos + ", Line Index: " + lineIndex);
        return lineIndex;
    }

    private int getColumn(int lineIndex, double xPos) {
        String line = document.getLine(lineIndex);

        MEASURING_TEXT.setFont(editorFont.get());

        int column = 0;
        double minDistance = Double.MAX_VALUE;

        // Iterate through the characters of the line to find the closest column
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

        return Math.clamp(column, 0, line.length());
    }

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

    private void insertNewLine() {
        int line = caretManager.getLine();
        int column = caretManager.getColumn();

        document.insertNewLine(line, column);
        caretManager.setPosition(line + 1, 0);
    }

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

    private void handleDelete() {
        int line = caretManager.getLine();
        int column = caretManager.getColumn();
        if (column < document.getLine(line).length()) {
            document.deleteText(line, column, 1);
        } else if (line < document.getLineCount() - 1) {
            document.joinLines(line, line + 1);
        }
    }

    private void redraw() {
        virtualFlow.requestLayout();
        updateCaretPosition();
    }

    private void updateCaretPosition() {
        if (!scrollbarsInitialized) return;

        int lineIndex = caretManager.getLine();
        int columnIndex = caretManager.getColumn();

        double contentX = getCaretX(lineIndex, columnIndex); // content coords
        double contentY = getCaretY(lineIndex);              // content coords

        this.caretManager.getCaret().setX(contentX - getHOffset());
        this.caretManager.getCaret().setY(contentY - getVOffset());
        this.caretManager.getCaret().startAnimation();
    }

    private double getCaretX(int lineIndex, int columnIndex) {
        String currentLineText = document.getLine(lineIndex);

        String textBeforeCaret = (columnIndex <= currentLineText.length())
                ? currentLineText.substring(0, columnIndex)
                : currentLineText;

        String displayedTextBeforeCaret =
                textBeforeCaret.replace("\t", " ".repeat(Math.max(0, tabSize.get())));

        MEASURING_TEXT.setFont(editorFont.get());
        MEASURING_TEXT.setText(displayedTextBeforeCaret);

        return MEASURING_TEXT.getLayoutBounds().getWidth()
                + virtualFlow.getPadding().getLeft();
    }

    private double getCaretY(int lineIndex) {
        double fullLineHeight = getFullLineHeight();
        return lineIndex * fullLineHeight + virtualFlow.getPadding().getTop();
    }

    private double getHOffset() {
        double contentWidth = getContentWidth();
        double viewportWidth = this.virtualFlow.getLayoutBounds().getWidth();
        return Math.max(0, contentWidth - viewportWidth) * getNormalizedScrollX();
    }

    private double getVOffset() {
        double contentHeight = getContentHeight();
        double viewportHeight = this.virtualFlow.getLayoutBounds().getHeight();
        return Math.max(0, contentHeight - viewportHeight) * getNormalizedScrollY();
    }

    private double getContentWidth() {
        double max = 0;
        MEASURING_TEXT.setFont(editorFont.get());
        for (String line : document.getLines()) {
            MEASURING_TEXT.setText(line.replace("\t", " ".repeat(Math.max(0, tabSize.get()))));
            max = Math.max(max, MEASURING_TEXT.getLayoutBounds().getWidth());
        }

        return max + virtualFlow.getPadding().getLeft() + virtualFlow.getPadding().getRight();
    }

    private double getContentHeight() {
        return document.getLineCount() * getFullLineHeight()
                + virtualFlow.getPadding().getTop() + virtualFlow.getPadding().getBottom();
    }

    public void setText(String text) {
        document.setText(text);
        caretManager.setPosition(0, 0);
        if (scrollbarsInitialized) {
            redraw();
        }
    }

    public String getText() {
        return document.getText();
    }

    public void setFont(Font font) {
        this.editorFont.set(font);
        redraw();
    }

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

    private void handleCaretLineChange(Integer oldValue, Integer newValue) {
        if (Objects.equals(oldValue, newValue)) return;
        if (newValue < 0 || newValue >= document.getLineCount()) return;

        double fullLineHeight = getFullLineHeight();
        double totalHeight = fullLineHeight * document.getLineCount();
        double visibleHeight = this.virtualFlow.getLayoutBounds().getHeight();

        double scrollY = getNormalizedScrollY();
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

        this.vBar.setValue(newScrollY);
        updateCaretPosition();
    }

    private void handleCaretColumnChange(Integer oldValue, Integer newValue) {
        if (Objects.equals(oldValue, newValue)) return;

        double contentWidth = getContentWidth();
        double viewportWidth = this.virtualFlow.getLayoutBounds().getWidth();

        if (contentWidth <= viewportWidth) {
            updateCaretPosition();
            return;
        }

        double caretX = getCaretX(caretManager.getLine(), newValue); // content coords
        double hOffset = getHOffset();
        double left = hOffset;
        double right = hOffset + viewportWidth;

        double newValueNorm = getNormalizedScrollX();
        double denom = Math.max(1e-6, contentWidth - viewportWidth);

        if (caretX < left) {
            newValueNorm = Math.clamp(caretX / denom, 0, 1);
        } else if (caretX > right) {
            newValueNorm = Math.clamp((caretX - viewportWidth) / denom, 0, 1);
        }

        this.hBar.setValue(newValueNorm);
        updateCaretPosition();
    }

    private void handleScroll(ScrollEvent event) {
        double scrollDelta = event.getDeltaY();

        double fullLineHeight = getFullLineHeight();
        double totalContentHeight = document.getLineCount() * fullLineHeight +
                virtualFlow.getPadding().getTop() + virtualFlow.getPadding().getBottom();
        double viewportHeight = this.virtualFlow.getLayoutBounds().getHeight();

        if (totalContentHeight <= viewportHeight) {
            event.consume();
            return;
        }

        double scrollableHeight = totalContentHeight - viewportHeight;
        double currentScrollOffsetPixels = getNormalizedScrollY() * scrollableHeight;
        double pixelScrollAmount = lineScrollSpeed.get() * fullLineHeight;
        double newScrollOffsetPixels = Math.clamp(
                (scrollDelta < 0 ?
                        currentScrollOffsetPixels + pixelScrollAmount :
                        currentScrollOffsetPixels - pixelScrollAmount),
                0, scrollableHeight);

        this.vBar.setValue(newScrollOffsetPixels / scrollableHeight);
        event.consume();
    }

    private double getFullLineHeight() {
        return lineHeight.get() + lineSpacing.get();
    }

    private void updateCaretState(boolean isFocused) {
        if (isFocused) {
            this.caretManager.getCaret().startAnimation();
        } else {
            this.caretManager.getCaret().stopAnimation();
        }

        updateCaretPosition();
    }

    private class TextLineCell extends ListCell<String> {
        private final TextFlow textFlow;
        private final Text textNode;

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
            setOnMousePressed(e -> {
                if (isEmpty()) return;

                double clickX = e.getX() - virtualFlow.getPadding().getLeft();
                clickX += getHOffset();

                int lineIndex = getIndex();
                int column = getColumn(lineIndex, clickX);

                caretManager.setPosition(lineIndex, column);
                updateCaretPosition();

                requestFocus();
                e.consume();
            });
        }

        @Override
        public void updateItem(String line, boolean empty) {
            super.updateItem(line, empty);

            if (line != null && !empty) {
                String displayedLine = line.replace("\t", " ".repeat(tabSize.get())).replace("\r", "");
                textNode.setText(displayedLine);
                setGraphic(textFlow);
            } else {
                textNode.setText("");
                setGraphic(null);
            }
        }
    }
}
