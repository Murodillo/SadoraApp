import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/toast'
import { Card, Empty, ErrorNotice, Field, formatDate, Loading, Modal, Tabs, TabPanel } from '../components/ui'
import { useArchivePmGroup, usePmEpics, usePmReleases, useSavePmEpic, useSavePmRelease } from './api'
import type { SaveEpic, SaveRelease } from './api'
import { Markdown, MarkdownEditor } from './MarkdownView'
import type { PmEpic, PmRelease } from './types'

type Tab = 'epics' | 'releases'

/**
 * Epics are the features being built ("Baby tracker"); releases are the dates the work is
 * aimed at ("Play Store 1.0"). Each shows how much of its work is done, and opens the board
 * filtered to it. Owner and Admin put one away; anyone in the section writes them.
 */
export function EpicsPage() {
  const [tab, setTab] = useState<Tab>('epics')
  const [archived, setArchived] = useState(false)
  return (
    <div className="grid" style={{ gap: 12 }}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <Tabs<Tab>
          value={tab}
          onChange={setTab}
          items={[
            { key: 'epics', label: 'Epiclar' },
            { key: 'releases', label: 'Relizlar' },
          ]}
        />
        <label className="row faint" style={{ gap: 6 }}>
          <input type="checkbox" checked={archived} onChange={(event) => setArchived(event.target.checked)} /> Arxivdagilar ham
        </label>
      </div>
      <TabPanel id={tab}>{tab === 'epics' ? <Epics archived={archived} /> : <Releases archived={archived} />}</TabPanel>
    </div>
  )
}

function Progress({ done, total }: { done: number; total: number }) {
  const share = total ? Math.round((done / total) * 100) : 0
  return (
    <div className="pm-progress" title={`${done} / ${total} tayyor`}>
      <div className="clay-track">
        <div className="clay-fill" style={{ width: `${share}%` }} />
      </div>
      <span className="faint">
        {done}/{total} · {share}%
      </span>
    </div>
  )
}

