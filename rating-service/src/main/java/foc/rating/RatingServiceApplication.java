/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-26, issue #250.
 * Scope: scaffolded following the supplier-service / order-service
 * pattern.
 * Reviewed by: [pending]
 * 2026-10-30, Claude Code (Fable 5), issue #252: @EnableScheduling for
 * the outbox publisher's poll (order/credit precedent).
 * Author review: [pending]
 */
package foc.rating;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// scheduling runs the outbox publisher's poll; it is the only
// @Scheduled job, so the default single scheduler thread is enough
@SpringBootApplication
@EnableScheduling
public class RatingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(RatingServiceApplication.class, args);
	}

}
