package dev.railroadide.railroad.vcs.git.remote;

/**
 * Remote configuration entry with fetch/push URLs and detected protocol.
 *
 * @param name remote name
 * @param fetchUrl fetch URL
 * @param pushUrl push URL
 * @param protocol detected URL protocol
 */
public record GitRemote(String name, String fetchUrl, String pushUrl, Protocol protocol) {
    /**
     * Supported remote URL protocol classifications.
     */
    public enum Protocol {
        HTTPS,
        SSH,
        GIT,
        FILE,
        UNKNOWN;

        /**
         * Detects protocol type from a remote URL.
         *
         * @param url remote URL text
         * @return detected protocol enum value
         */
        public static Protocol fromUrl(String url) {
            if (url.startsWith("https://") || url.startsWith("http://")) {
                return HTTPS;
            } else if (url.startsWith("ssh://") || url.contains("@")) {
                return SSH;
            } else if (url.startsWith("git://")) {
                return GIT;
            } else if (url.startsWith("file://") || url.startsWith("/")) {
                return FILE;
            } else {
                return UNKNOWN;
            }
        }
    }
}
