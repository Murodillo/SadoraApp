import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiFailure } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/toast'
import { Card, ConfirmDialog, Empty, ErrorNotice, Field, formatDateTime, Loading, Modal } from '../components/ui'
import {
  fetchPmDocVersion,
  useArchivePmDoc,
  useCreatePmDoc,
  useDeletePmDoc,
  usePmDoc,
  usePmDocTree,
  usePmDocVersions,
  usePmMeta,
  useSavePmDoc,
} from './api'
import { buildTree, parentChoices } from './docs'
import type { TreeNode } from './docs'
import { personName } from './fields'
import { Markdown, MarkdownEditor } from './MarkdownView'
import type { PmDoc, PmDocNode, PmDocVersion } from './types'

/**
 * The team's docs: a tree of Markdown pages on the left, the open one on the right. A save
 * names the version it was opened at, so two people editing one page cannot erase each
 * other; the second is told, keeps her text, and opens the newer version.
 */
export function DocsPage() {
  const { id } = useParams()
  const [archived, setArchived] = useState(false)
  const tree = usePmDocTree(archived)
  const [creating, setCreating] = useState<{ parentId?: string } | null>(null)
  const roots = useMemo(() => buildTree(tree.data ?? []), [tree.data])

  return (
    <div className="pm-docs">
      <aside className="card pm-doc-tree">
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <strong>{archived ? 'Arxiv' : 'Hujjatlar'}</strong>
          {!archived && (
            <button className="btn primary small" onClick={() => setCreating({})}>
              + Sahifa
            </button>
          )}
        </div>
        {tree.isLoading ? (
          <Loading rows={4} />
        ) : tree.error ? (
          <ErrorNotice error={tree.error} />
        ) : !roots.length ? (
          <p className="faint">{archived ? "Arxiv bo'sh." : "Hali hujjat yo'q."}</p>
        ) : (
          <Tree nodes={roots} active={id} />
        )}
        <button className="link-btn faint" onClick={() => setArchived(!archived)}>
          {archived ? '← Hujjatlarga qaytish' : 'Arxivni ko‘rish'}
        </button>
      </aside>
      <section className="pm-doc-main">
        {id ? (
          <DocView key={id} id={id} nodes={tree.data ?? []} onAddChild={() => setCreating({ parentId: id })} />
        ) : (
          <Card>
            <Empty art="ic3d_book">
              Chapdan sahifani tanlang yoki yangisini yarating. Speclar, auditlar, qarorlar va retseptlar shu yerda yashaydi — Claude ham
              shu yerga yozadi.
            </Empty>
          </Card>
        )}
      </section>
      {creating && <NewDoc parentId={creating.parentId} nodes={tree.data ?? []} onClose={() => setCreating(null)} />}
    </div>
  )
}

function Tree({ nodes, active }: { nodes: TreeNode[]; active?: string }) {
  return (
    <ul className="pm-tree">
      {nodes.map((node) => (
        <li key={node.id}>
          <Link to={`/pm/docs/${node.id}`} className={`pm-tree-item${node.id === active ? ' active' : ''}`}>
            {node.title}
          </Link>
          {node.children.length > 0 && <Tree nodes={node.children} active={active} />}
        </li>
      ))}
    </ul>
  )
}

function NewDoc({ parentId, nodes, onClose }: { parentId?: string; nodes: PmDocNode[]; onClose: () => void }) {
  const create = useCreatePmDoc()
  const navigate = useNavigate()
  const [title, setTitle] = useState('')
  const [parent, setParent] = useState(parentId ?? '')
  return (
    <Modal title="Yangi sahifa" onClose={onClose}>
      <form
        className="grid"
        style={{ gap: 12 }}
        onSubmit={(event) => {
          event.preventDefault()
          create.mutate(
            { title: title.trim(), parentId: parent || undefined },
            {
              onSuccess: (doc) => {
                onClose()
                navigate(`/pm/docs/${doc.id}?edit=1`)
              },
            },
          )
        }}
      >
        <Field label="Sarlavha">
          <input value={title} maxLength={160} onChange={(event) => setTitle(event.target.value)} autoFocus required />
        </Field>
        <Field label="Qayerda">
          <select value={parent} onChange={(event) => setParent(event.target.value)}>
            <option value="">Eng yuqorida</option>
            {parentChoices(nodes).map((option) => (
              <option key={option.id} value={option.id}>
                {option.label}
              </option>
            ))}
          </select>
        </Field>
        <ErrorNotice error={create.error} />
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <button type="button" className="btn ghost" onClick={onClose}>
            Bekor qilish
          </button>
          <button type="submit" className="btn primary" disabled={!title.trim() || create.isPending}>
            Yaratish
          </button>
        </div>
      </form>
    </Modal>
  )
}

