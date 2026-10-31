// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
// Scope: the chat composer's client-side image rules (C7) and the base64
// encoding the STOMP frame carries (C8).
// Author review: Ko-Khan, pending pull request review.

import { describe, expect, it } from 'vitest'
import {
  encodeImage,
  imageProblem,
  MAX_IMAGE_BYTES,
} from '@/features/chat/image'

function file(type: string, bytes: number): File {
  return new File([new Uint8Array(bytes)], 'photo', { type })
}

describe('chat image rules', () => {
  it('accepts JPEG, PNG and WebP up to 2 MB', () => {
    expect(imageProblem(file('image/jpeg', 10))).toBeNull()
    expect(imageProblem(file('image/png', MAX_IMAGE_BYTES))).toBeNull()
    expect(imageProblem(file('image/webp', 10))).toBeNull()
  })

  it('refuses other types and larger files', () => {
    expect(imageProblem(file('image/gif', 10))).toMatch(/JPEG, PNG or WebP/)
    expect(imageProblem(file('image/png', MAX_IMAGE_BYTES + 1))).toMatch(/2 MB/)
  })

  it('encodes the bytes as plain base64 with the file type', async () => {
    const encoded = await encodeImage(
      new File([new Uint8Array([1, 2, 3])], 'p.png', { type: 'image/png' }),
    )
    expect(encoded).toEqual({ contentType: 'image/png', data: 'AQID' })
  })
})
