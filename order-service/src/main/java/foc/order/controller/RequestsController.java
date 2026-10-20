/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-13.
 * Scope: empty controller skeleton — one unmapped stub method per
 * OrderEvents constant for the author to fill out. The tool chose no
 * routes, HTTP methods, parameters, request/response shapes or status
 * codes; those are left to the author.
 * Same day: body of create(), as specified by the author — builds a
 * Requests from the method's parameters (the entity's caller-supplied
 * columns) and stores it through RequestsService.create, which sets
 * status CREATED. Still unmapped.
 * Same day: remaining stubs, as specified by the author — each
 * delegates to the RequestsService method of the same name, taking that
 * method's parameters and returning its Requests result. Still unmapped.
 * Same day: HTTP mappings for the author's endpoints.txt (POST /create,
 * /accept, /collect, /drop, /cancel, /complete). JSON request bodies,
 * creditAmount and deadline added to /create, {"message": ...}
 * responses with 200 / 409 (illegal transition) / 404 (unknown id), and
 * no event publishing were each chosen by the author via neutral-options
 * Q&A (see ai/usage-log.md). expire() is left unmapped.
 * 2026-10-16: POST /arrive for the author's ARRIVED state, in the same
 * shape as /collect.
 * 2026-10-18: the caller comes from the bearer token (SecurityConfig):
 * /create makes them the requester and /accept the courier, so
 * requesterId and courierId left the bodies (the author's decision);
 * the other routes pass the caller to RequestsService's ownership
 * checks.
 * Same day: InvalidRequestException (missing or invalid /create fields)
 * answered 400 with its message (the author's decision).
 * Same day: the author's read endpoints - GET /requests (open errands),
 * /requests/{id}, /requests/mine and /users/{id}/requests - through
 * RequestsQueryService.
 * Same day: the lists take the author's filters and sort (RequestFilter,
 * bound from the query string); a value of the wrong type is a 400.
 * 2026-10-20: a step that loses a race (another step committed on the
 * same request after this one read it, caught by the @Version check) is
 * answered 409 "This request's state has changed" instead of a 500 (the
 * author's decision).
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order.controller;

import foc.order.InvalidRequestException;
import foc.order.RequestFilter;
import foc.order.RequestsQueryService;
import foc.order.RequestsService;
import foc.order.dto.PageResponse;
import foc.order.dto.RequestDetail;
import foc.order.dto.RequestSummary;
import foc.order.model.Requests;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.NoSuchElementException;

@RestController
public class RequestsController {

    // JSON request bodies, one per endpoint shape.
    // The requester and courier are never in a body: they are the caller.
    public record CreateBody(Long supplierId, String supplierName,
            String dropoffLocation, String item, Integer creditAmount, Instant deadline) {
    }

    public record RequestIdBody(Long requestId) {
    }

    // Body of every response, success or error.
    public record MessageResponse(String message) {
    }

    private final RequestsService requestsService;
    private final RequestsQueryService queries;

    public RequestsController(RequestsService requestsService, RequestsQueryService queries) {
        this.requestsService = requestsService;
        this.queries = queries;
    }

    // --- reads (newest first, page/size as in the supplier listing)

    // Open errands for couriers (F2.1): CREATED requests only.
    @GetMapping("/requests")
    public PageResponse<RequestSummary> openRequests(
            @RequestParam(required = false) String state,
            RequestFilter filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(queries.openRequests(state, filter, page, size), RequestSummary::of);
    }

    // The caller's requests, as requester or as courier.
    @GetMapping("/requests/mine")
    public PageResponse<RequestSummary> mine(
            Authentication caller,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String state,
            RequestFilter filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(queries.mine(callerId(caller), role, state, filter, page, size), RequestSummary::of);
    }

    // One request in full: its requester, its courier, or ADMIN / OWNER.
    @GetMapping("/requests/{id}")
    public RequestDetail detail(@PathVariable Long id, Authentication caller) {
        return RequestDetail.of(queries.detail(id, callerId(caller), isAdmin(caller)));
    }

    // Any user's requests, as requester or courier: ADMIN / OWNER only
    // (enforced in SecurityConfig).
    @GetMapping("/users/{userId}/requests")
    public PageResponse<RequestSummary> userRequests(
            @PathVariable Long userId,
            @RequestParam(required = false) String state,
            RequestFilter filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(queries.involving(userId, state, filter, page, size), RequestSummary::of);
    }

    // --- request steps

    // OrderEvents.CREATE: adds one row built from the body, with the
    // caller as its requester. The service sets status (PENDING) and
    // createdAt; id and version are generated; the courier and later
    // timestamps stay null.
    @PostMapping("/create")
    public MessageResponse create(@RequestBody CreateBody body, Authentication caller) {
        Requests request = new Requests();
        request.setRequesterId(callerId(caller));
        request.setSupplierId(body.supplierId());
        request.setSupplierName(body.supplierName());
        request.setDropoffLocation(body.dropoffLocation());
        request.setItem(body.item());
        request.setCreditAmount(body.creditAmount());
        request.setDeadline(body.deadline());
        return message(requestsService.create(request), "created");
    }

    // OrderEvents.ACCEPT: the caller becomes the courier.
    @PostMapping("/accept")
    public MessageResponse accept(@RequestBody RequestIdBody body, Authentication caller) {
        return message(requestsService.accept(body.requestId(), callerId(caller)), "accepted");
    }

    // OrderEvents.COLLECT: the assigned courier only
    @PostMapping("/collect")
    public MessageResponse collect(@RequestBody RequestIdBody body, Authentication caller) {
        return message(requestsService.collect(body.requestId(), callerId(caller)), "collected");
    }

    // OrderEvents.ARRIVE: the assigned courier only
    @PostMapping("/arrive")
    public MessageResponse arrive(@RequestBody RequestIdBody body, Authentication caller) {
        return message(requestsService.arrive(body.requestId(), callerId(caller)), "arrived");
    }

    // OrderEvents.CANCEL: the requester only
    @PostMapping("/cancel")
    public MessageResponse cancel(@RequestBody RequestIdBody body, Authentication caller) {
        return message(requestsService.cancel(body.requestId(), callerId(caller)), "cancelled");
    }

    // OrderEvents.DROP: the assigned courier only
    @PostMapping("/drop")
    public MessageResponse drop(@RequestBody RequestIdBody body, Authentication caller) {
        return message(requestsService.drop(body.requestId(), callerId(caller)), "dropped");
    }

    // OrderEvents.EXPIRE -> requestsService.expire(requestId). Not in
    // endpoints.txt, so not reachable over HTTP.
    public Requests expire(Long requestId) {
        return requestsService.expire(requestId);
    }

    // OrderEvents.COMPLETE: the requester only
    @PostMapping("/complete")
    public MessageResponse complete(@RequestBody RequestIdBody body, Authentication caller) {
        return message(requestsService.complete(body.requestId(), callerId(caller)), "completed");
    }

    // A new request with missing fields or invalid values; the message
    // lists every problem.
    @ExceptionHandler(InvalidRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public MessageResponse handleInvalidRequest(InvalidRequestException e) {
        return new MessageResponse(e.getMessage());
    }

    // A list filter of the wrong type (e.g. minCredit=abc, or an instant
    // without an offset), named in the message.
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public MessageResponse handleBadFilter(BindException e) {
        String field = e.getFieldError() == null ? "a query parameter" : e.getFieldError().getField();
        return new MessageResponse("Invalid value for " + field);
    }

    // page, size or a path id of the wrong type
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public MessageResponse handleBadParameter(MethodArgumentTypeMismatchException e) {
        return new MessageResponse("Invalid value for " + e.getName());
    }

    // The event is not allowed from the request's current state.
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public MessageResponse handleIllegalTransition(IllegalStateException e) {
        return new MessageResponse(e.getMessage());
    }

    // Another step changed the request between this one's read and its
    // write, so the @Version check refused the write.
    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public MessageResponse handleConcurrentChange(OptimisticLockingFailureException e) {
        return new MessageResponse("This request's state has changed");
    }

    // No request has the given id, or the step is not the caller's (the
    // same answer, so the request's existence is not revealed).
    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public MessageResponse handleUnknownRequest(NoSuchElementException e) {
        return new MessageResponse("Request not found");
    }

    private static boolean isAdmin(Authentication caller) {
        return caller.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_OWNER"));
    }

    // The caller's user id: the verified token's subject.
    private static Long callerId(Authentication caller) {
        return Long.valueOf(caller.getName());
    }

    private static MessageResponse message(Requests request, String outcome) {
        return new MessageResponse("Request " + request.getId() + " " + outcome);
    }

}
