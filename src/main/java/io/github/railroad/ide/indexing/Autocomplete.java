package io.github.railroad.ide.indexing;

import io.github.railroad.ide.classparser.stub.ClassStub;
import io.github.railroad.ide.classparser.stub.Stub;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Autocomplete {
    private final Trie classTrie = new Trie();
    private final Map<String, ClassStub> classStubs = new HashMap<>();

    public Autocomplete(List<ClassStub> stubs) {
        for (ClassStub stub : stubs) {
            String fullName = stub.getFullName();
            this.classStubs.put(fullName, stub);
            String simpleName = stub.name();
            this.classTrie.insert(simpleName);
        }
    }

    public List<String> getCompletions(String prefix) {
        return this.classTrie.findCompletions(prefix);
    }

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
