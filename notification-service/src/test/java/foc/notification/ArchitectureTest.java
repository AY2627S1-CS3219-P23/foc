/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-20.
 * Scope: ArchUnit test for issue #69, enforcing team decision D11
 * (docs/notification-service.md, "Broker decoupling"): broker-specific
 * types must stay confined to the messaging.rabbitmq adapter package.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Enforces the D11 ports-and-adapters boundary: only
 * {@code foc.notification.messaging.rabbitmq} may depend on RabbitMQ
 * types. Every other package — including the sibling
 * {@code messaging.stomp} adapter — must stay broker-agnostic, per the
 * design doc's "Broker decoupling" section.
 */
class ArchitectureTest {

	private static final JavaClasses CLASSES = new ClassFileImporter().importPackages("foc.notification");

	@Test
	void onlyTheRabbitMqAdapterDependsOnBrokerTypes() {
		ArchRule rule = noClasses()
				.that().resideOutsideOfPackage("foc.notification.messaging.rabbitmq..")
				.should().dependOnClassesThat()
				.resideInAnyPackage("org.springframework.amqp..", "com.rabbitmq..");

		rule.check(CLASSES);
	}
}
