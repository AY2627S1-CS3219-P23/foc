// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
// Scope: the chat pop-up (C14): the request's chat history over REST, live
// messages over chat-service's own STOMP socket (C2), and a composer for
// text and an image (C7, C8) that only the two parties of an open chat
// get. Admins open the same pop-up read-only, with a picker when the
// request has had more than one chat (C13). Reconnect timings copy
// NotificationProvider's.
// 2026-10-31: the parties shown by username, not "Requester"/"Courier"
// (read-time lookup, as the order pages do); a party opening the chat,
// and every message they receive while it is open, marks it read and
// clears that request's badge (C19).
// Author review: Ko-Khan, pending pull request review.

import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Client, ReconnectionTimeMode } from '@stomp/stompjs'
import { Modal } from '@/shared/components/Modal'
import { errorMessage } from '@/lib/api/http'
import { useAuth } from '@/features/user/useAuth'
import { useCurrentUserId } from '@/features/order/useCurrentUserId'
import { useUsername } from '@/features/order/useUsername'
import {
  chatBrokerUrl,
  fetchImageUrl,
  listChats,
  listMessages,
  markRead,
} from './api'
import {
  encodeImage,
  imageProblem,
  MAX_TEXT_LENGTH,
  type EncodedImage,
} from './image'
import type { ChatFrame, ChatMessage, ChatView } from './types'
import { useChatNotifications } from './useChatNotifications'

const PAGE_SIZE = 50

interface ChatPopupProps {
  requestId: number
  // admins: history only, no composer (C13)
  readOnly: boolean
  onClose: () => void
}

export function ChatPopup({ requestId, readOnly, onClose }: ChatPopupProps) {
  const { token } = useAuth()
  const accessToken = token?.accessToken ?? null
  const me = useCurrentUserId()

  const [chats, setChats] = useState<ChatView[] | null>(null)
  const [chatId, setChatId] = useState<string | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    listChats(requestId)
      .then((found) => {
        if (cancelled) return
        setChats(found)
        setChatId(found[0]?.id ?? null)
      })
      .catch((err) => {
        if (!cancelled)
          setLoadError(errorMessage(err, 'Could not load the chat.'))
      })
    return () => {
      cancelled = true
    }
  }, [requestId])

  const chat = chats?.find((c) => c.id === chatId) ?? null

  return (
    <Modal title={`Chat — Request #${requestId}`} onClose={onClose}>
      {loadError && <p className="text-sm text-red-600">{loadError}</p>}
      {!loadError && chats === null && (
        <p className="text-sm text-gray-500">Loading…</p>
      )}
      {chats !== null && chats.length === 0 && (
        <p className="text-sm text-gray-500">
          {readOnly
            ? 'This request has no chat history.'
            : 'There is no open chat for this request.'}
        </p>
      )}
      {chats !== null && chats.length > 1 && (
        <label className="mb-3 block text-sm text-gray-600">
          Chat
          <select
            className="ml-2 rounded-md border border-gray-300 px-2 py-1 text-sm"
            value={chatId ?? ''}
            onChange={(e) => setChatId(e.target.value)}
          >
            {chats.map((c) => (
              <ChatOption key={c.id} chat={c} />
            ))}
          </select>
        </label>
      )}
      {chat && accessToken && (
        <ChatThread
          key={chat.id}
          chat={chat}
          me={me}
          token={accessToken}
          canSend={!readOnly && chat.status === 'OPEN'}
          isParty={!readOnly}
        />
      )}
    </Modal>
  )
}

// one chat in an admin's picker, named by its courier
function ChatOption({ chat }: { chat: ChatView }) {
  const courier = useUsername(chat.courierId)
  return (
    <option value={chat.id}>
      {courier} · {new Date(chat.openedAt).toLocaleString()}
      {chat.status === 'CLOSED' ? ' (ended)' : ''}
    </option>
  )
}

interface ChatThreadProps {
  chat: ChatView
  me: number | null
  token: string
  canSend: boolean
  // the caller is the requester or the courier (not an admin reading)
  isParty: boolean
}

