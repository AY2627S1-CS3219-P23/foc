/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: the REST surface over MockMvc: bearer tokens required, the
 * party and admin views, the image endpoint, and order-service's
 * internal calls behind the internal token (C12).
 * 2026-10-31: the unread count in GET /chats and POST .../read (C19).
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import foc.chat.PostgresTestContainer;
import foc.chat.TestTokens;
import foc.chat.dto.ChatView;
import foc.chat.dto.MessageView;
import foc.chat.dto.SendMessage;
import foc.chat.security.Caller;
import foc.chat.service.ChatPublisher;
import foc.chat.service.ChatService;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest extends PostgresTestContainer {

    private static final AtomicLong REQUEST_IDS = new AtomicLong(2_000_000_000L + System.nanoTime() % 1_000_000L);
    private static final long REQUESTER = 11;
    private static final long COURIER = 22;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChatService chatService;

    @MockitoBean
    private ChatPublisher publisher;

    @Test
    void everyChatRouteNeedsABearerToken() throws Exception {
        mockMvc.perform(get("/chats").param("requestId", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aPartyGetsTheOpenChatAndItsMessages() throws Exception {
        long request = REQUEST_IDS.incrementAndGet();
        ChatView chat = chatService.open(request, REQUESTER, COURIER);
        chatService.send(new Caller(COURIER, false), chat.id(), new SendMessage("here", null));

        mockMvc.perform(get("/chats").param("requestId", Long.toString(request))
                        .header("Authorization", "Bearer " + TestTokens.user(REQUESTER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(chat.id().toString()))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
        mockMvc.perform(get("/chats/{id}/messages", chat.id())
                        .header("Authorization", "Bearer " + TestTokens.user(REQUESTER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].text").value("here"))
                .andExpect(jsonPath("$[0].senderId").value(COURIER))
                .andExpect(jsonPath("$[0].hasImage").value(false));
    }

    @Test
    void markingReadResetsTheUnreadCount() throws Exception {
        long request = REQUEST_IDS.incrementAndGet();
        ChatView chat = chatService.open(request, REQUESTER, COURIER);
        chatService.send(new Caller(COURIER, false), chat.id(), new SendMessage("here", null));

        mockMvc.perform(get("/chats").param("requestId", Long.toString(request))
                        .header("Authorization", "Bearer " + TestTokens.user(REQUESTER)))
                .andExpect(jsonPath("$[0].unreadCount").value(1));
        mockMvc.perform(post("/chats/{id}/read", chat.id())
                        .header("Authorization", "Bearer " + TestTokens.user(REQUESTER)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/chats").param("requestId", Long.toString(request))
                        .header("Authorization", "Bearer " + TestTokens.user(REQUESTER)))
                .andExpect(jsonPath("$[0].unreadCount").value(0));
    }

    @Test
    void aStrangerIsToldTheChatDoesNotExist() throws Exception {
        ChatView chat = chatService.open(REQUEST_IDS.incrementAndGet(), REQUESTER, COURIER);

        mockMvc.perform(get("/chats/{id}/messages", chat.id())
                        .header("Authorization", "Bearer " + TestTokens.user(99)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(get("/chats/not-a-uuid/messages")
                        .header("Authorization", "Bearer " + TestTokens.user(99)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anAdminReadsAClosedChat() throws Exception {
        long request = REQUEST_IDS.incrementAndGet();
        ChatView chat = chatService.open(request, REQUESTER, COURIER);
        chatService.close(request);

        mockMvc.perform(get("/chats").param("requestId", Long.toString(request))
                        .header("Authorization", "Bearer " + TestTokens.admin(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CLOSED"));
        mockMvc.perform(get("/chats/{id}/messages", chat.id())
                        .header("Authorization", "Bearer " + TestTokens.token(1, "OWNER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/chats/{id}/messages", chat.id())
                        .header("Authorization", "Bearer " + TestTokens.user(REQUESTER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anImageIsServedWithItsContentType() throws Exception {
        ChatView chat = chatService.open(REQUEST_IDS.incrementAndGet(), REQUESTER, COURIER);
        MessageView sent = chatService.send(new Caller(REQUESTER, false), chat.id(),
                new SendMessage(null, new SendMessage.Image("image/png", Base64.getEncoder().encodeToString(PNG))));

        mockMvc.perform(get("/chats/{c}/messages/{m}/image", chat.id(), sent.id())
                        .header("Authorization", "Bearer " + TestTokens.user(COURIER)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(content().bytes(PNG));
    }

    // ---- internal calls (C11, C12) ----------------------------------

    @Test
    void theInternalCallsNeedTheInternalToken() throws Exception {
        String body = "{\"requestId\":5,\"requesterId\":11,\"courierId\":22}";

        mockMvc.perform(post("/internal/chats/open").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/internal/chats/open").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-Internal-Token", "wrong"))
                .andExpect(status().isUnauthorized());
        // a user's bearer token is not the internal token
        mockMvc.perform(post("/internal/chats/close").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":5}")
                        .header("Authorization", "Bearer " + TestTokens.admin(1)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void orderServiceOpensAndClosesAChat() throws Exception {
        long request = REQUEST_IDS.incrementAndGet();

        mockMvc.perform(post("/internal/chats/open").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":" + request + ",\"requesterId\":11,\"courierId\":22}")
                        .header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/chats").param("requestId", Long.toString(request))
                        .header("Authorization", "Bearer " + TestTokens.user(COURIER)))
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(post("/internal/chats/close").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":" + request + "}")
                        .header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/chats").param("requestId", Long.toString(request))
                        .header("Authorization", "Bearer " + TestTokens.user(COURIER)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anOpenCallWithoutItsIdsIsRefused() throws Exception {
        mockMvc.perform(post("/internal/chats/open").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":5}")
                        .header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isBadRequest());
    }
}
