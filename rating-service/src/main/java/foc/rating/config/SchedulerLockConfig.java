/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (DP10).
 * Scope: ShedLock on the @Scheduled jobs, so each runs on one replica at a
 * time (team decision, Leong Wei Zhi via options Q&A): the JDBC lock
 * provider on the service's own database, with the database's clock and
 * the application's transaction manager, as the ShedLock README
 * configures it.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.rating.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Every {@code @Scheduled} method also carries {@code @SchedulerLock}: a
 * replica runs the job only while it holds the job's row in the
 * {@code shedlock} table (Flyway), and a replica that finds the row held
 * skips that run rather than waiting. The lock is taken on a direct call
 * too (the method is proxied), so a scheduled method must be public and
 * not final.
 *
 * <p>The lock times come from the database's clock ({@code usingDbTime}),
 * not each replica's. A replica that dies holding a lock frees it once the
 * job's {@code lockAtMostFor} has passed; the default below is for a job
 * that names none.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource, PlatformTransactionManager transactionManager) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                // the application's JPA transaction manager, as the README
                // asks for with Hibernate: the lock's own REQUIRES_NEW
                // transaction then suspends a caller's transaction instead
                // of committing that caller's connection
                .withTransactionManager(transactionManager)
                .usingDbTime()
                .build());
    }
}
