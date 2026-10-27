/*
AI Assistance Disclosure:
Tool: Claude Code (Sonnet 5.5), date: 2026-10-26
Scope: issue #225 (N8.5, D12) tests for SupplierBookmarkCleanupClient
       against a throwaway local HTTP server standing in for
       supplier-service: it POSTs /purge/bookmarks with a SERVICE-role
       bearer token that verifies with the shared secret, sends the ids in
       batches of 500, and reports a non-2xx answer or an unreachable
       server as BookmarkCleanupException.
Author review: Alastair Tan Choon Wei to review via the PR.
*/

package foc.user.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import foc.user.exception.BookmarkCleanupException;
import foc.user.security.JwtIssuer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class SupplierBookmarkCleanupClientTest {

    private static final String SECRET = "test-jwt-secret-that-is-at-least-32-bytes-long";

    private HttpServer server;
    private int status;
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private SupplierBookmarkCleanupClient client;

    @BeforeEach
    void startServer() throws IOException {
        status = 200;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"deleted\":0}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        client = clientFor(server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static SupplierBookmarkCleanupClient clientFor(int port) {
        return new SupplierBookmarkCleanupClient("http://127.0.0.1:" + port,
            new JwtIssuer(SECRET, Duration.ofHours(1)));
    }

    @Test
    @DisplayName("Should POST the ids to /purge/bookmarks with a SERVICE token that verifies")
    void sendsTheIdsWithAServiceToken() {
        client.purgeBookmarks(List.of(5L, 6L));

        assertThat(paths).containsExactly("POST /purge/bookmarks");
        assertThat(bodies).containsExactly("{\"userIds\":[5,6]}");
        String header = authorizations.get(0);
        assertThat(header).startsWith("Bearer ");
        Claims claims = Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .build().parseSignedClaims(header.substring("Bearer ".length())).getPayload();
        assertThat(claims.get("role", String.class)).isEqualTo("SERVICE");
    }

    @Test
    @DisplayName("Should send the ids in batches of 500")
    void batchesTheIds() {
        List<Long> ids = new ArrayList<>(LongStream.rangeClosed(1, 1200).boxed().toList());

        client.purgeBookmarks(ids);

        assertThat(bodies).hasSize(3);
        assertThat(bodies.get(0)).startsWith("{\"userIds\":[1,2,").contains(",500]");
        assertThat(bodies.get(2)).endsWith(",1200]}");
    }

    @Test
    @DisplayName("Should make no call for an empty list")
    void emptyListMakesNoCall() {
        client.purgeBookmarks(List.of());

        assertThat(paths).isEmpty();
    }

    @Test
    @DisplayName("Should report a non-2xx answer as a cleanup failure")
    void failsOnAnErrorStatus() {
        status = 500;

        assertThatThrownBy(() -> client.purgeBookmarks(List.of(5L)))
            .isInstanceOf(BookmarkCleanupException.class);
    }

    @Test
    @DisplayName("Should report a 403 (token refused) as a cleanup failure")
    void failsWhenTheTokenIsRefused() {
        status = 403;

        assertThatThrownBy(() -> client.purgeBookmarks(List.of(5L)))
            .isInstanceOf(BookmarkCleanupException.class);
    }

    @Test
    @DisplayName("Should report an unreachable supplier-service as a cleanup failure")
    void failsWhenUnreachable() {
        int port = server.getAddress().getPort();
        server.stop(0);

        assertThatThrownBy(() -> clientFor(port).purgeBookmarks(List.of(5L)))
            .isInstanceOf(BookmarkCleanupException.class);
    }
}
