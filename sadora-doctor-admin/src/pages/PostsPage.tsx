import { useState } from 'react'
import { fieldsOf, messageOf } from '../api/client'
import { useCreatePost, useDoctorProfile } from '../api/hooks'
import { lengthProblem, limits } from '../api/limits'
import type { CommunityPost, CommunityTopic } from '../api/types'
import { useApprovedDoctor } from '../auth/doctor'
import { CommentThread } from '../components/CommentThread'
import { specialtyLabel, topicLabel, topicLabels, topicOrder } from '../components/labels'
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
  Loading,
  Modal,
  Spinner,
  Stat,
  VerifiedMark,
} from '../components/ui'

/**
 * Her public page as readers see it — the counts and her recent posts — beside the form
 * for a new one. A doctor's post goes into the same chat as everyone's, under her name.
 */
export function PostsPage() {
  const doctor = useApprovedDoctor()
  const profile = useDoctorProfile(doctor.profileId)

  if (!doctor.profileId) {
    return <ErrorNotice error="Ochiq sahifangiz hali tayyor emas. Birozdan keyin qayta oching." />
  }

  return (
    <div className="grid" style={{ gap: 16 }}>
      {profile.isPending ? (
        <Loading rows={1} height={84} />
      ) : profile.isError ? (
        <ErrorNotice error={profile.error} onRetry={() => void profile.refetch()} />
      ) : (
        <div className="grid stat-row">
          <Stat label="Postlar" value={profile.data.postCount} />
          <Stat label="Javoblar" value={profile.data.answerCount} />
          <Stat label="Tajriba" value={`${profile.data.experienceYears} yil`} />
          <Stat label="Tasdiqlangan" value={formatDate(profile.data.verifiedSince)} />
        </div>
      )}

      <div className="two-col reverse">
        <NewPostCard />
        <Card title="So'nggi postlarim">
          {profile.isPending ? (
            <Loading rows={3} height={64} />
          ) : profile.isError ? (
            <Empty>Postlar yuklanmadi.</Empty>
          ) : !profile.data.posts.length ? (
            <Empty>Hali post yozmagansiz. Chapdagi shakl orqali birinchisini yozing.</Empty>
          ) : (
            <ul className="my-posts">
              {profile.data.posts.map((post) => (
                <li key={post.id}>
                  <MyPost post={post} />
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  )
}

/**
 * A new post, shown as the chat will show it before it goes out: under her real name,
 * with the check mark, in the room she picked. There is no taking a post back quietly
 * once readers have seen it, so the last look is part of writing it.
 */
function NewPostCard() {
  const doctor = useApprovedDoctor()
  const create = useCreatePost()
  const { notify } = useToast()
  const [topic, setTopic] = useState<CommunityTopic | ''>('')
  const [body, setBody] = useState('')
  const [attempted, setAttempted] = useState(false)
  const [previewing, setPreviewing] = useState(false)
  const [serverErrors, setServerErrors] = useState<Record<string, string>>({})

  const topicError = serverErrors.topic ?? (attempted && !topic ? "Bo'limni tanlang" : null)
  const bodyError = serverErrors.body ?? (attempted ? lengthProblem(body, limits.postMin, limits.postMax) : null)

  function submit(event: React.FormEvent) {
    event.preventDefault()
    setAttempted(true)
    setServerErrors({})
    if (!topic || lengthProblem(body, limits.postMin, limits.postMax)) return
    setPreviewing(true)
  }

  function publish() {
    if (!topic) return
    create.mutate(
      { topic, body: body.trim() },
      {
        onSuccess: () => {
          setPreviewing(false)
          notify('Post chop etildi')
          setTopic('')
          setBody('')
          setAttempted(false)
        },
        onError: (error) => {
          // Back to the form, where a field's detail can sit next to the field.
          setPreviewing(false)
          notify(messageOf(error), 'error')
          setServerErrors(fieldsOf(error))
        },
      },
    )
  }

  return (
    <Card title="Yangi post">
      <form className="grid" style={{ gap: 12 }} onSubmit={submit} noValidate>
        <Field label="Bo'lim" error={topicError}>
          <select value={topic} onChange={(event) => setTopic(event.target.value as CommunityTopic | '')}>
            <option value="" disabled>
              Bo'limni tanlang
            </option>
            {topicOrder.map((key) => (
              <option key={key} value={key}>
                {topicLabels[key]}
              </option>
            ))}
          </select>
        </Field>

        <Field label="Matn" error={bodyError} hint={<Counter length={body.length} max={limits.postMax} />}>
          <textarea
            rows={9}
            value={body}
            maxLength={limits.postMax}
            placeholder="Ayollarga foydali bo'ladigan maslahat yoki tushuntirish…"
            onChange={(event) => {
              setBody(event.target.value)
              if (serverErrors.body) setServerErrors({})
            }}
          />
        </Field>

        <p className="faint" style={{ margin: 0 }}>
          Post chatda ismingiz va ✓ belgisi bilan chiqadi. Umumiy maslahat bering — tashxis qo'ymang.
        </p>

        <button className="btn primary" type="submit" disabled={create.isPending} style={{ justifySelf: 'end' }}>
          Ko'rib chiqish
        </button>
      </form>

      {previewing && topic && (
        <Modal title="Post chatda shunday ko'rinadi" onClose={() => setPreviewing(false)} wide>
          <article className="preview-post">
            <header className="question-head">
              <Avatar name={doctor.fullName ?? ''} tint={0} doctor />
              <div>
                <b className="row" style={{ gap: 4 }}>
                  {doctor.fullName}
                  <VerifiedMark />
                </b>
                <div className="faint">
                  {specialtyLabel(doctor.specialty)} · {topicLabel(topic)} · hozirgina
                </div>
              </div>
            </header>
            <p className="post-body">{body.trim()}</p>
          </article>
          <div className="row" style={{ justifyContent: 'flex-end' }}>
            <button className="btn ghost" type="button" onClick={() => setPreviewing(false)} disabled={create.isPending}>
              Tahrirlash
            </button>
            <button className="btn primary" type="button" onClick={publish} disabled={create.isPending}>
              {create.isPending && <Spinner />}
              {create.isPending ? 'Yuborilmoqda…' : 'Chop etish'}
            </button>
          </div>
        </Modal>
      )}
    </Card>
  )
}

function MyPost({ post }: { post: CommunityPost }) {
  const [open, setOpen] = useState(false)
  return (
    <article className="my-post">
      <div className="q-meta">
        <span className="badge free">{topicLabel(post.topic)}</span>
        <span className="faint">{formatDateTime(post.createdAt)}</span>
      </div>
      <p className="post-body">{post.body}</p>
      <div className="row faint" style={{ justifyContent: 'space-between' }}>
        <span>
          {post.likeCount} yoqdi · {post.commentCount} izoh
        </span>
        <button className="btn ghost small" type="button" onClick={() => setOpen((value) => !value)} aria-expanded={open}>
          {open ? 'Izohlarni yopish' : "Izohlarni ko'rish"}
        </button>
      </div>
      {open && (
        <div className="my-post-thread">
          <CommentThread postId={post.id} />
        </div>
      )}
    </article>
  )
}
