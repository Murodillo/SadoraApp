import type { ReactNode } from 'react'
import type { CyclePhase, DoctorSummary, HealthMetric } from '../api/types'
import { lifeStageLabel, RECORD_DISCLAIMER } from './labels'
import { formatDateTime } from './ui'

/*
 * The record a patient attached, drawn the way the doctor app's PatientRecordScreen
 * draws it: who she is, her cycle or pregnancy, what she has felt, what she takes, her
 * visits, how she has been, food, and her devices. A section with nothing in it is left
 * out rather than drawn empty. The words are the doctor app's `TabStringsUz`.
 */

const months = ['yanvar', 'fevral', 'mart', 'aprel', 'may', 'iyun', 'iyul', 'avgust', 'sentabr', 'oktabr', 'noyabr', 'dekabr']

/** `2026-09-04` -> `4-sentabr 2026`, read from the string so no timezone can move the day. */
export function formatLocalDate(value: string | null | undefined): string {
  if (!value) return '—'
  const [year, month, day] = value.split('-').map(Number)
  if (!year || !month || !day || !months[month - 1]) return value
  return `${day}-${months[month - 1]} ${year}`
}

function formatMonthYear(value: string): string {
  const [year, month] = value.split('-').map(Number)
  return year && month && months[month - 1] ? `${months[month - 1]} ${year}` : value
}

const phaseLabels: Record<CyclePhase, string> = {
  period: 'Hayz',
  follicular: 'Follikulyar',
  fertile: 'Fertil oyna',
  luteal: 'Lyutein',
}

const metricLabels: Partial<Record<HealthMetric, string>> = {
  steps: 'Qadamlar',
  resting_heart_rate: 'Tinch holatdagi puls',
  heart_rate: 'Puls',
  hrv: 'HRV',
  sleep_duration: 'Uyqu',
  spo2: 'SpO₂, %',
  body_temperature: 'Tana harorati',
  respiratory_rate: 'Nafas tezligi',
  weight: 'Vazn',
}

const metricUnits: Partial<Record<HealthMetric, string>> = {
  steps: 'qadam',
  heart_rate: 'zarba/daq',
  resting_heart_rate: 'zarba/daq',
  hrv: 'ms',
  respiratory_rate: 'nafas/daq',
  body_temperature: '°C',
  weight: 'kg',
}

const wholeMetrics: HealthMetric[] = ['steps', 'heart_rate', 'resting_heart_rate', 'sleep_duration', 'spo2', 'hrv']

function oneDecimal(value: number): string {
  return (Math.round(value * 10) / 10).toFixed(1)
}

function metricValue(metric: HealthMetric, value: number): string {
  if (metric === 'sleep_duration') {
    const minutes = Math.round(value)
    return `${Math.floor(minutes / 60)} soat ${minutes % 60} daq`
  }
  const number = wholeMetrics.includes(metric) ? String(Math.round(value)) : oneDecimal(value)
  return [number, metricUnits[metric] ?? ''].filter(Boolean).join(' ')
}

type Fact = [label: string, value: ReactNode]

