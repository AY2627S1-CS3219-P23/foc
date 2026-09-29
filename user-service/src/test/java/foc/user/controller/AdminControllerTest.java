/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-26
Scope: Generated controller tests for the admin endpoints (issue #96)
       against a Testcontainers Postgres, following ProfileControllerTest.
       The caller is mocked with the user id as the principal name and
       ROLE_ADMIN / ROLE_OWNER authorities until JWT auth (#91) lands.
       2026-09-26 (Claude Code, Opus 5.5): review follow-up cases added
       (owner removal, non-numeric principal and path id, non-admins
       getting 403 before input is checked, page sizes, combined search
       and role filter, more sort fields, literal _, lower-case and
       missing body roles, unauthenticated PATCH/DELETE, removed users
       across endpoints).
       2026-09-27 (Claude Code, Opus 5.5), PR #135 review: HEAD /users
       case added (USER gets 403, admin gets 200); the literal _ search
       switched to "o_n", which a wildcard _ would match.
Author review: Ryan reviewed and ensured tests run successfully.
       2026-09-29 (Claude Code, Opus 5.5), PR #140 review: GET /users now
       hides soft-deleted accounts (team decision); list expectations
       updated and a case added that a deleted account is left out.
       2026-09-29 (Claude Code, Opus 5.5), issue #91: requests without
       credentials now get 401 from the JWT entry point (was a provisional
       403).
       2026-09-29 (Claude Code, Opus 5.5), issue #147: includeDeleted cases
       (team decision), and the invalid-body reasons now that
       ProblemDetailAdvice reports them.
*/

package foc.user.controller;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import foc.user.PostgresTestContainer;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerTest extends PostgresTestContainer {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private User owner;
    private User admin;
    private User otherAdmin;
    private User alex;
    private User deleted;

    @BeforeEach
    void seedUsers() {
        userRepository.deleteAll();
        owner = userRepository.save(new User("owner@u.nus.edu", "the_owner", "hashed_password", Role.OWNER));
        admin = userRepository.save(new User("admin@u.nus.edu", "an_admin", "hashed_password", Role.ADMIN));
        otherAdmin = userRepository.save(new User("zed@u.nus.edu", "zed_admin", "hashed_password", Role.ADMIN));
        alex = userRepository.save(new User("e1234567@u.nus.edu", "student_alex", "hashed_password", Role.USER));

        User gone = new User("e7654321@u.nus.edu", "gone_user", "hashed_password", Role.USER);
        gone.setDeletedAt(Instant.now());
        deleted = userRepository.save(gone);
    }

    private static RequestPostProcessor as(User caller) {
        return user(caller.getId().toString()).roles(caller.getRole().name());
    }

    private static String roleBody(String role) {
        return "{\"role\":\"" + role + "\"}";
    }

    // access

    @Test
    @DisplayName("Admin endpoints should return 403 for a USER caller")
    void userCallerForbidden() throws Exception {
        mockMvc.perform(get("/users").with(as(alex)))
            .andExpect(status().isForbidden());
        mockMvc.perform(patch("/users/{id}", admin.getId()).with(as(alex))
                .contentType(MediaType.APPLICATION_JSON).content(roleBody("USER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/users/{id}", admin.getId()).with(as(alex)))
            .andExpect(status().isForbidden());

        assertThat(userRepository.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("Admin endpoints should return 403 to a USER caller before checking the input")
    void userCallerForbiddenBeforeInputChecks() throws Exception {
        mockMvc.perform(get("/users").param("size", "30").with(as(alex)))
            .andExpect(status().isForbidden());
        mockMvc.perform(patch("/users/{id}", "abc").with(as(alex))
                .contentType(MediaType.APPLICATION_JSON).content(roleBody("SUPERUSER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/users/{id}", "abc").with(as(alex)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HEAD /users should follow the same ADMIN/OWNER rule as GET")
    void headListRestricted() throws Exception {
        // Spring MVC serves HEAD through the @GetMapping list handler
        mockMvc.perform(head("/users").with(as(alex)))
            .andExpect(status().isForbidden());
        mockMvc.perform(head("/users").param("size", "30").with(as(alex)))
            .andExpect(status().isForbidden());
        mockMvc.perform(head("/users").with(as(admin)))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Methods the user routes don't open to everyone should return 403 to a USER caller")
    void unlistedMethodsRestricted() throws Exception {
        // no profile PATCH yet (#92), so this would reach the admin PATCH handler
        mockMvc.perform(patch("/users/me").with(as(alex))
                .contentType(MediaType.APPLICATION_JSON).content(roleBody("ADMIN")))
            .andExpect(status().isForbidden());
        // would otherwise answer 200 / 405 and reveal the admin endpoints
        mockMvc.perform(options("/users").with(as(alex)))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/users").with(as(alex)))
            .andExpect(status().isForbidden());
        mockMvc.perform(options("/users/{id}", admin.getId()).with(as(alex)))
            .andExpect(status().isForbidden());

        assertThat(userRepository.findById(alex.getId()).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("Admin endpoints should be rejected without credentials")
    void unauthenticated() throws Exception {
        mockMvc.perform(get("/users"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/users/{id}", alex.getId())
                .contentType(MediaType.APPLICATION_JSON).content(roleBody("ADMIN")))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/users/{id}", alex.getId()))
            .andExpect(status().isUnauthorized());

        User unchanged = userRepository.findById(alex.getId()).orElseThrow();
        assertThat(unchanged.getRole()).isEqualTo(Role.USER);
        assertThat(unchanged.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("Admin endpoints should return 400 for a non-numeric path id")
    void nonNumericPathId() throws Exception {
        mockMvc.perform(patch("/users/{id}", "abc").with(as(admin))
                .contentType(MediaType.APPLICATION_JSON).content(roleBody("ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(delete("/users/{id}", "abc").with(as(admin)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /users/me and GET /users/{id} should stay open to USER callers")
    void profileRoutesUnaffected() throws Exception {
        mockMvc.perform(get("/users/me").with(as(alex)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/users/{id}", admin.getId()).with(as(alex)))
            .andExpect(status().isOk());
    }

    // list

    @Test
    @DisplayName("GET /users should list every active account, sorted by id by default")
    void list_all() throws Exception {
        mockMvc.perform(get("/users").with(as(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(4))
            .andExpect(jsonPath("$.content[0].id").value(owner.getId()))
            .andExpect(jsonPath("$.content[3].id").value(alex.getId()))
            .andExpect(jsonPath("$.content[0].username").value("the_owner"))
            .andExpect(jsonPath("$.content[0].email").value("owner@u.nus.edu"))
            .andExpect(jsonPath("$.content[0].role").value("OWNER"))
            .andExpect(jsonPath("$.content[0].createdAt").exists())
            .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
            .andExpect(jsonPath("$.page.size").value(100))
            .andExpect(jsonPath("$.page.number").value(0))
            .andExpect(jsonPath("$.page.totalElements").value(4))
            .andExpect(jsonPath("$.page.totalPages").value(1));
    }

    @Test
    @DisplayName("GET /users should leave out soft-deleted accounts, even when searched for")
    void list_hidesSoftDeleted() throws Exception {
        mockMvc.perform(get("/users").param("search", "gone_user").with(as(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(0))
            .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /users?includeDeleted=true should list soft-deleted accounts with their deletion time")
    void list_includeDeleted() throws Exception {
        mockMvc.perform(get("/users").param("includeDeleted", "true").with(as(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(5))
            .andExpect(jsonPath("$.content[4].username").value("gone_user"))
            .andExpect(jsonPath("$.content[4].deletedAt").exists())
            // an active account's response has no deletedAt at all
            .andExpect(jsonPath("$.content[0].deletedAt").doesNotExist());
        mockMvc.perform(get("/users").param("includeDeleted", "true").param("search", "gone_user").with(as(admin)))
            .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /users should return 400 for a non-boolean includeDeleted")
    void list_includeDeletedInvalid() throws Exception {
        mockMvc.perform(get("/users").param("includeDeleted", "maybe").with(as(admin)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Invalid request parameter"));
    }

    @Test
    @DisplayName("GET /users should be available to OWNER callers")
    void list_owner() throws Exception {
        mockMvc.perform(get("/users").with(as(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(4));
    }

    @Test
    @DisplayName("GET /users should search username and email partially and case-insensitively")
    void list_searchText() throws Exception {
        mockMvc.perform(get("/users").param("search", "STUDENT_A").with(as(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(alex.getId()));

        mockMvc.perform(get("/users").param("search", "ZED@U.NUS").with(as(admin)))
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(otherAdmin.getId()));
    }

    @Test
    @DisplayName("GET /users should match the id partially as text")
    void list_searchId() throws Exception {
        String id = alex.getId().toString();

        mockMvc.perform(get("/users").param("search", id).with(as(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[?(@.id == " + id + ")]").exists());
    }

    @Test
    @DisplayName("GET /users should treat LIKE wildcards in the search literally")
    void list_searchWildcard() throws Exception {
        mockMvc.perform(get("/users").param("search", "%").with(as(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("GET /users should treat _ in the search literally")
    void list_searchUnderscore() throws Exception {
        userRepository.save(new User("e1111111@u.nus.edu", "nounderscore", "hashed_password", Role.USER));

        // "o_n" as a LIKE pattern would match "nounderscore" ("oun") and
        // "owner@u.nus.edu" ("own"); taken literally it matches nothing
        mockMvc.perform(get("/users").param("search", "o_n").with(as(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get("/users").param("search", "n_admin").with(as(admin)))
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(admin.getId()));
    }

    @Test
    @DisplayName("GET /users should combine search with the role filter")
    void list_searchAndRole() throws Exception {
        // "admin" matches both admins (username and email) and no one else
        mockMvc.perform(get("/users").param("search", "admin").param("role", "ADMIN").with(as(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2));
        mockMvc.perform(get("/users").param("search", "admin").param("role", "USER").with(as(admin)))
            .andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get("/users").param("search", "zed").param("role", "ADMIN").with(as(admin)))
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(otherAdmin.getId()));
    }

    @Test
    @DisplayName("GET /users should filter by role")
    void list_filterRole() throws Exception {
        mockMvc.perform(get("/users").param("role", "ADMIN").with(as(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].id").value(admin.getId()))
            .andExpect(jsonPath("$.content[1].id").value(otherAdmin.getId()));
    }

    @Test
    @DisplayName("GET /users should sort by role rank, ties broken by id")
    void list_sortRole() throws Exception {
        mockMvc.perform(get("/users").param("sort", "role,desc").with(as(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].role").value("OWNER"))
            .andExpect(jsonPath("$.content[1].id").value(admin.getId()))
            .andExpect(jsonPath("$.content[2].id").value(otherAdmin.getId()))
            .andExpect(jsonPath("$.content[3].id").value(alex.getId()));

        mockMvc.perform(get("/users").param("sort", "role").with(as(admin)))
            .andExpect(jsonPath("$.content[0].role").value("USER"))
            .andExpect(jsonPath("$.content[3].role").value("OWNER"));
    }

    @Test
    @DisplayName("GET /users should sort by other fields in either direction")
    void list_sortUsername() throws Exception {
        mockMvc.perform(get("/users").param("sort", "username,desc").with(as(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].username").value("zed_admin"))
            .andExpect(jsonPath("$.content[3].username").value("an_admin"));

        mockMvc.perform(get("/users").param("sort", "id,desc").with(as(admin)))
            .andExpect(jsonPath("$.content[0].id").value(alex.getId()));
    }

    @Test
    @DisplayName("GET /users should sort by email and by createdAt, with the direction case-insensitive")
    void list_sortEmailAndCreatedAt() throws Exception {
        mockMvc.perform(get("/users").param("sort", "email").with(as(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].email").value("admin@u.nus.edu"))
            .andExpect(jsonPath("$.content[3].email").value("zed@u.nus.edu"));

        // seeded users can share a timestamp, so check the order rather than ids
        String body = mockMvc.perform(get("/users").param("sort", "createdAt,DESC").with(as(admin)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        List<Instant> createdAt = JsonPath.<List<String>>read(body, "$.content[*].createdAt")
            .stream().map(Instant::parse).toList();
        assertThat(createdAt).hasSize(4).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    @DisplayName("GET /users should page with the chosen size")
    void list_paging() throws Exception {
        for (int i = 0; i < 21; i++) {
            userRepository.save(new User("bulk" + i + "@u.nus.edu", "bulk_" + i, "hashed_password", Role.USER));
        }

        mockMvc.perform(get("/users").param("size", "20").param("page", "1").with(as(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(5))
            .andExpect(jsonPath("$.page.size").value(20))
            .andExpect(jsonPath("$.page.number").value(1))
            .andExpect(jsonPath("$.page.totalElements").value(25))
            .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    @DisplayName("GET /users should accept a page size of 50")
    void list_pageSize50() throws Exception {
        mockMvc.perform(get("/users").param("size", "50").with(as(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.size").value(50))
            .andExpect(jsonPath("$.content.length()").value(4));
    }

    @Test
    @DisplayName("GET /users should return 400 for page sizes outside 20, 50 and 100, and a negative page")
    void list_pageOutOfBounds() throws Exception {
        for (String size : new String[] {"0", "-1", "19", "21", "99", "101", "1000"}) {
            mockMvc.perform(get("/users").param("size", size).with(as(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("size must be one of 20, 50 or 100"));
        }
        mockMvc.perform(get("/users").param("page", "-1").with(as(admin)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("page must not be negative"));
        mockMvc.perform(get("/users").param("size", "abc").with(as(admin)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /users should return 400 problem+json for invalid parameters")
    void list_invalidParameters() throws Exception {
        mockMvc.perform(get("/users").param("size", "30").with(as(admin)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("size must be one of 20, 50 or 100"));
        mockMvc.perform(get("/users").param("sort", "passwordHash").with(as(admin)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/users").param("role", "SUPERUSER").with(as(admin)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // role change

    @Test
    @DisplayName("PATCH /users/{id} should promote a USER to ADMIN")
    void changeRole_promote() throws Exception {
        mockMvc.perform(patch("/users/{id}", alex.getId()).with(as(admin))
                .contentType(MediaType.APPLICATION_JSON).content(roleBody("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(alex.getId()))
            .andExpect(jsonPath("$.role").value("ADMIN"));

        assertThat(userRepository.findById(alex.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("PATCH /users/{id} should let an OWNER promote")
    void changeRole_ownerPromotes() throws Exception {
        mockMvc.perform(patch("/users/{id}", alex.getId()).with(as(owner))
                .contentType(MediaType.APPLICATION_JSON).content(roleBody("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("PATCH /users/{id} should let an ADMIN demote another ADMIN")
    void changeRole_demoteOtherAdmin() throws Exception {
        mockMvc.perform(patch("/users/{id}", otherAdmin.getId()).with(as(admin))
                .contentType(MediaType.APPLICATION_JSON).content(roleBody("USER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("PATCH /users/{id} should let an ADMIN demote themselves")
    void changeRole_selfDemote() throws Exception {
        mockMvc.perform(patch("/users/{id}", admin.getId()).with(as(admin))
                .contentType(MediaType.APPLICATION_JSON).content(roleBody("USER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("USER"));

        assertThat(userRepository.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("PATCH /users/{id} should return 200 unchanged when the role is already held")
    void changeRole_noOp() throws Exception {
        mockMvc.perform(patch("/users/{id}", alex.getId()).with(as(admin))
                .contentType(MediaType.APPLICATION_JSON).content(roleBody("USER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("PATCH /users/{id} should return 403 for an OWNER target, even from an OWNER")
    void changeRole_ownerTarget() throws Exception {
        mockMvc.perform(patch("/users/{id}", owner.getId()).with(as(owner))
                .contentType(MediaType.APPLICATION_JSON).content(roleBody("ADMIN")))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("OWNER accounts cannot be changed or removed"));

        assertThat(userRepository.findById(owner.getId()).orElseThrow().getRole()).isEqualTo(Role.OWNER);
    }

    @Test
    @DisplayName("PATCH /users/{id} should return 403 when OWNER is requested")
    void changeRole_ownerRequested() throws Exception {
        mockMvc.perform(patch("/users/{id}", admin.getId()).with(as(owner))
                .contentType(MediaType.APPLICATION_JSON).content(roleBody("OWNER")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("OWNER can only be granted through owner setup"));

        assertThat(userRepository.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("PATCH /users/{id} should return 400 for an unknown or missing role")
    void changeRole_invalidBody() throws Exception {
        mockMvc.perform(patch("/users/{id}", alex.getId()).with(as(admin))
                .contentType(MediaType.APPLICATION_JSON).content(roleBody("SUPERUSER")))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Request body is missing or malformed"));
        mockMvc.perform(patch("/users/{id}", alex.getId()).with(as(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Role is required."));
        mockMvc.perform(patch("/users/{id}", alex.getId()).with(as(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":null}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Role is required."));
        mockMvc.perform(patch("/users/{id}", alex.getId()).with(as(admin))
                .contentType(MediaType.APPLICATION_JSON).content(""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Request body is missing or malformed"));

        assertThat(userRepository.findById(alex.getId()).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("PATCH /users/{id} should reject a lower-case role name")
    void changeRole_lowerCaseRole() throws Exception {
        mockMvc.perform(patch("/users/{id}", alex.getId()).with(as(admin))
                .contentType(MediaType.APPLICATION_JSON).content(roleBody("admin")))
            .andExpect(status().isBadRequest());

        assertThat(userRepository.findById(alex.getId()).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("PATCH /users/{id} should return 404 for a missing or deleted user")
    void changeRole_notFound() throws Exception {
        mockMvc.perform(patch("/users/{id}", deleted.getId()).with(as(admin))
                .contentType(MediaType.APPLICATION_JSON).content(roleBody("ADMIN")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("User not found"));
        mockMvc.perform(patch("/users/{id}", deleted.getId() + 1000).with(as(admin))
                .contentType(MediaType.APPLICATION_JSON).content(roleBody("ADMIN")))
            .andExpect(status().isNotFound());
    }

    // removal

    @Test
    @DisplayName("DELETE /users/{id} should soft delete the account")
    void remove_softDeletes() throws Exception {
        mockMvc.perform(delete("/users/{id}", alex.getId()).with(as(admin)))
            .andExpect(status().isNoContent());

        assertThat(userRepository.findById(alex.getId()).orElseThrow().getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("DELETE /users/{id} should let an OWNER remove an account")
    void remove_byOwner() throws Exception {
        mockMvc.perform(delete("/users/{id}", admin.getId()).with(as(owner)))
            .andExpect(status().isNoContent());

        assertThat(userRepository.findById(admin.getId()).orElseThrow().getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("A removed user should drop out of profile lookups and the admin list")
    void remove_hiddenEverywhere() throws Exception {
        mockMvc.perform(delete("/users/{id}", alex.getId()).with(as(admin)))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/users/{id}", alex.getId()).with(as(admin)))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/users/me").with(as(alex)))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/users").param("search", "student_alex").with(as(admin)))
            .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("DELETE /users/{id} should return 401 when the principal is not a user id")
    void remove_nonNumericPrincipal() throws Exception {
        mockMvc.perform(delete("/users/{id}", alex.getId()).with(user("not-a-number").roles("ADMIN")))
            .andExpect(status().isUnauthorized());

        assertThat(userRepository.findById(alex.getId()).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("DELETE /users/{id} should let an ADMIN remove another ADMIN")
    void remove_otherAdmin() throws Exception {
        mockMvc.perform(delete("/users/{id}", otherAdmin.getId()).with(as(admin)))
            .andExpect(status().isNoContent());

        assertThat(userRepository.findById(otherAdmin.getId()).orElseThrow().getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("DELETE /users/{id} should return 403 for the caller's own account")
    void remove_self() throws Exception {
        mockMvc.perform(delete("/users/{id}", admin.getId()).with(as(admin)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("Use DELETE /users/me to delete your own account"));

        assertThat(userRepository.findById(admin.getId()).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("DELETE /users/{id} should return 403 for an OWNER target")
    void remove_ownerTarget() throws Exception {
        mockMvc.perform(delete("/users/{id}", owner.getId()).with(as(admin)))
            .andExpect(status().isForbidden());

        assertThat(userRepository.findById(owner.getId()).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("DELETE /users/{id} should return 404 for a missing or already deleted user")
    void remove_notFound() throws Exception {
        mockMvc.perform(delete("/users/{id}", deleted.getId()).with(as(admin)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("User not found"));
        mockMvc.perform(delete("/users/{id}", deleted.getId() + 1000).with(as(admin)))
            .andExpect(status().isNotFound());
    }
}
