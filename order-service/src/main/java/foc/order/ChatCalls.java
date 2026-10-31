/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: which order transitions owe chat-service a call (C3-C5): an
 * accept opens the chat; leaving ACCEPTED, COLLECTED or ARRIVED for any
 * other state closes it (drop, expire, dispute, escalate, complete).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.order;

import foc.order.model.ChatCall;
import foc.order.model.Requests;
import foc.order.repository.ChatCallRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Writes the call in the caller's transaction (RequestsService's), so it
 * is owed exactly when the state change commits; ChatCallDispatcher sends
 * it.
 */
@Component
public class ChatCalls {

    // the states a request's chat is open in (C3)
    static final Set<OrderStates> CHAT_OPEN_STATES =
            EnumSet.of(OrderStates.ACCEPTED, OrderStates.COLLECTED, OrderStates.ARRIVED);

    private final ChatCallRepository calls;

    public ChatCalls(ChatCallRepository calls) {
        this.calls = calls;
    }

    /** The courier was just assigned: open their chat with the requester. */
    public void accepted(Requests request) {
        calls.save(ChatCall.open(request.getId(), request.getRequesterId(), request.getCourierId(), Instant.now()));
    }

    /** Called on every transition; closes the chat when the request leaves the open states. */
    public void transitioned(Requests request, OrderStates from, OrderStates to) {
        if (CHAT_OPEN_STATES.contains(from) && !CHAT_OPEN_STATES.contains(to)) {
            calls.save(ChatCall.close(request.getId(), Instant.now()));
        }
    }
}
