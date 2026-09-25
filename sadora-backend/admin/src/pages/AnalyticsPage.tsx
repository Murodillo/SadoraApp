import { useState } from 'react'
import { useAnalytics, useStats } from '../api/hooks'
import type { AdminAnalytics, NamedCount } from '../api/types'
import { movingAverage, share, som } from '../components/analytics'
import { BarChart, Donut, Funnel, LineChart, RankedBars, Ring } from '../components/charts'
import { Card, Delta, Empty, ErrorNotice, formatTime, Loading, Stat } from '../components/ui'
import { languageLabels, lifeStageLabels, PeriodPicker } from './DashboardPage'
import type { Period } from './DashboardPage'

const funnelLabels: Record<string, { label: string; hint?: string }> = {
  registered: { label: "Ro'yxatdan o'tgan" },
  onboarded: { label: 'Onboardingni tugatgan', hint: 'Bosqich va rozilik tanlagan' },
  active_30d: { label: "So'nggi 30 kunda faol", hint: "Oxirgi so'rovi bo'yicha" },
  ever_premium: { label: 'Premium olgan', hint: "Qo'lda berilgan ham" },
  premium_now: { label: 'Hozir Premium' },
  paying: { label: "To'lab turgan", hint: "Do'kon yoki Payme/Click orqali, qo'lda berilganlarsiz" },
}

const platformLabels: Record<string, string> = {
  android: 'Android',
  ios: 'iOS',
}

const streakLabels: Record<string, string> = {
  '1-2': '1–2 kun',
  '3-6': '3–6 kun',
  '7-13': '7–13 kun',
  '14-29': '14–29 kun',
  '30+': '30+ kun',
  lapsed: 'Uzilgan',
}

const consentLabels: Record<string, string> = {
  store_health: "Sog'liq ma'lumotini saqlash",
  ai_insights: 'AI tahlillari',
  analytics: 'Analitika',
  marketing: 'Marketing',
}

/**
 * The analytics page.
 *
 * Everything here is counted from rows the product writes for its own reasons; nothing
 * was added to the app to feed a chart, and nothing here is a health value. What it
 * answers is the questions the dashboard's six numbers cannot: are people coming back,
 * where do they drop out on the way to paying, and which phones are they on.
 */
