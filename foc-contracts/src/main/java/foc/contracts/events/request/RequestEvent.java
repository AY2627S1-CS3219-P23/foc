/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: order-domain event refinement of DomainEvent, per the
 * author's Method-B decisions (docs/notification-service.md D16-D19).
 * 2026-09-20: order→request rename and events.core/.request package
 * split applied (author decision D22, docs/notification-service.md).
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.contracts.events.request;

import foc.contracts.events.core.DomainEvent;

/**
 * A request-domain event: every request-lifecycle record implements this,
 * giving consumers uniform typed access to the request reference without
 * knowing the concrete event class.
 */
public interface RequestEvent extends DomainEvent {

	/** The request this fact is about. */
	String requestId();

}
