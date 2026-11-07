/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5.5), 2026-11-07, issue #300.
 * Scope: wrote these tests for the Supplier Service's NFR1.1 (listings
 * retrieved within 5 seconds) and NFR2.1 (store up to 1,000 suppliers),
 * run against a real PostgreSQL via Testcontainers like the other
 * services' tests. The 5 s bound and the 1,000 target come from
 * docs/supplier-service.md; the tool chose no requirement or design.
 * Author review: [pending]
 */
package foc.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import foc.supplier.model.SupplierCategories;
import foc.supplier.model.Suppliers;
import foc.supplier.repository.SupplierCategoriesRepository;
import foc.supplier.repository.SuppliersRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * NFR2.1 (store up to 1,000 suppliers) and NFR1.1 (supplier listings
 * retrieved within 5 seconds), on a real PostgreSQL holding exactly 1,000
 * suppliers.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SupplierNfrTest extends PostgresTestContainer {

    // NFR2.1 (docs/supplier-service.md)
    private static final int CAPACITY = 1000;
    // NFR1.1: listings retrieved within 5 seconds
    private static final long LISTING_LIMIT_MS = 5000;

    // matches src/test/resources/application.yaml's supplier.jwt.secret
    private static final String SECRET = "test-secret-0123456789abcdef0123456789abcdef";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SuppliersRepository suppliersRepository;

    @Autowired
    private SupplierCategoriesRepository supplierCategoriesRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String userToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder()
                .subject("1")
                .claim("role", "USER")
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(key)
                .compact();
    }

    // The app's startup seeder fills an empty database with the CSV, so
    // start every test from exactly CAPACITY generated suppliers instead.
    @BeforeEach
    void loadExactlyCapacitySuppliers() {
        Integer generated = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM suppliers WHERE name LIKE 'NFR Supplier %'", Integer.class);
        if (suppliersRepository.count() == CAPACITY && generated != null && generated == CAPACITY) {
            return;
        }
        // SQL rather than deleteAll(): Hibernate can't bulk-delete
        // SupplierCategories (composite @IdClass key with @MapsId)
        jdbcTemplate.execute("TRUNCATE TABLE supplier_categories, suppliers RESTART IDENTITY CASCADE");

        List<Suppliers> suppliers = new ArrayList<>();
        for (int i = 0; i < CAPACITY; i++) {
            Suppliers supplier = new Suppliers();
            supplier.setName(String.format("NFR Supplier %04d", i));
            supplier.setBuilding("Building " + (i % 40));
            supplier.setFloor("1");
            supplier.setLocationDescription("Generated supplier " + i);
            supplier.setLatitude(1.29 + (i % 100) / 10000.0);
            supplier.setLongitude(103.77 + (i % 100) / 10000.0);
            supplier.setStartingTime(LocalTime.of(8, 0));
            supplier.setClosingTime(LocalTime.of(20, 0));
            supplier.setCategory(flatCategory(i));
            suppliers.add(supplier);
        }
        List<Suppliers> saved = suppliersRepository.saveAll(suppliers);

        List<SupplierCategories> categories = new ArrayList<>();
        for (int i = 0; i < CAPACITY; i++) {
            for (String category : flatCategory(i).split("/")) {
                SupplierCategories row = new SupplierCategories();
                row.setSupplier(saved.get(i));
                row.setCategory(category);
                categories.add(row);
            }
        }
        supplierCategoriesRepository.saveAll(categories);
    }

    // Food (i%4 = 0), Shopping (1), Printing (2), Food/Coffee (3), so
    // exactly CAPACITY / 2 suppliers carry the "Food" category.
    private static String flatCategory(int i) {
        return switch (i % 4) {
            case 0 -> "Food";
            case 1 -> "Shopping";
            case 2 -> "Printing";
            default -> "Food/Coffee";
        };
    }

    @Test
    void storesExactlyTheCapacityTargetOfSuppliers() {
        assertThat(suppliersRepository.count()).isEqualTo(CAPACITY);
    }

    @Test
    void everyOneOfTheCapacitySuppliersCanBeListedExactlyOnce() throws Exception {
        Set<String> seen = new HashSet<>();
        int pageSize = 100;

        for (int page = 0; page < CAPACITY / pageSize; page++) {
            String body = mockMvc.perform(get("/suppliers")
                            .param("page", String.valueOf(page))
                            .param("size", String.valueOf(pageSize))
                            .header("Authorization", userToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements", equalTo(CAPACITY)))
                    .andExpect(jsonPath("$.totalPages", equalTo(CAPACITY / pageSize)))
                    .andExpect(jsonPath("$.content.length()", equalTo(pageSize)))
                    .andReturn().getResponse().getContentAsString();
            List<String> ids = JsonPath.read(body, "$.content[*].id");
            seen.addAll(ids);
        }

        assertThat(seen).hasSize(CAPACITY);
    }

    @Test
    void allSuppliersCanBeRetrievedInOneRequest() throws Exception {
        mockMvc.perform(get("/suppliers").param("size", String.valueOf(CAPACITY))
                        .header("Authorization", userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", equalTo(CAPACITY)));
    }

    @Test
    void searchAndCategoryFilterAreCorrectAtCapacity() throws Exception {
        mockMvc.perform(get("/suppliers").param("search", "NFR Supplier 0007")
                        .header("Authorization", userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", equalTo(1)))
                .andExpect(jsonPath("$.content[0].name", equalTo("NFR Supplier 0007")));

        mockMvc.perform(get("/suppliers").param("category", "Food")
                        .header("Authorization", userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", equalTo(CAPACITY / 2)));
    }

    // Each request is sent once to warm up the JVM, Hibernate and the
    // connection pool (not part of the NFR), then timed.
    @ParameterizedTest(name = "GET {0} completes within 5 s at 1,000 suppliers")
    @ValueSource(strings = {
            "/suppliers",
            "/suppliers?size=1000",
            "/suppliers?search=supplier",
            "/suppliers?search=NFR%20Supplier%200007",
            "/suppliers?category=Food",
            "/suppliers?category=Food&search=building",
            "/suppliers?lat=1.2966&lng=103.7764",
            "/suppliers?lat=1.2966&lng=103.7764&category=Food",
            "/suppliers?sort=name,desc",
            "/suppliers/categories"
    })
    void listingIsRetrievedWithinFiveSeconds(String uri) throws Exception {
        String token = userToken();
        mockMvc.perform(get(uri).header("Authorization", token)).andExpect(status().isOk());

        long start = System.nanoTime();
        mockMvc.perform(get(uri).header("Authorization", token)).andExpect(status().isOk());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs)
                .as("GET %s took %d ms, limit %d ms", uri, elapsedMs, LISTING_LIMIT_MS)
                .isLessThan(LISTING_LIMIT_MS);
    }
}
