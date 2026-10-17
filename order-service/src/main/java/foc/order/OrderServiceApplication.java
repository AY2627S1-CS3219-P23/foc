/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-10.
 * Scope: scaffolded following the supplier-service / notification-service
 * pattern.
 * 2026-10-15: @EnableScheduling, for the outbox publisher and the
 * pending-timeout sweep.
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OrderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderServiceApplication.class, args);
	}

}
