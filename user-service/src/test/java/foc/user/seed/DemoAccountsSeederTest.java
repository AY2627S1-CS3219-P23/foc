/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: tests for DemoAccountsSeeder with USER_SEED_DEMO on: 1 owner, 3
       admins and 100 users; each role logs in with its password; a rerun
       adds nothing; an account already present is skipped; every seeded
       email and username passes the sign-up rules.
       2026-10-02 (Claude Code, Opus 5.5), issue #154: a soft-deleted demo
       account is not re-seeded.
       2026-10-05 (Claude Code, Opus 5.5), issue #154: the switch is set
       with @TestPropertySource, to override PostgresTestContainer's pin;
       a lost insert race is logged, not thrown.
       2026-10-07 (Claude Code, Opus 5.5), PR #162 Copilot review: the race
       stub carries the unique-violation SQLState; any other integrity
       failure is rethrown.
Author review: Ryan to review via the PR.
*/

package foc.user.seed;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

import foc.user.PostgresTestContainer;
import foc.user.dto.AccountRules;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.repository.UserRepository;

// @TestPropertySource, not @SpringBootTest(properties): it has to outrank
// the user.seed.demo=false that PostgresTestContainer sets the same way
@SpringBootTest
@TestPropertySource(properties = "user.seed.demo=true")
@AutoConfigureMockMvc
class DemoAccountsSeederTest extends PostgresTestContainer {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DemoAccountsSeeder seeder;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // the seeder already ran at startup; start each test from a known state
    @BeforeEach
    void seedFresh() {
        userRepository.deleteAll();
        seeder.run();
    }

    // other test classes share the database
    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    private Map<Role, Long> countByRole() {
        return userRepository.findAll().stream()
            .collect(Collectors.groupingBy(User::getRole, Collectors.counting()));
    }

    private void login(String username, String password, int expectedStatus) throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernameOrEmail\":\"" + username + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().is(expectedStatus));
    }

    @Test
    @DisplayName("Seeds 1 owner, 3 admins and 100 users")
    void seedsEveryRole() {
        assertThat(countByRole()).containsExactlyInAnyOrderEntriesOf(Map.of(
            Role.OWNER, 1L,
            Role.ADMIN, (long) DemoAccountsSeeder.ADMINS,
            Role.USER, (long) DemoAccountsSeeder.USERS));
        assertThat(userRepository.findByUsernameIgnoreCase("demo_owner").orElseThrow().getRole())
            .isEqualTo(Role.OWNER);
    }

    @Test
    @DisplayName("Each role logs in with its own password")
    void rolesLogInWithTheirPasswords() throws Exception {
        login("demo_owner", DemoAccountsSeeder.OWNER_PASSWORD, 200);
        login("demo_admin_2", DemoAccountsSeeder.ADMIN_PASSWORD, 200);
        login("demo_user_042", DemoAccountsSeeder.USER_PASSWORD, 200);
        // another role's password is wrong
        login("demo_user_043", DemoAccountsSeeder.ADMIN_PASSWORD, 401);
    }

    @Test
    @DisplayName("Running it again adds nothing")
    void rerunAddsNothing() {
        long before = userRepository.count();

        seeder.run();

        assertThat(userRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("Only missing accounts are added; existing ones are left as they are")
    void addsOnlyMissingAccounts() {
        User user = userRepository.findByUsernameIgnoreCase("demo_user_005").orElseThrow();
        userRepository.delete(user);
        User admin = userRepository.findByUsernameIgnoreCase("demo_admin_1").orElseThrow();
        admin.setRole(Role.USER);
        userRepository.save(admin);

        seeder.run();

        assertThat(userRepository.findByUsernameIgnoreCase("demo_user_005")).isPresent();
        // a demoted demo admin stays demoted
        assertThat(userRepository.findByUsernameIgnoreCase("demo_admin_1").orElseThrow().getRole())
            .isEqualTo(Role.USER);
        assertThat(userRepository.count()).isEqualTo(1 + DemoAccountsSeeder.ADMINS + DemoAccountsSeeder.USERS);
    }

    @Test
    @DisplayName("A soft-deleted demo account stays deleted after a rerun")
    void softDeletedAccountStaysDeleted() {
        // relies on existsByEmail / existsByUsernameIgnoreCase counting
        // soft-deleted rows; narrowing them would re-insert the account and
        // fail on the unique email
        User user = userRepository.findByUsernameIgnoreCase("demo_user_010").orElseThrow();
        user.softDelete(Instant.now());
        userRepository.save(user);
        long before = userRepository.count();

        seeder.run();

        assertThat(userRepository.findByUsernameIgnoreCase("demo_user_010").orElseThrow().getDeletedAt())
            .isNotNull();
        assertThat(userRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("Losing an insert race to another instance is logged, not thrown")
    void lostInsertRaceDoesNotThrow() {
        // every account looks missing, then the insert hits the unique index
        UserRepository racing = mock(UserRepository.class);
        when(racing.save(any(User.class)))
            .thenThrow(new DataIntegrityViolationException("idx_users_email",
                new SQLException("duplicate key", "23505")));
        long before = userRepository.count();

        assertThatCode(() -> new DemoAccountsSeeder(racing, passwordEncoder, transactionManager).run())
            .doesNotThrowAnyException();

        assertThat(userRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("An integrity failure that is not a duplicate key still stops startup")
    void otherIntegrityFailureIsRethrown() {
        // 23502 is not_null_violation: not the race, so not swallowed
        UserRepository broken = mock(UserRepository.class);
        when(broken.save(any(User.class)))
            .thenThrow(new DataIntegrityViolationException("users.email",
                new SQLException("null value", "23502")));

        assertThatThrownBy(() -> new DemoAccountsSeeder(broken, passwordEncoder, transactionManager).run())
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Every seeded email and username passes the sign-up rules, and none repeat")
    void accountsFollowTheSignupRules() {
        Pattern email = Pattern.compile(AccountRules.EMAIL_PATTERN);
        Pattern username = Pattern.compile(AccountRules.USERNAME_PATTERN);

        for (DemoAccountsSeeder.DemoAccount account : DemoAccountsSeeder.accounts()) {
            assertThat(email.matcher(account.email()).matches()).as(account.email()).isTrue();
            assertThat(username.matcher(account.username()).matches()).as(account.username()).isTrue();
            assertThat(account.username().length())
                .isBetween(AccountRules.USERNAME_MIN, AccountRules.USERNAME_MAX);
        }
        assertThat(DemoAccountsSeeder.accounts().stream()
                .collect(Collectors.toMap(DemoAccountsSeeder.DemoAccount::email, Function.identity())))
            .hasSize(DemoAccountsSeeder.accounts().size());
    }
}
