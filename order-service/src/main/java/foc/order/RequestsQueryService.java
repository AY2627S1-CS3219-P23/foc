/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-18.
 * Scope: the read side of the author's endpoint design (GET /requests,
 * /requests/{id}, /requests/mine, /users/{id}/requests): open errands
 * are CREATED only, a request's detail is its requester's, its courier's
 * or an admin's, lists are newest first and paged (page >= 0, size
 * 1-100, as credit-service's history). Bad parameters are an
 * InvalidRequestException (400). These choices are the author's.
 * Same day: the author's filters on all three lists (supplier, reward
 * range, deadline and created ranges) and a sort parameter (createdAt,
 * newest first, or deadline, nearest first), through
 * RequestSpecifications; ranges checked as credit-service's history.
 * Same day: the open list hides requests past their deadline.
 * 2026-10-20: state takes several states, comma-separated (e.g.
 * state=CREATED,ACCEPTED), so a page can list all active or all finished
 * requests in one paged query (the author's decision).
 * Reviewed by: Ko-Khan (via pull request).
 * 2026-10-23, Claude Code (Opus 5.5), issue #240: the admin list (every
 * user's requests by state and closeReason, both comma-separated, plus
 * the shared filters) and the batch status lookup (an unknown id answers
 * null; at most 100 ids). Both shapes are team decisions (DSP-5). Author
 * review: Ryan Ang, pending pull request review.
 * 2026-10-26, Claude Code (Opus 5.5), issue #239: the disputes of a page
 * of requests in one query, for the dispute object on the list and
 * detail responses (team decision: no query per row). Author review:
 * Ryan Ang, pending pull request review.
 * 2026-10-27, Claude Code (Opus 5.5), issue #242: the admin list's userId
 * (requester or courier, combinable with every other filter) and the
 * closedFrom / closedTo filter on every list (team decisions). Author
 * review: Ryan Ang, pending pull request review.
 */
package foc.order;

import foc.order.model.RequestDispute;
import foc.order.model.Requests;
import foc.order.repository.RequestDisputeRepository;
import foc.order.repository.RequestSpecifications;
import foc.order.repository.RequestsRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import static foc.order.repository.RequestSpecifications.*;

@Service
@Transactional(readOnly = true)
public class RequestsQueryService {

    /** Which side of the request the caller is on, for /requests/mine. */
    public enum Role {
        REQUESTER, COURIER
    }

    static final int MAX_PAGE_SIZE = 100;

    // most ids in one batch status lookup (DSP-5)
    static final int MAX_STATUS_IDS = 100;

    // the id breaks ties so pages never overlap
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final Sort NEAREST_DEADLINE_FIRST = Sort.by(Sort.Order.asc("deadline"), Sort.Order.asc("id"));

    // the only states a request with a dispute can be in
    private static final Set<OrderStates> DISPUTE_STATES = EnumSet.of(OrderStates.DISPUTED, OrderStates.RESOLVED);

    private final RequestsRepository requestsRepository;
    private final RequestDisputeRepository disputes;

    public RequestsQueryService(RequestsRepository requestsRepository, RequestDisputeRepository disputes) {
        this.requestsRepository = requestsRepository;
        this.disputes = disputes;
    }

    // Open errands for couriers (F2.1): CREATED only, so PENDING requests
    // (reward not yet reserved) and everyone's later states stay hidden;
    // and only those whose deadline is still ahead, since the expiry
    // sweep may not have run yet (the author's decision).
    public Page<Requests> openRequests(String state, RequestFilter filter, int page, int size) {
        EnumSet<OrderStates> states = parseStates(state);
        if (states != null && !states.equals(EnumSet.of(OrderStates.CREATED))) {
            throw new InvalidRequestException("state must be CREATED");
        }
        return find(allOf(status(OrderStates.CREATED), after("deadline", Instant.now())), filter, page, size);
    }

    // One request, if the caller is its requester, its courier or an
    // admin; anyone else gets the unknown-request 404.
    public Requests detail(Long requestId, Long callerId, boolean admin) {
        Requests request = requestsRepository.findById(requestId).orElseThrow();
        boolean party = request.getRequesterId().equals(callerId) || callerId.equals(request.getCourierId());
        if (!party && !admin) {
            throw new NoSuchElementException();
        }
        return request;
    }

    // The caller's requests on one side, optionally in one state.
    public Page<Requests> mine(Long callerId, String role, String state, RequestFilter filter, int page, int size) {
        Specification<Requests> side = parseRole(role) == Role.REQUESTER ? requester(callerId) : courier(callerId);
        return find(allOf(side, statusIn(parseStates(state))), filter, page, size);
    }

    // Any user's requests, as requester or courier (the admin view; the
    // route is limited to ADMIN / OWNER in SecurityConfig).
    public Page<Requests> involving(Long userId, String state, RequestFilter filter, int page, int size) {
        return find(allOf(RequestSpecifications.involving(userId), statusIn(parseStates(state))), filter, page, size);
    }

    // Every user's requests, optionally in some states, with some close
    // reasons and involving one user as requester or courier (the admin
    // list; ADMIN / OWNER in SecurityConfig). The dispute queue is
    // state=DISPUTED (#239).
    public Page<Requests> admin(String state, String closeReason, Long userId, RequestFilter filter, int page,
            int size) {
        return find(allOf(statusIn(parseStates(state)), closeReasonIn(parseCloseReasons(closeReason)),
                userId == null ? null : RequestSpecifications.involving(userId)), filter, page, size);
    }

