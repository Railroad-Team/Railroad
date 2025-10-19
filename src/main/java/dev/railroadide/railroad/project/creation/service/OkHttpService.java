package dev.railroadide.railroad.project.creation.service;

import dev.railroadide.core.project.creation.service.HttpService;
import dev.railroadide.railroad.Railroad;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

public record OkHttpService(OkHttpClient client) implements HttpService {
    @Override
    public void download(URI uri, Path dest) throws IOException {
        int attempt = 0;
        while (true) {
            attempt++;
            var request = new Request.Builder().url(uri.toString()).get().build();
            try (var response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IOException("HTTP " + response.code() + " for " + uri);
                }

                var tmpFile = Files.createTempFile(dest.getParent(), ".dl", ".tmp");
                try (var in = response.body().byteStream(); var out = Files.newOutputStream(tmpFile)) {
                    in.transferTo(out);
                }

                Files.move(tmpFile, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (IOException exception) {
                if (shouldRetry(exception)) {
                    Railroad.LOGGER.warn("Failed to download {} (attempt {}). Retrying in 5 seconds...", uri, attempt);
                    sleep(Duration.ofSeconds(5));
                    continue;
                }

                throw exception;
            }
        }
    }

    @Override
    public boolean isNotFound(URI uri) throws IOException {
        var request = new Request.Builder().url(uri.toString()).head().build();
        try (var response = client.newCall(request).execute()) {
            return response.code() == 404;
        }
    }

    private boolean shouldRetry(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketException || current instanceof SocketTimeoutException) {
                return true;
            }
            if (current instanceof UncheckedIOException uio && uio.getCause() != null) {
                current = uio.getCause();
                continue;
            }
            String message = current.getMessage();
            if (message != null && message.contains("Software caused connection abort")) {
                return true;
            }

            current = current.getCause();
        }
        return false;
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