function Epics({ archived }: { archived: boolean }) {
  const epics = usePmEpics(archived)
  const [editing, setEditing] = useState<PmEpic | 'new' | null>(null)
  return (
    <Card
      title="Epiclar"
      action={
        <button className="btn primary small" onClick={() => setEditing('new')}>
          + Epic
        </button>
      }
    >
      {epics.isLoading ? (
        <Loading rows={3} />
      ) : epics.error ? (
        <ErrorNotice error={epics.error} />
      ) : !epics.data?.length ? (
        <Empty art="ic3d_bulb">Hali epic yo'q. Har bir katta feature — bitta epic.</Empty>
      ) : (
        <div className="pm-group-grid">
          {epics.data.map((epic) => (
            <GroupCard
              key={epic.id}
              kind="epics"
              id={epic.id}
              title={epic.title}
              badge={epic.status === 'done' ? { text: 'Yakunlangan', tone: 'ok' } : { text: 'Ochiq', tone: 'premium' }}
              description={epic.description}
              archived={epic.archived}
              done={epic.done}
              total={epic.tasks}
              boardLink={`/pm?epic=${epic.id}`}
              onEdit={() => setEditing(epic)}
            />
          ))}
        </div>
      )}
      {editing && <EpicForm epic={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
    </Card>
  )
}

function Releases({ archived }: { archived: boolean }) {
  const releases = usePmReleases(archived)
  const [editing, setEditing] = useState<PmRelease | 'new' | null>(null)
  return (
    <Card
      title="Relizlar"
      action={
        <button className="btn primary small" onClick={() => setEditing('new')}>
          + Reliz
        </button>
      }
    >
      {releases.isLoading ? (
        <Loading rows={3} />
      ) : releases.error ? (
        <ErrorNotice error={releases.error} />
      ) : !releases.data?.length ? (
        <Empty art="ic3d_globe">Hali reliz yo'q. Masalan: «Play Store 1.0».</Empty>
      ) : (
        <div className="pm-group-grid">
          {releases.data.map((release) => (
            <GroupCard
              key={release.id}
              kind="releases"
              id={release.id}
              title={release.name}
              subtitle={release.targetDate ? `Muddat: ${formatDate(release.targetDate)}` : 'Muddatsiz'}
              badge={release.status === 'released' ? { text: `Chiqdi ${formatDate(release.releasedAt)}`, tone: 'ok' } : { text: 'Rejada', tone: 'warn' }}
              description={release.description}
              archived={release.archived}
              done={release.done}
              total={release.tasks}
              boardLink={`/pm?release=${release.id}`}
              onEdit={() => setEditing(release)}
            />
          ))}
        </div>
      )}
      {editing && <ReleaseForm release={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
    </Card>
  )
}

function GroupCard(props: {
  kind: 'epics' | 'releases'
  id: string
  title: string
  subtitle?: string
  badge: { text: string; tone: string }
  description: string
  archived: boolean
  done: number
  total: number
  boardLink: string
  onEdit: () => void
}) {
  const archive = useArchivePmGroup()
  const { can } = useAuth()
  const { notify } = useToast()
  return (
    <div className={`pm-group${props.archived ? ' archived' : ''}`}>
      <div className="row" style={{ justifyContent: 'space-between', gap: 8 }}>
        <strong>{props.title}</strong>
        <span className={`badge ${props.badge.tone}`}>{props.badge.text}</span>
      </div>
      {props.subtitle && <div className="faint">{props.subtitle}</div>}
      <Progress done={props.done} total={props.total} />
      {props.description && (
        <div className="pm-group-description">
          <Markdown text={props.description} />
        </div>
      )}
      <div className="row" style={{ gap: 8 }}>
        <Link to={props.boardLink} className="btn ghost small">
          Doskada ochish
        </Link>
        <button className="btn ghost small" onClick={props.onEdit}>
          Tahrirlash
        </button>
        {can(['OWNER', 'ADMIN']) && (
          <button
            className="btn ghost small"
            onClick={() =>
              archive.mutate(
                { kind: props.kind, id: props.id, archived: !props.archived },
                { onSuccess: () => notify(props.archived ? 'Qaytarildi' : 'Arxivlandi', 'info') },
              )
            }
          >
            {props.archived ? 'Qaytarish' : 'Arxivlash'}
          </button>
        )}
      </div>
    </div>
  )
}

function EpicForm({ epic, onClose }: { epic?: PmEpic; onClose: () => void }) {
  const save = useSavePmEpic()
  const [body, setBody] = useState<SaveEpic>({ title: epic?.title ?? '', description: epic?.description ?? '', status: epic?.status ?? 'open' })
  return (
    <Modal title={epic ? 'Epicni tahrirlash' : 'Yangi epic'} onClose={onClose} wide>
      <form
        className="grid"
        style={{ gap: 12 }}
        onSubmit={(event) => {
          event.preventDefault()
          save.mutate({ id: epic?.id, body }, { onSuccess: onClose })
        }}
      >
        <Field label="Nomi">
          <input value={body.title} maxLength={160} onChange={(event) => setBody({ ...body, title: event.target.value })} autoFocus required />
        </Field>
        <Field label="Holat">
          <select value={body.status} onChange={(event) => setBody({ ...body, status: event.target.value as SaveEpic['status'] })}>
            <option value="open">Ochiq</option>
            <option value="done">Yakunlangan</option>
          </select>
        </Field>
        <MarkdownEditor label="Tavsif" value={body.description} onChange={(description) => setBody({ ...body, description })} rows={6} />
        <ErrorNotice error={save.error} />
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <button type="button" className="btn ghost" onClick={onClose}>
            Bekor qilish
          </button>
          <button type="submit" className="btn primary" disabled={!body.title.trim() || save.isPending}>
            Saqlash
          </button>
        </div>
      </form>
    </Modal>
  )
}

function ReleaseForm({ release, onClose }: { release?: PmRelease; onClose: () => void }) {
  const save = useSavePmRelease()
  const [body, setBody] = useState<SaveRelease>({
    name: release?.name ?? '',
    description: release?.description ?? '',
    targetDate: release?.targetDate,
    status: release?.status ?? 'planned',
  })
  return (
    <Modal title={release ? 'Relizni tahrirlash' : 'Yangi reliz'} onClose={onClose} wide>
      <form
        className="grid"
        style={{ gap: 12 }}
        onSubmit={(event) => {
          event.preventDefault()
          save.mutate({ id: release?.id, body }, { onSuccess: onClose })
        }}
      >
        <Field label="Nomi">
          <input value={body.name} maxLength={120} onChange={(event) => setBody({ ...body, name: event.target.value })} autoFocus required />
        </Field>
        <div className="two-col even">
          <Field label="Muddat">
            <input type="date" value={body.targetDate ?? ''} onChange={(event) => setBody({ ...body, targetDate: event.target.value || undefined })} />
          </Field>
          <Field label="Holat">
            <select value={body.status} onChange={(event) => setBody({ ...body, status: event.target.value as SaveRelease['status'] })}>
              <option value="planned">Rejada</option>
              <option value="released">Chiqdi</option>
            </select>
          </Field>
        </div>
        <MarkdownEditor label="Tavsif" value={body.description} onChange={(description) => setBody({ ...body, description })} rows={6} />
        <ErrorNotice error={save.error} />
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <button type="button" className="btn ghost" onClick={onClose}>
            Bekor qilish
          </button>
          <button type="submit" className="btn primary" disabled={!body.name.trim() || save.isPending}>
            Saqlash
          </button>
        </div>
      </form>
    </Modal>
  )
}
