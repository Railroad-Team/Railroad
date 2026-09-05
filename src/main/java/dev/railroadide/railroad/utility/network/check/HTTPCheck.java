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

/**
 * Tests an HTTP endpoint with a synchronous HEAD request, accepting response codes from 200 through 399.
 * Redirect handling follows the selected client's defaults. Caught failures are logged when
 * {@link #shouldLogFailures()} is true.
 *
 * @param clientMode HTTP client implementation to use; must be non-null when running a check
 */
public record HTTPCheck(HttpClientMode clientMode) implements NetworkCheck {
    /** Creates an HTTP probe using {@link HttpClientMode#URL_CONNECTION}. */
    public HTTPCheck() {
        this(HttpClientMode.URL_CONNECTION);
    }

    /**
     * Sends a HEAD request using the configured client.
     * URL connections receive connect and read timeouts; the JDK HTTP client receives connect and request timeouts;
     * OkHttp receives call, connect, read, and write timeouts. An interrupted JDK HTTP request restores the thread's
     * interrupt flag and returns false. Invalid URL or timeout arguments are handled differently by each client:
     * OkHttp catches illegal arguments, while the other clients may propagate them.
     *
     * @param address absolute HTTP or HTTPS URL to probe
     * @param timeout timeout in milliseconds; must be positive for {@link HttpClientMode#JAVA_NET}, while zero
     *            disables the configured timeouts for the other clients
     * @return true for a response code from 200 through 399, or false for other responses or a handled failure
     * @throws NullPointerException if the client mode or address is null
     * @throws IllegalArgumentException if the selected client rejects an argument and does not handle the exception
     */
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
                    if (shouldLogFailures()) {
                        Railroad.LOGGER.error("Requested protocol is not supported.", exception);
                    }

                    yield false;
                } catch (MalformedURLException exception) {
                    if (shouldLogFailures()) {
                        Railroad.LOGGER.error("The provided URL is malformed.", exception);
                    }

                    yield false;
                } catch (URISyntaxException exception) {
                    if (shouldLogFailures()) {
                        Railroad.LOGGER.error("The provided URL has an invalid syntax.", exception);
                    }

                    yield false;
                } catch (IOException exception) {
                    if (shouldLogFailures()) {
                        Railroad.LOGGER.error("An I/O error occurred while trying to connect.", exception);
                    }

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
                    if (shouldLogFailures()) {
                        Railroad.LOGGER.error("An I/O error occurred while trying to connect.", exception);
                    }

                    yield false;
                } catch (InterruptedException exception) {
                    if (shouldLogFailures()) {
                        Railroad.LOGGER.error("The operation was interrupted.", exception);
                    }

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
                    if (shouldLogFailures()) {
                        Railroad.LOGGER.error("The provided URL is malformed.", exception);
                    }

                    yield false;
                } catch (IOException exception) {
                    if (shouldLogFailures()) {
                        Railroad.LOGGER.error("An I/O error occurred while trying to connect.", exception);
                    }

                    yield false;
                }
            }
        };
    }

    /** HTTP client implementations available to the probe. */
    public enum HttpClientMode {
        /** Uses {@link HttpURLConnection} with connect and read timeouts. */
        URL_CONNECTION,
        /** Uses the JDK {@link HttpClient} with connect and request timeouts. */
        JAVA_NET,
        /** Uses {@link OkHttpClient} with call, connect, read, and write timeouts. */
        OKHTTP
    }
}
