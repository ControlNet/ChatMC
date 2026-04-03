package space.controlnet.mineagent.common.tools.http;

import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.common.testing.TimeoutUtility;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class HttpToolFixtureRegressionTest {
    @Test
    void taskHttp_fixture_duplicateHeadersAndBinaryResponses_areStable() throws Exception {
        byte[] expectedBody = new byte[] {0x00, 0x01, 0x02, (byte) 0x80, (byte) 0xff};

        try (HttpToolLoopbackFixture fixture = HttpToolLoopbackFixture.create("task3-binary")) {
            fixture.addFixedResponse("/binary", HttpToolLoopbackFixture.FixedResponse.binary(
                    206,
                    expectedBody,
                    List.of(
                            new HttpToolEntry("X-Duplicate", "first"),
                            new HttpToolEntry("X-Duplicate", "second")
                    )
            ));

            HttpResponse<byte[]> response = httpClient(HttpClient.Redirect.NEVER).send(
                    HttpRequest.newBuilder(fixture.uri("/binary"))
                            .timeout(Duration.ofSeconds(2))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );

            assertEquals(206, response.statusCode());
            assertEquals(List.of("first", "second"), response.headers().allValues("X-Duplicate"));
            assertEquals(Optional.of("application/octet-stream"), response.headers().firstValue("Content-Type"));
            assertArrayEquals(expectedBody, response.body());
            fixture.awaitRequestCount("/binary", 1, Duration.ofSeconds(2));
        }
    }

    @Test
    void taskHttp_fixture_delayedResponse_supportsTimeoutScenarios() throws Exception {
        try (HttpToolLoopbackFixture fixture = HttpToolLoopbackFixture.create("task3-timeout")) {
            fixture.addEchoResponse("/slow-echo", HttpToolLoopbackFixture.EchoResponse.text(
                    200,
                    StandardCharsets.UTF_8,
                    List.of(new HttpToolEntry("X-Delay", "enabled"))
            ).withDelay(Duration.ofMillis(400)));

            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            Thread worker = new Thread(() -> {
                try {
                    httpClient(HttpClient.Redirect.NEVER).send(
                            HttpRequest.newBuilder(fixture.uri("/slow-echo"))
                                    .timeout(Duration.ofMillis(100))
                                    .POST(HttpRequest.BodyPublishers.ofString("delayed-body", StandardCharsets.UTF_8))
                                    .build(),
                            HttpResponse.BodyHandlers.ofByteArray()
                    );
                } catch (Throwable throwable) {
                    errorRef.set(throwable);
                }
            }, "http-tool-fixture-timeout-worker");

            worker.start();
            fixture.awaitRequestCount("/slow-echo", 1, Duration.ofSeconds(2));
            TimeoutUtility.awaitThreadCompletion("task3/http-fixture-delayed-response", worker, Duration.ofSeconds(2));

            Throwable throwable = errorRef.get();
            assertInstanceOf(HttpTimeoutException.class, throwable);
            assertEquals("delayed-body", fixture.lastRecordedExchange("/slow-echo").bodyText(StandardCharsets.UTF_8));
        }
    }

    @Test
    void taskHttp_fixture_redirectTextAndEchoBehaviors_areDeterministic() throws Exception {
        try (HttpToolLoopbackFixture fixture = HttpToolLoopbackFixture.create("task3-redirects")) {
            fixture.addRedirectChain(List.of(
                            new HttpToolLoopbackFixture.RedirectStep("/redirect/start", 302, "/redirect/step-two"),
                            new HttpToolLoopbackFixture.RedirectStep("/redirect/step-two", 307, "/text/final")
                    ),
                    "/text/final",
                    HttpToolLoopbackFixture.FixedResponse.text(
                            200,
                            "café reçu",
                            StandardCharsets.ISO_8859_1,
                            List.of(new HttpToolEntry("X-Text", "terminal"))
                    )
            );
            fixture.addEchoResponse("/echo", HttpToolLoopbackFixture.EchoResponse.binary(
                    201,
                    List.of(new HttpToolEntry("X-Echo", "body"))
            ));

            HttpResponse<byte[]> redirected = httpClient(HttpClient.Redirect.ALWAYS).send(
                    HttpRequest.newBuilder(fixture.uri("/redirect/start"))
                            .timeout(Duration.ofSeconds(2))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );

            assertEquals(200, redirected.statusCode());
            assertEquals(fixture.uri("/text/final"), redirected.uri());
            assertEquals(Optional.of("terminal"), redirected.headers().firstValue("X-Text"));
            assertEquals("café reçu", new String(redirected.body(), StandardCharsets.ISO_8859_1));
            assertEquals(1, fixture.recordedExchanges("/redirect/start").size());
            assertEquals(1, fixture.recordedExchanges("/redirect/step-two").size());
            assertEquals(1, fixture.recordedExchanges("/text/final").size());

            byte[] echoedBody = "payload=body-check".getBytes(StandardCharsets.UTF_8);
            HttpResponse<byte[]> echoResponse = httpClient(HttpClient.Redirect.NEVER).send(
                    HttpRequest.newBuilder(fixture.uri("/echo?mode=body-check"))
                            .timeout(Duration.ofSeconds(2))
                            .POST(HttpRequest.BodyPublishers.ofByteArray(echoedBody))
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );

            assertEquals(201, echoResponse.statusCode());
            assertEquals(Optional.of("body"), echoResponse.headers().firstValue("X-Echo"));
            assertArrayEquals(echoedBody, echoResponse.body());
            assertEquals("mode=body-check", fixture.lastRecordedExchange("/echo").rawQuery());
            assertArrayEquals(echoedBody, fixture.lastRecordedExchange("/echo").bodyBytes());
            assertTrue(fixture.lastRecordedExchange("/echo").headers().stream()
                    .anyMatch(header -> header.name().equalsIgnoreCase("Content-Length")));
        }
    }

    private static HttpClient httpClient(HttpClient.Redirect redirect) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(redirect)
                .build();
    }
}
