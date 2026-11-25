package dev.railroadide.railroad.ide.search;

import dev.railroadide.railroad.ide.activity.ActivityProgress;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.TextRange;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceSnapshot;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceChange;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEdit;
import dev.railroadide.railroad.ide.ui.codeeditor.find.FindQuery;
import dev.railroadide.railroad.plugin.spi.editor.TextReplacement;
import dev.railroadide.railroad.utility.ProjectPathIdentityUtils;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.PatternSyntaxException;

/**
 * Searches the text documents of one captured workspace and turns accepted matches into a {@link WorkspaceEdit}.
 * <p>
 * The workspace capture already reflects unsaved editor content and excludes directories Railroad does not manage,
 * so a search sees exactly what the editors and the workspace-edit service see. Results stay bound to the captured
 * snapshots, which makes applying a replacement fail as a whole instead of editing text that moved since.
 */
public final class ProjectTextSearch {
    /**
     * Documents larger than this many characters are skipped; they are generated or data files, not sources.
     */
    public static final int MAX_DOCUMENT_LENGTH = 5_000_000;

    private ProjectTextSearch() {
    }

    /**
     * Matches of the query in one document.
     *
     * @param document captured snapshot the ranges belong to
     * @param relativePath path relative to the project root, with {@code /} separators
     * @param matches non-overlapping match ranges in document order
     */
    public record FileMatches(TextDocumentSnapshot document, String relativePath, List<TextRange> matches) {
        /**
         * Creates a result with an immutable copy of its matches.
         *
         * @param document captured snapshot
         * @param relativePath project-relative path
         * @param matches match ranges
         */
        public FileMatches {
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(relativePath, "relativePath");
            matches = List.copyOf(matches);
        }
    }

    /**
     * Outcome of a completed search.
     *
     * @param files files with at least one match, ordered by relative path
     * @param searchedFiles number of documents whose text was searched
     * @param limitReached whether the search stopped at the match limit, so files may be missing
     */
    public record Result(List<FileMatches> files, int searchedFiles, boolean limitReached) {
        /**
         * Creates a result with an immutable copy of its files.
         *
         * @param files matching files
         * @param searchedFiles searched document count
         * @param limitReached whether the match limit stopped the search
         */
        public Result {
            files = List.copyOf(files);
        }

        /**
         * Counts the matches of every file.
         *
         * @return total match count
         */
        public int matchCount() {
            return files.stream().mapToInt(file -> file.matches().size()).sum();
        }
    }

    /**
     * Searches every text document of a workspace that lies inside the project and passes the file mask, in
     * relative-path order. Each file with matches is passed to {@code sink} as soon as it has been searched.
     *
     * @param workspace captured workspace
     * @param root project directory; documents outside it are skipped
     * @param query query to evaluate; must not be empty
     * @param mask file restriction
     * @param limit maximum total number of matches
     * @param cancelled checked between and during documents; the search ends with a {@link CancellationException}
     *            once it returns {@code true}
     * @param progress receives the number of searched files, for example to show the search in the status bar
     * @param sink receives each matching file, on the calling thread
     * @return all matching files
     * @throws PatternSyntaxException if the query is an invalid regular expression
     * @throws CancellationException if the search was cancelled
     */
    public static Result search(
        WorkspaceSnapshot workspace,
        Path root,
        FindQuery query,
        FileMask mask,
        int limit,
        BooleanSupplier cancelled,
        ActivityProgress progress,
        Consumer<FileMatches> sink
    ) {
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(sink, "sink");
        query.compile();
        FileMask.Matcher files = mask.matcher();
        Path projectRoot = root.toAbsolutePath().normalize();
        Path realRoot = ProjectPathIdentityUtils.normalize(projectRoot);
        record Candidate(TextDocumentSnapshot document, String relativePath) {
        }

        List<Candidate> candidates = new ArrayList<>();
        for (DocumentSnapshot snapshot : workspace.documents().values()) {
            if (!(snapshot instanceof TextDocumentSnapshot text) || text.text().length() > MAX_DOCUMENT_LENGTH)
                continue;

            String relative = text.uri().filePath()
                .map(file -> relativePath(file, projectRoot, realRoot))
                .orElse(null);
            if (relative != null && files.matches(relative)) {
                candidates.add(new Candidate(text, relative));
            }
        }

        candidates.sort(Comparator.comparing(Candidate::relativePath, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Candidate::relativePath));
        Runnable searchedFile = progress.count("railroad.activity.files", ActivityProgress.Unit.COUNT,
            candidates.size());
        List<FileMatches> found = new ArrayList<>();
        int remaining = limit;
        int searched = 0;
        boolean limitReached = false;
        for (Candidate candidate : candidates) {
            if (remaining <= 0) {
                limitReached = true;
                break;
            }

            checkCancelled(cancelled);
            List<TextRange> matches = query.findAll(new CancellableText(candidate.document().text(), cancelled),
                remaining + 1);
            searched++;
            searchedFile.run();
            if (matches.size() > remaining) {
                matches = matches.subList(0, remaining);
                limitReached = true;
            }

            if (matches.isEmpty())
                continue;

            remaining -= matches.size();
            var result = new FileMatches(candidate.document(), candidate.relativePath(), matches);
            found.add(result);
            sink.accept(result);
            if (limitReached)
                break;
        }

        return new Result(found, searched, limitReached);
    }

