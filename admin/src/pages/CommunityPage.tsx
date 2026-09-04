import { useState } from 'react'
import {
  useCommunityStats,
  useHideComment,
  useHidePost,
  useModerationComments,
  useModerationPosts,
  useModerationReports,
  useResolveReport,
  useRestrictAuthor,
} from '../api/hooks'
import type { ModerationFilters } from '../api/hooks'
import type { ModerationPost, ModerationReport, ReportReason } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Card, Empty, ErrorNotice, Field, formatDateTime, Loading, Modal, Stat } from '../components/ui'

const PAGE_SIZE = 25

const topicLabels: Record<string, string> = {
  cycle: 'Sikl',
  pregnancy: 'Homiladorlik',
  wellbeing: 'Kayfiyat',
  body: 'Tana',
}

const reasonLabels: Record<ReportReason, string> = {
  spam: 'Spam',
  abuse: 'Haqorat',
  misinformation: 'Xavfli maslahat',
  personal_data: "Shaxsiy ma'lumot",
  other: 'Boshqa',
}

type Tab = 'posts' | 'reports'

/**
 * The secret chat's moderation queue.
 *
 * Everything on this page is alias-only. There is no link from a post to a user card
 * and the API has no way to produce one; the "muallifni cheklash" action reaches the
 * author through the post id, and the moderator never learns who she is.
 */
export function CommunityPage() {
  const stats = useCommunityStats()
  const [tab, setTab] = useState<Tab>('posts')

  return (
    <div className="grid" style={{ gap: 16 }}>
      <div className="notice privacy">
        Bu sahifada faqat taxallus va matn ko'rinadi. Post muallifining hisobiga o'tish yo'li
        yo'q — cheklash ham post orqali qo'llanadi, moderator kimligini bilmaydi.
      </div>

      {stats.data && (
        <div className="grid stat-row">
          <Stat label="Jami post" value={stats.data.postsTotal} />
          <Stat label="Bugun" value={stats.data.postsToday} />
          <Stat label="Yashirilgan" value={stats.data.hiddenPosts} />
          <Stat label="Ochiq shikoyat" value={stats.data.openReports} />
        </div>
      )}

      <div className="tabs">
        <button className={`tab${tab === 'posts' ? ' active' : ''}`} onClick={() => setTab('posts')}>
          Postlar
        </button>
        <button className={`tab${tab === 'reports' ? ' active' : ''}`} onClick={() => setTab('reports')}>
          Shikoyatlar{stats.data?.openReports ? ` (${stats.data.openReports})` : ''}
        </button>
      </div>

      {tab === 'posts' ? <PostsTab /> : <ReportsTab />}
    </div>
  )
}

// ---------------------------------------------------------------- posts

