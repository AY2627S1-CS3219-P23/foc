/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Fable 5), 2026-09-19.
 * Scope: nullable-component marker added while addressing PR #75
 * review feedback (converter-side required-field validation); the
 * only nullable field, OrderCancelled.courierId, was decided by the
 * author with the D17 field vocabulary.
 * Reviewed by: Leong Wei Zhi (via pull request).
 */
package foc.contracts.events;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a record component that may legitimately be {@code null} on
 * the wire (e.g. {@link OrderCancelled}'s {@code courierId} for a
 * pre-acceptance cancel). Every unmarked component is required:
 * consumers reject events with missing required fields as conversion
 * failures instead of letting {@code null}s reach business logic.
 *
 * <p>Plain Java annotation — no framework dependency (D15).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface Nullable {
}
