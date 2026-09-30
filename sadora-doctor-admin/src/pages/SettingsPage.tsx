import { useState } from 'react'
import { fieldsOf, messageOf } from '../api/client'
import { useDoctorSettings, useUpdateDoctorSettings } from '../api/hooks'
import type { DoctorHours, DoctorSettings } from '../api/types'
import {
  DEFAULT_END,
  DEFAULT_START,
  endFromInput,
  endToInput,
  formatSom,
  groupDigits,
  hoursProblem,
  minutesToTime,
  parseSom,
  priceProblem,
  PRICE_MAX_SOM,
  PRICE_MIN_SOM,
  timeToMinutes,
  weekdayLabels,
} from '../api/work'
import { useToast } from '../components/toast'
import { Card, ErrorNotice, Field, Loading, Spinner } from '../components/ui'

/** One row of the week editor: a day she works, with its hours, or a day off. */
interface DayDraft {
  on: boolean
  start: string
  end: string
}

function weekFrom(hours: DoctorHours[]): DayDraft[] {
  return weekdayLabels.map((_, index) => {
    const day = hours.find((item) => item.weekday === index + 1)
    return day
      ? { on: true, start: minutesToTime(day.startMinute), end: endToInput(day.endMinute) }
      : { on: false, start: minutesToTime(DEFAULT_START), end: minutesToTime(DEFAULT_END) }
  })
}

/** The week as the server keeps it, or the first day that cannot be read. */
function weekToHours(week: DayDraft[]): { hours: DoctorHours[]; problems: Record<number, string> } {
  const hours: DoctorHours[] = []
  const problems: Record<number, string> = {}
  week.forEach((day, index) => {
    if (!day.on) return
    const startMinute = timeToMinutes(day.start)
    const endMinute = endFromInput(day.end)
    if (startMinute === null || endMinute === null) {
      problems[index] = 'Vaqtni kiriting'
      return
    }
    const entry = { weekday: index + 1, startMinute, endMinute }
    const problem = hoursProblem(entry)
    if (problem) problems[index] = problem
    else hours.push(entry)
  })
  return { hours, problems }
}

const sameHours = (a: DoctorHours[], b: DoctorHours[]) =>
  a.length === b.length &&
  a.every((day, index) => {
    const other = b[index]!
    return other.weekday === day.weekday && other.startMinute === day.startMinute && other.endMinute === day.endMinute
  })

/**
 * "Ish vaqti va narx": what a consultation with her costs, when she answers, and the
 * switch for a day she cannot. The busy switch saves at once; price and hours together,
 * with one button.
 */
export function SettingsPage() {
  const settings = useDoctorSettings()
  if (settings.isPending) {
    return (
      <div className="two-col even">
        <Card>
          <Loading rows={4} height={40} />
        </Card>
        <Card>
          <Loading rows={7} height={32} />
        </Card>
      </div>
    )
  }
  if (!settings.data) return <ErrorNotice error={settings.error} onRetry={() => void settings.refetch()} />
  // Keyed on what the server last said, so a save or another tab's change resets the form.
  return <SettingsForm key={JSON.stringify([settings.data.priceMinor, settings.data.hours])} settings={settings.data} />
}

