package dev.railroadide.railroad.ide.ui.codeeditor;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;

import java.util.Collections;
import java.util.Optional;

@Getter
public class DocumentModel {
    private final ObservableList<String> lines = FXCollections.observableArrayList();

    public DocumentModel() {
        this.lines.add(""); // Initialize with an empty line to ensure there's always at least one line
    }

    public void setText(String text) {
        lines.clear();
        Collections.addAll(lines, text.split("\n", -1)); // -1 to keep trailing empty lines
        if(lines.isEmpty()) {
            lines.add(""); // Ensure there's always a final empty line
        }
    }

    public String getText() {
        return String.join("\n", lines);
    }

    public int getLineCount() {
        return lines.size();
    }

    public Optional<String> getLineIfExists(int index) {
        if (index < 0 || index >= lines.size())
            return Optional.empty(); // Return empty if index is out of bounds

        return Optional.of(lines.get(index));
    }

    public String getLineOrThrow(int index) throws IndexOutOfBoundsException {
        return getLineIfExists(index)
                .orElseThrow(() -> new IndexOutOfBoundsException("Line index out of bounds: " + index));
    }

    public String getLine(int index) {
        return getLineIfExists(index)
                .orElse(""); // Return empty string if line does not exist
    }

    public void insertText(int line, int column, String text) {
        if (line >= 0 && line < lines.size()) {
            String currentLine = lines.get(line);
            column = Math.max(0, Math.min(column, currentLine.length()));
            String newLine = currentLine.substring(0, column) + text + currentLine.substring(column);
            lines.set(line, newLine);
        }
    }

    public void deleteText(int line, int column, int length) {
        if (line >= 0 && line < lines.size()) {
            String currentLine = lines.get(line);
            column = Math.max(0, Math.min(column, currentLine.length()));
            int endColumn = Math.min(column + length, currentLine.length());
            String newLine = currentLine.substring(0, column) + currentLine.substring(endColumn);
            lines.set(line, newLine);
        }
    }

    public void insertNewLine(int line, int column) {
        if (line >= 0 && line < lines.size()) {
            String currentLine = lines.get(line);
            column = Math.max(0, Math.min(column, currentLine.length()));
            String beforeCaret = currentLine.substring(0, column);
            String afterCaret = currentLine.substring(column);

            lines.set(line, beforeCaret);
            lines.add(line + 1, afterCaret);
        }
    }

    public void joinLines(int line1, int line2) {
        if (line1 >= 0 && line1 < lines.size() && line2 >= 0 && line2 < lines.size() && line1 != line2) {
            String combined = lines.get(line1) + lines.get(line2);
            lines.set(Math.min(line1, line2), combined);
            lines.remove(Math.max(line1, line2));
        }
    }

    public void clear() {
        lines.clear();
        lines.add(""); // Reset to a single empty line
    }

    public void appendLine(String line) {
        if (line != null && !line.isEmpty()) {
            lines.add(line);
        }
    }

    public void removeLine(int index) {
        if (index >= 0 && index < lines.size()) {
            lines.remove(index);
            if (lines.isEmpty()) {
                lines.add(""); // Ensure there's always at least one line
            }
        }
    }

    public void replaceLine(int index, String newLine) {
        if (index >= 0 && index < lines.size()) {
            lines.set(index, newLine);
        } else if (index == lines.size()) {
            // If the index is equal to the size, append a new line
            appendLine(newLine);
        } else {
            throw new IndexOutOfBoundsException("Line index out of bounds: " + index);
        }
    }

    public void replaceText(int line, int column, String text) {
        if (line >= 0 && line < lines.size()) {
            String currentLine = lines.get(line);
            column = Math.max(0, Math.min(column, currentLine.length()));
            String newLine = currentLine.substring(0, column) + text + currentLine.substring(column + text.length());
            lines.set(line, newLine);
        }
    }

    public void appendText(String text) {
        if (text != null && !text.isEmpty()) {
            String[] newLines = text.split("\n", -1); // -1 to keep trailing empty lines
            for (String line : newLines) {
                if (!line.isEmpty() || !lines.isEmpty()) { // Avoid adding empty lines at the start
                    lines.add(line);
                }
            }

            if (lines.isEmpty() || !lines.getLast().isEmpty()) {
                lines.add(""); // Ensure there's always a final empty line
            }
        }
    }

    public int getLongestLineLength() {
        return lines.stream()
                .mapToInt(String::length)
                .max()
                .orElse(0);
    }
}
