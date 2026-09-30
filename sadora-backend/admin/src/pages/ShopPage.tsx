import { useState } from 'react'
import {
  useCreateShopProduct,
  useDeleteShopProduct,
  useShopProducts,
  useUpdateShopProduct,
} from '../api/hooks'
import type { AdminShopProduct, SaveShopProductBody, ShopKind } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { acceptSlug, slugPattern } from '../api/limits'
import { useToast } from '../components/toast'
import { Card, ErrorNotice, Field, Loading, Modal, Spinner, Switch, TabPanel, Tabs } from '../components/ui'

const KINDS: { value: ShopKind; label: string }[] = [
  { value: 'premium', label: 'Obuna (Premium)' },
  { value: 'device', label: 'Qurilma' },
  { value: 'vitamin', label: 'Dori va vitamin' },
]

/** One tab per kind: the three live in one table, but nobody manages them together. */
const TABS: { kind: ShopKind; label: string; title: string; add: string; notice: string }[] = [
  {
    kind: 'premium',
    label: 'Obunalar',
    title: 'Premium obunalar',
    add: 'Yangi obuna',
    notice: 'Premium server tomonidan darhol beriladi, shuning uchun unga narx emas, kunlar soni yoziladi.',
  },
  {
    kind: 'device',
    label: 'Qurilmalar',
    title: 'Qurilmalar',
    add: 'Yangi qurilma',
    notice: 'Soat, bilaguzuk va uzuklar hamkor do‘konlarda sotiladi — bu yerda faqat chegirma foizi va u qancha gul turishi belgilanadi.',
  },
  {
    kind: 'vitamin',
    label: 'Dorilar',
    title: 'Dorilar va vitaminlar',
    add: 'Yangi dori',
    notice: 'Vitamin va dorilar hamkor dorixonalarda sotiladi — bu yerda faqat chegirma foizi va u qancha gul turishi belgilanadi.',
  },
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
 * The Gul shop's catalogue, a tab per kind: subscriptions, devices, medicines.
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
  const { notify } = useToast()
  const editable = can(['OWNER', 'ADMIN'])

  const [editing, setEditing] = useState<AdminShopProduct | null>(null)
  const [creating, setCreating] = useState(false)
  const [slug, setSlug] = useState('')
  const [form, setForm] = useState<SaveShopProductBody>(EMPTY)
  const [tab, setTab] = useState<ShopKind>('premium')

  if (products.isLoading) return <Loading rows={8} />
  if (products.error) return <ErrorNotice error={products.error} />

  function openCreate() {
    // A new product starts as the tab it was added from. A Premium row has no price and
    // no discount, so it starts with days instead.
    setForm(tab === 'premium' ? { ...EMPTY, kind: tab, discountPercent: 0, premiumDays: 30 } : { ...EMPTY, kind: tab })
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
      update.mutate(
        { id: editing.id, product: body },
        {
          onSuccess: () => {
            notify(`${body.title} saqlandi`)
            setEditing(null)
          },
        },
      )
    } else {
      create.mutate(
        { slug: acceptSlug(slug), product: body },
        {
          onSuccess: () => {
            notify(`${body.title} katalogga qo‘shildi`)
            setCreating(false)
          },
        },
      )
    }
  }

  const all = products.data ?? []
  const current = TABS.find((t) => t.kind === tab) ?? TABS[0]!
  const rows = all.filter((product) => product.kind === tab)
  const premium = tab === 'premium'
  const open = creating || editing !== null

  return (
    <div className="grid" style={{ gap: 16 }}>
      <Tabs<ShopKind>
        value={tab}
        onChange={setTab}
        items={TABS.map((t) => ({
          key: t.kind,
          label: (
            <>
              {t.label}
              <span className="badge" style={{ marginLeft: 6 }}>
                {all.filter((product) => product.kind === t.kind).length}
              </span>
            </>
          ),
        }))}
      />

      <TabPanel id={tab}>
        <div className="grid" style={{ gap: 16 }}>
          <div className="notice">{current.notice}</div>

          {(create.error || update.error || remove.error) && (
            <ErrorNotice error={create.error ?? update.error ?? remove.error} />
          )}

          <Card
            title={current.title}
            action={
              editable && (
                <button className="btn small" onClick={openCreate}>
                  {current.add}
                </button>
              )
            }
          >
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Mahsulot</th>
                    {premium ? <th>Muddati</th> : (
                      <>
                        <th>Narx</th>
                        <th>Chegirma</th>
                      </>
                    )}
                    <th>Gul</th>
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
                      {premium ? (
                        <td>{product.premiumDays} kun</td>
                      ) : (
                        <>
                          <td>{product.priceUzs ? product.priceUzs.toLocaleString('ru-RU') : '—'}</td>
                          <td>{product.discountPercent}%</td>
                        </>
                      )}
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
                                if (confirm(warning)) {
                                  remove.mutate(product.id, {
                                    onSuccess: () =>
                                      notify(product.redeemed ? `${product.title} yashirildi` : `${product.title} o‘chirildi`, 'info'),
                                  })
                                }
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
                      <td colSpan={premium ? 7 : 8} className="faint">
                        Bu bo‘limda hozircha mahsulot yo‘q
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </Card>
        </div>
      </TabPanel>

      {open && (
      <Modal
        title={editing ? editing.title : current.add}
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
                onChange={(event) => setSlug(acceptSlug(event.target.value))}
                pattern={slugPattern}
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

          <Field label="Narxi (gul)">
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

          <Switch label="Ilovada ko‘rinsin" checked={form.active} onChange={(active) => setForm({ ...form, active })} />

          {/* What the user will actually see, computed the same way the app computes it.
              A discount is easy to mistype as a coin price, and this line catches it. */}
          {form.kind !== 'premium' && form.priceUzs ? (
            <div className="notice">
              Ilovada:{' '}
              <b>
                {Math.round(form.priceUzs - (form.priceUzs * form.discountPercent) / 100).toLocaleString('ru-RU')} so‘m
              </b>{' '}
              <span className="faint">({form.priceUzs.toLocaleString('ru-RU')} so‘m o‘rniga)</span> ·{' '}
              {form.coinCost.toLocaleString('ru-RU')} gul
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
              {(create.isPending || update.isPending) && <Spinner />}
              {create.isPending || update.isPending ? 'Saqlanmoqda…' : 'Saqlash'}
            </button>
          </div>
        </div>
      </Modal>
      )}
    </div>
  )
}
