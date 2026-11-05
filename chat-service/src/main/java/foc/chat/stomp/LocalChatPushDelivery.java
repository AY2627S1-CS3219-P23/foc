/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-11-05, issue #48 (DP10).
 * Scope: cross-replica STOMP fanout over AMQP (team decision, Leong Wei
 * Zhi via options Q&A): a push from any replica goes to this replica's
 * simple broker.
 * Author review: Leong Wei Zhi, pending pull request review.
 */
package foc.chat.stomp;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

// only the sessions connected to this replica get it; the others'
// replicas deliver their own copies
@Component
class LocalChatPushDelivery {

    private final SimpMessagingTemplate template;

    LocalChatPushDelivery(SimpMessagingTemplate template) {
        this.template = template;
    }

    void deliver(ChatPush push) {
        switch (push.kind()) {
            case TOPIC -> template.convertAndSend(push.destination(), push.payload());
            case USER -> template.convertAndSendToUser(push.user(), push.destination(), push.payload());
        }
    }
}
