package dev.railroadide.railroad.utility.network.check;

import dev.railroadide.railroad.Railroad;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public record HTTPCheck(HttpClientMode clientMode) implements NetworkCheck {
    public HTTPCheck() {
        this(HttpClientMode.URL_CONNECTION);
    }

    @Override
    public boolean check(String address, int timeout) {
        return switch (this.clientMode) {
            case URL_CONNECTION -> {
                try {
                    URL url = new URI(address).toURL();
                    var connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(timeout);
                    connection.setReadTimeout(timeout);
                    connection.setRequestMethod("HEAD");

                    int responseCode = connection.getResponseCode();
                    yield (200 <= responseCode && responseCode < 400);
                } catch (ProtocolException exception) {
                    if (shouldLogFailures())
                        Railroad.LOGGER.error("Requested protocol is not supported.", exception);

                    yield false;
                } catch (MalformedURLException exception) {
                    if (shouldLogFailures())
                        Railroad.LOGGER.error("The provided URL is malformed.", exception);

                    yield false;
                } catch (URISyntaxException exception) {
                    if (shouldLogFailures())
                        Railroad.LOGGER.error("The provided URL has an invalid syntax.", exception);

                    yield false;
                } catch (IOException exception) {
                    if (shouldLogFailures())
                        Railroad.LOGGER.error("An I/O error occurred while trying to connect.", exception);

                    yield false;
                }
            }

            case JAVA_NET -> {
                try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeout))
                    .build()) {
                    var request = HttpRequest.newBuilder()
                        .uri(URI.create(address))
                        .timeout(Duration.ofMillis(timeout))
                        .HEAD()
                        .build();

                    var response = client.send(request, HttpResponse.BodyHandlers.discarding());
                    yield (200 <= response.statusCode() && response.statusCode() < 400);
                } catch (IOException exception) {
                    if (shouldLogFailures())
                        Railroad.LOGGER.error("An I/O error occurred while trying to connect.", exception);

                    yield false;
                } catch (InterruptedException exception) {
                    if (shouldLogFailures())
                        Railroad.LOGGER.error("The operation was interrupted.", exception);

                    Thread.currentThread().interrupt();
                    yield false;
                }
            }

            case OKHTTP -> {
                try {
                    var client = new OkHttpClient.Builder()
                        .callTimeout(Duration.ofMillis(timeout))
                        .connectTimeout(Duration.ofMillis(timeout))
                        .readTimeout(Duration.ofMillis(timeout))
                        .writeTimeout(Duration.ofMillis(timeout))
                        .build();

                    var request = new Request.Builder()
                        .url(address)
                        .head()
                        .build();

                    try (Response response = client.newCall(request).execute()) {
                        client.dispatcher().executorService().shutdown();
                        client.connectionPool().evictAll();
                        yield (200 <= response.code() && response.code() < 400);
                    }
                } catch (IllegalArgumentException exception) {
                    if (shouldLogFailures())
                        Railroad.LOGGER.error("The provided URL is malformed.", exception);

                    yield false;
                } catch (IOException exception) {
                    if (shouldLogFailures())
                        Railroad.LOGGER.error("An I/O error occurred while trying to connect.", exception);

                    yield false;
                }
            }
        };
    }

    public enum HttpClientMode {
        URL_CONNECTION,
        JAVA_NET,
        OKHTTP
    }
}
