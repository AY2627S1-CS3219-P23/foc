/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-10-30, issue #254.
 * Scope: the report repository; JpaRepository's findAll(Pageable)
 * already serves the admin list, so there are no derived queries.
 * Author review: [pending]
 */
package foc.rating.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import foc.rating.entity.UserReport;

public interface UserReportRepository extends JpaRepository<UserReport, UUID> {
}
