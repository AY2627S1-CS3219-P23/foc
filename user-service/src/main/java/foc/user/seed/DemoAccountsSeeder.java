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
       2026-10-05 (issue #154): a seeding run that loses an insert race
       is logged as a warning instead of stopping the service.
       2026-10-07 (Claude Code, Opus 5.5), PR #162 Copilot review: only a
       unique violation counts as that race; any other integrity
       failure is rethrown.
       2026-10-10 (Claude Code, Opus 5.5), PR #162 re-review (Leong Wei
       Zhi): a run that loses the race seeds once more instead of
       leaving the accounts to the other instance (Ryan's choice among
       the reviewer's options).
Author review: Ryan to review via the PR.
2026-10-28 (Claude Code, Opus 5.5), issue #267, revised USR-8 (team
decision): each account is inserted through UserCreation, so every demo
user has its user.created and a credit account once the stack is up.
Author review: Ryan Ang, pending pull request review.
2026-11-06 (Claude Code, Opus 5.5), issue #48, DP11 (team decision,
Leong Wei Zhi): realistic fictional usernames from fixed lists instead
of demo_owner / demo_admin_N / demo_user_NNN; emails, counts, passwords
and the race handling unchanged. Author review: Leong Wei Zhi, pending
pull request review.
*/

package foc.user.seed;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.repository.UserRepository;
import foc.user.service.UserCreation;

