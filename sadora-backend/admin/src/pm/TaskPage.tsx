import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/toast'
import { Card, ConfirmDialog, Empty, ErrorNotice, Field, formatDate, formatDateTime, Loading } from '../components/ui'
import { useAddPmComment, useArchivePmTask, useDeletePmComment, useDeletePmTask, usePmMeta, usePmTask, useUpdatePmTask } from './api'
import { describeEvent, priorityLabels, statusLabels, typeLabels } from './board'
import {
  ComponentChips,
  epicOptions,
  LabelInput,
  OptionalSelect,
  peopleOptions,
  personName,
  PrioritySelect,
  releaseOptions,
  StatusSelect,
  TypeSelect,
} from './fields'
import { Markdown, MarkdownEditor } from './MarkdownView'
import { TaskForm } from './TaskForm'
import type { PmClearable, PmMeta, PmTaskDetail, UpdatePmTask } from './types'

/**
 * One task: its words on the left, its fields on the right, then subtasks, comments, the
 * commits that named it and what happened to it. Every field saves on change.
 */
export function TaskPage() {
  const { key } = useParams()
  const task = usePmTask(key?.toUpperCase())
  const meta = usePmMeta()

  if (task.isLoading) return <Loading rows={6} />
  if (task.error) return <ErrorNotice error={task.error} />
  if (!task.data) return <Empty>Task topilmadi.</Empty>
  return <TaskView task={task.data} meta={meta.data} />
}

