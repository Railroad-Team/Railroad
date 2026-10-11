package dev.railroadide.railroad.debug.breakpoint;

import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Maps source lines across edits, including whole-buffer replacements from reload and undo. */
public final class BreakpointLineMapping {
    private final RawText before;
    private final RawText after;
    private final List<Edit> edits;
    private final String oldText;
    private final String newText;
    private final int position;
    private final int removedLength;
    private final int insertedLength;

    /**
     * Computes line correspondence for a replacement of an entire document.
     *
     * @param before previous contents
     * @param after replacement contents
     */
    public BreakpointLineMapping(String before, String after) {
        this(before, after, -1, 0, 0);
    }

    /**
     * Tracks an exact replacement, using a content diff for whole-document replacements.
     *
     * @param before contents before the edit
     * @param after contents after the edit
     * @param position zero-based UTF-16 edit offset, or -1 to use a content diff
     * @param removedLength number of UTF-16 units replaced
     * @param insertedLength number of UTF-16 units inserted
     */
    public BreakpointLineMapping(String before, String after, int position, int removedLength, int insertedLength) {
        oldText = before;
        newText = after;
        // Whole-buffer replacements (including external reloads) need a content diff.
        this.position = position == 0 && removedLength == before.length() ? -1 : position;
        this.removedLength = removedLength;
        this.insertedLength = insertedLength;
        // Include the editor's final paragraph even when it is empty.
        this.before = new RawText((before + "\n").getBytes(StandardCharsets.UTF_8));
        this.after = new RawText((after + "\n").getBytes(StandardCharsets.UTF_8));
        edits = this.position < 0
            ? new HistogramDiff().diff(RawTextComparator.DEFAULT, this.before, this.after)
            : List.of();
    }

    /**
     * Finds the corresponding breakpoint line after the edit.
     *
     * @param line original one-based line number
     * @return new one-based line, or zero when the original line was deleted
     */
    public int map(int line) {
        if (newText.isEmpty() && !oldText.isEmpty())
            return 0;
        if (position >= 0)
            return mapExactEdit(line);
        int index = line - 1;
        int delta = 0;
        for (Edit edit : edits) {
            if (index < edit.getBeginA())
                break;
            if (index < edit.getEndA()) {
                if (edit.getLengthB() == 0)
                    return 0;
                return mapReplacement(index, edit) + 1;
            }
            delta += edit.getLengthB() - edit.getLengthA();
        }
        return line + delta;
    }

    private int mapExactEdit(int line) {
        int start = 0;
        for (int current = 1; current < line; current++) {
            int newline = oldText.indexOf('\n', start);
            if (newline < 0)
                return line;
            start = newline + 1;
        }
        int end = oldText.indexOf('\n', start);
        if (end < 0) {
            end = oldText.length();
        }
        int removedEnd = position + removedLength;
        int lineEnd = end < oldText.length() ? end + 1 : end;
        if (removedLength > 0 && insertedLength == 0 && start >= position && lineEnd <= removedEnd)
            return 0;

        int anchor = start;
        while (anchor < end && Character.isWhitespace(oldText.charAt(anchor))) {
            anchor++;
        }
        if (anchor < position)
            return line;
        if (anchor >= removedEnd)
            return lineAt(newText, anchor + insertedLength - removedLength);

        int relativeLine = line - lineAt(oldText, position);
        int insertedLines = lineAt(newText, position + insertedLength) - lineAt(newText, position);
        return lineAt(newText, position) + Math.min(relativeLine, insertedLines);
    }

    private static int lineAt(String text, int offset) {
        int line = 1;
        for (int index = 0; index < offset; index++) {
            if (text.charAt(index) == '\n') {
                line++;
            }
        }
        return line;
    }

    private int mapReplacement(int index, Edit edit) {
        // Follow the first non-whitespace character when a line is split or joined.
        // Common prefix/suffix matching also preserves indentation-only replacements.
        String oldText = before.getString(edit.getBeginA(), edit.getEndA(), false);
        String newText = after.getString(edit.getBeginB(), edit.getEndB(), false);
        int offset = 0;
        for (int line = edit.getBeginA(); line < index; line++) {
            offset += before.getString(line).length() + 1;
        }
        String oldLine = before.getString(index);
        int column = 0;
        while (column < oldLine.length() && Character.isWhitespace(oldLine.charAt(column))) {
            column++;
        }
        offset += column;

        int prefix = 0;
        while (prefix < Math.min(oldText.length(), newText.length())
            && oldText.charAt(prefix) == newText.charAt(prefix)) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < Math.min(oldText.length(), newText.length()) - prefix
            && oldText.charAt(oldText.length() - suffix - 1) == newText.charAt(newText.length() - suffix - 1)) {
            suffix++;
        }

        int newOffset;
        if (offset < prefix) {
            newOffset = offset;
        } else if (offset >= oldText.length() - suffix) {
            newOffset = offset + newText.length() - oldText.length();
        } else
            // Edited code keeps its relative line within the replacement where possible.
            return edit.getBeginB() + Math.min(index - edit.getBeginA(), edit.getLengthB() - 1);
        int newLine = edit.getBeginB();
        for (int character = 0; character < newOffset; character++) {
            if (newText.charAt(character) == '\n') {
                newLine++;
            }
        }
        return Math.min(newLine, edit.getEndB() - 1);
    }
}
