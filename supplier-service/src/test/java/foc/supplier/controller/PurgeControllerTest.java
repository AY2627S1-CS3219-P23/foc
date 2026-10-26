/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5.5), 2026-10-26.
 * Scope: issue #225 (N8.5, D12) integration tests for POST
 * /purge/bookmarks against the H2-backed service: only a SERVICE token may
 * call it (a USER, ADMIN or OWNER login token is 403, no token is 401),
 * it removes every bookmark of the given users and nobody else's, it is
 * idempotent, and a malformed or oversized body is a 400.
 * Reviewed by: [pending]
 */
package foc.supplier.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import foc.supplier.model.SupplierBookmarks;
import foc.supplier.model.Suppliers;
import foc.supplier.repository.SupplierBookmarksRepository;
import foc.supplier.repository.SuppliersRepository;
import foc.supplier.service.BookmarkLock;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PurgeControllerTest {

    // matches src/test/resources/application.yaml's supplier.jwt.secret
    private static final String SECRET = "test-secret-0123456789abcdef0123456789abcdef";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SuppliersRepository suppliersRepository;

    @Autowired
    private SupplierBookmarksRepository bookmarksRepository;

    @MockitoBean
    private BookmarkLock bookmarkLock;

    private Suppliers supplier;

    private static String token(String role) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder()
                .subject("user-service")
                .claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }

    @BeforeEach
    void setUp() {
        when(bookmarkLock.tryLock(any())).thenReturn(true);
        Suppliers s = new Suppliers();
        s.setName("~Purge Cafe");
        supplier = suppliersRepository.save(s);
        for (long userId : new long[] {101, 102, 103}) {
            bookmarksRepository.save(new SupplierBookmarks(userId, supplier));
        }
        bookmarksRepository.flush();
    }

    private long remaining() {
        return bookmarksRepository.findAll().stream()
                .filter(b -> b.getSupplierId().equals(supplier.getId()))
                .count();
    }

    @Test
    void aServiceTokenRemovesTheGivenUsersBookmarksAndNobodyElses() throws Exception {
        mockMvc.perform(post("/purge/bookmarks")
                        .header("Authorization", token("SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[101,102]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted", equalTo(2)));

        assertThat(remaining()).isEqualTo(1);
    }

    @Test
    void repeatingTheCallDeletesNothingMore() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/purge/bookmarks")
                            .header("Authorization", token("SERVICE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userIds\":[101]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deleted", equalTo(i == 0 ? 1 : 0)));
        }
    }

    @Test
    void anEmptyListIsAccepted() throws Exception {
        mockMvc.perform(post("/purge/bookmarks")
                        .header("Authorization", token("SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted", equalTo(0)));
    }

    @Test
    void noLoginTokenCanCallIt() throws Exception {
        for (String role : new String[] {"USER", "ADMIN", "OWNER"}) {
            mockMvc.perform(post("/purge/bookmarks")
                            .header("Authorization", token(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userIds\":[101]}"))
                    .andExpect(status().isForbidden());
        }
        assertThat(remaining()).isEqualTo(3);
    }

    @Test
    void aMissingTokenIs401() throws Exception {
        mockMvc.perform(post("/purge/bookmarks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[101]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aMissingOrMalformedBodyIs400() throws Exception {
        for (String body : new String[] {"{}", "{\"userIds\":null}", "{\"userIds\":[null]}", "not json"}) {
            mockMvc.perform(post("/purge/bookmarks")
                            .header("Authorization", token("SERVICE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void moreThanTheBatchLimitIs400() throws Exception {
        String ids = LongStream.rangeClosed(1, 1001).mapToObj(String::valueOf).collect(Collectors.joining(","));

        mockMvc.perform(post("/purge/bookmarks")
                        .header("Authorization", token("SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[" + ids + "]}"))
                .andExpect(status().isBadRequest());
    }
}