function ChatThread({ chat, me, token, canSend, isParty }: ChatThreadProps) {
  const requester = useUsername(chat.requesterId)
  const courier = useUsername(chat.courierId)
  const notifications = useChatNotifications()
  const setUnread = notifications?.setUnread

  // A party looking at an open chat has read it: on opening, and again on
  // each message that arrives while it is open. Failures are harmless (the
  // badge just stays), so they are not shown.
  const readNow = useCallback(() => {
    if (!isParty || chat.status !== 'OPEN') return
    setUnread?.(chat.requestId, 0)
    markRead(chat.id).catch(() => {})
  }, [isParty, chat.status, chat.id, chat.requestId, setUnread])

  useEffect(() => {
    readNow()
  }, [readNow])

  // oldest first, as shown
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [hasMore, setHasMore] = useState(false)
  const [ended, setEnded] = useState(chat.status === 'CLOSED')
  const [error, setError] = useState<string | null>(null)
  const [connected, setConnected] = useState(false)
  const clientRef = useRef<Client | null>(null)
  const bottomRef = useRef<HTMLDivElement | null>(null)

  // Adds messages without duplicates: a live frame can race the history
  // page that already holds it.
  const merge = useCallback((incoming: ChatMessage[]) => {
    setMessages((prev) => {
      const seen = new Set(prev.map((m) => m.id))
      const added = incoming.filter((m) => !seen.has(m.id))
      if (added.length === 0) return prev
      return [...prev, ...added].sort((a, b) =>
        a.sentAt.localeCompare(b.sentAt),
      )
    })
  }, [])

  useEffect(() => {
    let cancelled = false
    listMessages(chat.id, undefined, PAGE_SIZE)
      .then((page) => {
        if (cancelled) return
        merge(page)
        setHasMore(page.length === PAGE_SIZE)
      })
      .catch((err) => {
        if (!cancelled)
          setError(errorMessage(err, 'Could not load the messages.'))
      })
    return () => {
      cancelled = true
    }
  }, [chat.id, merge])

  // the live channel: the chat's topic, and this session's own errors
  useEffect(() => {
    const client = new Client({
      brokerURL: chatBrokerUrl(),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 1_000,
      maxReconnectDelay: 30_000,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      onConnect: () => {
        setConnected(true)
        client.subscribe(`/topic/chats/${chat.id}`, (frame) => {
          try {
            const body = JSON.parse(frame.body) as ChatFrame
            if (body.type === 'MESSAGE') {
              merge([body.message])
              if (body.message.senderId !== me) readNow()
            }
            if (body.type === 'CLOSED') setEnded(true)
          } catch {
            console.warn('unparseable chat frame ignored')
          }
        })
        client.subscribe('/user/queue/errors', (frame) => {
          try {
            setError((JSON.parse(frame.body) as { detail: string }).detail)
          } catch {
            setError('The message could not be sent.')
          }
        })
        // a reconnect may have missed frames: catch up on the newest page
        listMessages(chat.id, undefined, PAGE_SIZE)
          .then(merge)
          .catch(() => {})
      },
      onWebSocketClose: () => setConnected(false),
      // a refused CONNECT or SUBSCRIBE (e.g. the chat ended while away):
      // stop retrying rather than re-sending the same frames every second
      onStompError: (frame) => {
        setError(frame.headers.message ?? 'The chat connection was refused.')
        void client.deactivate()
      },
    })
    clientRef.current = client
    client.activate()
    return () => {
      clientRef.current = null
      void client.deactivate()
    }
  }, [chat.id, token, merge, me, readNow])

  // keep the newest message in view
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' })
  }, [messages.length])

  async function loadEarlier() {
    const oldest = messages[0]
    if (!oldest) return
    try {
      const page = await listMessages(chat.id, oldest.sentAt, PAGE_SIZE)
      merge(page)
      setHasMore(page.length === PAGE_SIZE)
    } catch (err) {
      setError(errorMessage(err, 'Could not load earlier messages.'))
    }
  }

  function send(text: string, image: EncodedImage | null): boolean {
    const client = clientRef.current
    if (!client?.connected) {
      setError('Not connected yet — try again in a moment.')
      return false
    }
    setError(null)
    client.publish({
      destination: `/app/chats/${chat.id}/messages`,
      body: JSON.stringify({ text: text || null, image }),
    })
    return true
  }

  const partyName = (senderId: number) => {
    const name = senderId === chat.requesterId ? requester : courier
    return senderId === me ? `${name} (you)` : name
  }

  return (
    <div className="flex flex-col gap-3">
      <p className="text-xs text-gray-500">
        Between <span className="font-medium text-gray-700">{requester}</span>{' '}
        (requester) and{' '}
        <span className="font-medium text-gray-700">{courier}</span> (courier)
      </p>
      <div className="h-80 overflow-y-auto rounded-md border border-gray-200 bg-gray-50 p-3">
        {hasMore && (
          <button
            type="button"
            onClick={loadEarlier}
            className="mb-2 w-full text-center text-xs text-gray-500 hover:text-gray-900"
          >
            Load earlier messages
          </button>
        )}
        {messages.length === 0 && (
          <p className="text-center text-sm text-gray-400">No messages yet.</p>
        )}
        <ul className="space-y-2">
          {messages.map((m) => (
            <li
              key={m.id}
              className={`flex flex-col ${m.senderId === me ? 'items-end' : 'items-start'}`}
            >
              <span className="text-[11px] text-gray-400">
                {partyName(m.senderId)} ·{' '}
                {new Date(m.sentAt).toLocaleTimeString([], {
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </span>
              <div
                className={`max-w-[80%] rounded-lg px-3 py-2 text-sm break-words whitespace-pre-wrap ${
                  m.senderId === me
                    ? 'bg-gray-900 text-white'
                    : 'border border-gray-200 bg-white text-gray-900'
                }`}
              >
                {m.hasImage && <ChatImage message={m} token={token} />}
                {m.text}
              </div>
            </li>
          ))}
        </ul>
        <div ref={bottomRef} />
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}
      {ended && <p className="text-sm text-gray-500">This chat has ended.</p>}
      {canSend && !ended && (
        <Composer onSend={send} disabled={!connected} onProblem={setError} />
      )}
    </div>
  )
}

function ChatImage({
  message,
  token,
}: {
  message: ChatMessage
  token: string
}) {
  const [url, setUrl] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let objectUrl: string | null = null
    let cancelled = false
    fetchImageUrl(message.chatId, message.id, token)
      .then((u) => {
        objectUrl = u
        if (cancelled) URL.revokeObjectURL(u)
        else setUrl(u)
      })
      .catch(() => {
        if (!cancelled) setFailed(true)
      })
    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [message.chatId, message.id, token])

  if (failed) return <p className="text-xs italic">Image unavailable</p>
  if (!url) return <p className="text-xs italic">Loading image…</p>
  return (
    <a href={url} target="_blank" rel="noreferrer">
      <img src={url} alt="Sent image" className="mb-1 max-h-48 rounded" />
    </a>
  )
}

