/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (DP10).
 * Scope: tests for ShedLock on the @Scheduled jobs (team decision, Leong
 * Wei Zhi via options Q&A), on the H2 context the other @SpringBootTest
 * classes share. Flyway is off there, so ShedlockMigrationTest covers the
 * table and the lock on Postgres.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.rating.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.javacrumbs.shedlock.core.LockProvider;
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
import org.springframework.scheduling.annotation.Scheduled;

@SpringBootTest
class SchedulerLockTest {

    // every @Scheduled method in the service and its lock: a new job adds
    // its line here, with a name no other job has
    private static final Map<String, String> EXPECTED_LOCKS = Map.ofEntries(
            Map.entry("OutboxPublisher.publishUnsent", "rating.outbox-publisher"));

    @Autowired
    private LockProvider lockProvider;

    @Autowired
    private ApplicationContext context;

    @Test
    void theLockProviderIsTheJdbcOne() {
        assertThat(lockProvider).isInstanceOf(JdbcTemplateLockProvider.class);
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

    static boolean isShedLock(Advisor advisor) {
        return advisor.getClass().getName().startsWith("net.javacrumbs.shedlock.");
    }

    private static List<Method> scheduledMethods() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter((reader, factory) -> true);
        List<Method> methods = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("foc.rating")) {
            for (Method method : Class.forName(candidate.getBeanClassName()).getDeclaredMethods()) {
                if (AnnotatedElementUtils.hasAnnotation(method, Scheduled.class)) {
                    methods.add(method);
                }
            }
        }
        return methods;
    }
}
