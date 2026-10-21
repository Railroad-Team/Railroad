package dev.railroadide.railroad.ide.indexing;

import java.util.HashMap;
import java.util.Map;

/**
 * A character-trie node holding child edges and an end-of-word marker.
 */
public class TrieNode {
    protected final Map<Character, TrieNode> children = new HashMap<>();
    protected boolean isEndOfWord;
}
