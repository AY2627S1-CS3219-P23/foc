/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5.5), 2026-10-26.
 * Scope: issue #225 (N8) integration tests for the bookmark endpoints,
 * end-to-end against the H2-backed service: any signed-in user (not just
 * admins) can add and remove their own bookmarks, the cap answers 409 with
 * the limit message, an unknown supplier is 404, users never see each
 * other's bookmarks, a simultaneous request is refused with nothing saved,
 * and GET /suppliers carries the flag and lists bookmarks first (most
 * recent first) only in the default view. The PostgreSQL advisory lock is
 * replaced by a mock, since H2 has no such function; the lock itself was
 * checked against the compose database.
 * Reviewed by: [pending]
 */
package foc.supplier.controller;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import foc.supplier.model.Suppliers;
import foc.supplier.repository.SuppliersRepository;
import foc.supplier.service.BookmarkLock;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookmarkControllerTest {

    // matches src/test/resources/application.yaml's supplier.jwt.secret
    private static final String SECRET = "test-secret-0123456789abcdef0123456789abcdef";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SuppliersRepository suppliersRepository;

    @MockitoBean
    private BookmarkLock bookmarkLock;

    private final List<Long> ids = new ArrayList<>();

    private static String token(String role, long userId) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }

    private static String user(long id) {
        return token("USER", id);
    }

    @BeforeEach
    void setUp() {
        when(bookmarkLock.tryLock(any())).thenReturn(true);
        ids.clear();
        // the names sort after any seeded supplier, in this order
        for (String name : List.of("~Bm Alpha", "~Bm Bravo", "~Bm Charlie", "~Bm Delta", "~Bm Echo", "~Bm Foxtrot")) {
            Suppliers supplier = new Suppliers();
            supplier.setName(name);
            ids.add(suppliersRepository.save(supplier).getId());
        }
    }

    private void bookmark(long userId, int index) throws Exception {
        mockMvc.perform(put("/suppliers/{id}/bookmark", ids.get(index)).header("Authorization", user(userId)))
                .andExpect(status().isOk());
        Thread.sleep(5); // distinct bookmarked-at times, for the newest-first order
    }

    @Test
    void anyUserCanBookmarkAndListAndRemove() throws Exception {
        mockMvc.perform(put("/suppliers/{id}/bookmark", ids.get(0)).header("Authorization", user(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookmarked", equalTo(true)))
                .andExpect(jsonPath("$.count", equalTo(1)));

        mockMvc.perform(get("/bookmarks").header("Authorization", user(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", equalTo(1)))
                .andExpect(jsonPath("$.limit", equalTo(5)))
                .andExpect(jsonPath("$.bookmarks[0].name", equalTo("~Bm Alpha")));

        mockMvc.perform(delete("/suppliers/{id}/bookmark", ids.get(0)).header("Authorization", user(1)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/bookmarks").header("Authorization", user(1)))
                .andExpect(jsonPath("$.count", equalTo(0)));
    }

    @Test
    void bookmarkingTwiceIsANoOp() throws Exception {
        bookmark(1, 0);
        mockMvc.perform(put("/suppliers/{id}/bookmark", ids.get(0)).header("Authorization", user(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", equalTo(1)));
    }

    @Test
    void removingAnAbsentBookmarkSucceeds() throws Exception {
        mockMvc.perform(delete("/suppliers/{id}/bookmark", ids.get(0)).header("Authorization", user(1)))
                .andExpect(status().isNoContent());
    }

    @Test
    void aSixthBookmarkIsRejectedWith409AndTheLimitMessage() throws Exception {
        for (int i = 0; i < 5; i++) {
            bookmark(1, i);
        }

        mockMvc.perform(put("/suppliers/{id}/bookmark", ids.get(5)).header("Authorization", user(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", equalTo(409)))
                .andExpect(jsonPath("$.detail", equalTo("You have reached the limit of 5 bookmarks.")));

        mockMvc.perform(get("/bookmarks").header("Authorization", user(1)))
                .andExpect(jsonPath("$.count", equalTo(5)));
    }

    @Test
    void theCapIsPerUser() throws Exception {
        for (int i = 0; i < 5; i++) {
            bookmark(1, i);
        }

        mockMvc.perform(put("/suppliers/{id}/bookmark", ids.get(5)).header("Authorization", user(2)))
                .andExpect(status().isOk());
    }

    @Test
    void aUnknownSupplierIs404() throws Exception {
        mockMvc.perform(put("/suppliers/{id}/bookmark", 999_999_999L).header("Authorization", user(1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void usersNeverSeeEachOthersBookmarks() throws Exception {
        bookmark(1, 0);

        mockMvc.perform(get("/bookmarks").header("Authorization", user(2)))
                .andExpect(jsonPath("$.count", equalTo(0)))
                .andExpect(jsonPath("$.bookmarks", hasSize(0)));
    }

    @Test
    void aSimultaneousRequestIsRefusedAndNothingIsSaved() throws Exception {
        when(bookmarkLock.tryLock(any())).thenReturn(false);

        mockMvc.perform(put("/suppliers/{id}/bookmark", ids.get(0)).header("Authorization", user(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", equalTo("You are bookmarking too fast, please try again.")));

        when(bookmarkLock.tryLock(any())).thenReturn(true);
        mockMvc.perform(get("/bookmarks").header("Authorization", user(1)))
                .andExpect(jsonPath("$.count", equalTo(0)));
    }

    @Test
    void bookmarkEndpointsNeedASignedInUser() throws Exception {
        mockMvc.perform(put("/suppliers/{id}/bookmark", ids.get(0))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/bookmarks")).andExpect(status().isUnauthorized());
    }

    @Test
    void theListFlagsBookmarksAndPutsTheNewestFirstInTheDefaultView() throws Exception {
        bookmark(1, 4); // Echo, older
        bookmark(1, 2); // Charlie, newer

        mockMvc.perform(get("/suppliers").param("size", "1000").param("sort", "name,asc")
                        .header("Authorization", user(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name", equalTo("~Bm Charlie")))
                .andExpect(jsonPath("$.content[0].bookmarked", equalTo(true)))
                .andExpect(jsonPath("$.content[1].name", equalTo("~Bm Echo")))
                .andExpect(jsonPath("$.content[1].bookmarked", equalTo(true)))
                .andExpect(jsonPath("$.content[2].bookmarked", equalTo(false)));

        // another user's list is untouched by these bookmarks
        mockMvc.perform(get("/suppliers").param("size", "1000").header("Authorization", user(2)))
                .andExpect(jsonPath("$.content[0].bookmarked", equalTo(false)));
    }

    @Test
    void aSearchKeepsItsOwnOrderButStillFlags() throws Exception {
        bookmark(1, 4); // Echo
        bookmark(1, 2); // Charlie

        mockMvc.perform(get("/suppliers").param("search", "~Bm").param("size", "1000")
                        .header("Authorization", user(1)))
                .andExpect(jsonPath("$.content[0].name", equalTo("~Bm Alpha")))
                .andExpect(jsonPath("$.content[0].bookmarked", equalTo(false)))
                .andExpect(jsonPath("$.content[2].name", equalTo("~Bm Charlie")))
                .andExpect(jsonPath("$.content[2].bookmarked", equalTo(true)));
    }
}
