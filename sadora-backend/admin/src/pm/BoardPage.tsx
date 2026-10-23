import { useMemo, useState } from 'react'
import type { DragEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useToast } from '../components/toast'
import { Empty, ErrorNotice, formatDate, Loading } from '../components/ui'
import { usePmMeta, usePmTasks, useUpdatePmTask } from './api'
import { columns, componentLabel, dropPosition, overdue, priorityLabels, statusLabels, statusOrder, subtaskProgress, typeLabels } from './board'
import { epicOptions, OptionalSelect, peopleOptions, personName, releaseOptions } from './fields'
import { TaskForm } from './TaskForm'
import type { PmMeta, PmStatus, PmTaskFilters, PmTaskSummary } from './types'

type View = 'board' | 'list' | 'archive'

/**
 * The team's board: five columns from Backlog to Tayyor, cards dragged between and within
 * them. Filters live in the URL, so a filtered board is a link someone can be sent. The
 * list view is the same tasks as a table; the archive is what was put away.
 */
export function BoardPage() {
  const meta = usePmMeta()
  const [params, setParams] = useSearchParams()
  const view = (params.get('view') as View) || 'board'
  const filters: PmTaskFilters = {
    q: params.get('q') ?? undefined,
    epic: params.get('epic') ?? undefined,
    release: params.get('release') ?? undefined,
    assignee: params.get('assignee') ?? undefined,
    component: params.get('component') ?? undefined,
    type: params.get('type') ?? undefined,
    priority: params.get('priority') ?? undefined,
    label: params.get('label') ?? undefined,
    archived: view === 'archive',
  }
  const tasks = usePmTasks(filters)
  const [creating, setCreating] = useState<PmStatus | null>(null)

  function setParam(name: string, value: string | undefined) {
    const next = new URLSearchParams(params)
    if (value) next.set(name, value)
    else next.delete(name)
    setParams(next, { replace: true })
  }

  const filtered = Object.entries(filters).some(([key, value]) => key !== 'archived' && value)

  return (
    <div className="grid" style={{ gap: 12 }}>
      <div className="pm-toolbar">
        <input
          type="search"
          placeholder="Qidirish: so'z yoki SAD-12"
          aria-label="Tasklarni qidirish"
          value={filters.q ?? ''}
          onChange={(event) => setParam('q', event.target.value || undefined)}
        />
        <OptionalSelect value={filters.epic} onChange={(value) => setParam('epic', value)} options={epicOptions(meta.data)} none="Barcha epiclar" />
        <OptionalSelect value={filters.release} onChange={(value) => setParam('release', value)} options={releaseOptions(meta.data)} none="Barcha relizlar" />
        <OptionalSelect
          value={filters.assignee}
          onChange={(value) => setParam('assignee', value)}
          options={[...(meta.data ? [{ id: meta.data.me.id, label: 'Menga tegishli' }] : []), ...peopleOptions(meta.data).filter((person) => person.id !== meta.data?.me.id)]}
          none="Har kim"
        />
        <OptionalSelect
          value={filters.component}
          onChange={(value) => setParam('component', value)}
          options={(meta.data?.components ?? []).map((key) => ({ id: key, label: componentLabel(key) }))}
          none="Barcha komponentlar"
        />
        <OptionalSelect
          value={filters.type}
          onChange={(value) => setParam('type', value)}
          options={Object.entries(typeLabels).map(([id, label]) => ({ id, label: label.text }))}
          none="Har qanday tur"
        />
        <div className="segmented" role="group" aria-label="Ko'rinish">
          {(
            [
              ['board', 'Doska'],
              ['list', "Ro'yxat"],
              ['archive', 'Arxiv'],
            ] as const
          ).map(([key, text]) => (
            <button key={key} type="button" className={view === key ? 'active' : ''} aria-pressed={view === key} onClick={() => setParam('view', key === 'board' ? undefined : key)}>
              {text}
            </button>
          ))}
        </div>
        <button className="btn primary small" onClick={() => setCreating('backlog')}>
          + Task
        </button>
      </div>
      {filtered && (
        <div className="row faint" style={{ gap: 8 }}>
          Filtr yoqilgan.
          <button className="link-btn" onClick={() => setParams(view === 'board' ? {} : { view }, { replace: true })}>
            Tozalash
          </button>
        </div>
      )}

      {tasks.isLoading ? (
        <Loading rows={6} />
      ) : tasks.error ? (
        <ErrorNotice error={tasks.error} />
      ) : view === 'board' ? (
        <Board tasks={tasks.data ?? []} meta={meta.data} onCreate={setCreating} />
      ) : !tasks.data?.length ? (
        <Empty art="ic3d_notebook">{view === 'archive' ? "Arxiv bo'sh." : 'Bu filtrga mos task yo‘q.'}</Empty>
      ) : (
        <TaskTable tasks={tasks.data} meta={meta.data} />
      )}

      {creating && (
        <TaskForm
          stay
          onClose={() => setCreating(null)}
          initial={{ status: creating, epicId: filters.epic, releaseId: filters.release, components: filters.component ? [filters.component] : [] }}
        />
      )}
    </div>
  )
}

