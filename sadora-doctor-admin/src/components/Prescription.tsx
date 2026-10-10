import { useState } from 'react'
import { fieldsOf, messageOf } from '../api/client'
import { useCancelPrescription, useConversationPrescriptions, useSendPrescription } from '../api/hooks'
import { limits } from '../api/limits'
import {
  emptyItem,
  FOOD_LABEL,
  FOODS,
  FORM_LABEL,
  FORM_UNIT,
  FORMS,
  itemFrom,
  presetTimes,
  problemsOf,
  summaryOf,
  toRequest,
} from '../api/prescriptions'
import type { ItemDraft } from '../api/prescriptions'
import type { Prescription } from '../api/types'
import { useToast } from './toast'
import { Counter, Field, formatDateTime, Modal, Spinner } from './ui'

const DISCLAIMER = 'Maslahat retsepti, rasmiy retsept emas'

/**
 * A prescription as a card: in the thread, where she may cancel it, and in the patient
 * panel. The status line says whether the patient added it to her medications.
 */
export function PrescriptionCard({ prescription, onCancel }: { prescription: Prescription; onCancel?: () => void }) {
  const cancelled = Boolean(prescription.cancelledAt)
  return (
    <div className={`rx-card${cancelled ? ' cancelled' : ''}`}>
      <div className="rx-head">
        <span className="rx-glyph" aria-hidden="true">
          💊
        </span>
        <span>
          <b>Retsept</b>
          <span className="faint">
            {prescription.doctor.fullName} · {formatDateTime(prescription.createdAt)}
          </span>
        </span>
      </div>
      <ol className="rx-items">
        {prescription.items.map((item, index) => (
          <li key={index}>
            <b className="rx-name">{item.name}</b>
            <span className="muted">{summaryOf(item)}</span>
            {item.note && <span className="faint">{item.note}</span>}
          </li>
        ))}
      </ol>
      {prescription.note && <p className="rx-note">{prescription.note}</p>}
      {cancelled ? (
        <p className="rx-status danger">
          Bekor qilingan{prescription.cancelReason ? ` — ${prescription.cancelReason}` : ''}
        </p>
      ) : (
        <div className="rx-foot">
          <span className={`rx-status${prescription.addedAt ? ' ok' : ''}`}>
            {prescription.addedAt ? '✓ Bemor tabletkalariga qo’shdi' : 'Bemor hali qo’shmagan'}
          </span>
          {onCancel && (
            <button type="button" className="btn ghost small" onClick={onCancel}>
              Bekor qilish
            </button>
          )}
        </div>
      )}
      <span className="faint rx-disclaimer">{DISCLAIMER}</span>
    </div>
  )
}

/**
 * Writing a prescription. One block per medicine; the last one she wrote this patient
 * can be copied in. Nothing goes until every block has a name, a dose, the food relation
 * and a length.
 */
export function PrescriptionDialog({ conversationId, onClose }: { conversationId: string; onClose: () => void }) {
  const send = useSendPrescription(conversationId)
  const history = useConversationPrescriptions(conversationId)
  const { notify } = useToast()
  const [items, setItems] = useState<ItemDraft[]>([emptyItem()])
  const [note, setNote] = useState('')
  const [tried, setTried] = useState(false)
  const previous = history.data?.find((item) => !item.cancelledAt)
  const ready = items.every((item) => problemsOf(item).size === 0)

  const update = (index: number, change: Partial<ItemDraft>) =>
    setItems((current) => current.map((item, i) => (i === index ? { ...item, ...change } : item)))

  function submit(event: React.FormEvent) {
    event.preventDefault()
    setTried(true)
    if (!ready || send.isPending) return
    send.mutate(toRequest(items, note), {
      onSuccess: () => {
        notify('Retsept yuborildi')
        onClose()
      },
      onError: (failure) => {
        const fields = fieldsOf(failure)
        notify(Object.values(fields)[0] ?? messageOf(failure), 'error')
      },
    })
  }

  return (
    <Modal title="Retsept yozish" wide onClose={onClose}>
      <form className="grid rx-form" style={{ gap: 14 }} onSubmit={submit}>
        {previous && (
          <button
            type="button"
            className="btn ghost"
            onClick={() => {
              setItems(previous.items.map(itemFrom))
              setNote(previous.note ?? '')
            }}
          >
            ⧉ Oldingi retseptdan nusxa
          </button>
        )}
        {items.map((item, index) => (
          <ItemBlock
            key={index}
            number={index + 1}
            item={item}
            showProblems={tried}
            onChange={(change) => update(index, change)}
            onRemove={items.length > 1 ? () => setItems((current) => current.filter((_, i) => i !== index)) : undefined}
          />
        ))}
        {items.length < limits.prescriptionItemsMax && (
          <button type="button" className="btn ghost" onClick={() => setItems((current) => [...current, emptyItem()])}>
            ＋ Dori qo'shish
          </button>
        )}
        <Field label="Umumiy izoh" hint={<Counter length={note.length} max={limits.prescriptionNoteMax} />}>
          <textarea
            rows={3}
            value={note}
            maxLength={limits.prescriptionNoteMax}
            placeholder="Ko'p suv iching, 2 haftadan keyin qayta ko'rinish…"
            onChange={(event) => setNote(event.target.value)}
          />
        </Field>
        <p className="faint" style={{ margin: 0 }}>
          {DISCLAIMER}
        </p>
        {tried && !ready && (
          <p className="field-error" role="alert" style={{ margin: 0 }}>
            Har bir dorining nomi, dozasi, ovqatga nisbatan va davomiyligini to'ldiring
          </p>
        )}
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <button type="button" className="btn ghost" onClick={onClose} disabled={send.isPending}>
            Bekor qilish
          </button>
          <button type="submit" className="btn primary" disabled={send.isPending}>
            {send.isPending && <Spinner />}
            Retseptni yuborish
          </button>
        </div>
      </form>
    </Modal>
  )
}

