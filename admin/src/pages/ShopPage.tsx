import { useState } from 'react'
import {
  useCreateShopProduct,
  useDeleteShopProduct,
  useShopProducts,
  useUpdateShopProduct,
} from '../api/hooks'
import type { AdminShopProduct, SaveShopProductBody, ShopKind } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Card, ErrorNotice, Field, Loading, Modal } from '../components/ui'

const KINDS: { value: ShopKind; label: string }[] = [
  { value: 'premium', label: 'Premium' },
  { value: 'vitamin', label: 'Vitamin' },
  { value: 'device', label: 'Qurilma' },
]

const EMPTY: SaveShopProductBody = {
  kind: 'vitamin',
  title: '',
  brand: '',
  description: '',
  emoji: '',
  priceUzs: null,
  discountPercent: 15,
  coinCost: 300,
  premiumDays: null,
  stock: null,
  active: true,
  position: 0,
}

/**
 * The Nur shop's catalogue.
 *
 * Two shapes of row live in one table, and the form switches between them, because the
 * server refuses anything else: a Premium row grants days and has no price, a partner
 * row has a price to discount and grants nothing. Mixing the two would be a pricing bug
 * on a live shop, so the schema, the service and this form all say the same thing.
 *
 * Deleting a product that has issued codes deactivates it instead. Those codes are
 * promises somebody is holding, and the page says so rather than silently doing
 * something different from what the button offered.
 */
