package dev.railroadide.railroad.ide.sst.lexer;

public enum TokenChannel {
    DEFAULT, // the main channel for most tokens
    TRIVIA, // whitespace, comments, etc.
    PREPROCESSOR, // preprocessor directives
    OTHER // for any other specialized tokens
}