interface ComposerProps {
  onSend: (text: string, image: EncodedImage | null) => boolean
  onProblem: (problem: string | null) => void
  disabled: boolean
}

function Composer({ onSend, onProblem, disabled }: ComposerProps) {
  const [text, setText] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [busy, setBusy] = useState(false)
  const fileInput = useRef<HTMLInputElement | null>(null)

  function pick(picked: File | null) {
    if (picked) {
      const problem = imageProblem(picked)
      if (problem) {
        onProblem(problem)
        if (fileInput.current) fileInput.current.value = ''
        return
      }
    }
    onProblem(null)
    setFile(picked)
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    const trimmed = text.trim()
    if (!trimmed && !file) return
    setBusy(true)
    try {
      const image = file ? await encodeImage(file) : null
      if (onSend(trimmed, image)) {
        setText('')
        setFile(null)
        if (fileInput.current) fileInput.current.value = ''
      }
    } catch {
      onProblem('Could not read the image.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="space-y-2">
      <textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        maxLength={MAX_TEXT_LENGTH}
        rows={2}
        placeholder="Write a message"
        aria-label="Message"
        className="w-full resize-none rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-gray-900 focus:outline-none"
        onKeyDown={(e) => {
          if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault()
            e.currentTarget.form?.requestSubmit()
          }
        }}
      />
      <div className="flex items-center justify-between gap-2">
        <label className="cursor-pointer text-sm text-gray-600 hover:text-gray-900">
          <input
            ref={fileInput}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            className="sr-only"
            onChange={(e) => pick(e.target.files?.[0] ?? null)}
          />
          {file ? `📎 ${file.name}` : 'Attach image'}
        </label>
        <span className="text-xs text-gray-400">
          {text.length}/{MAX_TEXT_LENGTH}
        </span>
        <button
          type="submit"
          disabled={disabled || busy || (!text.trim() && !file)}
          className="rounded-md bg-gray-900 px-3 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
        >
          {disabled ? 'Connecting…' : busy ? 'Sending…' : 'Send'}
        </button>
      </div>
    </form>
  )
}
