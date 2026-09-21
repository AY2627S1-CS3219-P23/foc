/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-18.
 * Scope: generated via Spring Initializr while scaffolding the service
 * for issue #61.
 * 2026-09-20, issue #68: @EnableScheduling added for the retention
 * purge scheduler (D9); it runs on the sole TaskScheduler bean already
 * declared in WebSocketStompConfig, so Boot's default scheduler stays
 * backed off and no second one is needed.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NotificationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificationServiceApplication.class, args);
	}

}
