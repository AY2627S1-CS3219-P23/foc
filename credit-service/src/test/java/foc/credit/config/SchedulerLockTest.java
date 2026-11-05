/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (DP10).
 * Scope: tests for ShedLock on the @Scheduled jobs (team decision, Leong
 * Wei Zhi via options Q&A), on the Testcontainers Postgres the other
 * @SpringBootTest classes share.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.credit.config;

import static org.assertj.core.api.Assertions.assertThat;

import foc.credit.PostgresTestContainer;
import foc.credit.service.ExpiryAndRedistributionScheduler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class SchedulerLockTest extends PostgresTestContainer {

    // every @Scheduled method in the service and its lock: a new job adds
    // its line here, with a name no other job has
    private static final Map<String, String> EXPECTED_LOCKS = Map.ofEntries(
            Map.entry("OutboxPublisher.publishUnsent", "credit.outbox-publisher"),
            Map.entry("OutboxRetentionScheduler.purgeSentReplies", "credit.outbox-retention"),
            Map.entry("ExpiryAndRedistributionScheduler.runDaily", "credit.expiry-and-redistribution"));

    @Autowired
    private LockProvider lockProvider;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void theLockProviderIsTheJdbcOne() {
        assertThat(lockProvider).isInstanceOf(JdbcTemplateLockProvider.class);
    }

    @Test
    void migrationCreatesTheShedlockTable() {
        List<String> columns = jdbc.queryForList("select column_name from information_schema.columns"
                + " where table_schema = current_schema() and table_name = 'shedlock'", String.class);

        assertThat(columns).containsExactlyInAnyOrder("name", "lock_until", "locked_at", "locked_by");
    }

    @Test
    void aHeldLockIsRefusedUntilItIsReleased() {
        LockConfiguration lock = lock("test.held-" + UUID.randomUUID());

        Optional<SimpleLock> first = lockProvider.lock(lock);
        assertThat(first).isPresent();
        // what a second replica sees while the job runs: it skips the run
        assertThat(lockProvider.lock(lock)).isEmpty();

        first.get().unlock();
        Optional<SimpleLock> again = lockProvider.lock(lock);
        assertThat(again).isPresent();
        again.get().unlock();
    }

    // the lock runs in its own transaction: taken inside a caller's
    // transaction, it neither commits nor ends that transaction
    @Test
    void takingALockLeavesTheCallersTransactionToRollBack() {
        String probe = "test.probe-" + UUID.randomUUID();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("insert into shedlock (name, lock_until, locked_at, locked_by)"
                    + " values (?, now(), now(), 'test')", probe);
            lockProvider.lock(lock("test.inner-" + UUID.randomUUID())).orElseThrow().unlock();
            status.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject("select count(*) from shedlock where name = ?", Integer.class, probe))
                .isZero();
    }

    private static LockConfiguration lock(String name) {
        return new LockConfiguration(Instant.now(), name, Duration.ofSeconds(30), Duration.ZERO);
    }

    @Test
    void everyScheduledJobTakesItsOwnLock() throws ClassNotFoundException {
        Map<String, String> locks = new TreeMap<>();
        for (Method method : scheduledMethods()) {
            SchedulerLock lock = method.getAnnotation(SchedulerLock.class);
            String id = method.getDeclaringClass().getSimpleName() + "." + method.getName();
            assertThat(lock).as(id + " has @SchedulerLock").isNotNull();
            assertThat(lock.lockAtMostFor()).as(id + " lockAtMostFor").isNotBlank();
            // the lock is an AOP proxy around the method, which skips
            // non-public and final methods without a word
            assertThat(Modifier.isPublic(method.getModifiers())).as(id + " is public").isTrue();
            assertThat(Modifier.isFinal(method.getModifiers())).as(id + " is final").isFalse();
            assertThat(Modifier.isFinal(method.getDeclaringClass().getModifiers())).as(id + "'s class is final")
                    .isFalse();
            locks.put(id, lock.name());
        }

        assertThat(locks).isEqualTo(new TreeMap<>(EXPECTED_LOCKS));
        assertThat(new HashSet<>(locks.values())).hasSize(locks.size());
    }

    // the jobs this context runs (the others are off in tests) are proxied
    // with ShedLock's advice, so a call through the bean takes the lock
    @Test
    void theJobBeansInTheContextAreLockProxies() throws ClassNotFoundException {
        for (Method method : scheduledMethods()) {
            Object bean = context.getBeanProvider(method.getDeclaringClass()).getIfAvailable();
            if (bean == null) {
                continue;
            }
            assertThat(AopUtils.isAopProxy(bean)).as(method.getDeclaringClass() + " is proxied").isTrue();
            assertThat(((Advised) bean).getAdvisors())
                    .as(method.getDeclaringClass() + " has ShedLock's advisor")
                    .anyMatch(SchedulerLockTest::isShedLock);
        }
    }

    // the startup run is the same routine: one lock, so a replica that
    // starts while another runs it skips rather than runs it twice
    @Test
    void theStartupRunTakesTheDailyRunsLock() throws NoSuchMethodException {
        SchedulerLock daily = ExpiryAndRedistributionScheduler.class.getMethod("runDaily")
                .getAnnotation(SchedulerLock.class);
        SchedulerLock startup = ExpiryAndRedistributionScheduler.class.getMethod("runAtStartup")
                .getAnnotation(SchedulerLock.class);

        assertThat(startup).isNotNull();
        assertThat(startup.name()).isEqualTo(daily.name());
        assertThat(startup.lockAtMostFor()).isEqualTo(daily.lockAtMostFor());
    }

    static boolean isShedLock(Advisor advisor) {
        return advisor.getClass().getName().startsWith("net.javacrumbs.shedlock.");
    }

    private static List<Method> scheduledMethods() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter((reader, factory) -> true);
        List<Method> methods = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("foc.credit")) {
            for (Method method : Class.forName(candidate.getBeanClassName()).getDeclaredMethods()) {
                if (AnnotatedElementUtils.hasAnnotation(method, Scheduled.class)) {
                    methods.add(method);
                }
            }
        }
        return methods;
    }
}
