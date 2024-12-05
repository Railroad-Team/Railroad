package io.github.railroad.ide.indexing;

import java.util.ArrayList;
import java.util.List;

public class Trie {
    private final TrieNode root = new TrieNode();

    public void insert(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            node = node.children.computeIfAbsent(c, k -> new TrieNode());
        }

        node.isEndOfWord = true;
    }

    public List<String> searchPrefix(String prefix) {
        List<String> results = new ArrayList<>();
        TrieNode node = root;
        for (char c : prefix.toCharArray()) {
            node = node.children.get(c);
            if (node == null) {
                return results;
            }
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

    public void print() {
        List<String> results = new ArrayList<>();
        findAllWords(root, new StringBuilder(), results);
        results.forEach(System.out::println);
    }
}