function Board({ tasks, meta, onCreate }: { tasks: PmTaskSummary[]; meta?: PmMeta; onCreate: (status: PmStatus) => void }) {
  const grouped = useMemo(() => columns(tasks), [tasks])
  const update = useUpdatePmTask()
  const { notify } = useToast()
  const [dragged, setDragged] = useState<PmTaskSummary | null>(null)
  const [target, setTarget] = useState<{ status: PmStatus; index: number } | null>(null)

  function slotFor(event: DragEvent<HTMLDivElement>, status: PmStatus): number {
    const cards = Array.from(event.currentTarget.querySelectorAll<HTMLElement>('[data-card]'))
    const index = cards.findIndex((card) => {
      const box = card.getBoundingClientRect()
      return event.clientY < box.top + box.height / 2
    })
    return index === -1 ? grouped[status].length : index
  }

  function drop(status: PmStatus, index: number) {
    const task = dragged
    setDragged(null)
    setTarget(null)
    if (!task) return
    const position = dropPosition(grouped[status], task.key, index)
    if (task.status === status && position === task.position) return
    update.mutate(
      { key: task.key, body: { status, position } },
      {
        onSuccess: () => task.status !== status && notify(`${task.key} → ${statusLabels[status]}`, 'info'),
        onError: (error) => notify(error instanceof Error ? error.message : "Ko'chirilmadi", 'error'),
      },
    )
  }

  return (
    <div className="pm-board">
      {statusOrder.map((status) => {
        const cards = grouped[status]
        return (
          <section
            key={status}
            className={`pm-column${target?.status === status ? ' over' : ''}`}
            aria-label={statusLabels[status]}
          >
            <header className="pm-column-head">
              <span>{statusLabels[status]}</span>
              <span className="faint">{cards.length}</span>
              <button className="btn ghost small" aria-label={`${statusLabels[status]} ustuniga task qo'shish`} onClick={() => onCreate(status)}>
                +
              </button>
            </header>
            <div
              className="pm-column-body"
              onDragOver={(event) => {
                if (!dragged) return
                event.preventDefault()
                const index = slotFor(event, status)
                if (target?.status !== status || target.index !== index) setTarget({ status, index })
              }}
              onDragLeave={(event) => {
                if (!event.currentTarget.contains(event.relatedTarget as Node)) setTarget(null)
              }}
              onDrop={(event) => {
                event.preventDefault()
                drop(status, slotFor(event, status))
              }}
            >
              {cards.map((task, index) => (
                <div key={task.id}>
                  {target?.status === status && target.index === index && <div className="pm-drop-line" />}
                  <TaskCard
                    task={task}
                    meta={meta}
                    dragging={dragged?.id === task.id}
                    onDragStart={() => setDragged(task)}
                    onDragEnd={() => {
                      setDragged(null)
                      setTarget(null)
                    }}
                    onMove={(next) => drop(next, grouped[next].length)}
                  />
                </div>
              ))}
              {target?.status === status && target.index === cards.length && <div className="pm-drop-line" />}
              {!cards.length && <div className="pm-column-empty faint">Bo'sh</div>}
            </div>
          </section>
        )
      })}
    </div>
  )
}

function TaskCard({
  task,
  meta,
  dragging,
  onDragStart,
  onDragEnd,
  onMove,
}: {
  task: PmTaskSummary
  meta?: PmMeta
  dragging: boolean
  onDragStart: () => void
  onDragEnd: () => void
  onMove: (status: PmStatus) => void
}) {
  const progress = subtaskProgress(task)
  const epic = meta?.epics.find((item) => item.id === task.epicId)
  const late = overdue(task)
  return (
    <article
      data-card
      className={`pm-card${dragging ? ' dragging' : ''}`}
      draggable
      onDragStart={(event) => {
        event.dataTransfer.effectAllowed = 'move'
        event.dataTransfer.setData('text/plain', task.key)
        onDragStart()
      }}
      onDragEnd={onDragEnd}
    >
      <div className="row" style={{ justifyContent: 'space-between', gap: 6 }}>
        <Link to={`/pm/${task.key}`} className="pm-key">
          {task.key}
        </Link>
        <span className={`badge ${typeLabels[task.type].tone}`}>{typeLabels[task.type].text}</span>
      </div>
      <Link to={`/pm/${task.key}`} className="pm-card-title">
        {task.title}
      </Link>
      {epic && <div className="pm-epic-tag">{epic.title}</div>}
      <div className="row pm-card-meta">
        {task.priority !== 'medium' && <span className={`badge ${priorityLabels[task.priority].tone}`}>{priorityLabels[task.priority].text}</span>}
        {task.components.slice(0, 2).map((key) => (
          <span key={key} className="badge free">
            {componentLabel(key)}
          </span>
        ))}
        {task.components.length > 2 && <span className="faint">+{task.components.length - 2}</span>}
      </div>
      <div className="row pm-card-foot">
        <span className="faint">{task.assigneeId ? personName(meta, task.assigneeId) : 'Ijrochisiz'}</span>
        <span className="spacer" />
        {progress && <span className="faint" title="Subtasklar">☑ {progress}</span>}
        {task.comments > 0 && <span className="faint" title="Izohlar">💬 {task.comments}</span>}
        {task.dueDate && <span className={late ? 'pm-late' : 'faint'}>{formatDate(task.dueDate)}</span>}
      </div>
      {/* Drag is a mouse gesture; the keyboard and a touch screen move a card with this. */}
      <label className="sr-only" htmlFor={`move-${task.id}`}>
        {task.key} holatini o'zgartirish
      </label>
      <select id={`move-${task.id}`} className="pm-card-move" value={task.status} onChange={(event) => onMove(event.target.value as PmStatus)}>
        {statusOrder.map((status) => (
          <option key={status} value={status}>
            {statusLabels[status]}
          </option>
        ))}
      </select>
    </article>
  )
}

function TaskTable({ tasks, meta }: { tasks: PmTaskSummary[]; meta?: PmMeta }) {
  return (
    <div className="table-wrap card">
      <table>
        <thead>
          <tr>
            <th>Kalit</th>
            <th>Sarlavha</th>
            <th>Holat</th>
            <th>Prioritet</th>
            <th>Ijrochi</th>
            <th>Epic</th>
            <th>Muddat</th>
            <th>Yangilangan</th>
          </tr>
        </thead>
        <tbody>
          {tasks.map((task) => (
            <tr key={task.id}>
              <td>
                <Link to={`/pm/${task.key}`} className="pm-key">
                  {task.key}
                </Link>
              </td>
              <td>
                <Link to={`/pm/${task.key}`}>{task.title}</Link>
                <div className="faint">{[typeLabels[task.type].text, ...task.components.map(componentLabel)].join(' · ')}</div>
              </td>
              <td>{statusLabels[task.status]}</td>
              <td>
                <span className={`badge ${priorityLabels[task.priority].tone}`}>{priorityLabels[task.priority].text}</span>
              </td>
              <td>{personName(meta, task.assigneeId)}</td>
              <td>{meta?.epics.find((epic) => epic.id === task.epicId)?.title ?? '—'}</td>
              <td className={overdue(task) ? 'pm-late' : 'faint'}>{task.dueDate ? formatDate(task.dueDate) : '—'}</td>
              <td className="faint">{formatDate(task.updatedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
