package io.github.railroad.ide.indexing;

import java.util.HashMap;
import java.util.Map;

public class TrieNode {
    protected final Map<Character, TrieNode> children = new HashMap<>();
    protected boolean isEndOfWord;
}
