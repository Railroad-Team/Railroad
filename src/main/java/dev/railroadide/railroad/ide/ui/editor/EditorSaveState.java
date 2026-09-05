package dev.railroadide.railroad.ide.ui.editor;

/**
 * Describes the current relationship between editor text and its backing file.
 */
public enum EditorSaveState {
    /**
     * The editor text matches the saved backing file.
     */
    CLEAN,
    /**
     * The editor contains changes that have not been saved.
     */
    DIRTY,
    /**
     * A save operation is in progress.
     */
    SAVING,
    /**
     * The latest save failed.
     */
    ERROR
}