function PostsTab() {
  const { can } = useAuth()
  const editable = can(['OWNER', 'ADMIN'])
  const [filters, setFilters] = useState<ModerationFilters>({ limit: PAGE_SIZE, offset: 0 })
  const [expanded, setExpanded] = useState<string | null>(null)
  const [dialog, setDialog] = useState<{ kind: 'hide' | 'restrict'; post: ModerationPost } | null>(null)
  const posts = useModerationPosts(filters)
  const hide = useHidePost()

  function update(patch: Partial<ModerationFilters>) {
    setFilters((current) => ({ ...current, ...patch, offset: 0 }))
  }

  const page = posts.data
  const offset = filters.offset

  return (
    <>
      <Card>
        <div className="filters">
          <div className="field">
            <label>Holat</label>
            <select
              value={filters.hidden === undefined ? '' : String(filters.hidden)}
              onChange={(event) =>
                update({ hidden: event.target.value === '' ? undefined : event.target.value === 'true' })
              }
            >
              <option value="">Barchasi</option>
              <option value="false">Ko'rinadi</option>
              <option value="true">Yashirilgan</option>
            </select>
          </div>
          <div className="field">
            <label>Bo'lim</label>
            <select value={filters.topic ?? ''} onChange={(event) => update({ topic: event.target.value || undefined })}>
              <option value="">Barchasi</option>
              {Object.entries(topicLabels).map(([key, label]) => (
                <option key={key} value={key}>
                  {label}
                </option>
              ))}
            </select>
          </div>
          <label className="row" style={{ gap: 6, alignSelf: 'end', paddingBottom: 8 }}>
            <input
              type="checkbox"
              style={{ width: 'auto' }}
              checked={Boolean(filters.reported)}
              onChange={(event) => update({ reported: event.target.checked || undefined })}
            />
            <span className="faint">Faqat shikoyat qilinganlar</span>
          </label>
        </div>
      </Card>

      <Card>
        {posts.error && <ErrorNotice error={posts.error} />}
        {hide.error && <ErrorNotice error={hide.error} />}
        {posts.isLoading && !page ? (
          <Loading rows={6} />
        ) : !page?.items.length ? (
          <Empty>Bu filtrlarga mos post yo'q.</Empty>
        ) : (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Muallif</th>
                    <th>Bo'lim</th>
                    <th style={{ width: '40%' }}>Matn</th>
                    <th>Yoqdi</th>
                    <th>Izoh</th>
                    <th>Shikoyat</th>
                    <th>Holat</th>
                    <th>Vaqt</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {page.items.map((post) => (
                    <PostRow
                      key={post.id}
                      post={post}
                      editable={editable}
                      expanded={expanded === post.id}
                      onToggle={() => setExpanded(expanded === post.id ? null : post.id)}
                      onHide={() => setDialog({ kind: 'hide', post })}
                      onRestore={() => hide.mutate({ id: post.id, hidden: false })}
                      onRestrict={() => setDialog({ kind: 'restrict', post })}
                    />
                  ))}
                </tbody>
              </table>
            </div>

            <div className="row" style={{ justifyContent: 'space-between', marginTop: 12 }}>
              <span className="faint">
                {offset + 1}–{offset + page.items.length} / {page.total}
              </span>
              <div className="row">
                <button
                  className="btn small"
                  disabled={offset === 0}
                  onClick={() => setFilters((c) => ({ ...c, offset: Math.max(0, offset - PAGE_SIZE) }))}
                >
                  Oldingi
                </button>
                <button
                  className="btn small"
                  disabled={offset + page.items.length >= page.total}
                  onClick={() => setFilters((c) => ({ ...c, offset: offset + PAGE_SIZE }))}
                >
                  Keyingi
                </button>
              </div>
            </div>
          </>
        )}
      </Card>

      {dialog?.kind === 'hide' && <HideDialog post={dialog.post} onClose={() => setDialog(null)} />}
      {dialog?.kind === 'restrict' && <RestrictDialog post={dialog.post} onClose={() => setDialog(null)} />}
    </>
  )
}

function PostRow({
  post,
  editable,
  expanded,
  onToggle,
  onHide,
  onRestore,
  onRestrict,
}: {
  post: ModerationPost
  editable: boolean
  expanded: boolean
  onToggle: () => void
  onHide: () => void
  onRestore: () => void
  onRestrict: () => void
}) {
  return (
    <>
      <tr className="clickable" onClick={onToggle}>
        <td style={{ fontWeight: 600, whiteSpace: 'nowrap' }}>{post.alias}</td>
        <td className="muted">{topicLabels[post.topic] ?? post.topic}</td>
        <td>{expanded ? post.body : excerpt(post.body)}</td>
        <td style={{ textAlign: 'right' }}>{post.likeCount}</td>
        <td style={{ textAlign: 'right' }}>{post.commentCount}</td>
        <td style={{ textAlign: 'right' }}>
          {post.openReports > 0 ? <span className="badge warn">{post.openReports}</span> : <span className="faint">0</span>}
        </td>
        <td>
          {post.hidden ? (
            <span className="badge danger" title={post.hiddenReason ?? ''}>
              Yashirilgan
            </span>
          ) : (
            <span className="badge ok">Ko'rinadi</span>
          )}
        </td>
        <td className="faint" style={{ whiteSpace: 'nowrap' }}>
          {formatDateTime(post.createdAt)}
        </td>
        <td onClick={(event) => event.stopPropagation()}>
          {editable && (
            <div className="row">
              {post.hidden ? (
                <button className="btn small" onClick={onRestore}>
                  Qaytarish
                </button>
              ) : (
                <button className="btn small danger" onClick={onHide}>
                  Yashirish
                </button>
              )}
              <button className="btn small ghost" onClick={onRestrict} title="Muallif yozishdan cheklanadi; kimligi ko'rinmaydi">
                Muallifni cheklash
              </button>
            </div>
          )}
        </td>
      </tr>
      {expanded && (
        <tr>
          <td colSpan={9} style={{ background: 'var(--surface-2)' }}>
            <CommentsPanel postId={post.id} editable={editable} />
          </td>
        </tr>
      )}
    </>
  )
}