    // The disputes of a page of requests, by request id, in one query
    // (none when no request on the page is disputed or resolved). A
    // request without a dispute has no entry.
    public Map<Long, RequestDispute> disputesOf(List<Requests> requests) {
        List<Long> ids = requests.stream()
                .filter(r -> DISPUTE_STATES.contains(r.getStatus()))
                .map(Requests::getId)
                .toList();
        Map<Long, RequestDispute> byRequest = new HashMap<>();
        if (!ids.isEmpty()) {
            disputes.findAllById(ids).forEach(d -> byRequest.put(d.getRequestId(), d));
        }
        return byRequest;
    }

    // One request's dispute, null if it has none.
    public RequestDispute disputeOf(Requests request) {
        return disputesOf(List.of(request)).get(request.getId());
    }

    // The current state of each id, in the order asked; an id no request
    // has maps to null, so the caller can tell it apart from a dropped
    // one. Ids are decimal strings, as JSON may carry them either way; a
    // repeated id is answered once.
    public Map<Long, OrderStates> statuses(List<String> ids) {
        if (ids == null) {
            throw new InvalidRequestException("ids is required");
        }
        if (ids.size() > MAX_STATUS_IDS) {
            throw new InvalidRequestException("ids must have at most " + MAX_STATUS_IDS + " entries");
        }
        Map<Long, OrderStates> statuses = new LinkedHashMap<>();
        for (String id : ids) {
            statuses.put(parseId(id), null);
        }
        for (Requests request : requestsRepository.findAllById(statuses.keySet())) {
            statuses.put(request.getId(), request.getStatus());
        }
        return statuses;
    }

    private static Long parseId(String id) {
        try {
            return Long.valueOf(id.trim());
        } catch (NullPointerException | NumberFormatException e) {
            throw new InvalidRequestException("ids must be request ids (numbers)");
        }
    }

    // The base query narrowed by the filter, in the filter's order.
    private Page<Requests> find(Specification<Requests> base, RequestFilter filter, int page, int size) {
        Specification<Requests> spec = allOf(base,
                supplier(filter.supplierId()),
                creditAtLeast(filter.minCredit()),
                creditAtMost(filter.maxCredit()),
                notBefore("deadline", instant(filter.deadlineAfter())),
                before("deadline", instant(filter.deadlineBefore())),
                notBefore("createdAt", instant(filter.createdFrom())),
                before("createdAt", instant(filter.createdTo())),
                notBefore("closedAt", instant(filter.closedFrom())),
                before("closedAt", instant(filter.closedTo())));
        checkFilter(filter);
        return requestsRepository.findAll(spec, pageOf(page, size, sortOf(filter.sort())));
    }

    // Ranges as credit-service's history: amounts at least 1, the lower
    // bound not above the upper, and a time range not empty.
    private static void checkFilter(RequestFilter f) {
        if (f.minCredit() != null && f.minCredit() < 1) {
            throw new InvalidRequestException("minCredit must be at least 1");
        }
        if (f.maxCredit() != null && f.maxCredit() < 1) {
            throw new InvalidRequestException("maxCredit must be at least 1");
        }
        if (f.minCredit() != null && f.maxCredit() != null && f.minCredit() > f.maxCredit()) {
            throw new InvalidRequestException("minCredit must not be above maxCredit");
        }
        checkRange("deadlineAfter", f.deadlineAfter(), "deadlineBefore", f.deadlineBefore());
        checkRange("createdFrom", f.createdFrom(), "createdTo", f.createdTo());
        checkRange("closedFrom", f.closedFrom(), "closedTo", f.closedTo());
    }

    // the upper bound is exclusive, so an equal pair would match nothing
    private static void checkRange(String fromName, OffsetDateTime from, String toName, OffsetDateTime to) {
        if (from != null && to != null && !from.toInstant().isBefore(to.toInstant())) {
            throw new InvalidRequestException(fromName + " must be before " + toName);
        }
    }

    private static Instant instant(OffsetDateTime time) {
        return time == null ? null : time.toInstant();
    }

    // null or blank: newest first
    private static Sort sortOf(String sort) {
        if (sort == null || sort.isBlank() || sort.trim().equals("createdAt")) {
            return NEWEST_FIRST;
        }
        if (sort.trim().equals("deadline")) {
            return NEAREST_DEADLINE_FIRST;
        }
        throw new InvalidRequestException("sort must be createdAt or deadline");
    }

    private static Pageable pageOf(int page, int size, Sort sort) {
        if (page < 0) {
            throw new InvalidRequestException("page must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, sort);
    }

    // One state or several, comma-separated ("CREATED,ACCEPTED"); null or
    // blank: any state. Every listed name must be a state.
    private static EnumSet<OrderStates> parseStates(String state) {
        return parseNames("state", state, OrderStates.class);
    }

    // Likewise for close reasons ("RESERVATION_TIMEOUT,DEADLINE_PASSED").
    private static EnumSet<CloseReason> parseCloseReasons(String closeReason) {
        return parseNames("closeReason", closeReason, CloseReason.class);
    }

    private static <E extends Enum<E>> EnumSet<E> parseNames(String parameter, String value, Class<E> type) {
        if (value == null || value.isBlank()) {
            return null;
        }
        EnumSet<E> constants = EnumSet.noneOf(type);
        for (String name : value.split(",")) {
            try {
                constants.add(Enum.valueOf(type, name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new InvalidRequestException(parameter + " must be one or more of " + Arrays.stream(
                        type.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", ")) + ", comma-separated");
            }
        }
        return constants;
    }

    private static Role parseRole(String role) {
        if (role == null || role.isBlank()) {
            throw new InvalidRequestException("role is required: requester or courier");
        }
        try {
            return Role.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("role must be requester or courier");
        }
    }
}
