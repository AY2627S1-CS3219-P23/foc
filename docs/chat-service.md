<!--
  AI-assisted (CS3219 AI Usage Policy disclosure):
  Tool: Claude Code (Opus 5.5), 2026-10-31.
  Scope: the decisions below transcribed from Ko-Khan's answers (chat
  prompt and options Q&A), plus a separate, clearly marked list of the
  implementation details the tool chose while building them. No
  rationale is drafted here; the decisions are the author's.
  2026-10-31: decisions C16-C21 (unread notifications and names) added
  from the same author's answers, with the interface they produced.
  Author review: Ko-Khan, pending pull request review.
-->

# Chat Service — requester–courier chat (nice-to-have N6)

Requester–courier chat for an accepted request. This file records the
decisions it was built from and the interface that resulted.

## Decisions (Ko-Khan, 2026-10-31)

| # | Decision |
| --- | --- |
| C1 | Chat is its own service, `chat-service/`, with its own database `chat-db` (postgres:17, loopback host port 5439). |
| C2 | Real-time traffic uses chat-service's own WebSocket endpoint, STOMP over WebSocket (not notification-service's channel). |
| C3 | The requester and the assigned courier can chat once the request is accepted and until it leaves the in-progress states: the chat is open while the request is `ACCEPTED`, `COLLECTED` or `ARRIVED`. |
| C4 | The chat closes when the courier drops the request (`ACCEPTED → CREATED`); a courier who accepts it next gets a new chat. |
| C5 | The chat also closes when the request is disputed (`DISPUTED` does not keep it open), and on every other way out of the in-progress states (expired, completed). |
| C6 | Once closed, the requester and courier can no longer read the chat; only admins can. |
| C7 | Messages are text, an image, or both. Text is at most 500 characters; one image per message, at most 2 MB, JPEG, PNG or WebP. |
| C8 | Text and image are both sent in the STOMP frame (the image base64-encoded). |
| C9 | Images are stored in Postgres. |
| C10 | Messages are kept 30 days (`CHAT_RETENTION_DAYS`, configurable), purged daily at 03:00 (`CHAT_PURGE_CRON`, configurable). |
| C11 | order-service calls chat-service to open and close chats. If chat-service is down, the order transition still commits and the call is retried every 15 s, up to 20 attempts, then logged at ERROR and given up. |
| C12 | chat-service accepts those calls only with a shared internal token (`CHAT_INTERNAL_TOKEN`). |
| C13 | Admins (`ADMIN`, `OWNER`) have read-only access to chat history. No message reporting for now. |
| C14 | In the web app, chat is a pop-up opened from a button on the order; admins get the same pop-up, read-only. |
| C15 | Like notification-service (D23), the STOMP CONNECT token is checked against the platform's logout denylist and password-reset cutoff in Redis, failing open. |
| C16 | The receiver of a message is notified through chat-service only; notification-service is not involved and nothing appears in the Notification Center. |
| C17 | A notification is shown only for the first unread message: when a chat goes from nothing unread to unread for the receiver; no more until they open the chat. |
| C18 | The notification shows the sender's name and a preview of the text (the first ~80 characters; "Sent an image" for an image-only message). |
| C19 | Unread means the other party's messages received after the receiver last opened that chat's pop-up; the count is shown as a badge on the order's Chat button. |
| C20 | The notification is an in-app toast, plus a browser (system) notification when the tab is in the background. |
| C21 | The chat shows the parties' usernames instead of "Requester" and "Courier". |

## Interface

Through the gateway at `/api/chat/**` (the prefix is stripped, as for the
other services).

### REST (bearer token)

| Method and path | Who | Answer |
| --- | --- | --- |
| `GET /chats?requestId={id}` | anyone signed in | A list. Parties: the open chat they are in, or an empty list. Admins: every chat the request has had, newest first. Anyone else: an empty list. |
| `GET /chats/{chatId}/messages?before={messageSentAt}&size={n}` | parties while the chat is open; admins always | Up to `size` messages (default 50, max 100) sent before `before` (default: now), newest first. Images are not inlined: a message carries `hasImage`. |
| `GET /chats/{chatId}/messages/{messageId}/image` | same as above | The image bytes with their content type. |
| `POST /chats/{chatId}/read` | parties while the chat is open; admins (no effect) | `204`; the caller's unread count for the chat starts over (C19). |

