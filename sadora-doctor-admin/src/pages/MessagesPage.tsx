import { Fragment, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'
import { messageOf, requestBlob } from '../api/client'
import {
  CONVERSATIONS_POLL_MS,
  sendTyping,
  useCloseConsultation,
  useConversations,
  usePatientRecord,
  useReportConversation,
  useSendMessage,
  useThread,
} from '../api/hooks'
import { formatBytes, ImageProblem, prepareImage } from '../api/image'
import type { PreparedImage } from '../api/image'
import { limits } from '../api/limits'
import type { Conversation, DirectMessage, ReportReason } from '../api/types'
import { consultationOpen, timeLeft } from '../api/consultation'
import { lifeStageLabel, reportReasonLabels, reportReasonOrder } from '../components/labels'
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
      <Avatar name={patientName(conversation)} tint={conversation.tint} />
      <span className="conv-main">
        <span className="conv-top">
          <b className="conv-name">{patientName(conversation)}</b>
          <span className="faint conv-time" title={formatDateTime(conversation.lastMessageAt)}>
            {formatListTime(conversation.lastMessageAt, now)}
          </span>
        </span>
        {conversation.patient && <span className="conv-sub faint">{patientLine(conversation)}</span>}
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
  const [confirmClose, setConfirmClose] = useState(false)
  const [reporting, setReporting] = useState(false)
  const [viewing, setViewing] = useState<{ url: string; caption: string } | null>(null)
  const records = messages.filter((message) => message.kind === 'record')
  const [recordId, setRecordId] = useState<string | null>(null)
  const side = useRef<HTMLElement>(null)
  const [sideFlash, setSideFlash] = useState(0)

  const open = conversation ? consultationOpen(conversation, now) : false
  const canReport = messages.some((message) => !message.isMine)
  const shownRecord = records.find((message) => message.id === recordId) ?? records.at(-1) ?? null

  function openRecord(message: DirectMessage) {
    setRecordId(message.id)
    setSideFlash((count) => count + 1)
    side.current?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
  }

  const name = conversation ? patientName(conversation) : ''

  return (
    <>
      <section className="card chat-thread" aria-label="Yozishma">
        <header className="chat-head">
          <button type="button" className="btn ghost small chat-back" onClick={onBack}>
            ← Suhbatlar
          </button>
          {conversation && <Avatar name={name} tint={conversation.tint} />}
          <div className="chat-head-who">
            <b>{name || '…'}</b>
            {conversation?.patient && <div className="faint">{patientLine(conversation)}</div>}
          </div>
          <div className="chat-head-actions">
            {conversation?.consultation && open && (
              <button type="button" className="btn small" onClick={() => setConfirmClose(true)}>
                Konsultatsiyani yakunlash
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

      <aside ref={side} className="card chat-side" aria-label="Bemor">
        {conversation ? (
          <PatientPanel
            conversation={conversation}
            open={open}
            record={shownRecord}
            conversationId={conversationId}
            flash={sideFlash}
          />
        ) : (
          <Loading rows={3} height={40} />
        )}
      </aside>

      {confirmClose && (
        <CloseConsultationDialog conversationId={conversationId} name={name} onClose={() => setConfirmClose(false)} />
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
  const { notify } = useToast()
  const [image, setImage] = useState<PreparedImage | null>(null)
  const [preparing, setPreparing] = useState(false)
  const file = useRef<HTMLInputElement>(null)
  const lastTyping = useRef(0)

  // The preview is an object URL: let it go when it is replaced, sent or left behind.
  useEffect(() => () => {
    if (image) URL.revokeObjectURL(image.previewUrl)
  }, [image])

  const locked = conversation.blocked
    ? "Bu suhbatga yozib bo'lmaydi: bemor bilan aloqa cheklangan."
    : !open
      ? "Konsultatsiya yopilgan — bemor yangisini ochsa, yana yozishingiz mumkin."
      : null

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

  return (
    <form
      className="chat-composer"
      onSubmit={(event) => {
        event.preventDefault()
        submit()
      }}
    >
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
          aria-label="Xabar"
          rows={Math.min(5, Math.max(1, draft.split('\n').length))}
          value={draft}
          maxLength={limits.messageMax}
          disabled={Boolean(locked)}
          placeholder={locked ? '' : image ? 'Rasmga izoh (ixtiyoriy)…' : 'Xabar yozing…'}
          onChange={(event) => {
            const text = event.target.value
            onDraft(text)
            const moment = Date.now()
            if (text.trim() && moment - lastTyping.current >= TYPING_EVERY_MS) {
              lastTyping.current = moment
              void sendTyping(conversationId)
            }
          }}
          onKeyDown={(event) => {
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
            <kbd>Enter</kbd> — yuborish · <kbd>Shift</kbd> + <kbd>Enter</kbd> — yangi qator
          </span>
          <Counter length={draft.length} max={limits.messageMax} />
        </div>
      )}
    </form>
  )
}

function PatientPanel({
  conversation,
  open,
  record,
  conversationId,
  flash,
}: {
  conversation: Conversation
  open: boolean
  record: DirectMessage | null
  conversationId: string
  flash: number
}) {
  const consultation = conversation.consultation
  const patient = conversation.patient
  return (
    <div className="side-body">
      <div className="side-person">
        <Avatar name={patientName(conversation)} tint={conversation.tint} />
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
          </>
        )}
      </dl>

      <h3 className="side-title">📋 Tibbiy karta</h3>
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

function CloseConsultationDialog({ conversationId, name, onClose }: { conversationId: string; name: string; onClose: () => void }) {
  const close = useCloseConsultation(conversationId)
  const { notify } = useToast()
  return (
    <Modal title="Konsultatsiyani yakunlash" onClose={onClose}>
      <p className="muted" style={{ margin: 0 }}>
        {name} bilan konsultatsiya hozir yopiladi: ikkalangiz ham yangi xabar yoza olmaysiz va uning tibbiy kartasi sizga
        ko'rinmay qoladi. Bemor xohlasa, yangi konsultatsiya ochadi.
      </p>
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <button type="button" className="btn ghost" onClick={onClose} disabled={close.isPending}>
          Bekor qilish
        </button>
        <button
          type="button"
          className="btn danger"
          disabled={close.isPending}
          onClick={() =>
            close.mutate(undefined, {
              onSuccess: () => {
                notify('Konsultatsiya yakunlandi')
                onClose()
              },
              onError: (error) => notify(messageOf(error), 'error'),
            })
          }
        >
          {close.isPending && <Spinner />}
          Yakunlash
        </button>
      </div>
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