function CommentsPanel({ postId, editable }: { postId: string; editable: boolean }) {
  const comments = useModerationComments(postId)
  const hide = useHideComment()
  if (comments.isLoading) return <Loading rows={2} />
  if (comments.error) return <ErrorNotice error={comments.error} />
  const items = comments.data ?? []
  if (!items.length) return <p className="faint" style={{ margin: 0 }}>Izoh yo'q.</p>
  return (
    <table>
      <tbody>
        {items.map((comment) => (
          <tr key={comment.id}>
            <td style={{ fontWeight: 600, whiteSpace: 'nowrap', width: 160 }}>{comment.alias}</td>
            <td>{comment.body}</td>
            <td style={{ textAlign: 'right', width: 60 }}>
              {comment.openReports > 0 && <span className="badge warn">{comment.openReports}</span>}
            </td>
            <td style={{ width: 110 }}>
              {comment.hidden ? <span className="badge danger">Yashirilgan</span> : <span className="badge ok">Ko'rinadi</span>}
            </td>
            <td className="faint" style={{ whiteSpace: 'nowrap' }}>
              {formatDateTime(comment.createdAt)}
            </td>
            <td style={{ width: 120 }}>
              {editable &&
                (comment.hidden ? (
                  <button className="btn small" onClick={() => hide.mutate({ id: comment.id, hidden: false })}>
                    Qaytarish
                  </button>
                ) : (
                  <button
                    className="btn small danger"
                    onClick={() => {
                      const reason = window.prompt('Yashirish sababi')
                      if (reason?.trim()) hide.mutate({ id: comment.id, hidden: true, reason: reason.trim() })
                    }}
                  >
                    Yashirish
                  </button>
                ))}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function HideDialog({ post, onClose }: { post: ModerationPost; onClose: () => void }) {
  const hide = useHidePost()
  const [reason, setReason] = useState('')
  return (
    <Modal title="Postni yashirish" onClose={onClose}>
      <p className="faint" style={{ margin: 0 }}>
        {post.alias}: “{excerpt(post.body, 120)}”
      </p>
      <Field label="Sabab (majburiy)">
        <input value={reason} onChange={(event) => setReason(event.target.value)} autoFocus />
      </Field>
      <p className="faint" style={{ margin: 0 }}>
        Post lentadan yo'qoladi, undagi ochiq shikoyatlar yopiladi. Sabab audit log'ga yoziladi.
      </p>
      {hide.error && <ErrorNotice error={hide.error} />}
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <button className="btn ghost" onClick={onClose}>
          Bekor qilish
        </button>
        <button
          className="btn danger"
          disabled={!reason.trim() || hide.isPending}
          onClick={() => hide.mutate({ id: post.id, hidden: true, reason: reason.trim() }, { onSuccess: onClose })}
        >
          {hide.isPending ? 'Yuborilmoqda…' : 'Yashirish'}
        </button>
      </div>
    </Modal>
  )
}

function RestrictDialog({ post, onClose }: { post: ModerationPost; onClose: () => void }) {
  const restrict = useRestrictAuthor()
  const [reason, setReason] = useState('')
  const [days, setDays] = useState('')
  return (
    <Modal title="Muallifni cheklash" onClose={onClose}>
      <p className="faint" style={{ margin: 0 }}>
        “{post.alias}” yozgan har bir post va izoh shu muallifga tegishli. Cheklov unga post va izoh
        yozishni yopadi; o'qish ochiq qoladi. Siz uning hisobini ko'rmaysiz.
      </p>
      <Field label="Sabab (majburiy)">
        <input value={reason} onChange={(event) => setReason(event.target.value)} autoFocus />
      </Field>
      <Field label="Muddat, kun (bo'sh — moderator ochguncha)">
        <input type="number" min={1} max={365} value={days} onChange={(event) => setDays(event.target.value)} />
      </Field>
      {restrict.error && <ErrorNotice error={restrict.error} />}
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <button className="btn ghost" onClick={onClose}>
          Bekor qilish
        </button>
        <button
          className="btn danger"
          disabled={!reason.trim() || restrict.isPending}
          onClick={() =>
            restrict.mutate(
              { postId: post.id, reason: reason.trim(), days: days ? Number(days) : null },
              { onSuccess: onClose },
            )
          }
        >
          {restrict.isPending ? 'Yuborilmoqda…' : 'Cheklash'}
        </button>
      </div>
    </Modal>
  )
}

// ---------------------------------------------------------------- reports

function ReportsTab() {
  const { can } = useAuth()
  const editable = can(['OWNER', 'ADMIN'])
  const [open, setOpen] = useState(true)
  const [offset, setOffset] = useState(0)
  const reports = useModerationReports(open, PAGE_SIZE, offset)
  const resolve = useResolveReport()
  const page = reports.data

  return (
    <>
      <Card>
        <div className="row">
          <label className="row" style={{ gap: 6 }}>
            <input
              type="checkbox"
              style={{ width: 'auto' }}
              checked={open}
              onChange={(event) => {
                setOpen(event.target.checked)
                setOffset(0)
              }}
            />
            <span className="faint">Faqat ochiqlari</span>
          </label>
        </div>
      </Card>

      <Card>
        {reports.error && <ErrorNotice error={reports.error} />}
        {resolve.error && <ErrorNotice error={resolve.error} />}
        {reports.isLoading && !page ? (
          <Loading rows={6} />
        ) : !page?.items.length ? (
          <Empty>{open ? "Ochiq shikoyat yo'q — navbat bo'sh." : "Shikoyat yo'q."}</Empty>
        ) : (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Vaqt</th>
                    <th>Sabab</th>
                    <th style={{ width: '45%' }}>Matn</th>
                    <th>Izoh</th>
                    <th>Holat</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {page.items.map((report) => (
                    <ReportRow key={report.id} report={report} editable={editable && !report.resolvedAt} />
                  ))}
                </tbody>
              </table>
            </div>
            <div className="row" style={{ justifyContent: 'space-between', marginTop: 12 }}>
              <span className="faint">
                {offset + 1}–{offset + page.items.length} / {page.total}
              </span>
              <div className="row">
                <button className="btn small" disabled={offset === 0} onClick={() => setOffset(Math.max(0, offset - PAGE_SIZE))}>
                  Oldingi
                </button>
                <button
                  className="btn small"
                  disabled={offset + page.items.length >= page.total}
                  onClick={() => setOffset(offset + PAGE_SIZE)}
                >
                  Keyingi
                </button>
              </div>
            </div>
          </>
        )}
      </Card>
    </>
  )
}

function ReportRow({ report, editable }: { report: ModerationReport; editable: boolean }) {
  const resolve = useResolveReport()
  return (
    <tr>
      <td className="faint" style={{ whiteSpace: 'nowrap' }}>
        {formatDateTime(report.createdAt)}
      </td>
      <td>
        <span className="badge warn">{reasonLabels[report.reason]}</span>
        <div className="faint">{report.commentId ? 'izoh' : 'post'}</div>
      </td>
      <td>{report.excerpt}</td>
      <td className="muted">{report.note ?? ''}</td>
      <td>
        {report.resolvedAt ? (
          <span className="badge free">{report.resolution === 'hidden' ? 'Yashirildi' : 'Rad etildi'}</span>
        ) : report.targetHidden ? (
          <span className="badge danger">Avval yashirilgan</span>
        ) : (
          <span className="badge ok">Ochiq</span>
        )}
      </td>
      <td>
        {editable && (
          <div className="row">
            <button
              className="btn small"
              disabled={resolve.isPending}
              onClick={() => resolve.mutate({ id: report.id, action: 'dismiss' })}
            >
              Rad etish
            </button>
            <button
              className="btn small danger"
              disabled={resolve.isPending}
              onClick={() => resolve.mutate({ id: report.id, action: 'hide', reason: `Shikoyat: ${reasonLabels[report.reason]}` })}
            >
              Yashirish
            </button>
          </div>
        )}
      </td>
    </tr>
  )
}

function excerpt(text: string, length = 160): string {
  return text.length > length ? `${text.slice(0, length)}…` : text
}
