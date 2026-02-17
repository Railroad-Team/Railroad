package dev.railroadide.railroad.vcs.git.remote;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses `git remote -v` output into structured {@link GitRemote} records.
 */
public class GitRemoteParser {
    /**
     * Parses `git remote -v` lines into remote records.
     *
     * @param lines command output lines
     * @return parsed remotes
     */
    public static List<GitRemote> parseRemoteUrls(List<String> lines) {
        List<String> mutableLines = (lines == null || lines.isEmpty()) ? Collections.emptyList() : new ArrayList<>(lines);
        return parseRemoteUrlsInternal(mutableLines);
    }

    private static List<GitRemote> parseRemoteUrlsInternal(List<String> lines) {
        List<GitRemote> remotes = new ArrayList<>(lines.isEmpty() ? 0 : lines.size() / 2);
        while (!lines.isEmpty()) {
            String line = lines.removeFirst();
            boolean isNextLinePush = !lines.isEmpty() && lines.getFirst().endsWith("(push)");
            String nextLine = isNextLinePush ? lines.removeFirst() : null;
            GitRemote remote = parseRemoteUrl(line, nextLine);
            if (remote != null) {
                remotes.add(remote);
            }
        }

        return remotes;
    }

    private static GitRemote parseRemoteUrl(@NotNull String line, @Nullable String nextLine) {
        String[] parts = line.split("\\s+");
        if (parts.length < 2)
            return null;

        String name = parts[0];
        String fetchUrl = parts[1];
        GitRemote.Protocol protocol = GitRemote.Protocol.fromUrl(fetchUrl);

        String pushUrl = fetchUrl;
        if (nextLine != null) {
            String[] nextParts = nextLine.split("\\s+");
            if (nextParts.length >= 2) {
                pushUrl = nextParts[1];
            }
        }

        return new GitRemote(name, fetchUrl, pushUrl, protocol);
    }
}