export function AnalyticsPage() {
  const [days, setDays] = useState<Period>(30)
  const analytics = useAnalytics(days)
  const stats = useStats()
  const report = analytics.data

  return (
    <div className="grid" style={{ gap: 16 }}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <span className="faint live">
          {report ? `Yangilangan ${formatTime(report.generatedAt)} · ${report.timezone} kunlari bo'yicha` : 'Yuklanmoqda…'}
        </span>
        <PeriodPicker value={days} onChange={setDays} />
      </div>

      <ErrorNotice error={analytics.error} />
      {analytics.isLoading && !report && <Loading rows={6} />}

      {report && (
        <>
          <Totals report={report} />

          <Card title={`Kunlik faollik — ${days} kun`}>
            <ActivityChart report={report} />
          </Card>

          <div className="two-col even">
            <Card title="Qaytish (retention)">
              <Retention report={report} />
              <p className="faint" style={{ margin: '12px 0 0' }}>
                Kohorta — ufqdan kamida shuncha kun oldin ro'yxatdan o'tganlar; "qaytgan" — ufq
                o'tgach ham so'rov yuborganlar. O'lchangan raqam, taxmin emas.
              </p>
            </Card>

            <Card title="Voronka: hisobdan to'lovgacha">
              <Funnel
                steps={report.funnel.map((step) => ({
                  key: step.key,
                  label: funnelLabels[step.key]?.label ?? step.key,
                  value: step.count,
                  hint: funnelLabels[step.key]?.hint,
                }))}
              />
            </Card>
          </div>

          <Card title={`Tushum va Premium — ${days} kun`}>
            {report.perDay.every((day) => day.revenueMinor === 0 && day.premiumStarted === 0) ? (
              <Empty>Bu davrda to'lov ham, yangi obuna ham bo'lmagan.</Empty>
            ) : (
              <div className="two-col even">
                <div>
                  <div className="faint" style={{ marginBottom: 4 }}>
                    Tushum, so'm
                  </div>
                  <BarChart
                    labels={report.perDay.map((day) => day.date)}
                    values={report.perDay.map((day) => Math.round(day.revenueMinor / 100))}
                    color="var(--c2)"
                    format={(value) => `${value.toLocaleString('ru-RU')} so'm`}
                  />
                </div>
                <div>
                  <div className="faint" style={{ marginBottom: 4 }}>
                    Boshlangan obunalar
                  </div>
                  <BarChart labels={report.perDay.map((day) => day.date)} values={report.perDay.map((day) => day.premiumStarted)} color="var(--c1)" />
                </div>
              </div>
            )}
          </Card>

          <div className="two-col even">
            <Card title="Platformalar">
              <Donut
                slices={report.platforms.map((item) => ({ key: item.key, label: platformLabels[item.key] ?? item.key, value: item.count }))}
                centerLabel="hisob"
                size={124}
              />
            </Card>
            <Card title="Ilova versiyalari">
              <RankedBars items={named(report.appVersions, (key) => key)} color="var(--c5)" />
            </Card>
          </div>

          <div className="two-col even">
            <Card title="Streaklar">
              <RankedBars items={named(report.streaks, (key) => streakLabels[key] ?? key)} color="var(--c4)" />
              <p className="faint" style={{ margin: '10px 0 0' }}>
                Jonli streak — bugun yoki kecha ochilgan. "Uzilgan" — streak boshlagan, lekin
                ikki kundan beri kirmagan hisoblar.
              </p>
            </Card>
            <Card title="Rozilik">
              <RankedBars items={named(report.consents, (key) => consentLabels[key] ?? key)} color="var(--c3)" />
              <p className="faint" style={{ margin: '10px 0 0' }}>
                Har bir hisob nimaga "ha" degan. Marketing va analitika roziligisiz push ham,
                hodisa ham yuborilmaydi.
              </p>
            </Card>
          </div>

          {stats.data && (
            <div className="two-col even">
              <Card title="Hayot bosqichi">
                <Donut
                  slices={Object.entries(stats.data.byLifeStage)
                    .sort(([, a], [, b]) => b - a)
                    .map(([stage, count]) => ({ key: stage, label: lifeStageLabels[stage] ?? stage, value: count }))}
                  size={124}
                  centerLabel="hisob"
                />
              </Card>
              <Card title="Til">
                <Donut
                  slices={Object.entries(stats.data.byLanguage)
                    .sort(([, a], [, b]) => b - a)
                    .map(([language, count]) => ({ key: language, label: languageLabels[language] ?? language, value: count }))}
                  size={124}
                  centerLabel="hisob"
                />
              </Card>
            </div>
          )}

          <Card title="Kunlar bo'yicha" action={<button className="btn small" onClick={() => exportCsv(report)}>CSV</button>}>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Kun</th>
                    <th style={{ textAlign: 'right' }}>Yangi</th>
                    <th style={{ textAlign: 'right' }}>Ochganlar</th>
                    <th style={{ textAlign: 'right' }}>Kirishlar</th>
                    <th style={{ textAlign: 'right' }}>Premium</th>
                    <th style={{ textAlign: 'right' }}>Tushum</th>
                    <th style={{ textAlign: 'right' }}>AI</th>
                    <th style={{ textAlign: 'right' }}>Postlar</th>
                    <th style={{ textAlign: 'right' }}>Yozuvlar</th>
                  </tr>
                </thead>
                <tbody>
                  {[...report.perDay].reverse().map((day) => (
                    <tr key={day.date}>
                      <td className="mono faint">{day.date}</td>
                      <Num value={day.signUps} />
                      <Num value={day.activeUsers} />
                      <Num value={day.signIns} />
                      <Num value={day.premiumStarted} />
                      <td style={{ textAlign: 'right' }}>{day.revenueMinor ? `${som(day.revenueMinor)} so'm` : <span className="faint">—</span>}</td>
                      <Num value={day.aiCalls} />
                      <Num value={day.posts} />
                      <Num value={day.entries} />
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        </>
      )}
    </div>
  )
}

function Num({ value }: { value: number }) {
  return <td style={{ textAlign: 'right' }}>{value ? value.toLocaleString('ru-RU') : <span className="faint">—</span>}</td>
}

function named(items: NamedCount[], label: (key: string) => string) {
  return items.map((item) => ({ key: item.key, label: label(item.key), value: item.count }))
}

function Totals({ report }: { report: AdminAnalytics }) {
  const { current, previous } = report
  const days = report.perDay.map((day) => day)
  return (
    <div className="grid stat-row">
      <Stat label="Yangi hisoblar" value={current.signUps} delta={{ previous: previous.signUps }} spark={days.map((day) => day.signUps)} />
      <Stat
        label="Ochishlar (kun·hisob)"
        value={current.activeUsers}
        delta={{ previous: previous.activeUsers }}
        hint={`kuniga o'rtacha ${(current.activeUsers / Math.max(1, report.days)).toFixed(1)}`}
        spark={days.map((day) => day.activeUsers)}
        sparkColor="var(--c3)"
      />
      <Stat label="Premium boshlandi" value={current.premiumStarted} delta={{ previous: previous.premiumStarted }} spark={days.map((day) => day.premiumStarted)} sparkColor="var(--c2)" />
      <Stat
        label="Tushum"
        value={`${som(current.revenueMinor)} so'm`}
        hint={<Delta current={current.revenueMinor} previous={previous.revenueMinor} />}
        spark={days.map((day) => day.revenueMinor)}
        sparkColor="var(--c2)"
      />
      <Stat label="AI javoblari" value={current.aiCalls} delta={{ previous: previous.aiCalls }} spark={days.map((day) => day.aiCalls)} sparkColor="var(--c5)" />
      <Stat label="Yozuvlar" value={current.entries} delta={{ previous: previous.entries }} hint="jurnal, ovqat, kunlik log" spark={days.map((day) => day.entries)} sparkColor="var(--c4)" />
    </div>
  )
}

function ActivityChart({ report }: { report: AdminAnalytics }) {
  const labels = report.perDay.map((day) => day.date)
  const active = report.perDay.map((day) => day.activeUsers)
  return (
    <>
      <LineChart
        labels={labels}
        height={220}
        series={[
          { key: 'active', label: 'Ilovani ochganlar', values: active, color: 'var(--c3)', area: true },
          { key: 'trend', label: "7 kunlik o'rtacha", values: movingAverage(active).map((value) => Math.round(value * 10) / 10), color: 'var(--c3)', dashed: true },
          { key: 'signups', label: "Ro'yxatdan o'tganlar", values: report.perDay.map((day) => day.signUps), color: 'var(--c1)', area: true },
          { key: 'entries', label: 'Yozuvlar', values: report.perDay.map((day) => day.entries), color: 'var(--c4)' },
        ]}
      />
      <div className="chart-legend">
        <span>
          <span className="swatch" style={{ background: 'var(--c3)' }} /> Ilovani ochganlar
        </span>
        <span>
          <span className="swatch" style={{ background: 'var(--c3)', opacity: 0.5 }} /> 7 kunlik o'rtacha
        </span>
        <span>
          <span className="swatch" style={{ background: 'var(--c1)' }} /> Ro'yxatdan o'tganlar
        </span>
        <span>
          <span className="swatch" style={{ background: 'var(--c4)' }} /> Yozuvlar
        </span>
      </div>
    </>
  )
}

function Retention({ report }: { report: AdminAnalytics }) {
  const colors = ['var(--c1)', 'var(--c3)', 'var(--c2)']
  return (
    <div className="retention">
      {report.retention.map((cohort, index) => {
        const percent = share(cohort.returned, cohort.cohort)
        return (
          <div className="retention-item" key={cohort.horizonDays}>
            <Ring percent={percent} color={colors[index]}>
              {cohort.cohort ? `${percent}%` : '—'}
            </Ring>
            <div>
              <div className="title">{cohort.horizonDays}-kun</div>
              <div className="faint">
                {cohort.cohort ? `${cohort.returned} / ${cohort.cohort} qaytdi` : 'kohorta hali yo‘q'}
              </div>
            </div>
          </div>
        )
      })}
    </div>
  )
}

/** The per-day table, as the analyst's spreadsheet wants it. Counts only — no accounts. */
function exportCsv(report: AdminAnalytics) {
  const header = ['date', 'signUps', 'activeUsers', 'signIns', 'premiumStarted', 'revenueSom', 'aiCalls', 'posts', 'entries']
  const rows = report.perDay.map((day) =>
    [day.date, day.signUps, day.activeUsers, day.signIns, day.premiumStarted, Math.round(day.revenueMinor / 100), day.aiCalls, day.posts, day.entries].join(','),
  )
  const blob = new Blob([[header.join(','), ...rows].join('\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `sadora-analytics-${report.days}d-${new Date().toISOString().slice(0, 10)}.csv`
  link.click()
  URL.revokeObjectURL(url)
}
