/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-18.
 * Scope: the read endpoints' query pieces as JPA Specifications, so the
 * optional filters (RequestFilter) combine into one paged query. Each
 * returns null when its value is absent, and null pieces are skipped.
 * Same day: after(), for hiding past-deadline requests from the open list.
 * 2026-10-20: statusIn(), for a list filtered by several states.
 * Reviewed by: Ko-Khan (via pull request).
 * 2026-10-23, Claude Code (Opus 5.5), issue #240: closeReasonIn(), for the
 * admin list's closeReason filter. Author review: Ryan Ang, pending pull
 * request review.
 */
package foc.order.repository;

import foc.order.CloseReason;
import foc.order.OrderStates;
import foc.order.model.Requests;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

public final class RequestSpecifications {

    private RequestSpecifications() {
    }

    /** All the given pieces together; null pieces are ignored. */
    @SafeVarargs
    public static Specification<Requests> allOf(Specification<Requests>... pieces) {
        return Stream.of(pieces).filter(Objects::nonNull)
                .reduce((root, query, cb) -> cb.conjunction(), Specification::and);
    }

    public static Specification<Requests> status(OrderStates status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** In any of the states; null matches every state. */
    public static Specification<Requests> statusIn(Collection<OrderStates> states) {
        return states == null ? null : (root, query, cb) -> root.get("status").in(states);
    }

    /** With any of the close reasons; null matches every request. */
    public static Specification<Requests> closeReasonIn(Collection<CloseReason> reasons) {
        return reasons == null ? null : (root, query, cb) -> root.get("closeReason").in(reasons);
    }

    public static Specification<Requests> requester(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("requesterId"), userId);
    }

    public static Specification<Requests> courier(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("courierId"), userId);
    }

    /** The user is the requester or the courier. */
    public static Specification<Requests> involving(Long userId) {
        return (root, query, cb) -> cb.or(
                cb.equal(root.get("requesterId"), userId), cb.equal(root.get("courierId"), userId));
    }

    public static Specification<Requests> supplier(Long supplierId) {
        return supplierId == null ? null : (root, query, cb) -> cb.equal(root.get("supplierId"), supplierId);
    }

    public static Specification<Requests> creditAtLeast(Integer min) {
        return min == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("creditAmount"), min);
    }

    public static Specification<Requests> creditAtMost(Integer max) {
        return max == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("creditAmount"), max);
    }

    /** {@code field} at or after the instant. */
    public static Specification<Requests> notBefore(String field, Instant from) {
        return from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get(field), from);
    }

    /** {@code field} strictly after the instant. */
    public static Specification<Requests> after(String field, Instant instant) {
        return (root, query, cb) -> cb.greaterThan(root.get(field), instant);
    }

    /** {@code field} strictly before the instant. */
    public static Specification<Requests> before(String field, Instant to) {
        return to == null ? null : (root, query, cb) -> cb.lessThan(root.get(field), to);
    }
}