    /**
     * Returns a file's path relative to the project. A document may be recorded under another spelling of the project
     * directory, such as a Windows short name or a link, so real paths are compared when the spellings differ.
     */
    private static @Nullable String relativePath(Path file, Path root, Path realRoot) {
        Path path = file.toAbsolutePath().normalize();
        Path base = root;
        if (!path.startsWith(root)) {
            base = realRoot;
            if (!path.startsWith(realRoot)) {
                path = ProjectPathIdentityUtils.normalize(path);
            }
        }

        if (!path.startsWith(base) || path.equals(base))
            return null;

        return base.relativize(path).toString().replace('\\', '/');
    }

    /**
     * Computes the replacement text of each listed match of one file.
     *
     * @param file matches of one document
     * @param query query that produced the matches
     * @param template replacement template, interpreted as by {@link FindQuery#replaceAll(String, String)}
     * @return replacement text by match range, for every listed match the query still produces
     * @throws IllegalArgumentException if the template references a missing group or ends in an escape
     * @throws IndexOutOfBoundsException if the template references a group index the pattern lacks
     */
    public static Map<TextRange, String> replacements(FileMatches file, FindQuery query, String template) {
        Set<TextRange> listed = new HashSet<>(file.matches());
        Map<TextRange, String> replacements = new LinkedHashMap<>();
        for (TextReplacement replacement : query.replaceAll(file.document().text(), template)) {
            if (listed.contains(replacement.range())) {
                replacements.put(replacement.range(), replacement.text());
            }
        }

        return replacements;
    }

    /**
     * Builds one workspace edit that replaces the listed matches of every file. Each file's change is bound to the
     * snapshot it was searched in.
     *
     * @param label nonblank operation name
     * @param files matches to replace; files without matches are skipped
     * @param query query that produced the matches
     * @param template replacement template, interpreted as by {@link FindQuery#replaceAll(String, String)}
     * @return edit replacing every listed match
     * @throws IllegalArgumentException if the template references a missing group or ends in an escape
     * @throws IndexOutOfBoundsException if the template references a group index the pattern lacks
     */
    public static WorkspaceEdit replacementEdit(
        String label,
        List<FileMatches> files,
        FindQuery query,
        String template
    ) {
        WorkspaceEdit.Builder edit = WorkspaceEdit.builder(label);
        for (FileMatches file : files) {
            List<TextReplacement> changes = replacements(file, query, template).entrySet().stream()
                .map(entry -> new TextReplacement(entry.getKey(), entry.getValue()))
                .toList();
            if (!changes.isEmpty()) {
                edit.change(new WorkspaceChange.TextEdit(file.document(), changes));
            }
        }

        return edit.build();
    }

