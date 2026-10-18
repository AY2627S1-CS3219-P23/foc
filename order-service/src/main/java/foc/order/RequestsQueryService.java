/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-18.
 * Scope: the read side of the author's endpoint design (GET /requests,
 * /requests/{id}, /requests/mine, /users/{id}/requests): open errands
 * are CREATED only, a request's detail is its requester's, its courier's
 * or an admin's, lists are newest first and paged (page >= 0, size
 * 1-100, as credit-service's history). Bad parameters are an
 * InvalidRequestException (400). These choices are the author's.
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order;

import foc.order.model.Requests;
import foc.order.repository.RequestsRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RequestsQueryService {

    /** Which side of the request the caller is on, for /requests/mine. */
    public enum Role {
        REQUESTER, COURIER
    }

    static final int MAX_PAGE_SIZE = 100;

    // newest first; the id breaks ties so pages never overlap
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final RequestsRepository requestsRepository;

    public RequestsQueryService(RequestsRepository requestsRepository) {
        this.requestsRepository = requestsRepository;
    }

    // Open errands for couriers (F2.1): CREATED only, so PENDING requests
    // (reward not yet reserved) and everyone's later states stay hidden.
    public Page<Requests> openRequests(String state, int page, int size) {
        OrderStates status = parseState(state);
        if (status != null && status != OrderStates.CREATED) {
            throw new InvalidRequestException("state must be CREATED");
        }
        return requestsRepository.findByStatus(OrderStates.CREATED, pageOf(page, size));
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
    public Page<Requests> mine(Long callerId, String role, String state, int page, int size) {
        Role side = parseRole(role);
        OrderStates status = parseState(state);
        Pageable pageable = pageOf(page, size);
        return side == Role.REQUESTER
                ? requestsRepository.findAsRequester(callerId, status, pageable)
                : requestsRepository.findAsCourier(callerId, status, pageable);
    }

    // Any user's requests, as requester or courier (the admin view; the
    // route is limited to ADMIN / OWNER in SecurityConfig).
    public Page<Requests> involving(Long userId, String state, int page, int size) {
        return requestsRepository.findInvolving(userId, parseState(state), pageOf(page, size));
    }

    private static Pageable pageOf(int page, int size) {
        if (page < 0) {
            throw new InvalidRequestException("page must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, NEWEST_FIRST);
    }

    // null or blank: any state
    private static OrderStates parseState(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        try {
            return OrderStates.valueOf(state.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("state must be one of " + Arrays.stream(OrderStates.values())
                    .map(Enum::name).collect(Collectors.joining(", ")));
        }
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
