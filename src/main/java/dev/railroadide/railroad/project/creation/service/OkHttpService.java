package dev.railroadide.railroad.project.creation.service;

import dev.railroadide.core.project.creation.service.HttpService;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public record OkHttpService(OkHttpClient client) implements HttpService {
    @Override
    public void download(URI uri, Path dest) throws IOException {
        var request = new Request.Builder().url(uri.toString()).get().build();
        try (var response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null)
                throw new IOException("HTTP " + response.code() + " for " + uri);

            var tmpFile = Files.createTempFile(dest.getParent(), ".dl", ".tmp");
            try (var in = response.body().byteStream(); var out = Files.newOutputStream(tmpFile)) {
                in.transferTo(out);
            }

            Files.move(tmpFile, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
    }

    @Override
    public boolean isNotFound(URI uri) throws IOException {
        var request = new Request.Builder().url(uri.toString()).head().build();
        try (var response = client.newCall(request).execute()) {
            return response.code() == 404;
        }
    }
}
