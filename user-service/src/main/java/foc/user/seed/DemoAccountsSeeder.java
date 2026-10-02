/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: demo accounts for local testing and demos — 1 OWNER, 3 ADMINs and
       100 USERs, inserted at startup. Team decisions: a startup seeder in
       user-service, off unless USER_SEED_DEMO=true (so a deployment never
       gets known-password accounts by accident), and one password per
       role. Follows supplier-service's SuppliersSeeder (CommandLineRunner
       in a seed package).
       2026-10-02 (Claude Code, Opus 5.5), issue #154: Locale.ROOT for the
       generated emails and usernames; comment on the ADMINS/USERS limits.
Author review: Ryan to review via the PR.
*/

package foc.user.seed;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.repository.UserRepository;

@Component
@ConditionalOnProperty(name = "user.seed.demo", havingValue = "true")
public class DemoAccountsSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountsSeeder.class);

    // one password per role (team decision); documented in the README
    public static final String OWNER_PASSWORD = "OwnerPass123";
    public static final String ADMIN_PASSWORD = "AdminPass123";
    public static final String USER_PASSWORD = "StudentPass123";

    // at most 99 admins and 99999 users: past that the email formats in
    // accounts() gain an eighth digit and fail AccountRules.EMAIL_PATTERN
    // (accountsFollowTheSignupRules catches it)
    public static final int ADMINS = 3;
    public static final int USERS = 100;

    // one seeded account
    record DemoAccount(String email, String username, Role role) {
    }

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoAccountsSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // e9xxxxxx emails (NUS format), clear of the hand-made dev accounts;
    // usernames say what they are
    static List<DemoAccount> accounts() {
        List<DemoAccount> accounts = new ArrayList<>();
        accounts.add(new DemoAccount("e9000001@u.nus.edu", "demo_owner", Role.OWNER));
        for (int i = 1; i <= ADMINS; i++) {
            accounts.add(new DemoAccount(
                String.format(Locale.ROOT, "e90001%02d@u.nus.edu", i), "demo_admin_" + i, Role.ADMIN));
        }
        // Locale.ROOT: a default locale with its own digits must not change them
        for (int i = 1; i <= USERS; i++) {
            accounts.add(new DemoAccount(
                String.format(Locale.ROOT, "e91%05d@u.nus.edu", i),
                String.format(Locale.ROOT, "demo_user_%03d", i), Role.USER));
        }
        return accounts;
    }

    // Inserts every demo account that isn't there yet, so a restart (or a
    // database that already holds some of them) doesn't duplicate or fail.
    // Each role's password is hashed once and the hash reused: bcrypt per
    // account would add seconds to startup for no benefit in demo data
    @Override
    @Transactional
    public void run(String... args) {
        String ownerHash = passwordEncoder.encode(OWNER_PASSWORD);
        String adminHash = passwordEncoder.encode(ADMIN_PASSWORD);
        String userHash = passwordEncoder.encode(USER_PASSWORD);

        int created = 0;
        for (DemoAccount account : accounts()) {
            if (userRepository.existsByEmail(account.email())
                    || userRepository.existsByUsernameIgnoreCase(account.username())) {
                continue;
            }
            String hash = switch (account.role()) {
                case OWNER -> ownerHash;
                case ADMIN -> adminHash;
                case USER -> userHash;
            };
            userRepository.save(new User(account.email(), account.username(), hash, account.role()));
            created++;
        }
        log.info("Demo accounts: {} created, {} already present (USER_SEED_DEMO=true)",
            created, accounts().size() - created);
    }
}
