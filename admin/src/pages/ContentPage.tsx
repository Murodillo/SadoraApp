import { useMemo, useState } from 'react'
import {
  useArticleCategories,
  useArticles,
  useCreateArticle,
  useDeleteArticle,
  usePublishArticle,
  useUpdateArticle,
} from '../api/hooks'
import { acceptSlug, limits, slugPattern } from '../api/limits'
import type { AdminArticle, ArticleBlock, ArticleKind, SaveArticleBody } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Card, Empty, ErrorNotice, Field, formatDateTime, Loading, Modal } from '../components/ui'

const kindLabels: Record<ArticleKind, string> = {
  article: 'Maqola',
  course: 'Kurs',
  video: 'Video',
}

/**
 * The Bilim library.
 *
 * The app carries no articles of its own, so what is written here is the whole library.
 * Publishing is deliberately a separate action from saving: an editor can leave a piece
 * half-written without it appearing in anybody's app.
 */
export function ContentPage() {
  const { can } = useAuth()
  const articles = useArticles()
  const categories = useArticleCategories()
  const publish = usePublishArticle()
  const remove = useDeleteArticle()
  const [editing, setEditing] = useState<AdminArticle | 'new' | null>(null)

  const mayEdit = can(['OWNER', 'ADMIN'])
  const categoryLabels = useMemo(
    () => Object.fromEntries((categories.data ?? []).map((it) => [it.key, it.label])),
    [categories.data],
  )

  return (
    <div className="grid" style={{ gap: 16 }}>
      <Card
        title="Maqolalar"
        action={
          mayEdit ? (
            <button className="btn" onClick={() => setEditing('new')}>
              Yangi maqola
            </button>
          ) : undefined
        }
      >
        {articles.isLoading && <Loading />}
        <ErrorNotice error={articles.error} />

        {articles.data && articles.data.length === 0 && (
          <Empty>Hozircha maqola yo'q. "Yangi maqola" bilan birinchisini yozing.</Empty>
        )}

        {articles.data && articles.data.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Sarlavha</th>
                <th>Turi</th>
                <th>Kategoriya</th>
                <th>Uzunlik</th>
                <th>Holat</th>
                <th>O'zgargan</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {articles.data.map((article) => (
                <tr key={article.slug}>
                  <td>
                    <div>{article.title}</div>
                    <div className="faint">{article.slug}</div>
                  </td>
                  <td>{kindLabels[article.kind]}</td>
                  <td>{categoryLabels[article.categoryKey] ?? article.categoryKey}</td>
                  <td>{article.readMinutes} daq.</td>
                  <td>
                    <span className={`badge${article.published ? ' ok' : ''}`}>
                      {article.published ? 'Chop etilgan' : 'Qoralama'}
                    </span>
                    {article.premium && <span className="badge premium">Premium</span>}
                  </td>
                  <td className="faint">{formatDateTime(article.updatedAt)}</td>
                  <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                    <button className="btn small ghost" onClick={() => setEditing(article)}>
                      {mayEdit ? 'Tahrirlash' : "Ko'rish"}
                    </button>
                    {mayEdit && (
                      <>
                        <button
                          className="btn small ghost"
                          onClick={() =>
                            publish.mutate({ slug: article.slug, published: !article.published })
                          }
                        >
                          {article.published ? 'Qoralamaga' : 'Chop etish'}
                        </button>
                        <button
                          className="btn small danger"
                          onClick={() => {
                            if (confirm(`"${article.title}" o'chirilsinmi?`)) remove.mutate(article.slug)
                          }}
                        >
                          O'chirish
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <ErrorNotice error={publish.error ?? remove.error} />
      </Card>

      {editing && (
        <ArticleEditor
          article={editing === 'new' ? null : editing}
          readOnly={!mayEdit}
          categories={(categories.data ?? []).map((it) => ({ key: it.key, label: it.label }))}
          onClose={() => setEditing(null)}
        />
      )}
    </div>
  )
}

// ---------------------------------------------------------------- editor

/**
 * The body is edited as plain text and converted on save.
 *
 * A line starting with `## ` is a heading, `- ` a bullet, `> ` a note, anything else a
 * paragraph. A rich editor would be nicer; this one is honest about the block types the
 * reader can actually draw, which a rich editor would have to be constrained to anyway.
 */
function ArticleEditor({
  article,
  readOnly,
  categories,
  onClose,
}: {
  article: AdminArticle | null
  readOnly: boolean
  categories: { key: string; label: string }[]
  onClose: () => void
}) {
  const create = useCreateArticle()
  const update = useUpdateArticle()

  const [slug, setSlug] = useState(article?.slug ?? '')
  const [title, setTitle] = useState(article?.title ?? '')
  const [excerpt, setExcerpt] = useState(article?.excerpt ?? '')
  const [kind, setKind] = useState<ArticleKind>(article?.kind ?? 'article')
  const [categoryKey, setCategoryKey] = useState(article?.categoryKey ?? categories[0]?.key ?? '')
  const [premium, setPremium] = useState(article?.premium ?? false)
  const [author, setAuthor] = useState(article?.author ?? '')
  const [authorRole, setAuthorRole] = useState(article?.authorRole ?? '')
  const [reviewedBy, setReviewedBy] = useState(article?.reviewedBy ?? '')
  const [disclaimer, setDisclaimer] = useState(article?.disclaimer ?? '')
  const [body, setBody] = useState(() => blocksToText(article?.blocks ?? []))

  const payload = (): SaveArticleBody => ({
    kind,
    categoryKey,
    title,
    excerpt,
    blocks: textToBlocks(body),
    premium,
    author: author || null,
    authorRole: authorRole || null,
    reviewedBy: reviewedBy || null,
    disclaimer: disclaimer || null,
    readMinutes: null,
  })

  const save = () => {
    if (article) {
      update.mutate({ slug: article.slug, article: payload() }, { onSuccess: onClose })
    } else {
      create.mutate({ slug, article: payload() }, { onSuccess: onClose })
    }
  }

  const busy = create.isPending || update.isPending

  return (
    <Modal title={article ? article.title : 'Yangi maqola'} onClose={onClose} wide>
      <div className="grid" style={{ gap: 12 }}>
        {!article && (
          <Field label="Slug — havolada shu ko'rinadi va keyin o'zgarmaydi">
            <input
              value={slug}
              onChange={(e) => setSlug(acceptSlug(e.target.value))}
              pattern={slugPattern}
              maxLength={limits.article.slugMax}
              placeholder="temirga-boy-taomlar"
              disabled={readOnly}
            />
          </Field>
        )}

        <Field label="Sarlavha">
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={limits.article.titleMax}
            disabled={readOnly}
          />
        </Field>

        <Field label="Qisqacha — kartochkada ko'rinadi">
          <textarea
            rows={2}
            value={excerpt}
            onChange={(e) => setExcerpt(e.target.value)}
            maxLength={limits.article.excerptMax}
            disabled={readOnly}
          />
        </Field>

        <div className="row">
          <Field label="Turi">
            <select
             
              value={kind}
              onChange={(e) => setKind(e.target.value as ArticleKind)}
              disabled={readOnly}
            >
              {Object.entries(kindLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </Field>

          <Field label="Kategoriya">
            <select
             
              value={categoryKey}
              onChange={(e) => setCategoryKey(e.target.value)}
              disabled={readOnly}
            >
              {categories.map((it) => (
                <option key={it.key} value={it.key}>
                  {it.label}
                </option>
              ))}
            </select>
          </Field>
        </div>

        <Field label="Premium — tanasi faqat obunachilarga ochiladi">
          <label className="row">
            <input
              type="checkbox"
              checked={premium}
              onChange={(e) => setPremium(e.target.checked)}
              disabled={readOnly}
            />
            <span>Faqat Premium</span>
          </label>
        </Field>

        <div className="row">
          <Field label="Muallif">
            <input
              value={author}
              onChange={(e) => setAuthor(e.target.value)}
              maxLength={limits.article.personMax}
              disabled={readOnly}
            />
          </Field>
          <Field label="Muallif roli">
            <input
              value={authorRole}
              onChange={(e) => setAuthorRole(e.target.value)}
              maxLength={limits.article.personMax}
              placeholder="Muallif · nutritsiolog"
              disabled={readOnly}
            />
          </Field>
        </div>

        <Field label="Ko'rib chiqqan mutaxassis">
          <input
            value={reviewedBy}
            onChange={(e) => setReviewedBy(e.target.value)}
            maxLength={limits.article.personMax}
            placeholder="Dr. S. Aliyeva ko'rib chiqqan"
            disabled={readOnly}
          />
        </Field>

        <Field label="Matn — `## sarlavha`, `- ro'yxat`, `> eslatma`, qolgani xatboshi">
          <textarea
            className="mono"
            rows={14}
            value={body}
            onChange={(e) => setBody(e.target.value)}
            disabled={readOnly}
          />
        </Field>

        <Field label="Ogohlantirish — maqola oxirida chiqadi">
          <textarea
            rows={2}
            value={disclaimer}
            onChange={(e) => setDisclaimer(e.target.value)}
            maxLength={limits.article.disclaimerMax}
            disabled={readOnly}
          />
        </Field>

        <ErrorNotice error={create.error ?? update.error} />

        {!readOnly && (
          <div className="row" style={{ justifyContent: 'flex-end', gap: 8 }}>
            <button className="btn small ghost" onClick={onClose}>
              Bekor qilish
            </button>
            <button className="btn" onClick={save} disabled={busy || !title || (!article && !slug)}>
              {busy ? 'Saqlanmoqda…' : 'Saqlash'}
            </button>
          </div>
        )}

        {article && (
          <p className="faint">
            Saqlash chop etish holatini o'zgartirmaydi — qoralama qoralamaligicha qoladi.
          </p>
        )}
      </div>
    </Modal>
  )
}

// ---------------------------------------------------------------- body text

export function blocksToText(blocks: ArticleBlock[]): string {
  return blocks
    .map((block) => {
      switch (block.type) {
        case 'heading':
          return `## ${block.text}`
        case 'bullets':
          return block.items.map((item) => `- ${item}`).join('\n')
        case 'note':
          return `> ${block.text}`
        default:
          return block.text
      }
    })
    .join('\n\n')
}

/** Consecutive `- ` lines become one bullets block, which is how the reader draws them. */
export function textToBlocks(text: string): ArticleBlock[] {
  const blocks: ArticleBlock[] = []
  let bullets: string[] = []

  const flush = () => {
    if (bullets.length > 0) {
      blocks.push({ type: 'bullets', items: bullets })
      bullets = []
    }
  }

  for (const rawLine of text.split('\n')) {
    const line = rawLine.trim()
    if (line === '') {
      flush()
      continue
    }
    if (line.startsWith('- ')) {
      bullets.push(line.slice(2).trim())
      continue
    }
    flush()
    if (line.startsWith('## ')) blocks.push({ type: 'heading', text: line.slice(3).trim() })
    else if (line.startsWith('> ')) blocks.push({ type: 'note', text: line.slice(2).trim() })
    else blocks.push({ type: 'paragraph', text: line })
  }
  flush()
  return blocks
}