function TaskView({ task, meta }: { task: PmTaskDetail; meta?: PmMeta }) {
  const update = useUpdatePmTask()
  const { notify } = useToast()
  const [editingTitle, setEditingTitle] = useState(false)
  const [title, setTitle] = useState(task.title)
  const [editingDescription, setEditingDescription] = useState(false)
  const [description, setDescription] = useState(task.description)
  const [addingSubtask, setAddingSubtask] = useState(false)

  function change(body: UpdatePmTask, done?: () => void) {
    update.mutate(
      { key: task.key, body },
      {
        onSuccess: () => done?.(),
        onError: (error) => notify(error instanceof Error ? error.message : 'Saqlanmadi', 'error'),
      },
    )
  }

  /** An optional field: a value sets it, nothing clears it. */
  function changeOptional(field: PmClearable, value: string | undefined) {
    change(value ? { [field]: value } : { clear: [field] })
  }

  return (
    <div className="grid" style={{ gap: 16 }}>
      <div className="row" style={{ gap: 8 }}>
        <Link to="/pm" className="faint">
          ← Doska
        </Link>
        {task.parentKey && (
          <>
            <span className="faint">/</span>
            <Link to={`/pm/${task.parentKey}`} className="pm-key">
              {task.parentKey}
            </Link>
          </>
        )}
      </div>

      {task.archived && <div className="notice">Bu task arxivda. Doskada ko'rinmaydi.</div>}

      <div className="pm-task-layout">
        <div className="grid" style={{ gap: 16, alignContent: 'start' }}>
          <Card>
            <div className="row" style={{ gap: 8, marginBottom: 8 }}>
              <span className="pm-key">{task.key}</span>
              <span className={`badge ${typeLabels[task.type].tone}`}>{typeLabels[task.type].text}</span>
              <span className={`badge ${priorityLabels[task.priority].tone}`}>{priorityLabels[task.priority].text}</span>
              <span className="badge free">{statusLabels[task.status]}</span>
            </div>
            {editingTitle ? (
              <form
                className="row"
                onSubmit={(event) => {
                  event.preventDefault()
                  change({ title }, () => setEditingTitle(false))
                }}
              >
                <input value={title} maxLength={200} onChange={(event) => setTitle(event.target.value)} style={{ flex: 1 }} autoFocus aria-label="Sarlavha" />
                <button className="btn primary small" type="submit" disabled={!title.trim()}>
                  Saqlash
                </button>
                <button className="btn ghost small" type="button" onClick={() => setEditingTitle(false)}>
                  Bekor
                </button>
              </form>
            ) : (
              <h2 className="pm-task-title">
                {task.title}{' '}
                <button
                  className="link-btn faint"
                  onClick={() => {
                    setTitle(task.title)
                    setEditingTitle(true)
                  }}
                >
                  tahrirlash
                </button>
              </h2>
            )}
          </Card>

          <Card
            title="Tavsif"
            action={
              !editingDescription && (
                <button
                  className="btn ghost small"
                  onClick={() => {
                    setDescription(task.description)
                    setEditingDescription(true)
                  }}
                >
                  Tahrirlash
                </button>
              )
            }
          >
            {editingDescription ? (
              <div className="grid" style={{ gap: 10 }}>
                <MarkdownEditor label="Tavsif" value={description} onChange={setDescription} rows={14} autoFocus />
                <div className="row" style={{ justifyContent: 'flex-end' }}>
                  <button className="btn ghost" onClick={() => setEditingDescription(false)}>
                    Bekor qilish
                  </button>
                  <button className="btn primary" disabled={update.isPending} onClick={() => change({ description }, () => setEditingDescription(false))}>
                    Saqlash
                  </button>
                </div>
              </div>
            ) : (
              <Markdown text={task.description} empty="Tavsif yozilmagan." />
            )}
          </Card>

          {!task.parentKey && (
            <Card
              title={`Subtasklar${task.subtasks.length ? ` · ${task.subtasks.filter((sub) => sub.status === 'done').length}/${task.subtasks.length}` : ''}`}
              action={
                <button className="btn ghost small" onClick={() => setAddingSubtask(true)}>
                  + Subtask
                </button>
              }
            >
              {task.subtasks.length ? (
                <ul className="pm-subtasks">
                  {task.subtasks.map((sub) => (
                    <li key={sub.id} className={sub.status === 'done' ? 'done' : ''}>
                      <Link to={`/pm/${sub.key}`} className="pm-key">
                        {sub.key}
                      </Link>
                      <Link to={`/pm/${sub.key}`}>{sub.title}</Link>
                      <span className="spacer" />
                      <span className="faint">{statusLabels[sub.status]}</span>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="faint">Subtask yo'q.</p>
              )}
            </Card>
          )}

          <Comments task={task} meta={meta} />

          {(task.mentionedIn ?? []).length > 0 && (
            <Card title="Hujjatlarda">
              <ul className="pm-commits">
                {(task.mentionedIn ?? []).map((doc) => (
                  <li key={doc.id}>
                    <Link to={`/pm/docs/${doc.id}`}>{doc.title}</Link>
                    <span className="faint">{doc.snippet}</span>
                  </li>
                ))}
              </ul>
            </Card>
          )}

          {task.commits.length > 0 && (
            <Card title="Commitlar">
              <ul className="pm-commits">
                {task.commits.map((commit) => (
                  <li key={commit.sha}>
                    <a href={commit.url} target="_blank" rel="noopener noreferrer" className="mono">
                      {commit.sha.slice(0, 7)}
                    </a>
                    <span>{commit.message.split('\n')[0]}</span>
                    <span className="faint">
                      {commit.branch} · {commit.author} · {formatDateTime(commit.committedAt)}
                    </span>
                  </li>
                ))}
              </ul>
            </Card>
          )}

          <Card title="Tarix">
            <ul className="pm-history">
              {task.events
                .slice()
                .reverse()
                .map((event, index) => (
                  <li key={index}>
                    <span className="faint">{formatDateTime(event.at)}</span> {personName(meta, event.actorId)} {describeEvent(event, (id) => personName(meta, id))}
                  </li>
                ))}
            </ul>
          </Card>
        </div>

        <aside className="grid pm-task-side" style={{ gap: 12, alignContent: 'start' }}>
          <Card>
            <div className="grid" style={{ gap: 12 }}>
              <Field label="Holat">
                <StatusSelect value={task.status} onChange={(status) => change({ status })} />
              </Field>
              <Field label="Ijrochi">
                <OptionalSelect value={task.assigneeId} onChange={(value) => changeOptional('assigneeId', value)} options={peopleOptions(meta)} none="Hech kim" />
              </Field>
              <Field label="Turi">
                <TypeSelect value={task.type} onChange={(type) => change({ type })} />
              </Field>
              <Field label="Prioritet">
                <PrioritySelect value={task.priority} onChange={(priority) => change({ priority })} />
              </Field>
              <Field label="Epic">
                <OptionalSelect value={task.epicId} onChange={(value) => changeOptional('epicId', value)} options={epicOptions(meta)} none="Epicsiz" />
              </Field>
              <Field label="Reliz">
                <OptionalSelect value={task.releaseId} onChange={(value) => changeOptional('releaseId', value)} options={releaseOptions(meta)} none="Relizsiz" />
              </Field>
              <Field label="Muddat">
                <input type="date" value={task.dueDate ?? ''} onChange={(event) => changeOptional('dueDate', event.target.value || undefined)} />
              </Field>
              <div className="field">
                <span className="pm-editor-label">Komponentlar</span>
                <ComponentChips value={task.components} onChange={(components) => change({ components })} components={meta?.components ?? []} />
              </div>
              <Field label="Labellar">
                <LabelInput value={task.labels} onChange={(labels) => change({ labels })} />
              </Field>
            </div>
          </Card>
          <Card>
            <div className="grid faint" style={{ gap: 4, fontSize: 12 }}>
              <span>Muallif: {personName(meta, task.reporterId)}</span>
              <span>Yaratilgan: {formatDateTime(task.createdAt)}</span>
              <span>Yangilangan: {formatDateTime(task.updatedAt)}</span>
              {task.doneAt && <span>Tayyor bo'lgan: {formatDate(task.doneAt)}</span>}
            </div>
          </Card>
          <Danger task={task} />
        </aside>
      </div>

      {addingSubtask && (
        <TaskForm stay onClose={() => setAddingSubtask(false)} initial={{ parentKey: task.key, status: 'todo', components: task.components }} />
      )}
    </div>
  )
}

function Comments({ task, meta }: { task: PmTaskDetail; meta?: PmMeta }) {
  const add = useAddPmComment()
  const remove = useDeletePmComment()
  const { can } = useAuth()
  const [draft, setDraft] = useState('')
  const lead = can(['OWNER', 'ADMIN'])
  return (
    <Card title={`Izohlar${task.comments.length ? ` · ${task.comments.length}` : ''}`}>
      <div className="grid" style={{ gap: 12 }}>
        {task.comments.map((comment) => (
          <div key={comment.id} className="pm-comment">
            <div className="row" style={{ gap: 8 }}>
              <strong>{personName(meta, comment.authorId)}</strong>
              <span className="faint">{formatDateTime(comment.createdAt)}</span>
              <span className="spacer" />
              {(lead || comment.authorId === meta?.me.id) && (
                <button className="link-btn faint" onClick={() => remove.mutate({ key: task.key, id: comment.id })}>
                  o'chirish
                </button>
              )}
            </div>
            <Markdown text={comment.body} />
          </div>
        ))}
        <MarkdownEditor label="Yangi izoh" value={draft} onChange={setDraft} rows={4} placeholder="Izoh yozing. SAD-12 — havola, rasmni paste qilish mumkin." />
        <ErrorNotice error={add.error} />
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <button
            className="btn primary small"
            disabled={!draft.trim() || add.isPending}
            onClick={() => add.mutate({ key: task.key, body: draft }, { onSuccess: () => setDraft('') })}
          >
            Izoh qo'shish
          </button>
        </div>
      </div>
    </Card>
  )
}

function Danger({ task }: { task: PmTaskDetail }) {
  const archive = useArchivePmTask()
  const remove = useDeletePmTask()
  const { can } = useAuth()
  const navigate = useNavigate()
  const { notify } = useToast()
  const [confirming, setConfirming] = useState(false)
  return (
    <Card>
      <div className="grid" style={{ gap: 8 }}>
        <button
          className="btn ghost small"
          disabled={archive.isPending}
          onClick={() =>
            archive.mutate(
              { key: task.key, archived: !task.archived },
              { onSuccess: () => notify(task.archived ? 'Arxivdan qaytarildi' : 'Arxivlandi', 'info') },
            )
          }
        >
          {task.archived ? 'Arxivdan qaytarish' : 'Arxivlash'}
        </button>
        {task.archived && can(['OWNER']) && (
          <button className="btn small danger" onClick={() => setConfirming(true)}>
            Butunlay o'chirish
          </button>
        )}
        <ErrorNotice error={archive.error} />
      </div>
      {confirming && (
        <ConfirmDialog
          title={`${task.key} butunlay o'chirilsinmi?`}
          confirmLabel="O'chirish"
          cancelLabel="Qoldirish"
          danger
          pending={remove.isPending}
          onClose={() => setConfirming(false)}
          onConfirm={() =>
            remove.mutate(task.key, {
              onSuccess: () => {
                notify(`${task.key} o'chirildi`, 'info')
                navigate('/pm?view=archive')
              },
            })
          }
        >
          <p>Subtasklari, izohlari va tarixi bilan birga yo'qoladi. Qaytarib bo'lmaydi.</p>
          <ErrorNotice error={remove.error} />
        </ConfirmDialog>
      )}
    </Card>
  )
}
