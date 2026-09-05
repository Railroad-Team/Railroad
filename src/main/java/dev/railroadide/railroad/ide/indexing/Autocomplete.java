package dev.railroadide.railroad.ide.indexing;

import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import dev.railroadide.railroad.ide.classparser.stub.Stub;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Offers prefix completions for class names and members from class-file stubs.
 */
public class Autocomplete {
    private final Trie classTrie = new Trie();
    private final Map<String, ClassStub> classStubs = new HashMap<>();

    /**
     * Indexes simple class names and retains stubs by fully qualified name.
     *
     * @param stubs class metadata used to seed completions
     */
    public Autocomplete(List<ClassStub> stubs) {
        for (ClassStub stub : stubs) {
            String fullName = stub.getFullName();
            this.classStubs.put(fullName, stub);
            String simpleName = stub.name();
            this.classTrie.insert(simpleName);
        }
    }

    /**
     * Finds stored simple class names beginning with the supplied prefix.
     *
     * @param prefix case-sensitive name prefix to match
     * @return matching class names
     */
    public List<String> getCompletions(String prefix) {
        return this.classTrie.findCompletions(prefix);
    }

    /**
     * Finds member names in the specified class that begin with the prefix.
     *
     * @param className fully qualified class name
     * @param prefix case-sensitive name prefix to match
     * @return matching member names, or an empty list for an unknown class
     */
    public List<String> suggestMembers(String className, String prefix) {
        ClassStub stub = this.classStubs.get(className);
        if (stub == null)
            return List.of();

        return stub.getMembers().stream()
            .map(Stub::name)
            .filter(member -> member.startsWith(prefix))
            .toList();
    }
}
