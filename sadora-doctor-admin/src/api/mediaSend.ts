import { useMemo, useSyncExternalStore } from 'react'
import { request } from './client'
import { messagesPath } from './hooks'
import { mediaErrorText } from './media'
import type { DraftMedia } from './mediaDraft'
import { primePhoto } from './photos'
import { cancelUpload, mediaThumbPath, uploadFile } from './upload'
import type { DirectMessage, MessageMedia, SendMessageRequest } from './types'

/*
 * Photos and videos from the send preview to the thread.
 *
 * The drafts she sends (`mediaDraft`) become a pending send: a bubble in the thread at
 * once, uploading one piece at a time, sent as a message when every file is finished.
 *
 * The pending sends live in this tab's memory and nowhere else. They carry on while she
 * reads another thread or another page; closing the tab ends them (the browser asks
 * first), and the server sweeps an upload no message took within a day. Signing out
 * drops them with everything else of the session.
 */

let counter = 0
const nextKey = () => `send-${Date.now().toString(36)}-${++counter}`

export type SendStatus = 'uploading' | 'sending' | 'failed'

export interface PendingItem extends DraftMedia {
  /** Set once the server has started the upload: what a retry resumes. */
  uploadId?: string
  /** What the server holds of this file. */
  sentBytes: number
  /** Set once the upload is finished. */
  media?: MessageMedia
}

export interface PendingSend {
  key: string
  conversationId: string
  caption: string
  createdAt: string
  items: PendingItem[]
  status: SendStatus
  /** Why it was not sent, in her words. */
  error?: string
}

let sends: readonly PendingSend[] = []
const listeners = new Set<() => void>()
const controllers = new Map<string, AbortController>()
const onSentOf = new Map<string, (message: DirectMessage) => void>()

function emit() {
  for (const listener of listeners) listener()
}

function find(key: string): PendingSend | undefined {
  return sends.find((send) => send.key === key)
}

function update(key: string, change: (send: PendingSend) => PendingSend) {
  if (!find(key)) return
  sends = sends.map((send) => (send.key === key ? change(send) : send))
  emit()
}

function patchItem(key: string, itemKey: string, patch: Partial<PendingItem>) {
  update(key, (send) => ({ ...send, items: send.items.map((item) => (item.key === itemKey ? { ...item, ...patch } : item)) }))
}

/** Takes a send out. `keepPreviews` hands what was drawn to the thumbnail cache instead of revoking it. */
function remove(key: string, keepPreviews = false) {
  const send = find(key)
  if (!send) return
  sends = sends.filter((item) => item.key !== key)
  onSentOf.delete(key)
  for (const item of send.items) {
    if (!item.previewUrl) continue
    if (keepPreviews && item.media && item.media.hasThumbnail !== false) primePhoto(mediaThumbPath(item.media.id), item.previewUrl)
    else URL.revokeObjectURL(item.previewUrl)
  }
  emit()
}

export function subscribeSends(listener: () => void): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export function currentSends(): readonly PendingSend[] {
  return sends
}

/** True while a file is on its way or a message about to go: what closing the tab would lose. */
export function sendsInFlight(): boolean {
  return sends.some((send) => send.status !== 'failed')
}

/** The pending sends of one conversation, oldest first. */
export function usePendingSends(conversationId: string): PendingSend[] {
  const all = useSyncExternalStore(subscribeSends, currentSends)
  return useMemo(() => all.filter((send) => send.conversationId === conversationId), [all, conversationId])
}

let guarding = false

/** Closing or reloading the tab mid-upload asks first: nothing carries on without the tab. */
function guardTab() {
  if (guarding || typeof window === 'undefined') return
  guarding = true
  window.addEventListener('beforeunload', (event) => {
    if (!sendsInFlight()) return
    event.preventDefault()
    event.returnValue = ''
  })
}

/**
 * Puts the drafts into the thread as a pending send and starts it. `onSent` gets the
 * line the server made, to put it where the pending bubble was.
 */
export function queueMediaSend(input: {
  conversationId: string
  caption: string
  items: DraftMedia[]
  onSent: (message: DirectMessage) => void
}): string {
  const send: PendingSend = {
    key: nextKey(),
    conversationId: input.conversationId,
    caption: input.caption,
    createdAt: new Date().toISOString(),
    items: input.items.map((item) => ({ ...item, sentBytes: 0 })),
    status: 'uploading',
  }
  sends = [...sends, send]
  onSentOf.set(send.key, input.onSent)
  emit()
  guardTab()
  void run(send.key)
  return send.key
}

/**
 * Uploads what is not finished yet, a file at a time, then sends the message. A retry
 * runs this again: finished files are not sent twice, and an upload cut short carries
 * on from what the server holds.
 */
async function run(key: string) {
  const controller = new AbortController()
  controllers.set(key, controller)
  update(key, (send) => ({ ...send, status: 'uploading', error: undefined }))
  try {
    for (const item of find(key)?.items ?? []) {
      if (item.media) continue
      const media = await uploadFile(
        item,
        item.uploadId,
        ({ uploadId, sentBytes }) => patchItem(key, item.key, { uploadId, sentBytes }),
        controller.signal,
      )
      patchItem(key, item.key, { media, sentBytes: item.blob.size })
    }
    const ready = find(key)
    if (!ready) return
    controller.signal.throwIfAborted()
    update(key, (send) => ({ ...send, status: 'sending' }))
    const body: SendMessageRequest = { body: ready.caption, mediaIds: ready.items.map((item) => item.media!.id) }
    const message = await request<DirectMessage>(messagesPath(ready.conversationId), {
      method: 'POST',
      body,
      signal: controller.signal,
    })
    onSentOf.get(key)?.(message)
    remove(key, true)
  } catch (error) {
    if (controller.signal.aborted) return
    update(key, (send) => ({ ...send, status: 'failed', error: mediaErrorText(error) }))
  } finally {
    if (controllers.get(key) === controller) controllers.delete(key)
  }
}

/** "Qayta yuborish": resumes each unfinished upload from what the server holds, then sends. */
export function retryMediaSend(key: string): void {
  const send = find(key)
  if (!send || send.status !== 'failed') return
  void run(key)
}

/**
 * Drops a pending send: whatever is on the wire stops, and every upload the server
 * started for it is deleted there, finished or not — no message will carry them.
 */
export function discardMediaSend(key: string): void {
  const send = find(key)
  if (!send) return
  controllers.get(key)?.abort()
  controllers.delete(key)
  remove(key)
  for (const item of send.items) {
    if (item.uploadId) void cancelUpload(item.uploadId).catch(() => undefined)
  }
}

/**
 * Stops and forgets every pending send. Called when the session ends: the uploads are
 * left to the server's daily sweep, since the token that could delete them is gone.
 */
export function clearMediaSends(): void {
  for (const controller of controllers.values()) controller.abort()
  controllers.clear()
  for (const send of sends) for (const item of send.items) if (item.previewUrl) URL.revokeObjectURL(item.previewUrl)
  sends = []
  onSentOf.clear()
  emit()
}