Each chat in `GET /chats` carries `unreadCount`, the caller's unread
messages in it (always 0 for admins).

Refusals are `application/problem+json`: `404` for a chat that does not
exist or that the caller may not see (the same answer, so a chat's
existence is not revealed).

`GET /chats/{chatId}/messages` answers newest first, each message as
`{id, chatId, senderId, text, hasImage, sentAt}`.

### STOMP

- Endpoint `/ws` (SockJS, so the browser connects to `/ws/websocket`,
  as for notification-service). CONNECT carries `Authorization: Bearer <jwt>`.
- Subscribe to `/topic/chats/{chatId}`: parties of an open chat, and
  admins. Frames are `{"type":"MESSAGE", ...message}` and, when the chat
  closes, `{"type":"CLOSED","chatId":...}`.
- Subscribe to `/user/queue/chat`: the caller's own unread pushes
  (C16-C19). For every message, the receiver gets
  `{chatId, requestId, unreadCount, first, senderId, preview, hasImage}`;
  `first` is true only for the message that took their unread count from
  0 to 1, and `preview` is the text's first 80 characters (null for an
  image-only message).
- Send to `/app/chats/{chatId}/messages` with
  `{"text": "...", "image": {"contentType": "image/png", "data": "<base64>"}}`
  (either may be omitted, not both): parties of an open chat only. A
  refused message comes back to the sending session only, on
  `/user/queue/errors`, as `{"detail": "..."}`.

### Internal (order-service only, `X-Internal-Token` header)

| Method and path | Effect |
| --- | --- |
| `POST /internal/chats/open` `{requestId, requesterId, courierId}` | Opens a chat for that request and courier. Idempotent: if that courier's chat is already open, nothing changes. Any other open chat of the request is closed first. |
| `POST /internal/chats/close` `{requestId}` | Closes the request's open chat, if any. Idempotent. |

Both answer `204`; a missing or wrong token is `401`.

## Implementation choices made by the tool (for review)

These were not asked; each can be changed in review.

- The first open/close attempt is made by a 1 s poll of order-service's
  `chat_call` table (written in the transition's transaction); a failed
  call is due again 15 s later. Calls for one request are sent in order:
  a request's later call waits while an earlier one is still pending.
- A chat is identified by a UUID; at most one open chat per request is
  enforced by a partial unique index.
- The live frames carry message metadata only; the image is fetched
  separately by REST, so 2 MB images are not fanned out over every socket.
- STOMP inbound frames are capped at 3 MB (a 2 MB image is about 2.7 MB
  in base64).
- The purge deletes messages older than the retention window, then
  closed chats with no messages left that closed before the window.
- An image's bytes must match its declared type (JPEG, PNG and WebP file
  signatures), so a file can't be sent under a type it isn't.
- `GET /chats` answers a list for everyone (one chat or none for a
  party, all of them for an admin) rather than a single chat or `404`.
- order-service's retry values are also environment variables
  (`ORDER_CHAT_RETRY_DELAY_MS`, `ORDER_CHAT_RETRY_MAX_ATTEMPTS`), with
  the decided 15 s and 20 as defaults.
- In the web app the Chat button appears on the requester's My Requests
  card and on the courier's Active Delivery page while the chat is open;
  admins get a read-only View chat on the Active Delivery page and on
  each disputed request in the admin Disputes section. With more than
  one chat (a dropped courier), admins pick one from a list.
- The web app keeps one chat-service socket open while signed in (for the
  unread queue), and the pop-up opens its own while it is shown. Every
  unread frame updates the badge live; only `first` frames toast. A
  frame for the chat currently open in the pop-up is not announced, and
  the pop-up marks the chat read on opening and on each message it
  receives while open.
- Browser-notification permission is asked the first time the user opens
  a chat (browsers only show the prompt after a click); a toast lasts 8 s;
  a newer toast for the same request replaces the older one.
- Usernames are looked up in the browser from user-service, as the order
  pages do (D32); chat-service stores only user ids. A party's own
  messages show their name with "(you)".
