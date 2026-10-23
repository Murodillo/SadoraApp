import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useToast } from '../components/toast'
import { ErrorNotice, Field, Modal } from '../components/ui'
import { useCreatePmTask, usePmMeta } from './api'
import { ComponentChips, epicOptions, LabelInput, OptionalSelect, peopleOptions, PrioritySelect, releaseOptions, StatusSelect, TypeSelect } from './fields'
import { MarkdownEditor } from './MarkdownView'
import type { CreatePmTask } from './types'

/**
 * A new task. [initial] pre-fills it — the column a "+" was pressed on, the parent of a
 * subtask, the board's current epic filter. Saved, it opens unless [stay] keeps the board.
 */
export function TaskForm({ initial, onClose, stay = false }: { initial?: Partial<CreatePmTask>; onClose: () => void; stay?: boolean }) {
  const meta = usePmMeta()
  const create = useCreatePmTask()
  const navigate = useNavigate()
  const { notify } = useToast()
  const [task, setTask] = useState<CreatePmTask>({
    title: '',
    description: '',
    type: 'task',
    status: 'backlog',
    priority: 'medium',
    components: [],
    labels: [],
    ...initial,
  })
  const set = (patch: Partial<CreatePmTask>) => setTask((current) => ({ ...current, ...patch }))

  function save() {
    create.mutate(
      { ...task, title: task.title.trim() },
      {
        onSuccess: (created) => {
          notify(`${created.key} yaratildi`)
          onClose()
          if (!stay) navigate(`/pm/${created.key}`)
        },
      },
    )
  }

  return (
    <Modal title={task.parentKey ? `${task.parentKey} uchun subtask` : 'Yangi task'} onClose={onClose} wide>
      <form
        className="grid"
        style={{ gap: 12 }}
        onSubmit={(event) => {
          event.preventDefault()
          save()
        }}
      >
        <Field label="Sarlavha">
          <input value={task.title} maxLength={200} onChange={(event) => set({ title: event.target.value })} autoFocus required />
        </Field>
        <div className="pm-form-grid">
          <Field label="Turi">
            <TypeSelect value={task.type!} onChange={(type) => set({ type })} />
          </Field>
          <Field label="Prioritet">
            <PrioritySelect value={task.priority!} onChange={(priority) => set({ priority })} />
          </Field>
          <Field label="Holat">
            <StatusSelect value={task.status!} onChange={(status) => set({ status })} />
          </Field>
          <Field label="Ijrochi">
            <OptionalSelect value={task.assigneeId} onChange={(assigneeId) => set({ assigneeId })} options={peopleOptions(meta.data)} none="Hech kim" />
          </Field>
          <Field label="Epic">
            <OptionalSelect value={task.epicId} onChange={(epicId) => set({ epicId })} options={epicOptions(meta.data)} none="Epicsiz" />
          </Field>
          <Field label="Reliz">
            <OptionalSelect value={task.releaseId} onChange={(releaseId) => set({ releaseId })} options={releaseOptions(meta.data)} none="Relizsiz" />
          </Field>
          <Field label="Muddat">
            <input type="date" value={task.dueDate ?? ''} onChange={(event) => set({ dueDate: event.target.value || undefined })} />
          </Field>
        </div>
        <div className="field">
          <span className="pm-editor-label">Komponentlar</span>
          <ComponentChips value={task.components ?? []} onChange={(components) => set({ components })} components={meta.data?.components ?? []} />
        </div>
        <Field label="Labellar">
          <LabelInput value={task.labels ?? []} onChange={(labels) => set({ labels })} />
        </Field>
        <MarkdownEditor label="Tavsif" value={task.description ?? ''} onChange={(description) => set({ description })} rows={8} />
        <ErrorNotice error={create.error} />
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <button type="button" className="btn ghost" onClick={onClose}>
            Bekor qilish
          </button>
          <button type="submit" className="btn primary" disabled={!task.title.trim() || create.isPending}>
            {create.isPending ? 'Saqlanmoqda…' : 'Yaratish'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