function SettingsForm({ settings }: { settings: DoctorSettings }) {
  const update = useUpdateDoctorSettings()
  const { notify } = useToast()
  const [price, setPrice] = useState(() => groupDigits(settings.priceMinor / 100))
  const [week, setWeek] = useState(() => weekFrom(settings.hours))
  const [serverErrors, setServerErrors] = useState<Record<string, string>>({})

  const som = parseSom(price)
  const priceError = serverErrors.priceMinor ?? priceProblem(som)
  const { hours, problems } = weekToHours(week)
  const hasProblems = Object.keys(problems).length > 0
  const priceMinor = som === null ? null : som * 100
  const dirty = priceMinor !== settings.priceMinor || hasProblems || !sameHours(hours, settings.hours)
  const net = som ? Math.round(som * 100 * (100 - settings.commissionPercent)) / 100 : 0

  function setDay(index: number, patch: Partial<DayDraft>) {
    setWeek((current) => current.map((day, at) => (at === index ? { ...day, ...patch } : day)))
    if (serverErrors.hours) setServerErrors({})
  }

  function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!dirty || priceError || hasProblems || priceMinor === null) return
    setServerErrors({})
    update.mutate(
      { priceMinor, hours },
      {
        onSuccess: () => notify('Saqlandi'),
        onError: (error) => {
          notify(messageOf(error), 'error')
          setServerErrors(fieldsOf(error))
        },
      },
    )
  }

  function reset() {
    setPrice(groupDigits(settings.priceMinor / 100))
    setWeek(weekFrom(settings.hours))
    setServerErrors({})
  }

  return (
    <form className="two-col even" onSubmit={submit} noValidate>
      <div className="grid" style={{ gap: 16, alignContent: 'start' }}>
        <BusySwitch settings={settings} />

        <Card title="Konsultatsiya narxi">
          <div className="grid" style={{ gap: 12 }}>
            <Field
              label="Narx, so'm"
              error={priceError}
              hint={som === 0 ? 'Bepul' : som ? formatSom(som * 100) : undefined}
            >
              <input
                inputMode="numeric"
                value={price}
                placeholder="0"
                onChange={(event) => {
                  setPrice(event.target.value)
                  if (serverErrors.priceMinor) setServerErrors({})
                }}
                onBlur={() => {
                  const value = parseSom(price)
                  if (value !== null) setPrice(groupDigits(value))
                }}
              />
            </Field>
            <p className="faint" style={{ margin: 0 }}>
              0 — konsultatsiyalaringiz bepul. Pullik bo'lsa, {groupDigits(PRICE_MIN_SOM)} dan {groupDigits(PRICE_MAX_SOM)}{' '}
              so'mgacha. Bemor to'lagandan keyin 24 soatlik konsultatsiya ochiladi; javob bermasangiz, pul unga qaytariladi.
            </p>
            <dl className="details">
              <dt>Sadora ulushi</dt>
              <dd>{settings.commissionPercent}%</dd>
              {som ? (
                <>
                  <dt>Sizga qoladi</dt>
                  <dd>{formatSom(net)} har bir konsultatsiyadan</dd>
                </>
              ) : null}
            </dl>
          </div>
        </Card>
      </div>

      <Card title="Ish vaqti">
        <div className="grid" style={{ gap: 12 }}>
          <p className="faint" style={{ margin: 0 }}>
            Bemorlar sahifangizda "Hozir javob beradi" yoki keyingi ish vaqtingizni ko'radi. Hech bir kun belgilanmasa, faqat
            "Bandman" tugmasi hal qiladi. Vaqt mintaqasi: {settings.timezone}.
          </p>
          <ul className="week" aria-label="Hafta kunlari">
            {week.map((day, index) => (
              <li key={weekdayLabels[index]} className={`week-day${day.on ? '' : ' off'}`}>
                <label className="week-name">
                  <input
                    type="checkbox"
                    checked={day.on}
                    onChange={(event) => setDay(index, { on: event.target.checked })}
                  />
                  {weekdayLabels[index]}
                </label>
                {day.on ? (
                  <span className="week-times">
                    <input
                      type="time"
                      aria-label={`${weekdayLabels[index]}: boshlanishi`}
                      value={day.start}
                      step={300}
                      onChange={(event) => setDay(index, { start: event.target.value })}
                    />
                    <span className="faint">—</span>
                    <input
                      type="time"
                      aria-label={`${weekdayLabels[index]}: tugashi`}
                      value={day.end}
                      step={300}
                      onChange={(event) => setDay(index, { end: event.target.value })}
                    />
                  </span>
                ) : (
                  <span className="faint week-times">Dam olish kuni</span>
                )}
                {problems[index] && (
                  <span className="field-error week-error" role="alert">
                    {problems[index]}
                  </span>
                )}
              </li>
            ))}
          </ul>
          <p className="faint" style={{ margin: 0 }}>
            Tugash vaqti 00:00 — kun yarim tungacha.
          </p>
          {serverErrors.hours && (
            <div className="notice error" role="alert">
              {serverErrors.hours}
            </div>
          )}
          <div className="row" style={{ justifyContent: 'flex-end' }}>
            <button className="btn ghost" type="button" onClick={reset} disabled={!dirty || update.isPending}>
              Bekor qilish
            </button>
            <button
              className="btn primary"
              type="submit"
              disabled={!dirty || Boolean(priceError) || hasProblems || update.isPending}
            >
              {update.isPending && <Spinner />}
              {update.isPending ? 'Saqlanmoqda…' : 'Saqlash'}
            </button>
          </div>
        </div>
      </Card>
    </form>
  )
}

/** For a day she cannot answer: saved at once, like the profile's consultation switch. */
function BusySwitch({ settings }: { settings: DoctorSettings }) {
  const update = useUpdateDoctorSettings()
  const { notify } = useToast()
  const busy = settings.busy

  function toggle() {
    const next = !busy
    update.mutate(
      { busy: next },
      {
        onSuccess: () => notify(next ? 'Band deb belgilandi' : 'Yana javob berasiz'),
        onError: (error) => notify(messageOf(error), 'error'),
      },
    )
  }

  return (
    <Card title="Holat">
      <label className="switch-row">
        <span className="switch-text">
          <b>Bandman</b>
          <span className="faint">
            {busy
              ? "Sahifangizda \"Hozir band\" ko'rinadi. Ochiq konsultatsiyalar davom etadi."
              : 'Ish vaqtingizda bemorlar sizni "Hozir javob beradi" deb ko\'radi.'}
          </span>
        </span>
        <input
          type="checkbox"
          role="switch"
          className="switch"
          aria-label="Bandman"
          checked={busy}
          disabled={update.isPending}
          onChange={toggle}
        />
      </label>
      {!settings.acceptsConsultations && (
        <div className="notice" style={{ marginTop: 12 }}>
          Konsultatsiya qabul qilish Profil sahifasida o'chirilgan — yangi konsultatsiya ochilmaydi.
        </div>
      )}
    </Card>
  )
}
