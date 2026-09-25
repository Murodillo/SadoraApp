import { useEffect, useState } from 'react'
import { fieldsOf, messageOf } from '../api/client'
import { QUESTIONS_LIMIT, useAnswer, useDoctorProfile, useQuestions } from '../api/hooks'
import { limits } from '../api/limits'
import type { CommunityPost, CommunityTopic } from '../api/types'
import { useApprovedDoctor } from '../auth/doctor'
import { CommentThread } from '../components/CommentThread'
import { ANSWER_DISCLAIMER, topicLabel, topicLabels, topicOrder } from '../components/labels'
import { usePresence } from '../components/presence'
import { useToast } from '../components/toast'
import {
  Avatar,
  Card,
  Counter,
  Empty,
  ErrorNotice,
  Field,
  formatAgo,
  formatDateTime,
  formatTime,
  Loading,
  Segmented,
  Spinner,
  Stat,
  SuccessMark,
  SwapPanel,
  VerifiedMark,
} from '../components/ui'

type Filter = CommunityTopic | 'all'

const filterItems: { key: Filter; label: string }[] = [
  { key: 'all', label: 'Hammasi' },
  ...topicOrder.map((topic) => ({ key: topic, label: topicLabels[topic] })),
]

const questionKey = (question: CommunityPost) => question.id

/**
 * The work list: questions in the chat no doctor has answered yet, one open at a time.
 *
 * The open question is held as a copy, not only as an id. Answering takes it off the
 * server's list — that is what "answered" means there — but she should still see her
 * answer land in the thread before she moves on, so the copy stays open until she picks
 * the next one. On the list it plays its exit instead of vanishing.
 */
export function QuestionsPage() {
  const doctor = useApprovedDoctor()
  const [filter, setFilter] = useState<Filter>('all')
  // Every topic, for the header count — the same query the rail's badge reads.
  const everything = useQuestions()
  const questions = useQuestions(filter === 'all' ? undefined : filter)
  const profile = useDoctorProfile(doctor.profileId)
  const shown = usePresence(questions.data, questionKey, { resetKey: filter })
  const [selected, setSelected] = useState<CommunityPost | null>(null)
  const [drafts, setDrafts] = useState<Record<string, string>>({})
  const [answered, setAnswered] = useState<ReadonlySet<string>>(new Set())

  // Keep the open copy current while it is on the list, and open the first question
  // when nothing is open — the list is a queue, and the top of it is where work starts.
  useEffect(() => {
    const items = questions.data
    if (!items) return
    setSelected((current) => {
      if (current) return items.find((item) => item.id === current.id) ?? current
      return items[0] ?? null
    })
  }, [questions.data])

  function changeFilter(next: Filter) {
    setFilter(next)
    setSelected(null)
  }

  const list = questions.data ?? []
  const next = list.find((item) => item.id !== selected?.id) ?? null
  const waiting = everything.data?.length

  return (
    <div className="grid" style={{ gap: 16 }}>
      <div className="grid stat-row">
        <Stat
          label="Javob kutmoqda"
          value={waiting === undefined ? '—' : waiting >= QUESTIONS_LIMIT ? `${QUESTIONS_LIMIT}+` : waiting}
          hint="barcha bo'limlarda"
        />
        <Stat
          label="Javoblaringiz"
          value={profile.data?.answerCount ?? '—'}
          hint={answered.size ? `shu safar +${answered.size}` : 'chatda jami'}
        />
        <Stat label="Postlaringiz" value={profile.data?.postCount ?? '—'} hint="ochiq sahifangizda" />
      </div>

      <div className="row" style={{ justifyContent: 'space-between' }}>
        <Segmented<Filter> label="Bo'lim" items={filterItems} value={filter} onChange={changeFilter} />
        <span className={`faint live${questions.isError ? ' stale' : ''}`}>
          {questions.isFetching ? 'Yangilanmoqda…' : `Yangilangan ${formatTime(questions.dataUpdatedAt)}`} · har daqiqada
        </span>
      </div>

      <div className="workspace">
        <section className="card question-list-card" aria-label="Savollar ro'yxati">
          {questions.isPending ? (
            <Loading rows={6} height={72} />
          ) : questions.isError ? (
            <ErrorNotice error={questions.error} onRetry={() => void questions.refetch()} />
          ) : !shown.length ? (
            <Empty>
              Hozircha javob kutayotgan savol yo'q.
              <div className="faint">Yangi savollar o'zi paydo bo'ladi.</div>
            </Empty>
          ) : (
            <>
              <div className="list-head faint">{list.length} ta javobsiz savol · eng yangisi tepada</div>
              <ul className="question-list">
                {shown.map(({ item: question, leaving }) => (
                  <li key={question.id} className={leaving ? 'leaving' : undefined} aria-hidden={leaving || undefined}>
                    <QuestionItem
                      question={question}
                      active={question.id === selected?.id}
                      leaving={leaving}
                      answered={answered.has(question.id)}
                      drafted={Boolean(drafts[question.id]?.trim())}
                      onOpen={() => setSelected(question)}
                    />
                  </li>
                ))}
              </ul>
            </>
          )}
        </section>

        {selected ? (
          <SwapPanel id={selected.id}>
            <QuestionDetail
              question={selected}
              answered={answered.has(selected.id)}
              draft={drafts[selected.id] ?? ''}
              onDraft={(text) => setDrafts((current) => ({ ...current, [selected.id]: text }))}
              onAnswered={() => {
                const id = selected.id
                setDrafts((current) => {
                  const rest = { ...current }
                  delete rest[id]
                  return rest
                })
                setAnswered((current) => new Set(current).add(id))
              }}
              onNext={next ? () => setSelected(next) : undefined}
            />
          </SwapPanel>
        ) : (
          <Card>
            <Empty>Javob berish uchun chapdagi ro'yxatdan savolni tanlang.</Empty>
          </Card>
        )}
      </div>
    </div>
  )
}