export function ShopPage() {
  const products = useShopProducts()
  const create = useCreateShopProduct()
  const update = useUpdateShopProduct()
  const remove = useDeleteShopProduct()
  const { can } = useAuth()
  const editable = can(['OWNER', 'ADMIN'])

  const [editing, setEditing] = useState<AdminShopProduct | null>(null)
  const [creating, setCreating] = useState(false)
  const [slug, setSlug] = useState('')
  const [form, setForm] = useState<SaveShopProductBody>(EMPTY)

  if (products.isLoading) return <Loading rows={8} />
  if (products.error) return <ErrorNotice error={products.error} />

  function openCreate() {
    setForm(EMPTY)
    setSlug('')
    setEditing(null)
    setCreating(true)
  }

  function openEdit(product: AdminShopProduct) {
    setForm({
      kind: product.kind,
      title: product.title,
      brand: product.brand ?? '',
      description: product.description ?? '',
      emoji: product.emoji ?? '',
      priceUzs: product.priceUzs ?? null,
      discountPercent: product.discountPercent,
      coinCost: product.coinCost,
      premiumDays: product.premiumDays ?? null,
      stock: product.stock ?? null,
      active: product.active,
      position: product.position,
    })
    setEditing(product)
    setCreating(false)
  }

  function submit() {
    // The two shapes are cleaned here as well as validated on the server: sending a
    // leftover price on a Premium row would be refused, and the operator would see a
    // field error for something the form left behind rather than something she typed.
    const body: SaveShopProductBody = {
      ...form,
      brand: form.brand?.trim() || null,
      description: form.description?.trim() || null,
      emoji: form.emoji?.trim() || null,
      priceUzs: form.kind === 'premium' ? null : form.priceUzs,
      premiumDays: form.kind === 'premium' ? form.premiumDays : null,
      discountPercent: form.kind === 'premium' ? 0 : form.discountPercent,
    }
    if (editing) {
      update.mutate({ id: editing.id, product: body }, { onSuccess: () => setEditing(null) })
    } else {
      create.mutate({ slug: slug.trim().toLowerCase(), product: body }, { onSuccess: () => setCreating(false) })
    }
  }

  const rows = products.data ?? []
  const open = creating || editing !== null

  return (
    <div className="grid" style={{ gap: 16 }}>
      <div className="notice">
        Vitamin va qurilmalar hamkorlarda sotiladi — bu yerda faqat <b>chegirma foizi</b>{' '}
        va u qancha nur turishi belgilanadi. Premium esa server tomonidan darhol
        beriladi, shuning uchun unga narx emas, <b>kunlar soni</b> yoziladi.
      </div>

      {(create.error || update.error || remove.error) && (
        <ErrorNotice error={create.error ?? update.error ?? remove.error} />
      )}

      <Card
        title="Mahsulotlar"
        action={
          editable && (
            <button className="btn small" onClick={openCreate}>
              Yangi mahsulot
            </button>
          )
        }
      >
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Mahsulot</th>
                <th>Turi</th>
                <th>Narx</th>
                <th>Chegirma</th>
                <th>Nur</th>
                <th>Qolgan</th>
                <th>Olingan</th>
                <th>Faol</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((product) => (
                <tr key={product.id} style={{ opacity: product.active ? 1 : 0.55 }}>
                  <td>
                    <div>
                      {product.emoji} {product.title}
                    </div>
                    <div className="faint">
                      {product.brand ? `${product.brand} · ` : ''}
                      <span className="mono">{product.slug}</span>
                    </div>
                  </td>
                  <td>{KINDS.find((k) => k.value === product.kind)?.label}</td>
                  <td>{product.priceUzs ? product.priceUzs.toLocaleString('ru-RU') : '—'}</td>
                  <td>
                    {product.kind === 'premium'
                      ? `${product.premiumDays} kun`
                      : `${product.discountPercent}%`}
                  </td>
                  <td>{product.coinCost.toLocaleString('ru-RU')}</td>
                  <td>{product.stock ?? '∞'}</td>
                  <td>{product.redeemed}</td>
                  <td>{product.active ? 'ha' : 'yo‘q'}</td>
                  <td style={{ whiteSpace: 'nowrap' }}>
                    {editable && (
                      <>
                        <button className="btn ghost small" onClick={() => openEdit(product)}>
                          Tahrirlash
                        </button>{' '}
                        <button
                          className="btn ghost small"
                          onClick={() => {
                            const warning = product.redeemed
                              ? `${product.title}: ${product.redeemed} ta kod berilgan, shuning uchun o‘chirilmaydi — faqat yashiriladi. Davom etamizmi?`
                              : `${product.title} o‘chirilsinmi?`
                            if (confirm(warning)) remove.mutate(product.id)
                          }}
                        >
                          O‘chirish
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
              {!rows.length && (
                <tr>
                  <td colSpan={9} className="faint">
                    Katalog bo‘sh
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </Card>

      {open && (
      <Modal
        title={editing ? editing.title : 'Yangi mahsulot'}
        onClose={() => {
          setCreating(false)
          setEditing(null)
        }}
        wide
      >
        <div className="grid" style={{ gap: 12 }}>
          {!editing && (
            <Field label="Slug (o‘zgarmaydi)">
              <input
                value={slug}
                onChange={(event) => setSlug(event.target.value)}
                placeholder="vitamin-d3"
              />
            </Field>
          )}

          <Field label="Turi">
            <select
              value={form.kind}
              onChange={(event) => setForm({ ...form, kind: event.target.value as ShopKind })}
            >
              {KINDS.map((kind) => (
                <option key={kind.value} value={kind.value}>
                  {kind.label}
                </option>
              ))}
            </select>
          </Field>

          <Field label="Nomi">
            <input value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} />
          </Field>

          <Field label="Emoji">
            <input
              value={form.emoji ?? ''}
              maxLength={4}
              onChange={(event) => setForm({ ...form, emoji: event.target.value })}
              placeholder="☀️"
            />
          </Field>

          {form.kind !== 'premium' && (
            <>
              <Field label="Brend">
                <input
                  value={form.brand ?? ''}
                  onChange={(event) => setForm({ ...form, brand: event.target.value })}
                  placeholder="Solgar"
                />
              </Field>
              <Field label="Narxi (so‘m)">
                <input
                  type="number"
                  min={0}
                  value={form.priceUzs ?? ''}
                  onChange={(event) =>
                    setForm({ ...form, priceUzs: event.target.value === '' ? null : Number(event.target.value) })
                  }
                />
              </Field>
              <Field label="Chegirma (%)">
                <input
                  type="number"
                  min={0}
                  max={100}
                  value={form.discountPercent}
                  onChange={(event) => setForm({ ...form, discountPercent: Number(event.target.value) })}
                />
              </Field>
              <Field label="Zaxira (bo‘sh — cheksiz)">
                <input
                  type="number"
                  min={0}
                  value={form.stock ?? ''}
                  onChange={(event) =>
                    setForm({ ...form, stock: event.target.value === '' ? null : Number(event.target.value) })
                  }
                />
              </Field>
            </>
          )}

          {form.kind === 'premium' && (
            <Field label="Necha kun Premium">
              <input
                type="number"
                min={1}
                max={365}
                value={form.premiumDays ?? ''}
                onChange={(event) =>
                  setForm({ ...form, premiumDays: event.target.value === '' ? null : Number(event.target.value) })
                }
              />
            </Field>
          )}

          <Field label="Narxi (nur)">
            <input
              type="number"
              min={0}
              value={form.coinCost}
              onChange={(event) => setForm({ ...form, coinCost: Number(event.target.value) })}
            />
          </Field>

          <Field label="Tavsif">
            <textarea
              rows={3}
              value={form.description ?? ''}
              onChange={(event) => setForm({ ...form, description: event.target.value })}
            />
          </Field>

          <Field label="Tartib raqami">
            <input
              type="number"
              value={form.position}
              onChange={(event) => setForm({ ...form, position: Number(event.target.value) })}
            />
          </Field>

          <label className="row" style={{ gap: 8, alignItems: 'center' }}>
            <input
              type="checkbox"
              style={{ width: 'auto' }}
              checked={form.active}
              onChange={(event) => setForm({ ...form, active: event.target.checked })}
            />
            Ilovada ko‘rinsin
          </label>

          {/* What the user will actually see, computed the same way the app computes it.
              A discount is easy to mistype as a coin price, and this line catches it. */}
          {form.kind !== 'premium' && form.priceUzs ? (
            <div className="notice">
              Ilovada:{' '}
              <b>
                {Math.round(form.priceUzs - (form.priceUzs * form.discountPercent) / 100).toLocaleString('ru-RU')} so‘m
              </b>{' '}
              <span className="faint">({form.priceUzs.toLocaleString('ru-RU')} so‘m o‘rniga)</span> ·{' '}
              {form.coinCost.toLocaleString('ru-RU')} nur
            </div>
          ) : null}

          <div className="row" style={{ justifyContent: 'flex-end', gap: 8 }}>
            <button
              className="btn small ghost"
              onClick={() => {
                setCreating(false)
                setEditing(null)
              }}
            >
              Bekor qilish
            </button>
            <button
              className="btn"
              onClick={submit}
              disabled={
                create.isPending ||
                update.isPending ||
                !form.title.trim() ||
                (!editing && !slug.trim()) ||
                (form.kind === 'premium' ? !form.premiumDays : form.priceUzs == null)
              }
            >
              {create.isPending || update.isPending ? 'Saqlanmoqda…' : 'Saqlash'}
            </button>
          </div>
        </div>
      </Modal>
      )}
    </div>
  )
}
