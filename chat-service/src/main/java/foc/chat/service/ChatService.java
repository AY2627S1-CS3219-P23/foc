/*
 * AI-assisted (CS3219 AI Usage Policy disclosure):
 * Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
 * Scope: the chat rules: open and close on order-service's calls (C3-C5,
 * C11), who may read (parties while open, admins always: C6, C13), who
 * may send (parties while open), and the message limits (C7). Pushes go
 * out after the transaction commits. The image signature check is the
 * tool's addition, listed in the doc.
 * 2026-10-31: unread counts and the receiver's push (C16-C19): each
 * party's unread count is the other party's messages since they last
 * opened the chat; a send pushes the receiver their new count, marking
 * the message that made the chat unread.
 * Author review: Ko-Khan, pending pull request review.
 */
package foc.chat.service;

import foc.chat.dto.ChatView;
import foc.chat.dto.MessageView;
import foc.chat.dto.SendMessage;
import foc.chat.dto.UnreadFrame;
import foc.chat.entity.Chat;
import foc.chat.entity.ChatMessage;
import foc.chat.entity.ChatStatus;
import foc.chat.repository.ChatMessageRepository;
import foc.chat.repository.ChatRepository;
import foc.chat.security.Caller;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    // history page sizes
    static final int DEFAULT_PAGE_SIZE = 50;
    static final int MAX_PAGE_SIZE = 100;
    // the text preview the receiver's notification shows (C18)
    static final int PREVIEW_LENGTH = 80;

    // the image types C7 allows, with the bytes their files start with, so
    // a declared type has to match the content
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};
    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "image/jpeg", "JPEG", "image/png", "PNG", "image/webp", "WebP");

    private final ChatRepository chats;
    private final ChatMessageRepository messages;
    private final ChatPublisher publisher;
    private final int maxTextLength;
    private final int maxImageBytes;

    public ChatService(ChatRepository chats, ChatMessageRepository messages, ChatPublisher publisher,
            @Value("${chat.message.max-text-length}") int maxTextLength,
            @Value("${chat.message.max-image-bytes}") int maxImageBytes) {
        this.chats = chats;
        this.messages = messages;
        this.publisher = publisher;
        this.maxTextLength = maxTextLength;
        this.maxImageBytes = maxImageBytes;
    }

    // ---- order-service's calls (C11) --------------------------------

    /**
     * Opens the chat between the request's requester and this courier.
     * Idempotent, since order-service retries: if that courier's chat is
     * already open nothing changes. Any other open chat of the request (a
     * close that never arrived) is closed first, so the new courier never
     * sees the previous one's messages (C4).
     */
    @Transactional
    public ChatView open(long requestId, long requesterId, long courierId) {
        Optional<Chat> current = chats.findByRequestIdAndStatus(requestId, ChatStatus.OPEN);
        if (current.isPresent()) {
            Chat chat = current.get();
            if (chat.getCourierId() == courierId && chat.getRequesterId() == requesterId) {
                return ChatView.of(chat);
            }
            closeChat(chat);
            // the close must reach the database before the insert, or the
            // partial unique index sees two open chats
            chats.flush();
        }
        Chat opened = chats.save(new Chat(requestId, requesterId, courierId, Instant.now()));
        log.info("chat open request={} chat={} courier={}", requestId, opened.getId(), courierId);
        return ChatView.of(opened);
    }

    /** Closes the request's open chat, if it has one. Idempotent. */
    @Transactional
    public void close(long requestId) {
        chats.findByRequestIdAndStatus(requestId, ChatStatus.OPEN).ifPresent(this::closeChat);
    }

    private void closeChat(Chat chat) {
        chat.close(Instant.now());
        UUID chatId = chat.getId();
        log.info("chat close request={} chat={}", chat.getRequestId(), chatId);
        afterCommit(() -> publisher.chatClosed(chatId));
    }

    // ---- reads (C6, C13) --------------------------------------------

    /**
     * The chats of a request the caller may see: for an admin every chat
     * it has had, newest first; for anyone else the open chat if they are
     * one of its parties, else none.
     */
    @Transactional(readOnly = true)
    public List<ChatView> chatsOf(Caller caller, long requestId) {
        if (caller.admin()) {
            return chats.findByRequestIdOrderByOpenedAtDesc(requestId).stream().map(ChatView::of).toList();
        }
        return chats.findByRequestIdAndStatus(requestId, ChatStatus.OPEN)
                .filter(chat -> chat.isParty(caller.userId()))
                .map(chat -> ChatView.of(chat, unreadCount(chat, caller.userId())))
                .stream().toList();
    }

    /**
     * The caller has opened the chat: their unread count starts over. For
     * an admin, who has no unread count, this only checks they may read it.
     */
    @Transactional
    public void markRead(Caller caller, UUID chatId) {
        Chat chat = readable(caller, chatId);
        if (chat.isParty(caller.userId())) {
            chat.markRead(caller.userId(), Instant.now());
        }
    }

    // the other party's messages since the receiver last opened the chat
    private long unreadCount(Chat chat, long receiverId) {
        Instant lastRead = chat.lastReadAt(receiverId);
        return lastRead == null
                ? messages.countByChatIdAndSenderIdNot(chat.getId(), receiverId)
                : messages.countByChatIdAndSenderIdNotAndSentAtAfter(chat.getId(), receiverId, lastRead);
    }

    /** Newest first, sent before {@code before}; {@code size} is clamped to 1..100. */
    @Transactional(readOnly = true)
    public List<MessageView> history(Caller caller, UUID chatId, Instant before, Integer size) {
        readable(caller, chatId);
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.clamp(size, 1, MAX_PAGE_SIZE);
        return messages.findPage(chatId, before == null ? Instant.now() : before, Limit.of(pageSize));
    }

    /** The message with its image bytes; not found if it has no image. */
    @Transactional(readOnly = true)
    public ChatMessage image(Caller caller, UUID chatId, UUID messageId) {
        readable(caller, chatId);
        return messages.findByIdAndChatId(messageId, chatId)
                .filter(ChatMessage::hasImage)
                .orElseThrow(ChatNotFoundException::new);
    }

    /** Whether the caller may read the chat, and so subscribe to it. */
    @Transactional(readOnly = true)
    public boolean canRead(Caller caller, UUID chatId) {
        return chats.findById(chatId).filter(chat -> mayRead(caller, chat)).isPresent();
    }

    private Chat readable(Caller caller, UUID chatId) {
        return chats.findById(chatId).filter(chat -> mayRead(caller, chat))
                .orElseThrow(ChatNotFoundException::new);
    }

    // parties while it is open (C3, C6); admins always (C13)
    private static boolean mayRead(Caller caller, Chat chat) {
        return caller.admin() || (chat.isOpen() && chat.isParty(caller.userId()));
    }

    // ---- sending (C7, C8) -------------------------------------------

    /**
     * Stores a message from one of the chat's parties and pushes it to
     * the subscribers once committed. Admins are read-only (C13), so an
     * admin who is not a party is refused like anyone else.
     */
    @Transactional
    public MessageView send(Caller caller, UUID chatId, SendMessage body) {
        Chat chat = chats.findById(chatId)
                .filter(c -> c.isParty(caller.userId()))
                .orElseThrow(ChatNotFoundException::new);
        if (!chat.isOpen()) {
            throw new ChatClosedException();
        }
        String text = text(body);
        DecodedImage image = image(body);
        if (text == null && image == null) {
            throw new InvalidMessageException("A message needs text or an image");
        }
        ChatMessage saved = messages.save(new ChatMessage(chatId, caller.userId(), text,
                image == null ? null : image.bytes(), image == null ? null : image.contentType(),
                Instant.now()));
        MessageView view = new MessageView(saved.getId(), chatId, saved.getSenderId(), saved.getText(),
                saved.hasImage(), saved.getSentAt());
        // counted after the save, so it includes this message (the query
        // flushes it first); 1 means this message made the chat unread
        long receiverId = chat.otherParty(caller.userId());
        long unread = unreadCount(chat, receiverId);
        UnreadFrame unreadFrame = new UnreadFrame(chatId, chat.getRequestId(), unread, unread == 1,
                caller.userId(), preview(text), saved.hasImage());
        afterCommit(() -> {
            publisher.messageSent(view);
            publisher.unread(receiverId, unreadFrame);
        });
        return view;
    }

    private static String preview(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= PREVIEW_LENGTH ? text : text.substring(0, PREVIEW_LENGTH - 1) + "…";
    }

    // trimmed; blank is no text
    private String text(SendMessage body) {
        if (body == null || body.text() == null || body.text().isBlank()) {
            return null;
        }
        String text = body.text().strip();
        if (text.length() > maxTextLength) {
            throw new InvalidMessageException("A message can be at most " + maxTextLength + " characters");
        }
        return text;
    }

    private DecodedImage image(SendMessage body) {
        if (body == null || body.image() == null) {
            return null;
        }
        String contentType = body.image().contentType();
        if (contentType == null || !IMAGE_TYPES.containsKey(contentType)) {
            throw new InvalidMessageException("Images must be JPEG, PNG or WebP");
        }
        String data = body.image().data();
        if (data == null || data.isEmpty()) {
            throw new InvalidMessageException("The image is empty");
        }
        // checked on the encoded length first, so an oversized image is
        // refused before it is decoded into memory
        if ((long) data.length() * 3 / 4 > maxImageBytes + 2L) {
            throw new InvalidMessageException(tooLarge());
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            throw new InvalidMessageException("The image is not valid base64");
        }
        if (bytes.length > maxImageBytes) {
            throw new InvalidMessageException(tooLarge());
        }
        if (!matchesType(contentType, bytes)) {
            throw new InvalidMessageException("The image is not a " + IMAGE_TYPES.get(contentType) + " file");
        }
        return new DecodedImage(contentType, bytes);
    }

    private String tooLarge() {
        return "Images can be at most " + (maxImageBytes / (1024 * 1024)) + " MB";
    }

    private static boolean matchesType(String contentType, byte[] bytes) {
        return switch (contentType) {
            case "image/jpeg" -> startsWith(bytes, JPEG, 0);
            case "image/png" -> startsWith(bytes, PNG, 0);
            // "RIFF", a 4-byte size, then "WEBP"
            case "image/webp" -> startsWith(bytes, RIFF, 0) && startsWith(bytes, WEBP, 8);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix, int offset) {
        return bytes.length >= offset + prefix.length
                && Arrays.equals(bytes, offset, offset + prefix.length, prefix, 0, prefix.length);
    }

    private record DecodedImage(String contentType, byte[] bytes) {
    }

    // A push must never announce something that was rolled back. Every
    // caller runs in a transaction; outside one the action would run at once.
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
