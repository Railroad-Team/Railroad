package dev.railroadide.railroad.ide.projectexplorer;

/**
 * Kinds of filesystem entries offered by the creation dialog.
 */
public enum FileCreateType {
    /**
     * Creates a generic file.
     */
    FILE,
    /**
     * Creates a directory.
     */
    FOLDER,
    /**
     * Creates a Java class source file.
     */
    JAVA_CLASS,
    /**
     * Creates a JSON file.
     */
    JSON,
    /**
     * Creates a plain text file.
     */
    TXT
}
