import { Fragment, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { fieldsOf, messageOf, requestBlob } from '../api/client'
import {
  CONVERSATIONS_POLL_MS,
  sendTyping,
  useCloseConsultation,
  useConversations,
  usePatientHistory,
  usePatientNote,
  usePatientRecord,
  useQuickReplies,
  useReportConversation,
  useSavePatientNote,
  useSendMessage,
  useThread,
} from '../api/hooks'
import { formatBytes, ImageProblem, prepareImage } from '../api/image'
import type { PreparedImage } from '../api/image'
import { limits } from '../api/limits'
import type { Conversation, ConsultationSession, DirectMessage, PatientHistory, QuickReply, ReportReason } from '../api/types'
import { consultationOpen, timeLeft } from '../api/consultation'
import {
  durationLabel,
  formatSom,
  insertReply,
  matchQuickReplies,
  NOTE_MAX,
  slashQuery,
  SUMMARY_MAX,
} from '../api/work'
import {
  closedReasonLabels,
  lifeStageLabel,
  paymentLabel,
  reportReasonLabels,
  reportReasonOrder,
} from '../components/labels'
import { PatientRecord } from '../components/PatientRecord'
import { useToast } from '../components/toast'
import {
  Avatar,
  Card,
  Counter,
  Empty,
  ErrorNotice,
  Field,
  formatDate,
  formatDateTime,
  formatTime,
  Loading,
  Modal,
  Segmented,
  Spinner,
} from '../components/ui'

type Filter = 'all' | 'open' | 'closed'

const filterItems: { key: Filter; label: string }[] = [
  { key: 'all', label: 'Hammasi' },
  { key: 'open', label: 'Ochiq' },
  { key: 'closed', label: 'Yopilgan' },
]

/** How often she may tell the patient she is typing: the server keeps it for six seconds. */
const TYPING_EVERY_MS = 3_000

/** A clock that moves on its own, for "5 soat qoldi" and for a window that runs out while she reads. */
function useNow(everyMs = 30_000): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), everyMs)
    return () => window.clearInterval(timer)
  }, [everyMs])
  return now
}

function patientName(conversation: Conversation): string {
  return conversation.patient?.name || conversation.alias
}

/**
 * Her photo, for the doctor she consults. Only a consultation's patient has one: an alias
 * who wrote without a consultation stays an initial on a tint, as in the chat.
 */
function patientPhoto(conversation: Conversation, history?: PatientHistory): string | null {
  if (conversation.patient) return conversation.patient.photoUrl ?? null
  return history?.patient?.photoUrl ?? null
}

function patientLine(conversation: Conversation): string {
  const patient = conversation.patient
  if (!patient) return ''
  return [patient.age != null ? `${patient.age} yosh` : null, lifeStageLabel(patient.lifeStage)].filter(Boolean).join(' · ')
}

function startOfDay(time: number): number {
  const date = new Date(time)
  date.setHours(0, 0, 0, 0)
  return date.getTime()
}

/** The list's stamp: the time today, "kecha", then the day. */
function formatListTime(iso: string, now: number): string {
  const time = Date.parse(iso)
  if (Number.isNaN(time)) return ''
  const today = startOfDay(now)
  if (time >= today) return formatTime(iso)
  const yesterday = new Date(today)
  yesterday.setDate(yesterday.getDate() - 1)
  if (time >= yesterday.getTime()) return 'kecha'
  return new Date(time).toLocaleDateString('uz-UZ', { day: '2-digit', month: '2-digit' })
}

/** The thread's day separator. */
function dayLabel(iso: string, now: number): string {
  const day = new Date(iso).toDateString()
  if (day === new Date(now).toDateString()) return 'Bugun'
  const yesterday = new Date(now)
  yesterday.setDate(yesterday.getDate() - 1)
  if (day === yesterday.toDateString()) return 'Kecha'
  return formatDate(iso)
}

function lastLine(conversation: Conversation): string {
  switch (conversation.lastMessageKind) {
    case 'image':
      return conversation.lastMessage ? `📷 Rasm · ${conversation.lastMessage}` : '📷 Rasm'
    case 'record':
      return '📋 Tibbiy karta'
    default:
      return conversation.lastMessage ?? ''
  }
}

/**
 * What a doctor sorts by at a glance: a patient who paid, and a window still waiting on
 * her first word. The same chips sit in the list and over the thread.
 */