@Component
@ConditionalOnProperty(name = "user.seed.demo", havingValue = "true")
public class DemoAccountsSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountsSeeder.class);

    // one password per role (team decision); documented in the README
    public static final String OWNER_PASSWORD = "OwnerPass123";
    public static final String ADMIN_PASSWORD = "AdminPass123";
    public static final String USER_PASSWORD = "StudentPass123";

    // fictional names (DP11), fixed so every stack seeds the same accounts;
    // underscores, not dots: AccountRules.USERNAME_PATTERN
    static final String OWNER_USERNAME = "daniel_koh";
    static final List<String> ADMIN_USERNAMES = List.of("priya_menon", "farhan_ismail", "chloe_ng");
    static final List<String> USER_USERNAMES = List.of(
        "weiling_tan", "arjun_nair", "siti_rahman", "junjie_lim", "nurul_huda",
        "kaixuan_ong", "rachel_goh", "haziq_osman", "meiling_chua", "rohan_pillai",
        "xinyi_teo", "aisyah_karim", "bryan_yeo", "kavya_iyer", "zhiwei_chen",
        "amirah_salleh", "ethan_wong", "divya_krishnan", "shufen_lee", "irfan_hamid",
        "jiahui_sim", "nicholas_desouza", "yuting_low", "hafiz_yusof", "sarah_pereira",
        "kenji_tanaka", "minjun_park", "anh_nguyen", "putri_wijaya", "haoran_liu",
        "jiayi_zhang", "vikram_raj", "nadia_aziz", "shawn_tay", "ananya_rao",
        "wenjie_huang", "syafiq_rahim", "clara_fernandez", "yixuan_wang", "darren_quek",
        "huimin_ho", "aqil_zainal", "meera_subramaniam", "jonathan_seah", "xiaoyu_li",
        "farah_hassan", "gabriel_soh", "pooja_sharma", "yongsheng_phua", "izzah_mansor",
        "marcus_toh", "sneha_reddy", "zhenyu_kwek", "alya_rashid", "timothy_chan",
        "hannah_loh", "suresh_kumar", "qianhui_yap", "danish_iskandar", "isabel_rodrigues",
        "thanh_tran", "lucas_neo", "jasmine_kaur", "boonkiat_lau", "nabilah_ahmad",
        "aditya_bose", "sihan_zheng", "yuhan_xu", "amir_hakim", "natalie_foo",
        "harish_balan", "kexin_guo", "ilyas_jamal", "megan_choo", "tarun_gupta",
        "ruiqi_peh", "sofia_lim", "adam_noor", "jingwen_tham", "rizwan_ali",
        "grace_chia", "shreya_das", "zihan_sun", "fatin_amin", "javier_ong",
        "lakshmi_venkat", "hongyi_koh", "daphne_wee", "zulfiqar_basri", "elaine_kok",
        "mohan_prakash", "yiting_ng", "aiman_razak", "olivia_sng", "rahul_varma",
        "shuting_lam", "hidayah_said", "benjamin_lee", "tomoko_sato", "joel_cheong");

    // at most 99 admins and 99999 users: past that the email formats in
    // accounts() gain an eighth digit and fail AccountRules.EMAIL_PATTERN
    // (accountsFollowTheSignupRules catches it)
    public static final int ADMINS = ADMIN_USERNAMES.size();
    public static final int USERS = USER_USERNAMES.size();

    private static final String UNIQUE_VIOLATION = "23505";

    // one seeded account
    record DemoAccount(String email, String username, Role role) {
    }

    private final UserRepository userRepository;
    private final UserCreation userCreation;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transaction;

    public DemoAccountsSeeder(
            UserRepository userRepository,
            UserCreation userCreation,
            PasswordEncoder passwordEncoder,
            PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.userCreation = userCreation;
        this.passwordEncoder = passwordEncoder;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    // e9xxxxxx emails (NUS format), clear of the hand-made dev accounts;
    // the Nth name in a list gets the Nth email of its role
    static List<DemoAccount> accounts() {
        List<DemoAccount> accounts = new ArrayList<>();
        accounts.add(new DemoAccount("e9000001@u.nus.edu", OWNER_USERNAME, Role.OWNER));
        // Locale.ROOT: a default locale with its own digits must not change them
        for (int i = 1; i <= ADMINS; i++) {
            accounts.add(new DemoAccount(
                String.format(Locale.ROOT, "e90001%02d@u.nus.edu", i), ADMIN_USERNAMES.get(i - 1), Role.ADMIN));
        }
        for (int i = 1; i <= USERS; i++) {
            accounts.add(new DemoAccount(
                String.format(Locale.ROOT, "e91%05d@u.nus.edu", i), USER_USERNAMES.get(i - 1), Role.USER));
        }
        return accounts;
    }

    // Inserts every demo account that isn't there yet, so a restart (or a
    // database that already holds some of them) doesn't duplicate or fail.
    // Each role's password is hashed once and the hash reused: bcrypt per
    // account would add seconds to startup for no benefit in demo data.
    // One transaction, so a failed run leaves nothing half-seeded
    @Override
    public void run(String... args) {
        if (seedUnlessRaced()) {
            return;
        }
        // another instance starting at the same time inserted an account
        // between the check and the insert, and this run was rolled back
        // whole. The other instance may have rolled back as well, so seed
        // once more: its committed accounts are visible now and are skipped
        log.info("Demo accounts: lost an insert race to another instance, seeding again");
        if (!seedUnlessRaced()) {
            // the service still starts
            log.warn("Demo accounts not seeded: lost an insert race twice, some may be missing");
        }
    }

    // false when the run hit a duplicate key and was rolled back. Caught
    // outside the transaction, which is already rolled back by then
    private boolean seedUnlessRaced() {
        try {
            transaction.executeWithoutResult(status -> seed());
            return true;
        } catch (DataIntegrityViolationException e) {
            // anything but a duplicate key (a missing column, a broken
            // constraint) is a real problem and must stop startup
            if (!isUniqueViolation(e)) {
                throw e;
            }
            log.debug("Demo accounts: duplicate key while seeding ({})",
                e.getMostSpecificCause().getMessage());
            return false;
        }
    }

    // SQLState 23505 is unique_violation: the users table's email and
    // username indexes
    private static boolean isUniqueViolation(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private void seed() {
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
            // with its user.created, in the run's one transaction (USR-8)
            userCreation.create(new User(account.email(), account.username(), hash, account.role()));
            created++;
        }
        log.info("Demo accounts: {} created, {} already present (USER_SEED_DEMO=true)",
            created, accounts().size() - created);
    }
}