    /**
     * Moves matches onto the current revision of their document when its text is unchanged. Opening a file in an
     * editor publishes a new revision, and may convert CRLF line endings to LF, without changing what the matches
     * refer to; ranges are shifted accordingly. Applying an edit requires the exact current revision, so matches must
     * be rebased before replacing.
     *
     * @param file matches bound to the searched revision
     * @param workspace current workspace capture
     * @return matches bound to the current revision, or an empty optional when the document moved, disappeared or
     *         its text changed
     */
    public static Optional<FileMatches> rebase(FileMatches file, WorkspaceSnapshot workspace) {
        TextDocumentSnapshot searched = file.document();
        if (!(workspace.document(searched.id()).orElse(null) instanceof TextDocumentSnapshot current)
            || !current.uri().equals(searched.uri()))
            return Optional.empty();

        if (current.text().equals(searched.text()))
            return Optional.of(new FileMatches(current, file.relativePath(), file.matches()));

        String before = searched.text();
        if (before.indexOf('\r') < 0 || !normalizeLineEndings(before).equals(current.text()))
            return Optional.empty();

        List<TextRange> ranges = new ArrayList<>(file.matches().size());
        for (TextRange range : file.matches()) {
            int start = range.start - crlfBefore(before, range.start);
            int end = range.end - crlfBefore(before, range.end);
            if (start < end) {
                ranges.add(new TextRange(start, end));
            }
        }

        return Optional.of(new FileMatches(current, file.relativePath(), ranges));
    }

    /**
     * Reports whether two workspace captures hold the same text documents, at the same locations, with the same text
     * apart from line endings. Revisions are ignored, so opening or saving an unchanged file keeps results current.
     *
     * @param searched capture a search ran against
     * @param current later capture
     * @return whether a search of {@code current} would find the same matches
     */
    public static boolean sameText(WorkspaceSnapshot searched, WorkspaceSnapshot current) {
        Map<DocumentId, DocumentSnapshot> before = searched.documents();
        Map<DocumentId, DocumentSnapshot> after = current.documents();
        if (!before.keySet().equals(after.keySet()))
            return false;

        for (Map.Entry<DocumentId, DocumentSnapshot> entry : before.entrySet()) {
            DocumentSnapshot first = entry.getValue();
            DocumentSnapshot second = after.get(entry.getKey());
            if (first == second)
                continue;

            if (!first.uri().equals(second.uri()))
                return false;

            boolean firstText = first instanceof TextDocumentSnapshot;
            boolean secondText = second instanceof TextDocumentSnapshot;
            if (firstText != secondText)
                return false;

            if (firstText && !sameLines(((TextDocumentSnapshot) first).text(), ((TextDocumentSnapshot) second).text()))
                return false;
        }

        return true;
    }

    private static boolean sameLines(String first, String second) {
        if (first.equals(second))
            return true;

        return (first.indexOf('\r') >= 0 || second.indexOf('\r') >= 0)
            && normalizeLineEndings(first).equals(normalizeLineEndings(second));
    }

    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static int crlfBefore(String text, int offset) {
        int count = 0;
        for (int index = text.indexOf("\r\n"); index >= 0 && index < offset; index = text.indexOf("\r\n", index + 2)) {
            count++;
        }

        return count;
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean())
            throw new CancellationException("Project search cancelled");
    }

    /**
     * Lets a cancelled search escape a long-running regular expression, which reads its input through
     * {@link #charAt(int)}. Instances are confined to the searching thread.
     */
    private static final class CancellableText implements CharSequence {
        private static final int CHECK_INTERVAL = 1 << 16;

        private final String text;
        private final BooleanSupplier cancelled;
        private int reads;

        private CancellableText(String text, BooleanSupplier cancelled) {
            this.text = text;
            this.cancelled = cancelled;
        }

        @Override
        public int length() {
            return text.length();
        }

        @Override
        public char charAt(int index) {
            if (++reads == CHECK_INTERVAL) {
                reads = 0;
                checkCancelled(cancelled);
            }

            return text.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return text.subSequence(start, end);
        }

        @Override
        public String toString() {
            return text;
        }
    }
}