function Facts({ facts }: { facts: Fact[] }) {
  return (
    <dl className="record-facts">
      {facts.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  )
}

function Section({ title, glyph, subtitle, children }: { title: string; glyph: string; subtitle?: string; children: ReactNode }) {
  return (
    <section className="record-section" aria-label={title}>
      <header className="record-section-head">
        <span className="record-glyph" aria-hidden="true">
          {glyph}
        </span>
        <div>
          <h4>{title}</h4>
          {subtitle && <div className="faint">{subtitle}</div>}
        </div>
      </header>
      {children}
    </section>
  )
}

const present = <T,>(facts: (T | null | undefined | false | '')[]): T[] => facts.filter(Boolean) as T[]

export function PatientRecord({ record }: { record: DoctorSummary }) {
  const { person, cycle, pregnancy, mind, nutrition, wearable } = record
  const symptoms = (record.symptomCounts ?? []).slice(0, 10)
  const medications = record.medications ?? []
  const appointments = record.appointments ?? []

  const personFacts = present<Fact>([
    person.heightCm != null && ["Bo'y", `${person.heightCm} sm`],
    person.weightKg != null && ['Vazn', `${person.weightKg} kg`],
  ])

  const cycleFacts = cycle
    ? present<Fact>([
        cycle.cycleDay != null && ['Sikl kuni', String(cycle.cycleDay)],
        cycle.phase && ['Faza', phaseLabels[cycle.phase] ?? cycle.phase],
        cycle.lastPeriodStart && ['Oxirgi hayz', formatLocalDate(cycle.lastPeriodStart)],
        cycle.history.averageCycleLength != null && ["O'rtacha sikl", `${cycle.history.averageCycleLength} kun`],
        cycle.history.averagePeriodLength != null && ["O'rtacha hayz", `${cycle.history.averagePeriodLength} kun`],
        cycle.history.shortestCycle != null &&
          cycle.history.longestCycle != null && ['Diapazon', `${cycle.history.shortestCycle}–${cycle.history.longestCycle}`],
        cycle.history.prediction?.nextPeriodStart && [
          'Keyingi hayz',
          `${formatLocalDate(cycle.history.prediction.nextPeriodStart)} (taxminiy)`,
        ],
      ])
    : []

  const pregnancyFacts = pregnancy
    ? present<Fact>([
        pregnancy.week != null && ['Hafta', String(pregnancy.week)],
        pregnancy.dueDate && ["Taxminiy tug'ruq sanasi", formatLocalDate(pregnancy.dueDate)],
        pregnancy.childBirthDate && ["Tug'ruq sanasi", formatLocalDate(pregnancy.childBirthDate)],
      ])
    : []
  const lessMovement = pregnancy?.lessMovementDays?.length ?? 0

  const mindFacts =
    mind && mind.daysLogged > 0
      ? present<Fact>([
          ['Qayd etilgan kunlar', `${mind.daysLogged} / ${mind.windowDays}`],
          mind.averageMood != null && ["O'rtacha kayfiyat", `${oneDecimal(mind.averageMood)} / 5`],
          mind.averageEnergy != null && ["O'rtacha energiya", `${oneDecimal(mind.averageEnergy)} / 5`],
          mind.averageStress != null && ["O'rtacha stress", `${oneDecimal(mind.averageStress)} / 5`],
        ])
      : []

  const nutritionFacts =
    nutrition && nutrition.daysLogged > 0
      ? present<Fact>([
          ['Qayd etilgan kunlar', `${nutrition.daysLogged} / ${nutrition.windowDays}`],
          nutrition.averageKcal != null && ["O'rtacha kaloriya", `${nutrition.averageKcal} kkal`],
          nutrition.averageWaterMl != null && ["O'rtacha suv", `${nutrition.averageWaterMl} ml`],
        ])
      : []

  const wearableFacts = (wearable?.averages ?? []).flatMap<Fact>((average) => {
    const label = metricLabels[average.metric]
    return label ? [[label, metricValue(average.metric, average.value)]] : []
  })

  return (
    <div className="record">
      <div className="record-person">
        <b className="record-name">{person.name}</b>
        <div className="muted">{present([person.age != null && `${person.age} yosh`, lifeStageLabel(person.lifeStage)]).join(' · ')}</div>
        {personFacts.length > 0 && <Facts facts={personFacts} />}
        <div className="faint">
          Ilovada: {formatMonthYear(person.memberSince)} dan · Tuzilgan: {formatDateTime(record.generatedAt)}
        </div>
      </div>

      {cycleFacts.length > 0 && (
        <Section title="Hayz sikli" glyph="💧">
          <Facts facts={cycleFacts} />
        </Section>
      )}

      {(pregnancyFacts.length > 0 || lessMovement > 0) && (
        <Section title="Homiladorlik" glyph="🤰">
          {pregnancyFacts.length > 0 && <Facts facts={pregnancyFacts} />}
          {lessMovement > 0 && <p className="record-alert">Bola harakati kam sezilgan kunlar: {lessMovement}</p>}
        </Section>
      )}

      {symptoms.length > 0 && (
        <Section title="Simptomlar" glyph="🩺" subtitle="Oxirgi 90 kun, eng ko'p uchraganlari">
          <Facts facts={symptoms.map<Fact>((symptom) => [symptom.label, `${symptom.days} kun`])} />
        </Section>
      )}

      {medications.length > 0 && (
        <Section title="Dorilar va qo'shimchalar" glyph="💊">
          <ul className="record-list">
            {medications.map((med, index) => (
              <li key={`${med.name}-${index}`} className={med.active ? undefined : 'inactive'}>
                <b>{present([med.name, present([med.dosage, med.unit]).join(' ')]).join(' · ')}</b>
                <span className="faint">
                  {present([
                    `${formatLocalDate(med.startedOn)}${med.endedOn ? ` – ${formatLocalDate(med.endedOn)}` : ''}`,
                    med.adherencePercent != null && `Qabul: ${med.adherencePercent}%`,
                    !med.active && 'Tugagan',
                  ]).join(' · ')}
                </span>
              </li>
            ))}
          </ul>
        </Section>
      )}

      {appointments.length > 0 && (
        <Section title="Ko'riklar va tekshiruvlar" glyph="📅">
          <ul className="record-list">
            {appointments.map((visit) => (
              <li key={visit.id}>
                <b>{visit.title}</b>
                <span className="faint">
                  {present([
                    `${formatLocalDate(visit.scheduledOn)}${visit.scheduledAt ? `, ${visit.scheduledAt.slice(0, 5)}` : ''}`,
                    visit.place,
                  ]).join(' · ')}
                </span>
              </li>
            ))}
          </ul>
        </Section>
      )}

      {mindFacts.length > 0 && (
        <Section title="Kayfiyat va holat" glyph="🙂">
          <Facts facts={mindFacts} />
        </Section>
      )}

      {nutritionFacts.length > 0 && (
        <Section title="Ovqatlanish" glyph="🥗">
          <Facts facts={nutritionFacts} />
        </Section>
      )}

      {wearableFacts.length > 0 && (
        <Section title="Qurilma ko'rsatkichlari" glyph="⌚">
          <Facts facts={wearableFacts} />
        </Section>
      )}

      <p className="disclaimer">{RECORD_DISCLAIMER}</p>
    </div>
  )
}