function DocView({ id, nodes, onAddChild }: { id: string; nodes: PmDocNode[]; onAddChild: () => void }) {
  const doc = usePmDoc(id)
  const meta = usePmMeta()
  const [editing, setEditing] = useState(() => new URLSearchParams(window.location.search).has('edit'))
  const [history, setHistory] = useState(false)

  if (doc.isLoading) return <Loading rows={6} />
  if (doc.error) return <ErrorNotice error={doc.error} />
  if (!doc.data) return null
  const page = doc.data

  if (editing) return <DocEditor doc={page} nodes={nodes} onDone={() => setEditing(false)} onReload={() => void doc.refetch()} />

  return (
    <Card>
      <div className="grid" style={{ gap: 12 }}>
        {page.archived && <div className="notice">Bu sahifa arxivda.</div>}
        <div className="row" style={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div>
            <h2 className="pm-doc-title">{page.title}</h2>
            <div className="faint">
              v{page.version} · {personName(meta.data, page.updatedBy)} · {formatDateTime(page.updatedAt)}
            </div>
          </div>
          <div className="row" style={{ gap: 6 }}>
            {!page.archived && (
              <button className="btn primary small" onClick={() => setEditing(true)}>
                Tahrirlash
              </button>
            )}
            <button className="btn ghost small" onClick={() => setHistory(true)}>
              Tarix
            </button>
            {!page.archived && (
              <button className="btn ghost small" onClick={onAddChild}>
                + Ichiga sahifa
              </button>
            )}
            <DocDanger doc={page} />
          </div>
        </div>
        <Markdown text={page.body} empty="Sahifa bo'sh. «Tahrirlash»ni bosing." />
      </div>
      {history && <History doc={page} onClose={() => setHistory(false)} />}
    </Card>
  )
}

function DocEditor({ doc, nodes, onDone, onReload }: { doc: PmDoc; nodes: PmDocNode[]; onDone: () => void; onReload: () => void }) {
  const save = useSavePmDoc()
  const { notify } = useToast()
  const [title, setTitle] = useState(doc.title)
  const [body, setBody] = useState(doc.body)
  const [parent, setParent] = useState(doc.parentId ?? '')
  // The version her words are written on. It moves only when she loads a newer one after a conflict.
  const [base, setBase] = useState(doc.version)
  const conflict = save.error instanceof ApiFailure && save.error.status === 409
  const newer = doc.version > base ? doc : null
  const dirty = title !== doc.title || body !== doc.body || parent !== (doc.parentId ?? '')

  // Leaving the tab with unsaved words asks first.
  useEffect(() => {
    if (!dirty) return
    const onLeave = (event: BeforeUnloadEvent) => event.preventDefault()
    window.addEventListener('beforeunload', onLeave)
    return () => window.removeEventListener('beforeunload', onLeave)
  }, [dirty])

  function submit() {
    save.mutate(
      { id: doc.id, body: { title: title.trim(), body, baseVersion: base, parentId: parent || undefined } },
      {
        onSuccess: (saved) => {
          notify(`Saqlandi (v${saved.version})`)
          onDone()
        },
      },
    )
  }

  return (
    <Card>
      <form
        className="grid"
        style={{ gap: 12 }}
        onSubmit={(event) => {
          event.preventDefault()
          submit()
        }}
      >
        <div className="two-col">
          <Field label="Sarlavha">
            <input value={title} maxLength={160} onChange={(event) => setTitle(event.target.value)} required />
          </Field>
          <Field label="Qayerda">
            <select value={parent} onChange={(event) => setParent(event.target.value)}>
              <option value="">Eng yuqorida</option>
              {parentChoices(nodes, doc.id).map((option) => (
                <option key={option.id} value={option.id}>
                  {option.label}
                </option>
              ))}
            </select>
          </Field>
        </div>
        <MarkdownEditor label="Matn" value={body} onChange={setBody} rows={24} autoFocus />
        {newer ? (
          <details className="notice" open>
            <summary>
              Boshqa odam saqlagan v{newer.version} — kerakli joylarini matningizga ko'chiring, keyin saqlang.{' '}
              <button type="button" className="link-btn" onClick={() => setBase(newer.version)}>
                Ko'chirdim, v{newer.version} ustiga saqlash
              </button>
            </summary>
            <div className="pm-version-body">
              <Markdown text={newer.body} />
            </div>
          </details>
        ) : conflict ? (
          <div className="notice">
            {save.error?.message}{' '}
            <button type="button" className="link-btn" onClick={onReload}>
              Yangi versiyani ko'rish
            </button>
          </div>
        ) : (
          <ErrorNotice error={save.error} />
        )}
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <span className="faint" style={{ marginRight: 'auto' }}>
            v{base} ustiga saqlanadi
          </span>
          <button type="button" className="btn ghost" onClick={onDone}>
            Bekor qilish
          </button>
          <button type="submit" className="btn primary" disabled={!title.trim() || save.isPending || Boolean(newer)}>
            {save.isPending ? 'Saqlanmoqda…' : 'Saqlash'}
          </button>
        </div>
      </form>
    </Card>
  )
}

function History({ doc, onClose }: { doc: PmDoc; onClose: () => void }) {
  const versions = usePmDocVersions(doc.id, true)
  const meta = usePmMeta()
  const save = useSavePmDoc()
  const { notify } = useToast()
  const [open, setOpen] = useState<PmDocVersion | null>(null)
  const [loading, setLoading] = useState<number | null>(null)

  async function show(version: number) {
    setLoading(version)
    try {
      setOpen(await fetchPmDocVersion(doc.id, version))
    } finally {
      setLoading(null)
    }
  }

  return (
    <Modal title={`«${doc.title}» tarixi`} onClose={onClose} wide>
      {open ? (
        <div className="grid" style={{ gap: 12 }}>
          <div className="row" style={{ justifyContent: 'space-between' }}>
            <span>
              <strong>v{open.version}</strong> · {open.title} · <span className="faint">{formatDateTime(open.createdAt)}</span>
            </span>
            <div className="row" style={{ gap: 6 }}>
              <button className="btn ghost small" onClick={() => setOpen(null)}>
                ← Ro'yxat
              </button>
              {open.version !== doc.version && !doc.archived && (
                <button
                  className="btn primary small"
                  disabled={save.isPending}
                  onClick={() =>
                    save.mutate(
                      { id: doc.id, body: { title: open.title, body: open.body, baseVersion: doc.version, parentId: doc.parentId } },
                      {
                        onSuccess: (saved) => {
                          notify(`v${open.version} tiklandi (endi v${saved.version})`)
                          onClose()
                        },
                      },
                    )
                  }
                >
                  Shu versiyani tiklash
                </button>
              )}
            </div>
          </div>
          <ErrorNotice error={save.error} />
          <div className="pm-version-body">
            <Markdown text={open.body} />
          </div>
        </div>
      ) : versions.isLoading ? (
        <Loading rows={4} />
      ) : versions.error ? (
        <ErrorNotice error={versions.error} />
      ) : (
        <ul className="pm-versions">
          {versions.data?.map((version) => (
            <li key={version.version}>
              <button className="link-btn" onClick={() => void show(version.version)} disabled={loading !== null}>
                v{version.version}
              </button>
              <span>{version.title}</span>
              <span className="faint">
                {personName(meta.data, version.editedBy)} · {formatDateTime(version.createdAt)} · {version.size} belgi
              </span>
              {version.version === doc.version && <span className="badge ok">hozirgi</span>}
            </li>
          ))}
        </ul>
      )}
    </Modal>
  )
}

function DocDanger({ doc }: { doc: PmDoc }) {
  const archive = useArchivePmDoc()
  const remove = useDeletePmDoc()
  const { can } = useAuth()
  const navigate = useNavigate()
  const { notify } = useToast()
  const [confirming, setConfirming] = useState(false)
  return (
    <>
      <button
        className="btn ghost small"
        disabled={archive.isPending}
        onClick={() =>
          archive.mutate(
            { id: doc.id, archived: !doc.archived },
            { onSuccess: () => notify(doc.archived ? 'Arxivdan qaytarildi' : 'Ichidagilari bilan arxivlandi', 'info') },
          )
        }
      >
        {doc.archived ? 'Qaytarish' : 'Arxivlash'}
      </button>
      {doc.archived && can(['OWNER']) && (
        <button className="btn small danger" onClick={() => setConfirming(true)}>
          O'chirish
        </button>
      )}
      {confirming && (
        <ConfirmDialog
          title={`«${doc.title}» butunlay o'chirilsinmi?`}
          confirmLabel="O'chirish"
          cancelLabel="Qoldirish"
          danger
          pending={remove.isPending}
          onClose={() => setConfirming(false)}
          onConfirm={() =>
            remove.mutate(doc.id, {
              onSuccess: () => {
                notify("Sahifa o'chirildi", 'info')
                navigate('/pm/docs')
              },
            })
          }
        >
          <p>Ichidagi sahifalar va barcha versiyalar bilan birga yo'qoladi. Qaytarib bo'lmaydi.</p>
          <ErrorNotice error={remove.error} />
        </ConfirmDialog>
      )}
    </>
  )
}
