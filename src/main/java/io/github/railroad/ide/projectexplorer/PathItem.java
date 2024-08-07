package io.github.railroad.ide.projectexplorer;

import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;

public class PathItem {
    @Setter
    @Getter
    private Path path;
    private int depthCount;

    public PathItem(Path path) {
        this.path = path;
        this.depthCount = 0;
    }

    @Override
    public String toString() {
        if(this.path.getFileName() == null) {
            return this.path.toString();
        } else {
            return this.path.getFileName().toString();
        }
    }

    public int getNewDepthCount() {
        return ++this.depthCount;
    }
}
