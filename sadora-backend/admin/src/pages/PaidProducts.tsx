import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useFrameProducts, usePetProducts, useUpdateFrameProduct, useUpdatePetProduct } from '../api/hooks'
import type { AdminFrameProduct, AdminPetProduct } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Card, ErrorNotice } from '../components/ui'

/*
 * What the app sells for money outside a subscription: the legendary pet and the avatar
 * frames. They sit on the shop page beside the Gul catalogue, so everything for sale is
 * priced in one place; the payments themselves stay on the billing page.
 */

export function PaidProducts() {
  return (
    <div className="grid" style={{ gap: 16 }}>
      <PetProductsCard />
      <FrameProductsCard />
    </div>
  )
}

/**
 * The legendary pet's price. One price everywhere: the app shows this one and Payme and
 * Click charge it; the stores charge what is set in their consoles, so a change here has
 * to be made there too. The sale itself is switched on the Funksiyalar page.
 */
function PetProductsCard() {
  const products = usePetProducts()
  const editable = useAuth().can(['OWNER', 'ADMIN'])
  return (
    <Card title="Legendar hamroh">
      <ErrorNotice error={products.error} />
      {products.data?.map((product) => <PetProductRow key={product.pet} product={product} editable={editable} />)}
      <p className="faint">
        Sotuvni <Link to="/features">Funksiyalar</Link> sahifasida yoqasiz. Play va App Store narxi ularning
        konsolida alohida o'zgartiriladi.
      </p>
    </Card>
  )
}

function PetProductRow({ product, editable }: { product: AdminPetProduct; editable: boolean }) {
  const update = useUpdatePetProduct()
  const [price, setPrice] = useState(String(Math.round(product.priceMinor / 100)))
  const whole = Number(price.replace(/\s/g, ''))
  const valid = Number.isFinite(whole) && whole >= 1000
  return (
    <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
      <b style={{ textTransform: 'capitalize' }}>{product.pet}</b>
      <span className="faint">{product.appStoreProductId ?? '—'} · {product.googlePlayProductId ?? '—'}</span>
      <input
        aria-label="Narx, so'm"
        inputMode="numeric"
        value={price}
        disabled={!editable}
        onChange={(event) => setPrice(event.target.value)}
        style={{ width: 140 }}
      />
      <span>so'm</span>
      <button
        className="btn small"
        disabled={!editable || !valid || update.isPending || whole * 100 === product.priceMinor}
        onClick={() => update.mutate({ pet: product.pet, priceMinor: whole * 100, active: product.active })}
      >
        Saqlash
      </button>
      <ErrorNotice error={update.error} />
    </div>
  )
}

/** What each frame is called in the panel; the keys are the contract's AvatarFrames. */
export const frameNames: Record<string, string> = {
  tulip: 'Lola',
  lavender: 'Lavanda',
  sakura: 'Sakura',
  moon: 'Oy va yulduzlar',
  rose: 'Atirgul toji',
  gold_flame: 'Oltin olov',
  gold_laurel: 'Oltin dafna',
  rainbow: 'Kamalak',
  humo_wing: 'Humo qanoti',
}

/**
 * Avatar frames sold for Gul or money. A badge's frame is not here: only the badge gives
 * it. Switching a frame off hides it from anyone who does not own it yet. The paid frames'
 * sale is switched on the Funksiyalar page.
 */
function FrameProductsCard() {
  const products = useFrameProducts()
  const editable = useAuth().can(['OWNER', 'ADMIN'])
  return (
    <Card title="Avatar ramkalari">
      <ErrorNotice error={products.error} />
      {products.data?.map((product) => <FrameProductRow key={product.key} product={product} editable={editable} />)}
      <p className="faint">
        Pullik ramkalar sotuvini <Link to="/features">Funksiyalar</Link> sahifasida yoqasiz. Play va App Store
        narxi ularning konsolida alohida o'zgartiriladi.
      </p>
    </Card>
  )
}

function FrameProductRow({ product, editable }: { product: AdminFrameProduct; editable: boolean }) {
  const update = useUpdateFrameProduct()
  const coins = product.unlock === 'coins'
  const current = coins ? (product.coinCost ?? 0) : Math.round((product.priceMinor ?? 0) / 100)
  const [price, setPrice] = useState(String(current))
  const whole = Number(price.replace(/\s/g, ''))
  const valid = Number.isFinite(whole) && (coins ? whole >= 1 : whole >= 1000)
  const save = (active: boolean) =>
    update.mutate(
      coins
        ? { key: product.key, coinCost: whole, active }
        : { key: product.key, priceMinor: whole * 100, active },
    )
  return (
    <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
      <b style={{ minWidth: 140 }}>{frameNames[product.key] ?? product.key}</b>
      {!coins && (
        <span className="faint">
          {product.appStoreProductId ?? '—'} · {product.googlePlayProductId ?? '—'}
        </span>
      )}
      <input
        aria-label={coins ? 'Narx, Gul' : "Narx, so'm"}
        inputMode="numeric"
        value={price}
        disabled={!editable}
        onChange={(event) => setPrice(event.target.value)}
        style={{ width: 120 }}
      />
      <span>{coins ? 'Gul' : "so'm"}</span>
      <button
        className="btn small"
        disabled={!editable || !valid || update.isPending || whole === current}
        onClick={() => save(product.active)}
      >
        Saqlash
      </button>
      <label style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
        <input
          type="checkbox"
          checked={product.active}
          disabled={!editable || update.isPending || !valid}
          onChange={(event) => save(event.target.checked)}
        />
        Sotuvda
      </label>
      <ErrorNotice error={update.error} />
    </div>
  )
}
