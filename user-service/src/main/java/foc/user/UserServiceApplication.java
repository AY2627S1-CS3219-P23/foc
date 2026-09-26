/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-20.
 * Scope: scaffolded for issue #84 following the notification-service
 * pattern.
 * 2026-09-27 (Claude Code, Fable 5), issue #93: @EnableScheduling added
 * for the day-31 account purge (AccountPurgeScheduler).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UserServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(UserServiceApplication.class, args);
	}

}
