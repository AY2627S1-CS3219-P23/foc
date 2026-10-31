/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: the chat rules against real Postgres (Flyway schema): open and
 * close (C3-C5, C11), who may read (C6, C13), who may send and the
 * message limits (C7), the pushes, and the retention purge (C10).
 * 2026-10-31: unread counts, mark-read and the receiver's push
 * (C16-C19).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import foc.chat.PostgresTestContainer;
import foc.chat.dto.ChatView;
import foc.chat.dto.MessageView;
import foc.chat.dto.SendMessage;
import foc.chat.dto.UnreadFrame;
import foc.chat.entity.ChatMessage;
import foc.chat.entity.ChatStatus;
import foc.chat.repository.ChatMessageRepository;
import foc.chat.repository.ChatRepository;
import foc.chat.security.Caller;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class ChatServiceTests extends PostgresTestContainer {

    // one database for every test class: each test uses its own request
    private static final AtomicLong REQUEST_IDS = new AtomicLong(System.nanoTime() % 1_000_000_000L);

    private static final long REQUESTER = 11;
    private static final long COURIER = 22;
    private static final long NEXT_COURIER = 33;
    private static final long STRANGER = 99;
    private static final Caller REQUESTER_CALLER = new Caller(REQUESTER, false);
    private static final Caller COURIER_CALLER = new Caller(COURIER, false);
    private static final Caller STRANGER_CALLER = new Caller(STRANGER, false);
    private static final Caller ADMIN = new Caller(1, true);

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatRetentionPurge purge;

    @Autowired
    private ChatRepository chats;

    @Autowired
    private ChatMessageRepository messages;

    @MockitoBean
    private ChatPublisher publisher;

    private static long newRequest() {
        return REQUEST_IDS.incrementAndGet();
    }

    private static SendMessage text(String text) {
        return new SendMessage(text, null);
    }

    private static SendMessage image(String contentType, byte[] bytes) {
        return new SendMessage(null, new SendMessage.Image(contentType, Base64.getEncoder().encodeToString(bytes)));
    }

    // ---- open and close ---------------------------------------------

    @Test
    void openingTwiceForTheSameCourierKeepsOneChat() {
        long request = newRequest();

        ChatView first = chatService.open(request, REQUESTER, COURIER);
        ChatView again = chatService.open(request, REQUESTER, COURIER);

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(chats.findByRequestIdOrderByOpenedAtDesc(request)).hasSize(1);
    }

    @Test
    void aNewCourierGetsANewChatAndTheOldOneCloses() {
        long request = newRequest();
        ChatView first = chatService.open(request, REQUESTER, COURIER);

        ChatView second = chatService.open(request, REQUESTER, NEXT_COURIER);

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(chats.findById(first.id()).orElseThrow().getStatus()).isEqualTo(ChatStatus.CLOSED);
        verify(publisher).chatClosed(first.id());
        // the dropped courier can't read the old chat any more; the new one
        // doesn't see it either
        assertThat(chatService.canRead(COURIER_CALLER, first.id())).isFalse();
        assertThat(chatService.chatsOf(new Caller(NEXT_COURIER, false), request))
                .extracting(ChatView::id).containsExactly(second.id());
    }

    @Test
    void closeEndsTheOpenChatAndIsIdempotent() {
        long request = newRequest();
        ChatView chat = chatService.open(request, REQUESTER, COURIER);

        chatService.close(request);
        chatService.close(request);

        assertThat(chats.findById(chat.id()).orElseThrow().getClosedAt()).isNotNull();
        verify(publisher).chatClosed(chat.id());
    }

    @Test
    void closingARequestWithNoChatDoesNothing() {
        chatService.close(newRequest());

        verify(publisher, never()).chatClosed(any());
    }

    // ---- reads ------------------------------------------------------

    @Test
    void partiesSeeTheOpenChatAndStrangersSeeNothing() {
        long request = newRequest();
        ChatView chat = chatService.open(request, REQUESTER, COURIER);

        assertThat(chatService.chatsOf(REQUESTER_CALLER, request)).extracting(ChatView::id).containsExactly(chat.id());
        assertThat(chatService.chatsOf(COURIER_CALLER, request)).extracting(ChatView::id).containsExactly(chat.id());
        assertThat(chatService.chatsOf(STRANGER_CALLER, request)).isEmpty();
        assertThatThrownBy(() -> chatService.history(STRANGER_CALLER, chat.id(), null, null))
                .isInstanceOf(ChatNotFoundException.class);
    }

    @Test
    void onceClosedOnlyAdminsCanReadTheHistory() {
        long request = newRequest();
        ChatView chat = chatService.open(request, REQUESTER, COURIER);
        chatService.send(REQUESTER_CALLER, chat.id(), text("on my way?"));
        chatService.close(request);

        assertThat(chatService.chatsOf(REQUESTER_CALLER, request)).isEmpty();
        assertThatThrownBy(() -> chatService.history(COURIER_CALLER, chat.id(), null, null))
                .isInstanceOf(ChatNotFoundException.class);
        assertThat(chatService.chatsOf(ADMIN, request)).extracting(ChatView::id).containsExactly(chat.id());
        assertThat(chatService.history(ADMIN, chat.id(), null, null))
                .extracting(MessageView::text).containsExactly("on my way?");
    }

    @Test
    void historyIsNewestFirstAndPagesBackwards() throws InterruptedException {
        ChatView chat = chatService.open(newRequest(), REQUESTER, COURIER);
        for (String text : List.of("one", "two", "three")) {
            chatService.send(REQUESTER_CALLER, chat.id(), text(text));
            // distinct sentAt values
            Thread.sleep(2);
        }

        List<MessageView> newest = chatService.history(COURIER_CALLER, chat.id(), null, 2);
        List<MessageView> older = chatService.history(COURIER_CALLER, chat.id(), newest.get(1).sentAt(), 2);

        assertThat(newest).extracting(MessageView::text).containsExactly("three", "two");
        assertThat(older).extracting(MessageView::text).containsExactly("one");
    }

    // ---- sending ----------------------------------------------------

    @Test
    void aPartysMessageIsStoredAndPushed() {
        ChatView chat = chatService.open(newRequest(), REQUESTER, COURIER);

        MessageView sent = chatService.send(COURIER_CALLER, chat.id(), text("  at the counter  "));

        assertThat(sent.text()).isEqualTo("at the counter");
        assertThat(sent.senderId()).isEqualTo(COURIER);
        assertThat(sent.hasImage()).isFalse();
        verify(publisher).messageSent(sent);
    }

    @Test
    void anImageIsStoredWithItsTypeAndServedBack() {
        ChatView chat = chatService.open(newRequest(), REQUESTER, COURIER);

        MessageView sent = chatService.send(REQUESTER_CALLER, chat.id(), image("image/png", PNG));

        assertThat(sent.hasImage()).isTrue();
        ChatMessage stored = chatService.image(COURIER_CALLER, chat.id(), sent.id());
        assertThat(stored.getImage()).isEqualTo(PNG);
        assertThat(stored.getImageContentType()).isEqualTo("image/png");
    }

    @Test
    void strangersAndAdminsCannotSend() {
        ChatView chat = chatService.open(newRequest(), REQUESTER, COURIER);

        assertThatThrownBy(() -> chatService.send(STRANGER_CALLER, chat.id(), text("hi")))
                .isInstanceOf(ChatNotFoundException.class);
        assertThatThrownBy(() -> chatService.send(ADMIN, chat.id(), text("hi")))
                .isInstanceOf(ChatNotFoundException.class);
    }

    @Test
    void nobodyCanSendToAClosedChat() {
        long request = newRequest();
        ChatView chat = chatService.open(request, REQUESTER, COURIER);
        chatService.close(request);

        assertThatThrownBy(() -> chatService.send(REQUESTER_CALLER, chat.id(), text("hello?")))
                .isInstanceOf(ChatClosedException.class);
    }

    @Test
    void messagesOutsideTheLimitsAreRefused() {
        ChatView chat = chatService.open(newRequest(), REQUESTER, COURIER);
        UUID id = chat.id();
        byte[] tooLarge = Arrays.copyOf(PNG, 2 * 1024 * 1024 + 1);

        assertThatThrownBy(() -> chatService.send(REQUESTER_CALLER, id, text("   ")))
                .isInstanceOf(InvalidMessageException.class);
        assertThatThrownBy(() -> chatService.send(REQUESTER_CALLER, id, text("x".repeat(501))))
                .isInstanceOf(InvalidMessageException.class).hasMessageContaining("500");
        assertThatThrownBy(() -> chatService.send(REQUESTER_CALLER, id, image("image/gif", PNG)))
                .isInstanceOf(InvalidMessageException.class);
        assertThatThrownBy(() -> chatService.send(REQUESTER_CALLER, id, image("image/jpeg", PNG)))
                .isInstanceOf(InvalidMessageException.class).hasMessageContaining("JPEG");
        assertThatThrownBy(() -> chatService.send(REQUESTER_CALLER, id, image("image/png", tooLarge)))
                .isInstanceOf(InvalidMessageException.class).hasMessageContaining("2 MB");
        assertThatThrownBy(() -> chatService.send(REQUESTER_CALLER, id,
                new SendMessage(null, new SendMessage.Image("image/png", "not base64!"))))
                .isInstanceOf(InvalidMessageException.class);
        assertThat(chatService.send(REQUESTER_CALLER, id, text("x".repeat(500))).text()).hasSize(500);
    }

    @Test
    void anImageOfExactlyTwoMegabytesIsAccepted() {
        ChatView chat = chatService.open(newRequest(), REQUESTER, COURIER);

        MessageView sent = chatService.send(REQUESTER_CALLER, chat.id(),
                image("image/png", Arrays.copyOf(PNG, 2 * 1024 * 1024)));

        assertThat(sent.hasImage()).isTrue();
    }

    // ---- unread (C16-C19) -------------------------------------------

    @Test
    void theReceiverIsPushedTheirUnreadCountAndOnlyTheFirstIsMarked() {
        long request = newRequest();
        ChatView chat = chatService.open(request, REQUESTER, COURIER);

        chatService.send(COURIER_CALLER, chat.id(), text("x".repeat(100)));
        chatService.send(COURIER_CALLER, chat.id(), image("image/png", PNG));

        ArgumentCaptor<UnreadFrame> frames = ArgumentCaptor.forClass(UnreadFrame.class);
        verify(publisher, times(2)).unread(eq(REQUESTER), frames.capture());
        UnreadFrame first = frames.getAllValues().get(0);
        UnreadFrame second = frames.getAllValues().get(1);
        assertThat(first.first()).isTrue();
        assertThat(first.unreadCount()).isEqualTo(1);
        assertThat(first.requestId()).isEqualTo(request);
        assertThat(first.senderId()).isEqualTo(COURIER);
        // the first 80 characters, the last one an ellipsis
        assertThat(first.preview()).hasSize(80).endsWith("…");
        assertThat(second.first()).isFalse();
        assertThat(second.unreadCount()).isEqualTo(2);
        assertThat(second.preview()).isNull();
        assertThat(second.hasImage()).isTrue();
    }

    @Test
    void openingTheChatResetsTheUnreadCount() {
        long request = newRequest();
        ChatView chat = chatService.open(request, REQUESTER, COURIER);
        chatService.send(COURIER_CALLER, chat.id(), text("one"));
        chatService.send(COURIER_CALLER, chat.id(), text("two"));
        // the requester's own messages never count for them
        chatService.send(REQUESTER_CALLER, chat.id(), text("mine"));

        assertThat(chatService.chatsOf(REQUESTER_CALLER, request).get(0).unreadCount()).isEqualTo(2);
        assertThat(chatService.chatsOf(COURIER_CALLER, request).get(0).unreadCount()).isEqualTo(1);

        chatService.markRead(REQUESTER_CALLER, chat.id());
        assertThat(chatService.chatsOf(REQUESTER_CALLER, request).get(0).unreadCount()).isZero();

        // the next message makes the chat unread again, so it is a first again
        chatService.send(COURIER_CALLER, chat.id(), text("three"));
        ArgumentCaptor<UnreadFrame> frames = ArgumentCaptor.forClass(UnreadFrame.class);
        verify(publisher, times(3)).unread(eq(REQUESTER), frames.capture());
        assertThat(frames.getValue().first()).isTrue();
        assertThat(frames.getValue().unreadCount()).isEqualTo(1);
    }

    @Test
    void strangersCannotMarkAChatRead() {
        ChatView chat = chatService.open(newRequest(), REQUESTER, COURIER);

        assertThatThrownBy(() -> chatService.markRead(STRANGER_CALLER, chat.id()))
                .isInstanceOf(ChatNotFoundException.class);
        // an admin may, and it changes nothing
        chatService.markRead(ADMIN, chat.id());
        verify(publisher, never()).unread(anyLong(), any());
    }

    // ---- retention --------------------------------------------------

    @Test
    void thePurgeDropsOldMessagesAndTheClosedChatsLeftEmpty() {
        long request = newRequest();
        ChatView chat = chatService.open(request, REQUESTER, COURIER);
        Instant old = Instant.now().minus(31, ChronoUnit.DAYS);
        messages.save(new ChatMessage(chat.id(), REQUESTER, "old", null, null, old));
        MessageView recent = chatService.send(REQUESTER_CALLER, chat.id(), text("recent"));

        purge.purge();

        assertThat(chatService.history(ADMIN, chat.id(), null, null))
                .extracting(MessageView::id).containsExactly(recent.id());
        // still holds a recent message, so it stays
        assertThat(chats.findById(chat.id())).isPresent();
    }
}
