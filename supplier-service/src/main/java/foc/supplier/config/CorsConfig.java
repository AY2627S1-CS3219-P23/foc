/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Sonnet 5), 2026-09-26.
 * Scope: CORS for issue #133 — the browser (web/, VITE_SUPPLIER_SERVICE_URL)
 * calls this service directly (no API gateway, per root AGENTS.md), so
 * the service must allow the web origin itself. Origin is configurable
 * via WEB_ALLOWED_ORIGIN so it still works if WEB_PORT changes; default
 * matches .env.example's WEB_PORT=5173. Author decision, mirroring
 * notification-service's NOTIFICATION_WS_ALLOWED_ORIGINS pattern.
 * Reviewed by: [pending]
 */
package foc.supplier.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String webAllowedOrigin;

    public CorsConfig(@Value("${supplier.web-allowed-origin:http://localhost:5173}") String webAllowedOrigin) {
        this.webAllowedOrigin = webAllowedOrigin;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(webAllowedOrigin)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}
