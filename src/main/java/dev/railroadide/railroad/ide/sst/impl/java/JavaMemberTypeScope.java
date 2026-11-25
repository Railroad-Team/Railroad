package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.semantic.api.Symbol;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.Deque;

/**
 * Resolves a simple type name against the member types in scope at a usage site, following Java's shadowing order:
 * the innermost enclosing type first, where its own name, its declared member types and then its inherited member
 * types are checked before moving outwards.
 */
public final class JavaMemberTypeScope {
    private JavaMemberTypeScope() {
    }

    /**
     * Finds the member type a simple name denotes from inside {@code innermostType}.
     *
     * @param simpleName the unqualified type name
     * @param innermostType the innermost type enclosing the usage site, or {@code null}
     * @param outerType returns the type lexically enclosing a type, or {@code null} at the top level
     * @param resolveType returns the normalized name of a known type for a qualified spelling, or {@code null}
     * @param directSuperTypes returns the qualified direct supertypes of a type, or {@code null} to skip inherited
     *            member types
     * @return the qualified name of the member type in scope, or {@code null} if none matches
     */
    public static @Nullable String resolve(
        String simpleName,
        @Nullable Symbol innermostType,
        Function<Symbol, @Nullable Symbol> outerType,
        Function<String, @Nullable String> resolveType,
        @Nullable Function<String, List<String>> directSuperTypes
    ) {
        for (Symbol type = innermostType; type != null; type = outerType.apply(type)) {
            String owner = type.qualifiedName().orElse(null);
            if (owner == null || owner.isBlank())
                continue;

            if (simpleName.equals(type.simpleName()))
                return owner;

            String declared = resolveType.apply(owner + "." + simpleName);
            if (declared != null)
                return declared;

            if (directSuperTypes == null)
                continue;

            Set<String> visited = new HashSet<>();
            visited.add(owner);
            for (String directSuper : directSuperTypes.apply(owner)) {
                String inherited = resolveInHierarchy(directSuper, simpleName, resolveType, directSuperTypes, visited);
                if (inherited != null)
                    return inherited;
            }
        }

        return null;
    }

    /**
     * Resolves a member type named through an explicit type qualifier. A declaration on one branch hides its
     * ancestors on that branch. Multiple paths to the same declaration still denote a single type.
     */
    public static @Nullable String resolveQualifiedMember(
        String owner,
        String simpleName,
        Function<String, @Nullable String> resolveType,
        @Nullable Function<String, List<String>> directSuperTypes
    ) {
        String declared = resolveType.apply(owner + "." + simpleName);
        if (declared != null || directSuperTypes == null)
            return declared;

        Set<String> visited = new HashSet<>();
        visited.add(owner);
        Deque<String> pending = new ArrayDeque<>(directSuperTypes.apply(owner));
        String match = null;
        while (!pending.isEmpty()) {
            String parent = pending.removeFirst();
            if (!visited.add(parent))
                continue;

            String member = resolveType.apply(parent + "." + simpleName);
            if (member == null) {
                pending.addAll(directSuperTypes.apply(parent));
            } else if (match == null || match.equals(member)) {
                match = member;
            } else
                return null;

        }

        return match;
    }

    private static @Nullable String resolveInHierarchy(
        String owner,
        String simpleName,
        Function<String, @Nullable String> resolveType,
        Function<String, List<String>> directSuperTypes,
        Set<String> visited
    ) {
        if (!visited.add(owner))
            return null;

        String member = resolveType.apply(owner + "." + simpleName);
        if (member != null)
            return member;

        for (String directSuper : directSuperTypes.apply(owner)) {
            String inherited = resolveInHierarchy(directSuper, simpleName, resolveType, directSuperTypes, visited);
            if (inherited != null)
                return inherited;
        }

        return null;
    }
}