function QuestionItem({
  question,
  active,
  leaving,
  answered,
  drafted,
  onOpen,
}: {
  question: CommunityPost
  active: boolean
  leaving: boolean
  answered: boolean
  drafted: boolean
  onOpen: () => void
}) {
  return (
    <button
      type="button"
      className={`q-item${active ? ' active' : ''}`}
      aria-current={active ? 'true' : undefined}
      disabled={leaving}
      tabIndex={leaving ? -1 : undefined}
      onClick={onOpen}
    >
      <span className="q-meta">
        <span className="badge free">{topicLabel(question.topic)}</span>
        {leaving && answered ? (
          <span className="badge ok">✓ Javob berildi</span>
        ) : (
          drafted && <span className="badge warn">Qoralama</span>
        )}
        <span className="faint" title={formatDateTime(question.createdAt)}>
          {formatAgo(question.createdAt)}
        </span>
      </span>
      <span className="q-body">{question.body}</span>
      <span className="q-foot faint">
        {question.alias} · {question.commentCount} izoh
      </span>
    </button>
  )
}

function QuestionDetail({
  question,
  answered,
  draft,
  onDraft,
  onAnswered,
  onNext,
}: {
  question: CommunityPost
  answered: boolean
  draft: string
  onDraft: (text: string) => void
  onAnswered: () => void
  onNext?: () => void
}) {
  return (
    <>
      <Card>
        <article className="question">
          <header className="question-head">
            <Avatar name={question.alias} tint={question.tint} />
            <div>
              <b>{question.alias}</b>
              <div className="faint">
                {topicLabel(question.topic)} · <span title={formatDateTime(question.createdAt)}>{formatAgo(question.createdAt)}</span>
              </div>
            </div>
          </header>
          <p className="post-body">{question.body}</p>
          <div className="faint">
            {question.likeCount} yoqdi · {question.commentCount} izoh
            {question.doctorAnswers > 0 && ` · ${question.doctorAnswers} shifokor javob bergan`}
          </div>
        </article>
      </Card>

      {answered && (
        <div className="answer-success">
          <SuccessMark />
          <div className="answer-success-text">
            <b>Javobingiz yuborildi</b>
            <span className="faint">Savol ro'yxatdan olindi — javob chatda ismingiz va ✓ belgisi bilan ko'rinadi.</span>
          </div>
          {onNext && (
            <button className="btn small primary" type="button" onClick={onNext}>
              Keyingi savol →
            </button>
          )}
        </div>
      )}

      <Card title="Izohlar">
        <CommentThread postId={question.id} emptyText="Hali izoh yo'q — birinchi bo'lib javob bering." />
      </Card>

      <Card>
        <AnswerComposer postId={question.id} draft={draft} onDraft={onDraft} onSent={onAnswered} />
      </Card>
    </>
  )
}

const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform || navigator.userAgent)

function AnswerComposer({
  postId,
  draft,
  onDraft,
  onSent,
}: {
  postId: string
  draft: string
  onDraft: (text: string) => void
  onSent: () => void
}) {
  const doctor = useApprovedDoctor()
  const answer = useAnswer()
  const { notify } = useToast()
  const [serverError, setServerError] = useState<string | null>(null)

  const length = draft.trim().length
  const ready = length >= limits.postMin && length <= limits.commentMax

  function send() {
    if (!ready || answer.isPending) return
    setServerError(null)
    answer.mutate(
      { postId, body: draft.trim() },
      {
        onSuccess: () => {
          notify('Javobingiz yuborildi')
          onSent()
        },
        onError: (error) => {
          notify(messageOf(error), 'error')
          setServerError(fieldsOf(error).body ?? null)
        },
      },
    )
  }

  return (
    <form
      className="composer"
      onSubmit={(event) => {
        event.preventDefault()
        send()
      }}
    >
      <Field label="Javobingiz" error={serverError} hint={<Counter length={draft.length} max={limits.commentMax} />}>
        <textarea
          rows={7}
          value={draft}
          maxLength={limits.commentMax}
          placeholder="Savolga shifokor sifatida javob yozing…"
          onChange={(event) => {
            onDraft(event.target.value)
            if (serverError) setServerError(null)
          }}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
              event.preventDefault()
              send()
            }
          }}
        />
      </Field>

      <p className="disclaimer">{ANSWER_DISCLAIMER}</p>

      <div className="row" style={{ justifyContent: 'space-between' }}>
        <span className="faint row" style={{ gap: 6 }}>
          Siz shifokor sifatida yozasiz:{' '}
          <b className="row" style={{ gap: 4 }}>
            {doctor.fullName}
            <VerifiedMark />
          </b>
        </span>
        <span className="row">
          <span className="faint">
            <kbd>{isMac ? '⌘' : 'Ctrl'}</kbd> + <kbd>Enter</kbd>
          </span>
          <button className="btn primary" type="submit" disabled={!ready || answer.isPending}>
            {answer.isPending && <Spinner />}
            {answer.isPending ? 'Yuborilmoqda…' : 'Javob berish'}
          </button>
        </span>
      </div>
    </form>
  )
}
