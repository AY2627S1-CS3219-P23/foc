/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, PR #271 review (Sinnez1).
 * Scope: the reviewer's gap — the H2 tests build their schema from the
 * entities and FlywayMigrationTests runs raw SQL, so nothing checked
 * the entities against V2. This boots the context on real Postgres
 * with Flyway on and Hibernate validating; ddl-auto stays none in
 * production per team precedent, so the drift check lives here.
 * Author review: [pending]
 */
package foc.rating;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Pins the JPA entities to the Flyway schema: the context boots against
 * the shared Postgres container with the real migrations applied and
 * {@code ddl-auto: validate}, so a column an entity expects that V2
 * does not create (or the reverse rename) fails here instead of on the
 * first real event. Startup is the assertion. Skips cleanly when
 * Docker is not available.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class SchemaValidationTest extends PostgresTestContainer {

    @Test
    void entitiesMatchTheFlywaySchema() {
        // context startup ran Flyway V1..V2 and Hibernate's validator
    }
}
