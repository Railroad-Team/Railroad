package dev.railroadide.railroad.vcs.git.status;

import dev.railroadide.railroad.vcs.git.util.GitRepository;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for `git status --porcelain=v1 -b -z` output records.
 */
public final class GitStatusParser {
    private GitStatusParser() {
    }

    private static final Pattern AHEAD_BEHIND = Pattern.compile(
        "\\[.*?(?:ahead\\s+(\\d+))?.*?\\s*(?:behind\\s+(\\d+))?.*?\\]"
    );

    /**
     * Parses porcelain-v1-z status output into a repository status snapshot.
     *
     * @param repo repository owning the records
     * @param records status records from git
     * @return parsed repository status
     */
    public static GitRepoStatus parsePorcelainV1Z(GitRepository repo, List<String> records) {
        if (records == null || records.isEmpty())
            return new GitRepoStatus("(unknown)", 0, 0, Collections.emptyList());

        BranchInfo branchInfo = parseBranchHeader(records.getFirst());
        List<GitFileChange> changes = new ArrayList<>();

        int index = 1;
        while (index < records.size()) {
            String record = records.get(index);
            GitFileChange change = GitFileChangeParser.parsePorcelainV1ZRecord(repo, record,
                (index + 1) < records.size() ? records.get(index + 1) : null);
            if (change != null) {
                changes.add(change);
                if (change.oldPath() != null) {
                    index++; // Skip next record for rename/copy
                }
            }

            index++;
        }

        return new GitRepoStatus(
            branchInfo.branch,
            branchInfo.ahead,
            branchInfo.behind,
            List.copyOf(changes)
        );
    }

    private static BranchInfo parseBranchHeader(String header) {
        // Examples:
        // "## main...origin/main [ahead 1, behind 2]"
        // "## main"
        // "## HEAD (no branch)"
        // "## No commits yet on main"

        header = header == null ? "" : header.trim();
        if (header.startsWith("## ")) {
            header = header.substring(3).trim();
        }

        int[] aheadAndBehind = extractAheadAndBehind(header);
        String branch = extractBranch(header);
        return new BranchInfo(branch, aheadAndBehind[0], aheadAndBehind[1]);
    }

    private static int[] extractAheadAndBehind(String header) {
        int ahead = 0;
        int behind = 0;

        Matcher matcher = AHEAD_BEHIND.matcher(header);
        if (matcher.find()) {
            // group 1 is ahead count, group 2 is behind count
            String aheadStr = matcher.group(1);
            String behindStr = matcher.group(2);
            if (aheadStr != null) {
                ahead = Integer.parseInt(aheadStr);
            }

            if (behindStr != null) {
                behind = Integer.parseInt(behindStr);
            }
        }

        return new int[]{ahead, behind};
    }

    private static @NonNull String extractBranch(String header) {
        String branch;
        if (header.startsWith("No commits yet on ")) {
            branch = header.substring("No commits yet on ".length()).trim();
        } else if (header.startsWith("HEAD")) {
            branch = "(detached)";
        } else {
            int dots = header.indexOf("...");
            String head = dots >= 0 ? header.substring(0, dots).trim() : header;

            int cut = head.indexOf(' ');
            if (cut >= 0) {
                head = head.substring(0, cut).trim();
            }

            branch = head.isBlank() ? "(unknown)" : head;
        }
        return branch;
    }

    private record BranchInfo(String branch, int ahead, int behind) {
    }
}