function ConsultationChips({ conversation, open }: { conversation: Conversation; open: boolean }) {
  const consultation = conversation.consultation
  if (!consultation) return null
  const paid = consultation.payment === 'paid'
  const waiting = open && !consultation.answered
  const refund = consultation.payment === 'refund_due'
  if (!paid && !waiting && !refund) return null
  return (
    <span className="chips">
      {paid && <span className="badge premium">To'langan</span>}
      {refund && <span className="badge danger">To'lov qaytariladi</span>}
      {waiting && <span className="badge warn">Javob kutilmoqda</span>}
    </span>
  )
}

/**
 * Her consultations: the list on the left, the open thread in the middle, the patient
 * and the record she attached on the right. The open conversation lives in the URL
 * (`?c=`), so a reload or the browser's back button keep her place — and on a narrow
 * screen, where only one column fits, back is how she returns to the list.
 */
export function MessagesPage() {
  const [params, setParams] = useSearchParams()
  const selectedId = params.get('c')
  const conversations = useConversations()
  const [filter, setFilter] = useState<Filter>('all')
  const [search, setSearch] = useState('')
  const [drafts, setDrafts] = useState<Record<string, string>>({})
  const now = useNow()

  const all = conversations.data ?? []
  const shown = useMemo(() => {
    const needle = search.trim().toLocaleLowerCase()
    return [...all]
      .filter((conversation) => {
        if (needle && !patientName(conversation).toLocaleLowerCase().includes(needle)) return false
        if (filter === 'open') return consultationOpen(conversation, now)
        if (filter === 'closed') return !consultationOpen(conversation, now)
        return true
      })
      .sort((a, b) => Date.parse(b.lastMessageAt) - Date.parse(a.lastMessageAt))
  }, [all, filter, search, now])
  const selected = all.find((conversation) => conversation.id === selectedId) ?? null
  const openCount = all.filter((conversation) => consultationOpen(conversation, now)).length

  function open(id: string | null) {
    setParams(id ? { c: id } : {}, { replace: false })
  }

  return (
    <div className={`chat-page${selectedId ? ' has-thread' : ''}`}>
      <section className="card chat-list" aria-label="Suhbatlar">
        <div className="chat-list-head">
          <input
            type="search"
            value={search}
            placeholder="Bemor ismi bo'yicha qidirish…"
            aria-label="Bemor ismi bo'yicha qidirish"
            onChange={(event) => setSearch(event.target.value)}
          />
          <Segmented<Filter> label="Konsultatsiyalar" items={filterItems} value={filter} onChange={setFilter} />
          <span className={`faint live${conversations.isError ? ' stale' : ''}`}>
            {conversations.isError
              ? 'Yangilab bo\'lmadi'
              : `${openCount} ta ochiq · har ${CONVERSATIONS_POLL_MS / 1000} soniyada yangilanadi`}
          </span>
        </div>

        <div className="chat-list-body">
          {conversations.isPending ? (
            <Loading rows={5} height={64} />
          ) : conversations.isError && !all.length ? (
            <ErrorNotice error={conversations.error} onRetry={() => void conversations.refetch()} />
          ) : !all.length ? (
            <Empty>
              Hozircha konsultatsiyalar yo'q.
              <div className="faint">Bemor sizga yozganda suhbat shu yerda paydo bo'ladi.</div>
            </Empty>
          ) : !shown.length ? (
            <Empty>Bu shartga mos suhbat yo'q.</Empty>
          ) : (
            <ul className="conv-list">
              {shown.map((conversation) => (
                <li key={conversation.id}>
                  <ConversationItem
                    conversation={conversation}
                    active={conversation.id === selectedId}
                    open={consultationOpen(conversation, now)}
                    drafted={Boolean(drafts[conversation.id]?.trim())}
                    now={now}
                    onOpen={() => open(conversation.id)}
                  />
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>

      {selectedId ? (
        <ChatThread
          key={selectedId}
          conversationId={selectedId}
          fallback={selected}
          draft={drafts[selectedId] ?? ''}
          onDraft={(text) => setDrafts((current) => ({ ...current, [selectedId]: text }))}
          onBack={() => open(null)}
          now={now}
        />
      ) : (
        <Card className="chat-empty">
          <Empty>
            Yozishmani ochish uchun chapdagi ro'yxatdan bemorni tanlang.
            <div className="faint">Bemorlar sizga konsultatsiya ochganda shu yerda yozishasiz.</div>
          </Empty>
        </Card>
      )}
    </div>
  )
}

function ConversationItem({
  conversation,
  active,
  open,
  drafted,
  now,
  onOpen,
}: {
  conversation: Conversation
  active: boolean
  open: boolean
  drafted: boolean
  now: number
  onOpen: () => void
}) {
  const unread = conversation.unread
  return (
    <button
      type="button"
      className={`conv-item${active ? ' active' : ''}${unread > 0 ? ' unread' : ''}`}
      aria-current={active ? 'true' : undefined}
      onClick={onOpen}
    >
      <Avatar name={patientName(conversation)} tint={conversation.tint} url={patientPhoto(conversation)} />
      <span className="conv-main">
        <span className="conv-top">
          <b className="conv-name">{patientName(conversation)}</b>
          <span className="faint conv-time" title={formatDateTime(conversation.lastMessageAt)}>
            {formatListTime(conversation.lastMessageAt, now)}
          </span>
        </span>
        {conversation.patient && <span className="conv-sub faint">{patientLine(conversation)}</span>}
        <ConsultationChips conversation={conversation} open={open} />
        <span className="conv-bottom">
          <span className="conv-preview">
            {conversation.lastMessageRead && (
              <span className="ticks read" aria-label="O'qildi">
                ✓✓
              </span>
            )}
            {drafted ? <span className="conv-draft">Qoralama</span> : lastLine(conversation)}
          </span>
          {conversation.consultation && (
            <span className={`badge ${open ? 'ok' : 'free'}`}>{open ? 'Ochiq' : 'Yopilgan'}</span>
          )}
          {unread > 0 && (
            <span key={unread} className="conv-unread" aria-label={`${unread} ta o'qilmagan`}>
              {unread > 99 ? '99+' : unread}
            </span>
          )}
        </span>
      </span>
    </button>
  )
}

function ChatThread({
  conversationId,
  fallback,
  draft,
  onDraft,
  onBack,
  now,
}: {
  conversationId: string
  fallback: Conversation | null
  draft: string
  onDraft: (text: string) => void
  onBack: () => void
  now: number
}) {
  const thread = useThread(conversationId)
  const conversation = thread.data?.conversation ?? fallback
  const messages = thread.data?.messages ?? []
  const [closing, setClosing] = useState<'close' | 'summary' | null>(null)
  const [reporting, setReporting] = useState(false)
  const [viewing, setViewing] = useState<{ url: string; caption: string } | null>(null)
  const records = messages.filter((message) => message.kind === 'record')
  const [pickedRecord, setPickedRecord] = useState<DirectMessage | null>(null)
  const [sideFlash, setSideFlash] = useState(0)

  const open = conversation ? consultationOpen(conversation, now) : false
  const isConsultation = Boolean(conversation?.consultation)
  const history = usePatientHistory(conversationId, isConsultation)
  const canReport = messages.some((message) => !message.isMine)
  const shownRecord = pickedRecord ?? records.at(-1) ?? null
  // A window that ended without her advice can still be given it, once.
  const lastSession = history.data?.sessions.at(-1)
  const canWriteSummary = isConsultation && !open && Boolean(lastSession) && !lastSession?.summary

  function openRecord(message: DirectMessage) {
    setPickedRecord(message)
    setSideFlash((count) => count + 1)
  }

  /** A record from the history: the line in the thread if it is loaded, else one standing in for it. */
  function openHistoryRecord(messageId: string, session: ConsultationSession) {
    const loaded = records.find((message) => message.id === messageId)
    openRecord(
      loaded ?? { id: messageId, body: '', createdAt: session.openedAt ?? '', isMine: false, kind: 'record' },
    )
  }

  const name = conversation ? patientName(conversation) : ''

  return (
    <>
      <section className="card chat-thread" aria-label="Yozishma">
        <header className="chat-head">
          <button type="button" className="btn ghost small chat-back" onClick={onBack}>
            ← Suhbatlar
          </button>
          {conversation && <Avatar name={name} tint={conversation.tint} url={patientPhoto(conversation, history.data)} />}
          <div className="chat-head-who">
            <b>{name || '…'}</b>
            {conversation?.patient && <div className="faint">{patientLine(conversation)}</div>}
            {conversation && <ConsultationChips conversation={conversation} open={open} />}
          </div>
          <div className="chat-head-actions">
            {conversation?.consultation && open && (
              <button type="button" className="btn small" onClick={() => setClosing('close')}>
                Konsultatsiyani yakunlash
              </button>
            )}
            {canWriteSummary && (
              <button type="button" className="btn small" onClick={() => setClosing('summary')}>
                Tavsiya yozish
              </button>
            )}
            <button
              type="button"
              className="btn small ghost danger"
              onClick={() => setReporting(true)}
              disabled={!canReport}
              title={canReport ? undefined : "Bemor hali hech narsa yozmagan"}
            >
              Shikoyat
            </button>
          </div>
        </header>

        {conversation?.consultation && <ConsultationBanner conversation={conversation} open={open} now={now} />}

        {thread.isPending && !thread.data ? (
          <div className="chat-scroll">
            <Loading rows={4} height={44} />
          </div>
        ) : thread.isError && !thread.data ? (
          <div className="chat-scroll">
            <ErrorNotice error={thread.error} onRetry={() => void thread.refetch()} />
          </div>
        ) : (
          <MessageList
            conversationId={conversationId}
            messages={messages}
            typing={Boolean(thread.data?.otherTyping)}
            typingName={name}
            now={now}
            onImage={(url, caption) => setViewing({ url, caption })}
            onRecord={openRecord}
            activeRecordId={shownRecord?.id ?? null}
          />
        )}

        {conversation && (
          <Composer
            conversationId={conversationId}
            conversation={conversation}
            open={open}
            draft={draft}
            onDraft={onDraft}
          />
        )}
      </section>

      <aside className="card chat-side" aria-label="Bemor">
        {conversation ? (
          <PatientPanel
            conversation={conversation}
            open={open}
            record={shownRecord}
            conversationId={conversationId}
            flash={sideFlash}
            onRecord={openHistoryRecord}
          />
        ) : (
          <Loading rows={3} height={40} />
        )}
      </aside>

      {closing && (
        <CloseConsultationDialog
          conversationId={conversationId}
          name={name}
          mode={closing}
          onClose={() => setClosing(null)}
        />
      )}
      {reporting && <ReportDialog conversationId={conversationId} onClose={() => setReporting(false)} />}
      {viewing && (
        <Modal title="Rasm" wide onClose={() => setViewing(null)}>
          <img className="msg-full" src={viewing.url} alt={viewing.caption || 'Bemor yuborgan rasm'} />
          {viewing.caption && <p className="comment-body">{viewing.caption}</p>}
        </Modal>
      )}
    </>
  )
}

function ConsultationBanner({ conversation, open, now }: { conversation: Conversation; open: boolean; now: number }) {
  const consultation = conversation.consultation!
  if (open) {
    return (
      <div className="chat-banner open" role="note">
        <span>
          <b>Konsultatsiya ochiq</b> · {timeLeft(consultation.expiresAt, now)} qoldi
        </span>
        <span className="faint">{formatDateTime(consultation.expiresAt)} da yopiladi</span>
      </div>
    )
  }
  return (
    <div className="chat-banner closed" role="note">
      <span>
        <b>Yopilgan</b> · {consultation.closedAt ? 'siz yakunlagansiz' : 'muddati tugagan'}
      </span>
      <span className="faint">{formatDateTime(consultation.closedAt ?? consultation.expiresAt)}</span>
    </div>
  )
}

function MessageList({
  conversationId,
  messages,
  typing,
  typingName,
  now,
  onImage,
  onRecord,
  activeRecordId,
}: {
  conversationId: string
  messages: DirectMessage[]
  typing: boolean
  typingName: string
  now: number
  onImage: (url: string, caption: string) => void
  onRecord: (message: DirectMessage) => void
  activeRecordId: string | null
}) {
  const scroller = useRef<HTMLDivElement>(null)
  // Follows new lines only while she is at the bottom; reading back up is left alone.
  const atBottom = useRef(true)
  const last = messages.at(-1)

  useLayoutEffect(() => {
    const element = scroller.current
    if (!element) return
    if (atBottom.current || last?.isMine) element.scrollTop = element.scrollHeight
  }, [messages.length, last?.id, last?.isMine, typing])

  if (!messages.length) {
    return (
      <div className="chat-scroll" ref={scroller}>
        <Empty>Hali xabar yo'q.</Empty>
      </div>
    )
  }

  let previousDay = ''
  return (
    <div
      className="chat-scroll"
      ref={scroller}
      onScroll={(event) => {
        const element = event.currentTarget
        atBottom.current = element.scrollHeight - element.scrollTop - element.clientHeight < 80
      }}
    >
      <ol className="chat-messages" aria-label="Xabarlar">
        {messages.map((message) => {
          const day = new Date(message.createdAt).toDateString()
          const separator = day !== previousDay
          previousDay = day
          return (
            <Fragment key={message.id}>
              {separator && (
                <li className="chat-day" aria-hidden="true">
                  <span>{dayLabel(message.createdAt, now)}</span>
                </li>
              )}
              <MessageBubble
                conversationId={conversationId}
                message={message}
                onImage={onImage}
                onRecord={onRecord}
                activeRecord={message.id === activeRecordId}
              />
            </Fragment>
          )
        })}
      </ol>
      {typing && (
        <div className="chat-typing" aria-live="polite">
          <span className="typing-dots" aria-hidden="true">
            <i />
            <i />
            <i />
          </span>
          {typingName} yozmoqda…
        </div>
      )}
    </div>
  )
}

function MessageBubble({
  conversationId,
  message,
  onImage,
  onRecord,
  activeRecord,
}: {
  conversationId: string
  message: DirectMessage
  onImage: (url: string, caption: string) => void
  onRecord: (message: DirectMessage) => void
  activeRecord: boolean
}) {
  const kind = message.kind ?? 'text'
  const stamp = (
    <span className="msg-stamp" title={formatDateTime(message.createdAt)}>
      {formatTime(message.createdAt)}
      {message.isMine &&
        (message.read ? (
          <span className="ticks read" aria-label="O'qildi">
            ✓✓
          </span>
        ) : (
          <span className="ticks" aria-label="Yuborildi">
            ✓
          </span>
        ))}
    </span>
  )

  let content: ReactNode
  if (kind === 'record') {
    content = (
      <div className={`msg-record${activeRecord ? ' active' : ''}`}>
        <span className="msg-record-glyph" aria-hidden="true">
          📋
        </span>
        <span className="msg-record-text">
          <b>Tibbiy karta</b>
          <span className="faint">Bemor kartasini biriktirdi</span>
        </span>
        <button type="button" className="btn small" onClick={() => onRecord(message)}>
          Ochish
        </button>
      </div>
    )
  } else if (kind === 'image') {
    content = (
      <>
        <MessagePhoto conversationId={conversationId} message={message} onOpen={onImage} />
        {message.body && <p className="msg-text">{message.body}</p>}
      </>
    )
  } else {
    content = <p className="msg-text">{message.body}</p>
  }

  return (
    <li className={`msg ${message.isMine ? 'mine' : 'theirs'} ${kind}`}>
      <div className="msg-bubble">
        {content}
        {stamp}
      </div>
    </li>
  )
}

/**
 * A photo in the thread. It is private, so it is fetched with her token and shown from
 * an object URL, which is let go when the bubble leaves. Its box is sized from the
 * width and height the server sent, so the thread does not jump when it arrives.
 */
function MessagePhoto({
  conversationId,
  message,
  onOpen,
}: {
  conversationId: string
  message: DirectMessage
  onOpen: (url: string, caption: string) => void
}) {
  const path = `/v1/community/conversations/${encodeURIComponent(conversationId)}/messages/${encodeURIComponent(message.id)}/image`
  const photo = useAuthorizedImage(path)
  const width = message.image?.width || 4
  const height = message.image?.height || 3
  const shownWidth = Math.min(260, width)
  return (
    <button
      type="button"
      className="msg-photo"
      style={{ width: shownWidth, aspectRatio: `${width} / ${height}` }}
      disabled={!photo.url}
      onClick={() => photo.url && onOpen(photo.url, message.body)}
      aria-label="Rasmni kattalashtirish"
    >
      {photo.url ? (
        <img src={photo.url} alt={message.body || 'Rasm'} />
      ) : photo.failed ? (
        <span className="faint">Rasm ochilmadi</span>
      ) : (
        <span className="skeleton" />
      )}
    </button>
  )
}

function useAuthorizedImage(path: string): { url?: string; failed?: boolean } {
  const [state, setState] = useState<{ url?: string; failed?: boolean }>({})
  useEffect(() => {
    const controller = new AbortController()
    let url: string | null = null
    requestBlob(path, { signal: controller.signal }).then(
      (blob) => {
        if (controller.signal.aborted) return
        url = URL.createObjectURL(blob)
        setState({ url })
      },
      () => {
        if (!controller.signal.aborted) setState({ failed: true })
      },
    )
    return () => {
      controller.abort()
      if (url) URL.revokeObjectURL(url)
    }
  }, [path])
  return state
}

function Composer({
  conversationId,
  conversation,
  open,
  draft,
  onDraft,
}: {
  conversationId: string
  conversation: Conversation
  open: boolean
  draft: string
  onDraft: (text: string) => void
}) {
  const send = useSendMessage(conversationId)
  const quickReplies = useQuickReplies()
  const { notify } = useToast()
  const [image, setImage] = useState<PreparedImage | null>(null)
  const [preparing, setPreparing] = useState(false)
  const file = useRef<HTMLInputElement>(null)
  const box = useRef<HTMLTextAreaElement>(null)
  const form = useRef<HTMLFormElement>(null)
  const lastTyping = useRef(0)
  const pendingCaret = useRef<number | null>(null)
  // The ⚡ list, opened by its button with a search of its own; or the one "/" opens,
  // searched by what follows the slash. Escape closes the slash list until the slash goes.
  const [picker, setPicker] = useState(false)
  const [pickerSearch, setPickerSearch] = useState('')
  const [slashClosed, setSlashClosed] = useState(false)
  const [active, setActive] = useState(0)

  // The preview is an object URL: let it go when it is replaced, sent or left behind.
  useEffect(() => () => {
    if (image) URL.revokeObjectURL(image.previewUrl)
  }, [image])

  const locked = conversation.blocked
    ? "Bu suhbatga yozib bo'lmaydi: bemor bilan aloqa cheklangan."
    : !open
      ? "Konsultatsiya yopilgan — bemor yangisini ochsa, yana yozishingiz mumkin."
      : null

  const slash = locked ? null : slashQuery(draft)
  const showSlash = slash !== null && !slashClosed
  const showing = !locked && (picker || showSlash)
  const search = picker ? pickerSearch : (slash ?? '')
  const matches = useMemo(() => matchQuickReplies(quickReplies.data ?? [], search), [quickReplies.data, search])

  useEffect(() => setActive(0), [search, showing])

  // A reply put in leaves the caret after it, in the box, ready to go on typing.
  useLayoutEffect(() => {
    const caret = pendingCaret.current
    if (caret === null) return
    pendingCaret.current = null
    box.current?.focus()
    box.current?.setSelectionRange(caret, caret)
  }, [draft])

  // A click anywhere outside the composer closes the ⚡ list.
  useEffect(() => {
    if (!picker) return
    const onDown = (event: MouseEvent) => {
      if (form.current && !form.current.contains(event.target as Node)) setPicker(false)
    }
    document.addEventListener('mousedown', onDown)
    return () => document.removeEventListener('mousedown', onDown)
  }, [picker])

  const length = draft.trim().length
  const ready = !locked && !send.isPending && !preparing && length <= limits.messageMax && (length > 0 || image !== null)

  function submit() {
    if (!ready) return
    const body = draft.trim()
    send.mutate(image ? { body, image: image.upload } : { body }, {
      onSuccess: () => {
        onDraft('')
        setImage(null)
        lastTyping.current = 0
      },
      onError: (error) => notify(messageOf(error), 'error'),
    })
  }

  async function choose(chosen: File | undefined) {
    if (!chosen) return
    setPreparing(true)
    try {
      setImage(await prepareImage(chosen))
    } catch (error) {
      notify(error instanceof ImageProblem ? error.message : "Rasmni o'qib bo'lmadi", 'error')
    } finally {
      setPreparing(false)
    }
  }

  /** Into the box, never sent: she reads it over and sends it herself. */
  function pick(reply: QuickReply) {
    const caret = box.current?.selectionStart ?? draft.length
    const next = insertReply(draft, caret, reply.body)
    const text = next.text.slice(0, limits.messageMax)
    pendingCaret.current = Math.min(next.caret, text.length)
    onDraft(text)
    setPicker(false)
    setPickerSearch('')
    setSlashClosed(false)
    if (text === draft) box.current?.focus()
  }

  function closeList() {
    if (picker) {
      setPicker(false)
      setPickerSearch('')
      box.current?.focus()
    } else {
      setSlashClosed(true)
    }
  }

  /** Arrows walk the list, Enter or Tab takes the reply, Escape closes it. True when the key was the list's. */
  function listKey(event: React.KeyboardEvent): boolean {
    if (!showing) return false
    if (event.key === 'Escape') {
      event.preventDefault()
      closeList()
      return true
    }
    if (!matches.length) return false
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      const step = event.key === 'ArrowDown' ? 1 : -1
      setActive((index) => (index + step + matches.length) % matches.length)
      return true
    }
    if ((event.key === 'Enter' || event.key === 'Tab') && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault()
      pick(matches[Math.min(active, matches.length - 1)]!)
      return true
    }
    return false
  }

  return (
    <form
      ref={form}
      className="chat-composer"
      onSubmit={(event) => {
        event.preventDefault()
        submit()
      }}
    >
      {showing && (
        <QuickReplyList
          replies={quickReplies.data}
          loading={quickReplies.isPending}
          failed={quickReplies.isError && !quickReplies.data}
          matches={matches}
          active={active}
          search={picker ? pickerSearch : null}
          slash={picker ? null : slash}
          onSearch={setPickerSearch}
          onSearchKey={(event) => void listKey(event)}
          onHover={setActive}
          onPick={pick}
        />
      )}
      {locked && (
        <p className="chat-locked" role="note">
          {locked}
        </p>
      )}
      {image && !locked && (
        <div className="chat-attachment">
          <img src={image.previewUrl} alt="Yuboriladigan rasm" />
          <span className="faint">
            {image.width}×{image.height} · {formatBytes(image.bytes)}
          </span>
          <button type="button" className="btn ghost small" onClick={() => setImage(null)} aria-label="Rasmni olib tashlash">
            ✕
          </button>
        </div>
      )}
      <div className="chat-input-row">
        <button
          type="button"
          className="btn ghost chat-attach"
          onClick={() => file.current?.click()}
          disabled={Boolean(locked) || preparing || send.isPending}
          aria-label="Rasm biriktirish"
          title="Rasm biriktirish"
        >
          {preparing ? <Spinner /> : '📎'}
        </button>
        <button
          type="button"
          className={`btn ghost chat-attach${picker ? ' on' : ''}`}
          onClick={() => {
            setPicker((current) => !current)
            setPickerSearch('')
          }}
          disabled={Boolean(locked)}
          aria-label="Tayyor javoblar"
          aria-expanded={picker}
          title="Tayyor javoblar ( / )"
        >
          ⚡
        </button>
        <input
          ref={file}
          type="file"
          accept="image/*"
          hidden
          onChange={(event) => {
            void choose(event.target.files?.[0])
            event.target.value = ''
          }}
        />
        <textarea
          ref={box}
          aria-label="Xabar"
          rows={Math.min(5, Math.max(1, draft.split('\n').length))}
          value={draft}
          maxLength={limits.messageMax}
          disabled={Boolean(locked)}
          placeholder={locked ? '' : image ? 'Rasmga izoh (ixtiyoriy)…' : 'Xabar yozing… ( / — tayyor javoblar)'}
          onChange={(event) => {
            const text = event.target.value
            onDraft(text)
            if (slashQuery(text) === null) setSlashClosed(false)
            const moment = Date.now()
            if (text.trim() && moment - lastTyping.current >= TYPING_EVERY_MS) {
              lastTyping.current = moment
              void sendTyping(conversationId)
            }
          }}
          onKeyDown={(event) => {
            if (!picker && listKey(event)) return
            if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
              event.preventDefault()
              submit()
            }
          }}
        />
        <button className="btn primary" type="submit" disabled={!ready}>
          {send.isPending && <Spinner />}
          Yuborish
        </button>
      </div>
      {!locked && (
        <div className="chat-composer-foot faint">
          <span>
            <kbd>Enter</kbd> — yuborish · <kbd>Shift</kbd> + <kbd>Enter</kbd> — yangi qator · <kbd>/</kbd> — tayyor
            javoblar
          </span>
          <Counter length={draft.length} max={limits.messageMax} />
        </div>
      )}
    </form>
  )
}

/** The quick replies over the composer: a search box when the ⚡ button opened it. */
function QuickReplyList({
  replies,
  loading,
  failed,
  matches,
  active,
  search,
  slash,
  onSearch,
  onSearchKey,
  onHover,
  onPick,
}: {
  replies: QuickReply[] | undefined
  loading: boolean
  failed: boolean
  matches: QuickReply[]
  active: number
  search: string | null
  slash: string | null
  onSearch: (text: string) => void
  onSearchKey: (event: React.KeyboardEvent) => void
  onHover: (index: number) => void
  onPick: (reply: QuickReply) => void
}) {
  const list = useRef<HTMLUListElement>(null)

  useEffect(() => {
    list.current?.querySelector<HTMLElement>('[aria-selected="true"]')?.scrollIntoView?.({ block: 'nearest' })
  }, [active])

  return (
    <div className="qr-pop" role="dialog" aria-label="Tayyor javoblar">
      {search !== null && (
        <input
          type="search"
          autoFocus
          value={search}
          placeholder="Tayyor javoblardan qidirish…"
          aria-label="Tayyor javoblardan qidirish"
          onChange={(event) => onSearch(event.target.value)}
          onKeyDown={onSearchKey}
        />
      )}
      {loading ? (
        <Loading rows={2} height={36} />
      ) : failed ? (
        <p className="faint qr-empty">Tayyor javoblarni olib bo'lmadi.</p>
      ) : !replies?.length ? (
        <p className="faint qr-empty">
          Hali tayyor javob yo'q. <Link to="/quick-replies">Qo'shish</Link>
        </p>
      ) : !matches.length ? (
        <p className="faint qr-empty">{slash ? `«${slash}» bo'yicha tayyor javob yo'q.` : "Mos tayyor javob yo'q."}</p>
      ) : (
        <ul ref={list} className="qr-list" role="listbox" aria-label="Tayyor javoblar ro'yxati">
          {matches.map((reply, index) => (
            <li
              key={reply.id}
              role="option"
              aria-selected={index === active}
              className={`qr-item${index === active ? ' active' : ''}`}
              // Keeps the focus in the box, so the caret it remembers is the one she left.
              onMouseDown={(event) => event.preventDefault()}
              onMouseEnter={() => onHover(index)}
              onClick={() => onPick(reply)}
            >
              <b>{reply.title}</b>
              <span className="faint">{reply.body}</span>
            </li>
          ))}
        </ul>
      )}
      <div className="qr-foot faint">
        <span>
          <kbd>↑</kbd> <kbd>↓</kbd> · <kbd>Enter</kbd> — qo'yish · <kbd>Esc</kbd> — yopish
        </span>
        <Link to="/quick-replies">Boshqarish</Link>
      </div>
    </div>
  )
}

function PatientPanel({
  conversation,
  open,
  record,
  conversationId,
  flash,
  onRecord,
}: {
  conversation: Conversation
  open: boolean
  record: DirectMessage | null
  conversationId: string
  flash: number
  onRecord: (messageId: string, session: ConsultationSession) => void
}) {
  const consultation = conversation.consultation
  const patient = conversation.patient
  const recordBlock = useRef<HTMLDivElement>(null)
  // The same query the history below reads, so this costs nothing extra.
  const history = usePatientHistory(conversationId, Boolean(consultation))

  // A record opened from the thread or the history is brought into view where it opens.
  useEffect(() => {
    if (flash) recordBlock.current?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
  }, [flash])

  return (
    <div className="side-body">
      <div className="side-person">
        <Avatar name={patientName(conversation)} tint={conversation.tint} url={patientPhoto(conversation, history.data)} size={44} />
        <div>
          <b>{patientName(conversation)}</b>
          {patient && <div className="faint">{patientLine(conversation)}</div>}
        </div>
      </div>

      <dl className="details">
        {patient?.age != null && (
          <>
            <dt>Yoshi</dt>
            <dd>{patient.age} yosh</dd>
          </>
        )}
        {patient && (
          <>
            <dt>Hayot davri</dt>
            <dd>{lifeStageLabel(patient.lifeStage)}</dd>
          </>
        )}
        {consultation && (
          <>
            <dt>Ochilgan</dt>
            <dd>{formatDateTime(consultation.openedAt)}</dd>
            <dt>{open ? 'Tugaydi' : 'Yopilgan'}</dt>
            <dd>{formatDateTime(open ? consultation.expiresAt : (consultation.closedAt ?? consultation.expiresAt))}</dd>
            {(consultation.priceMinor ?? 0) > 0 && (
              <>
                <dt>To'lov</dt>
                <dd>
                  {formatSom(consultation.priceMinor)}{' '}
                  <span className={`badge ${paymentLabel(consultation.payment).tone}`}>{paymentLabel(consultation.payment).text}</span>
                </dd>
              </>
            )}
          </>
        )}
      </dl>

      {consultation && <PatientNoteBox key={conversationId} conversationId={conversationId} />}
      {consultation && <PatientHistoryList conversationId={conversationId} onRecord={onRecord} />}

      <h3 className="side-title">📋 Tibbiy karta</h3>
      <div ref={recordBlock}>
        {record ? (
          <div key={flash} className={flash ? 'side-record flash-in' : 'side-record'}>
            <RecordPanel conversationId={conversationId} message={record} open={open} />
          </div>
        ) : (
          <p className="faint" style={{ margin: 0 }}>
            Bemor hali tibbiy kartasini biriktirmagan. Biriktirsa, u shu yerda ochiladi.
          </p>
        )}
      </div>
    </div>
  )
}

/**
 * Her note on this patient — what to ask next time, what she already tried. The server
 * keeps it for her alone: never the patient, never staff. Saving it empty deletes it.
 */
function PatientNoteBox({ conversationId }: { conversationId: string }) {
  const note = usePatientNote(conversationId)
  const save = useSavePatientNote(conversationId)
  const { notify } = useToast()
  // Null until she types, so the note that arrives from the server fills the box by itself.
  const [text, setText] = useState<string | null>(null)
  const saved = note.data?.body ?? ''
  const value = text ?? saved
  const dirty = text !== null && text.trim() !== saved
  const tooLong = value.length > NOTE_MAX

  function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!dirty || tooLong || save.isPending) return
    save.mutate(value.trim(), {
      onSuccess: (kept) => {
        setText(null)
        notify(kept.body ? 'Eslatma saqlandi' : "Eslatma o'chirildi")
      },
      onError: (error) => notify(messageOf(error), 'error'),
    })
  }

  return (
    <form className="side-note" onSubmit={submit}>
      <div className="side-note-head">
        <h3 className="side-title">🔒 Shaxsiy eslatma</h3>
        <span className="faint">Faqat siz ko'rasiz</span>
      </div>
      {note.isPending ? (
        <Loading rows={1} height={64} />
      ) : note.isError && !note.data ? (
        <ErrorNotice error={note.error} onRetry={() => void note.refetch()} />
      ) : (
        <>
          <textarea
            aria-label="Shaxsiy eslatma"
            rows={3}
            value={value}
            maxLength={NOTE_MAX}
            placeholder="Keyingi safar nimani so'rash, nima tavsiya qilingan…"
            onChange={(event) => setText(event.target.value)}
          />
          <div className="side-note-foot">
            <span className="faint">
              {dirty ? 'Saqlanmagan' : note.data?.updatedAt ? `Saqlangan: ${formatDateTime(note.data.updatedAt)}` : ''}
            </span>
            <span className="row" style={{ gap: 6 }}>
              {value.length > NOTE_MAX * 0.8 && <Counter length={value.length} max={NOTE_MAX} />}
              {dirty && (
                <button type="button" className="btn ghost small" onClick={() => setText(null)} disabled={save.isPending}>
                  Bekor
                </button>
              )}
              <button type="submit" className="btn small primary" disabled={!dirty || tooLong || save.isPending}>
                {save.isPending && <Spinner />}
                Saqlash
              </button>
            </span>
          </div>
        </>
      )}
    </form>
  )
}

const stars = (rating: number) => '★'.repeat(Math.max(0, Math.min(5, rating))) + '☆'.repeat(Math.max(0, 5 - rating))

/** Every window she has had with this patient, newest first. */
function PatientHistoryList({
  conversationId,
  onRecord,
}: {
  conversationId: string
  onRecord: (messageId: string, session: ConsultationSession) => void
}) {
  const history = usePatientHistory(conversationId)
  const sessions = [...(history.data?.sessions ?? [])].reverse()
  return (
    <section className="side-history" aria-label="Konsultatsiyalar tarixi">
      <h3 className="side-title">🕘 Tarix</h3>
      {history.isPending ? (
        <Loading rows={2} height={48} />
      ) : history.isError && !history.data ? (
        <ErrorNotice error={history.error} onRetry={() => void history.refetch()} />
      ) : !sessions.length ? (
        <p className="faint" style={{ margin: 0 }}>
          Hali konsultatsiya bo'lmagan.
        </p>
      ) : (
        <ol className="hist-list">
          {sessions.map((session, index) => {
            const payment = paymentLabel(session.payment)
            const current = index === 0 && !session.closedAt && Date.parse(session.expiresAt ?? '') > Date.now()
            const reply =
              session.firstReplyAt && session.openedAt
                ? (Date.parse(session.firstReplyAt) - Date.parse(session.openedAt)) / 60_000
                : null
            return (
              <li key={session.id} className="hist-item">
                <div className="hist-top">
                  <b title={formatDateTime(session.openedAt)}>{formatDate(session.openedAt)}</b>
                  <span className={`badge ${payment.tone}`}>
                    {payment.text}
                    {session.priceMinor > 0 ? ` · ${formatSom(session.priceMinor)}` : ''}
                  </span>
                  {current && <span className="badge ok">Hozirgi</span>}
                </div>
                <div className="faint hist-meta">
                  {current
                    ? 'Ochiq'
                    : ((session.closedReason && closedReasonLabels[session.closedReason]) ?? 'Yopilgan')}
                  {' · '}
                  {reply !== null ? `birinchi javob: ${durationLabel(reply)}` : "javob yozilmagan"}
                </div>
                {session.summary && (
                  <p className="hist-summary">
                    <span className="faint">Tavsiya: </span>
                    {session.summary}
                  </p>
                )}
                {session.rating != null && (
                  <div className="hist-rating">
                    <span className="stars" aria-label={`Baho: ${session.rating} / 5`}>
                      {stars(session.rating)}
                    </span>
                    {session.review && <span className="muted"> «{session.review}»</span>}
                  </div>
                )}
                {session.recordMessageIds.length > 0 && (
                  <div className="hist-records">
                    {session.recordMessageIds.map((messageId, at) => (
                      <button
                        key={messageId}
                        type="button"
                        className="btn ghost small"
                        onClick={() => onRecord(messageId, session)}
                      >
                        📋 Karta{session.recordMessageIds.length > 1 ? ` ${at + 1}` : ''}
                      </button>
                    ))}
                  </div>
                )}
              </li>
            )
          })}
        </ol>
      )}
    </section>
  )
}

function RecordPanel({ conversationId, message, open }: { conversationId: string; message: DirectMessage; open: boolean }) {
  const record = usePatientRecord(conversationId, message.id, open)
  if (record.isPending) return <Loading rows={4} height={48} />
  if (record.isError) return <ErrorNotice error={record.error} onRetry={() => void record.refetch()} />
  if (record.data.kind === 'closed') {
    return <div className="notice">Konsultatsiya yopilgan — karta endi ko'rinmaydi</div>
  }
  return (
    <>
      <div className="faint">Biriktirilgan: {formatDateTime(message.createdAt)}</div>
      <PatientRecord record={record.data.summary} />
    </>
  )
}

/**
 * Ends the consultation, with her advice if she writes one — the patient keeps it as her
 * doctor's word on this visit. For a window that already ended without it, the same
 * dialog only writes the advice.
 */
function CloseConsultationDialog({
  conversationId,
  name,
  mode,
  onClose,
}: {
  conversationId: string
  name: string
  mode: 'close' | 'summary'
  onClose: () => void
}) {
  const close = useCloseConsultation(conversationId)
  const { notify } = useToast()
  const [summary, setSummary] = useState('')
  const [error, setError] = useState<string | null>(null)
  const closing = mode === 'close'
  const tooLong = summary.trim().length > SUMMARY_MAX
  const ready = !close.isPending && !tooLong && (closing || summary.trim().length > 0)

  function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!ready) return
    setError(null)
    close.mutate(summary, {
      onSuccess: () => {
        notify(closing ? 'Konsultatsiya yakunlandi' : 'Tavsiya yuborildi')
        onClose()
      },
      onError: (failure) => {
        notify(messageOf(failure), 'error')
        setError(fieldsOf(failure).summary ?? null)
      },
    })
  }

  return (
    <Modal title={closing ? 'Konsultatsiyani yakunlash' : 'Tavsiya yozish'} wide onClose={onClose}>
      <form className="grid" style={{ gap: 12 }} onSubmit={submit}>
        {closing ? (
          <p className="muted" style={{ margin: 0 }}>
            {name} bilan konsultatsiya hozir yopiladi: ikkalangiz ham yangi xabar yoza olmaysiz va uning tibbiy kartasi
            sizga ko'rinmay qoladi. Bemor xohlasa, yangi konsultatsiya ochadi.
          </p>
        ) : (
          <p className="muted" style={{ margin: 0 }}>
            Konsultatsiya tavsiyasiz yopilgan. {name} tavsiyangizni ilovada ko'radi. Uni bir marta yozish mumkin.
          </p>
        )}
        <Field
          label={closing ? "Tavsiya (bemor ko'radi, ixtiyoriy)" : "Tavsiya (bemor ko'radi)"}
          error={error ?? (tooLong ? `Eng ko'pi ${SUMMARY_MAX} belgi` : null)}
          hint={<Counter length={summary.length} max={SUMMARY_MAX} />}
        >
          <textarea
            rows={5}
            value={summary}
            maxLength={SUMMARY_MAX}
            placeholder="Qisqacha xulosa va keyingi qadamlar: tahlillar, dori, qachon qayta yozish…"
            onChange={(event) => {
              setSummary(event.target.value)
              setError(null)
            }}
          />
        </Field>
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <button type="button" className="btn ghost" onClick={onClose} disabled={close.isPending}>
            Bekor qilish
          </button>
          <button type="submit" className={`btn ${closing ? 'danger' : 'primary'}`} disabled={!ready}>
            {close.isPending && <Spinner />}
            {closing ? 'Yakunlash' : 'Yuborish'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function ReportDialog({ conversationId, onClose }: { conversationId: string; onClose: () => void }) {
  const report = useReportConversation(conversationId)
  const { notify } = useToast()
  const [reason, setReason] = useState<ReportReason>('abuse')
  const [note, setNote] = useState('')

  function submit(event: React.FormEvent) {
    event.preventDefault()
    if (report.isPending || note.length > limits.reportNoteMax) return
    const trimmed = note.trim()
    report.mutate(trimmed ? { reason, note: trimmed } : { reason }, {
      onSuccess: () => {
        notify('Shikoyat yuborildi')
        onClose()
      },
      onError: (error) => notify(messageOf(error), 'error'),
    })
  }

  return (
    <Modal title="Shikoyat" onClose={onClose}>
      <form className="grid" style={{ gap: 12 }} onSubmit={submit}>
        <Field label="Sabab">
          <select value={reason} onChange={(event) => setReason(event.target.value as ReportReason)}>
            {reportReasonOrder.map((key) => (
              <option key={key} value={key}>
                {reportReasonLabels[key]}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Izoh (ixtiyoriy)" hint={<Counter length={note.length} max={limits.reportNoteMax} />}>
          <textarea rows={3} value={note} maxLength={limits.reportNoteMax} onChange={(event) => setNote(event.target.value)} />
        </Field>
        <p className="faint" style={{ margin: 0 }}>
          Shikoyat bemorning oxirgi xabariga yuboriladi. Moderator faqat shu xabarni va uning atrofidagi bir necha
          xabarni ko'radi.
        </p>
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <button type="button" className="btn ghost" onClick={onClose} disabled={report.isPending}>
            Bekor qilish
          </button>
          <button type="submit" className="btn danger" disabled={report.isPending}>
            {report.isPending && <Spinner />}
            Shikoyat yuborish
          </button>
        </div>
      </form>
    </Modal>
  )
}
