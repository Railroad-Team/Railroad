package dev.railroadide.railroad.ide.indexing;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores words by character prefix for case-sensitive completion searches.
 */
public class Trie {
    private final TrieNode root = new TrieNode();

    /**
     * Adds a word to the prefix tree.
     *
     * @param word word to store in the trie
     */
    public void insert(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            node = node.children.computeIfAbsent(c, k -> new TrieNode());
        }

        node.isEndOfWord = true;
    }

    /**
     * Collects stored words beginning with the supplied prefix.
     *
     * @param prefix case-sensitive name prefix to match
     * @return matching words, with no guaranteed ordering
     */
    public List<String> findCompletions(String prefix) {
        List<String> results = new ArrayList<>();
        TrieNode node = root;
        for (char c : prefix.toCharArray()) {
            node = node.children.get(c);
            if (node == null)
                return results;
        }

        findAllWords(node, new StringBuilder(prefix), results);
        return results;
    }

    private void findAllWords(TrieNode node, StringBuilder prefix, List<String> results) {
        if (node.isEndOfWord) {
            results.add(prefix.toString());
        }

        for (char c : node.children.keySet()) {
            prefix.append(c);
            findAllWords(node.children.get(c), prefix, results);
            prefix.deleteCharAt(prefix.length() - 1);
        }
    }

    /**
     * Prints each stored word to standard output.
     */
    public void print() {
        List<String> results = new ArrayList<>();
        findAllWords(root, new StringBuilder(), results);
        results.forEach(System.out::println);
    }
}