function ItemBlock({
  number,
  item,
  showProblems,
  onChange,
  onRemove,
}: {
  number: number
  item: ItemDraft
  showProblems: boolean
  onChange: (change: Partial<ItemDraft>) => void
  onRemove?: () => void
}) {
  const problems = showProblems ? problemsOf(item) : new Set<string>()
  const required = "To'ldirilishi shart"
  return (
    <fieldset className="rx-block">
      <legend>
        💊 {number}-dori
        {onRemove && (
          <button type="button" className="btn ghost small" onClick={onRemove}>
            Olib tashlash
          </button>
        )}
      </legend>
      <Field label="Dori nomi" error={problems.has('name') ? required : null}>
        <input
          value={item.name}
          maxLength={limits.medicationNameMax}
          placeholder="Masalan, Amoksitsillin"
          onChange={(event) => onChange({ name: event.target.value })}
        />
      </Field>
      <div className="rx-label">Shakli</div>
      <div className="rx-chips">
        {FORMS.map((form) => (
          <button
            key={form}
            type="button"
            className={`rx-chip${item.form === form ? ' on' : ''}`}
            aria-pressed={item.form === form}
            onClick={() => onChange({ form })}
          >
            {FORM_LABEL[form]}
          </button>
        ))}
      </div>
      <div className="rx-row">
        <Field label="Doza" error={problems.has('dose') ? required : null}>
          <input value={item.dose} maxLength={limits.prescriptionDoseMax} placeholder="1" onChange={(event) => onChange({ dose: event.target.value })} />
        </Field>
        <Field label="Birligi">
          <input
            value={item.unit ?? FORM_UNIT[item.form]}
            maxLength={limits.prescriptionUnitMax}
            onChange={(event) => onChange({ unit: event.target.value })}
          />
        </Field>
      </div>
      <div className="rx-label">Qachon ichiladi</div>
      <div className="rx-chips">
        {[1, 2, 3, 4].map((count) => (
          <button
            key={count}
            type="button"
            className={`rx-chip${item.times.length === count ? ' on' : ''}`}
            aria-pressed={item.times.length === count}
            onClick={() => onChange({ times: presetTimes(count) })}
          >
            Kuniga {count} marta
          </button>
        ))}
      </div>
      <div className="rx-times">
        {item.times.map((time, slot) => (
          <input
            key={slot}
            type="time"
            step={1800}
            value={time}
            aria-label={`${slot + 1}-qabul vaqti`}
            onChange={(event) => onChange({ times: item.times.map((value, i) => (i === slot ? event.target.value : value)) })}
          />
        ))}
      </div>
      {problems.has('times') && <span className="field-error">Vaqtlar to'g'ri va har xil bo'lsin</span>}
      <div className="rx-chips">
        <button type="button" className={`rx-chip${item.everyDays === null ? ' on' : ''}`} onClick={() => onChange({ everyDays: null })}>
          Har kuni
        </button>
        {[2, 3, 7].map((every) => (
          <button
            key={every}
            type="button"
            className={`rx-chip${item.everyDays === every ? ' on' : ''}`}
            onClick={() => onChange({ everyDays: every })}
          >
            Har {every} kunda
          </button>
        ))}
      </div>
      <div className="rx-label">Ovqatga nisbatan</div>
      <div className="rx-chips">
        {FOODS.map((food) => (
          <button
            key={food}
            type="button"
            className={`rx-chip${item.food === food ? ' on' : ''}`}
            aria-pressed={item.food === food}
            onClick={() => onChange({ food })}
          >
            {FOOD_LABEL[food]}
          </button>
        ))}
      </div>
      {problems.has('food') && <span className="field-error">{required}</span>}
      <div className="rx-row">
        <Field label="Nechanchi kundan">
          <input
            type="number"
            min={1}
            max={limits.prescriptionStartDayMax}
            value={item.startDay}
            onChange={(event) =>
              onChange({ startDay: Math.min(limits.prescriptionStartDayMax, Math.max(1, Number(event.target.value) || 1)) })
            }
          />
        </Field>
        <Field label="Necha kun" error={problems.has('days') ? required : null}>
          <input
            type="number"
            min={1}
            max={limits.prescriptionDaysMax}
            value={item.ongoing ? '' : item.days}
            placeholder={item.ongoing ? 'Doimiy' : '5'}
            onChange={(event) => onChange({ days: event.target.value, ongoing: false })}
          />
        </Field>
        <label className="rx-check">
          <input type="checkbox" checked={item.ongoing} onChange={(event) => onChange({ ongoing: event.target.checked })} /> Doimiy
        </label>
      </div>
      <Field label="Izoh (ixtiyoriy)">
        <input value={item.note} maxLength={limits.prescriptionItemNoteMax} onChange={(event) => onChange({ note: event.target.value })} />
      </Field>
    </fieldset>
  )
}

/** Asks why, then cancels. The patient is shown the reason and her courses stop. */
export function CancelPrescriptionDialog({
  conversationId,
  prescription,
  onClose,
}: {
  conversationId: string
  prescription: Prescription
  onClose: () => void
}) {
  const cancel = useCancelPrescription(conversationId)
  const { notify } = useToast()
  const [reason, setReason] = useState('')
  const ready = reason.trim().length > 0 && !cancel.isPending

  return (
    <Modal title="Retsept bekor qilinsinmi?" onClose={onClose}>
      <form
        className="grid"
        style={{ gap: 12 }}
        onSubmit={(event) => {
          event.preventDefault()
          if (!ready) return
          cancel.mutate(
            { id: prescription.id, reason: reason.trim() },
            {
              onSuccess: () => {
                notify('Retsept bekor qilindi')
                onClose()
              },
              onError: (failure) => notify(messageOf(failure), 'error'),
            },
          )
        }}
      >
        <p className="muted" style={{ margin: 0 }}>
          Bemorga xabar boradi va shu retseptdagi dorilar to'xtatiladi. Buni qaytarib bo'lmaydi.
        </p>
        <Field label="Sabab (majburiy, bemor ko'radi)" hint={<Counter length={reason.length} max={limits.prescriptionCancelReasonMax} />}>
          <textarea
            rows={3}
            value={reason}
            maxLength={limits.prescriptionCancelReasonMax}
            placeholder="Masalan: doza xato yozildi"
            onChange={(event) => setReason(event.target.value)}
          />
        </Field>
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <button type="button" className="btn ghost" onClick={onClose} disabled={cancel.isPending}>
            Yopish
          </button>
          <button type="submit" className="btn danger" disabled={!ready}>
            {cancel.isPending && <Spinner />}
            {cancel.isPending ? 'Bekor qilinmoqda…' : 'Retseptni bekor qilish'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

/** The consultation's prescriptions in the patient panel, newest first. */
export function PatientPrescriptions({ conversationId }: { conversationId: string }) {
  const list = useConversationPrescriptions(conversationId)
  const [cancelling, setCancelling] = useState<Prescription | null>(null)
  return (
    <>
      <h3 className="side-title">💊 Retseptlar</h3>
      {list.isPending ? (
        <p className="faint">Yuklanmoqda…</p>
      ) : !list.data?.length ? (
        <p className="faint">Bu bemorga hali retsept yozilmagan.</p>
      ) : (
        <div className="grid" style={{ gap: 8 }}>
          {list.data.map((prescription) => (
            <PrescriptionCard
              key={prescription.id}
              prescription={prescription}
              onCancel={prescription.cancelledAt ? undefined : () => setCancelling(prescription)}
            />
          ))}
        </div>
      )}
      {cancelling && (
        <CancelPrescriptionDialog conversationId={conversationId} prescription={cancelling} onClose={() => setCancelling(null)} />
      )}
    </>
  )
}
