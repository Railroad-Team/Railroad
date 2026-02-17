package dev.railroadide.railroad.vcs.git.execution;

/**
 * Callback interface for streaming git process output.
 */
public interface GitOutputListener {
    GitOutputListener NO_OP = new GitOutputListener() {
        /**
         * Ignores stdout lines.
         *
         * @param line stdout line text
         */
        @Override
        public void onStdout(String line) {
            // No-op
        }

        /**
         * Ignores stdout records.
         *
         * @param record stdout record text
         */
        @Override
        public void onStdoutRecord(String record) {
            // No-op
        }

        /**
         * Ignores stderr lines.
         *
         * @param line stderr line text
         */
        @Override
        public void onStderr(String line) {
            // No-op
        }
    };

    /**
     * Receives a single stdout line.
     *
     * @param line stdout line text
     */
    void onStdout(String line);

    /**
     * Receives a null-delimited stdout record.
     *
     * @param record stdout record text
     */
    void onStdoutRecord(String record);

    /**
     * Receives a single stderr line.
     *
     * @param line stderr line text
     */
    void onStderr(String line);
}
