package dev.railroadide.railroad.project.creation.service;

import dev.railroadide.core.project.creation.service.GitService;
import org.eclipse.jgit.api.Git;

import java.nio.file.Path;

public class JGitService implements GitService {
    @Override
    public void init(Path repoDir) throws Exception {
        Git.init().setDirectory(repoDir.toFile()).call().close();
    }

    @Override
    public void initialCommit(Path repoDir, String message) throws Exception {
        try (Git git = Git.open(repoDir.toFile())) {
            git.add().addFilepattern(".").call();
            git.commit().setMessage(message).call();
        }
    }
}
