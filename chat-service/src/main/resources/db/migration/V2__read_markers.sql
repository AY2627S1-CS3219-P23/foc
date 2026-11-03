-- AI-assisted (CS3219 AI Usage Policy disclosure):
-- Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
-- Scope: when each party last opened the chat, so their unread count is
-- the other party's messages sent after it (decisions C16-C19).
-- Author review: Ko-Khan, pending pull request review.

-- null: that party has never opened the chat, so every message from the
-- other party is unread
alter table chat add column requester_last_read_at timestamp(6) with time zone;
alter table chat add column courier_last_read_at timestamp(6) with time zone;

-- the unread count: one chat's messages from the other party after a time
create index idx_chat_message_chat_sender_sent on chat_message (chat_id, sender_id, sent_at);
