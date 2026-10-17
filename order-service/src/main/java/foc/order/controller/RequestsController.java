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
 * Reviewed by: Ko-Khan (via pull request).
 */
package foc.order.controller;

import foc.order.RequestsService;
import foc.order.model.Requests;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.NoSuchElementException;

@RestController
public class RequestsController {

    // JSON request bodies, one per endpoint shape.
    public record CreateBody(Long requesterId, Long supplierId, String supplierName,
            String dropoffLocation, String item, Integer creditAmount, Instant deadline) {
    }

    public record AcceptBody(Long requestId, Long courierId) {
    }

    public record RequestIdBody(Long requestId) {
    }

    // Body of every response, success or error.
    public record MessageResponse(String message) {
    }

    private final RequestsService requestsService;

    public RequestsController(RequestsService requestsService) {
        this.requestsService = requestsService;
    }

    // OrderEvents.CREATE: adds one row built from the body. The service
    // sets status (CREATED) and createdAt; id and version are generated;
    // the courier and later timestamps stay null.
    @PostMapping("/create")
    public MessageResponse create(@RequestBody CreateBody body) {
        Requests request = new Requests();
        request.setRequesterId(body.requesterId());
        request.setSupplierId(body.supplierId());
        request.setSupplierName(body.supplierName());
        request.setDropoffLocation(body.dropoffLocation());
        request.setItem(body.item());
        request.setCreditAmount(body.creditAmount());
        request.setDeadline(body.deadline());
        return message(requestsService.create(request), "created");
    }

    // OrderEvents.ACCEPT -> requestsService.accept(requestId, courierId)
    @PostMapping("/accept")
    public MessageResponse accept(@RequestBody AcceptBody body) {
        return message(requestsService.accept(body.requestId(), body.courierId()), "accepted");
    }

    // OrderEvents.COLLECT -> requestsService.collect(requestId)
    @PostMapping("/collect")
    public MessageResponse collect(@RequestBody RequestIdBody body) {
        return message(requestsService.collect(body.requestId()), "collected");
    }

    // OrderEvents.ARRIVE -> requestsService.arrive(requestId)
    @PostMapping("/arrive")
    public MessageResponse arrive(@RequestBody RequestIdBody body) {
        return message(requestsService.arrive(body.requestId()), "arrived");
    }

    // OrderEvents.CANCEL -> requestsService.cancel(requestId)
    @PostMapping("/cancel")
    public MessageResponse cancel(@RequestBody RequestIdBody body) {
        return message(requestsService.cancel(body.requestId()), "cancelled");
    }

    // OrderEvents.DROP -> requestsService.drop(requestId)
    @PostMapping("/drop")
    public MessageResponse drop(@RequestBody RequestIdBody body) {
        return message(requestsService.drop(body.requestId()), "dropped");
    }

    // OrderEvents.EXPIRE -> requestsService.expire(requestId). Not in
    // endpoints.txt, so not reachable over HTTP.
    public Requests expire(Long requestId) {
        return requestsService.expire(requestId);
    }

    // OrderEvents.COMPLETE -> requestsService.complete(requestId)
    @PostMapping("/complete")
    public MessageResponse complete(@RequestBody RequestIdBody body) {
        return message(requestsService.complete(body.requestId()), "completed");
    }

    // The event is not allowed from the request's current state.
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public MessageResponse handleIllegalTransition(IllegalStateException e) {
        return new MessageResponse(e.getMessage());
    }

    // No request has the given id.
    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public MessageResponse handleUnknownRequest(NoSuchElementException e) {
        return new MessageResponse("Request not found");
    }

    private static MessageResponse message(Requests request, String outcome) {
        return new MessageResponse("Request " + request.getId() + " " + outcome);
    }

}
