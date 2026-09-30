import { useState } from 'react'
import { fieldsOf, messageOf } from '../api/client'
import { useDeleteQuickReply, useQuickReplies, useSaveQuickReply } from '../api/hooks'
import { lengthProblem, limits } from '../api/limits'
import type { QuickReply } from '../api/types'
import { matchQuickReplies, QUICK_REPLIES_MAX, QUICK_REPLY_TITLE_MAX } from '../api/work'
import { useToast } from '../components/toast'
import { Card, Counter, Empty, ErrorNotice, Field, Loading, Modal, Spinner } from '../components/ui'

/**
 * "Tayyor javoblar": the lines she writes again and again, kept once. In a chat they are
 * a click away — or "/" and a word — and land in the box for her to finish, never sent
 * on their own.
 */
export function QuickRepliesPage() {
  const replies = useQuickReplies()
  const [editing, setEditing] = useState<QuickReply | 'new' | null>(null)
  const [deleting, setDeleting] = useState<QuickReply | null>(null)

  const list = matchQuickReplies(replies.data ?? [], '')
  const full = list.length >= QUICK_REPLIES_MAX

  return (
    <div className="grid" style={{ gap: 16, maxWidth: 860 }}>
      <Card
        title="Tayyor javoblar"
        action={
          <button
            type="button"
            className="btn primary small"
            onClick={() => setEditing('new')}
            disabled={!replies.data || full}
            title={full ? `Eng ko'pi ${QUICK_REPLIES_MAX} ta` : undefined}
          >
            + Qo'shish
          </button>
        }
      >
        <p className="faint" style={{ marginTop: 0 }}>
          Chatda <kbd>/</kbd> yozing yoki ⚡ tugmasini bosing — javob yozish maydoniga qo'yiladi, o'zingiz tahrirlab
          yuborasiz. {list.length} / {QUICK_REPLIES_MAX}
        </p>
        {replies.isPending ? (
          <Loading rows={3} height={56} />
        ) : !replies.data ? (
          <ErrorNotice error={replies.error} onRetry={() => void replies.refetch()} />
        ) : !list.length ? (
          <Empty>
            Hali tayyor javob yo'q.
            <div className="faint">Masalan: salomlashish, tahlil topshirish tartibi, qabulga yozilish.</div>
          </Empty>
        ) : (
          <ul className="reply-list">
            {list.map((reply) => (
              <li key={reply.id} className="reply-item">
                <div className="reply-main">
                  <b>{reply.title}</b>
                  <p className="reply-body">{reply.body}</p>
                </div>
                <div className="reply-actions">
                  <button type="button" className="btn small" onClick={() => setEditing(reply)} aria-label={`${reply.title}: tahrirlash`}>
                    Tahrirlash
                  </button>
                  <button
                    type="button"
                    className="btn small ghost danger"
                    onClick={() => setDeleting(reply)}
                    aria-label={`${reply.title}: o'chirish`}
                  >
                    O'chirish
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>

      {editing && (
        <QuickReplyDialog
          reply={editing === 'new' ? null : editing}
          position={editing === 'new' ? Math.max(-1, ...list.map((item) => item.position)) + 1 : editing.position}
          onClose={() => setEditing(null)}
        />
      )}
      {deleting && <DeleteDialog reply={deleting} onClose={() => setDeleting(null)} />}
    </div>
  )
}

function QuickReplyDialog({ reply, position, onClose }: { reply: QuickReply | null; position: number; onClose: () => void }) {
  const save = useSaveQuickReply()
  const { notify } = useToast()
  const [title, setTitle] = useState(reply?.title ?? '')
  const [body, setBody] = useState(reply?.body ?? '')
  const [serverErrors, setServerErrors] = useState<Record<string, string>>({})

  const titleError = serverErrors.title ?? lengthProblem(title, 1, QUICK_REPLY_TITLE_MAX)
  const bodyError = serverErrors.body ?? lengthProblem(body, 1, limits.messageMax)

  function submit(event: React.FormEvent) {
    event.preventDefault()
    if (save.isPending || titleError || bodyError) return
    save.mutate(
      { id: reply?.id, body: { title: title.trim(), body: body.trim(), position } },
      {
        onSuccess: () => {
          notify(reply ? 'Saqlandi' : "Tayyor javob qo'shildi")
          onClose()
        },
        onError: (error) => {
          notify(messageOf(error), 'error')
          setServerErrors(fieldsOf(error))
        },
      },
    )
  }

  return (
    <Modal title={reply ? 'Tayyor javobni tahrirlash' : 'Yangi tayyor javob'} wide onClose={onClose}>
      <form className="grid" style={{ gap: 12 }} onSubmit={submit} noValidate>
        <Field label="Sarlavha" error={title ? titleError : serverErrors.title} hint={<Counter length={title.length} max={QUICK_REPLY_TITLE_MAX} />}>
          <input
            value={title}
            maxLength={QUICK_REPLY_TITLE_MAX}
            placeholder="Masalan: Salomlashish"
            onChange={(event) => {
              setTitle(event.target.value)
              if (serverErrors.title) setServerErrors({})
            }}
          />
        </Field>
        <Field label="Matn" error={body ? bodyError : serverErrors.body} hint={<Counter length={body.length} max={limits.messageMax} />}>
          <textarea
            rows={6}
            value={body}
            maxLength={limits.messageMax}
            placeholder="Bemorga yuboriladigan matn…"
            onChange={(event) => {
              setBody(event.target.value)
              if (serverErrors.body) setServerErrors({})
            }}
          />
        </Field>
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <button type="button" className="btn ghost" onClick={onClose} disabled={save.isPending}>
            Bekor qilish
          </button>
          <button type="submit" className="btn primary" disabled={save.isPending || Boolean(titleError || bodyError)}>
            {save.isPending && <Spinner />}
            Saqlash
          </button>
        </div>
      </form>
    </Modal>
  )
}

function DeleteDialog({ reply, onClose }: { reply: QuickReply; onClose: () => void }) {
  const remove = useDeleteQuickReply()
  const { notify } = useToast()
  return (
    <Modal title="Tayyor javobni o'chirish" onClose={onClose}>
      <p className="muted" style={{ margin: 0 }}>
        «{reply.title}» o'chiriladi. Yuborilgan xabarlarga ta'sir qilmaydi.
      </p>
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <button type="button" className="btn ghost" onClick={onClose} disabled={remove.isPending}>
          Bekor qilish
        </button>
        <button
          type="button"
          className="btn danger"
          disabled={remove.isPending}
          onClick={() =>
            remove.mutate(reply.id, {
              onSuccess: () => {
                notify("O'chirildi")
                onClose()
              },
              onError: (error) => notify(messageOf(error), 'error'),
            })
          }
        >
          {remove.isPending && <Spinner />}
          O'chirish
        </button>
      </div>
    </Modal>
  )
}
