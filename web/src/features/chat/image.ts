// AI-assisted (CS3219 AI Usage Policy disclosure):
// Tool: Claude Code (Opus 5.5), 2026-10-31, N6 chat (docs/chat-service.md).
// Scope: the client-side copy of the image rules (C7: JPEG/PNG/WebP, at
// most 2 MB), checked before a file is read, and the base64 encoding the
// STOMP frame carries it in (C8). chat-service checks both again.
// Author review: Ko-Khan, pending pull request review.

export const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const
export const MAX_IMAGE_BYTES = 2 * 1024 * 1024
export const MAX_TEXT_LENGTH = 500

export interface EncodedImage {
  contentType: string
  data: string // base64, no data: prefix
}

// a reason to refuse the file, or null if it may be sent
export function imageProblem(file: File): string | null {
  if (!(IMAGE_TYPES as readonly string[]).includes(file.type)) {
    return 'Images must be JPEG, PNG or WebP.'
  }
  if (file.size > MAX_IMAGE_BYTES) {
    return 'Images can be at most 2 MB.'
  }
  return null
}

export function encodeImage(file: File): Promise<EncodedImage> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const url = String(reader.result)
      resolve({ contentType: file.type, data: url.slice(url.indexOf(',') + 1) })
    }
    reader.onerror = () =>
      reject(reader.error ?? new Error('Could not read the image'))
    reader.readAsDataURL(file)
  })
}
