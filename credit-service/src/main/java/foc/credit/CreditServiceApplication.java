/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-09.
 * Scope: scaffolded following the user-service / supplier-service
 * pattern.
 * 2026-10-10: @EnableScheduling, for the outbox publisher and the expiry
 * scheduler to come (the author's instruction).
 * Author review: Ryan Ang, pending pull request review.
 */
package foc.credit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CreditServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CreditServiceApplication.class, args);
	}

}
