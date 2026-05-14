package dev.railroadide.railroad.project.creation;

public interface ProgressReporter {
    void progress(int stepIndex, int total);

    void info(String line);

    void setArg(Object... args);
}
