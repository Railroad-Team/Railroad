package dev.railroadide.railroad.ide.sst.lexer;

/** Groups tokens by their role in parsing and source processing. */
public enum TokenChannel {
    /** Main source tokens consumed by the parser. */
    DEFAULT, // the main channel for most tokens
    /** Whitespace, comments, and other source trivia. */
    TRIVIA, // whitespace, comments, etc.
    /** Preprocessor directives. */
    PREPROCESSOR, // preprocessor directives
    /** Specialized tokens outside the standard channels. */
    OTHER // for any other specialized tokens
}
