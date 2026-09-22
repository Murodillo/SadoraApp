import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAnalytics, useRecentEvents, useStats } from '../api/hooks'
import type { AdminAnalytics } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { som } from '../components/analytics'
import { BarChart, Donut, LineChart } from '../components/charts'
import { Card, Delta, ErrorNotice, formatDateTime, formatTime, Loading, Stat } from '../components/ui'

export const lifeStageLabels: Record<string, string> = {
  cycle: 'Sikl',
  trying_to_conceive: 'Rejalashtirish',
  pregnancy: 'Homiladorlik',
  postpartum: "Tug'ruqdan keyin",
  perimenopause: 'Perimenopauza',
  menopause: 'Menopauza',
}

export const languageLabels: Record<string, string> = {
  uz: "O'zbekcha",
  ru: 'Ruscha',
  en: 'Inglizcha',
}

const actionLabels: Record<string, string> = {
  'user.signed_up': "Ro'yxatdan o'tdi",
  'user.signed_in': 'Kirdi',
  'user.signed_out': 'Chiqdi',
  'user.onboarded': 'Onboardingni tugatdi',
  'user.blocked': 'Bloklandi',
  'user.unblocked': 'Blokdan chiqdi',
  'user.profile_updated': 'Profilni yangiladi',
  'user.consent_changed': "Rozilikni o'zgartirdi",
  'user.deletion_requested': "O'chirishni so'radi",
  'subscription.granted': 'Premium berildi',
  'admin.signed_in': 'Operator kirdi',
  'admin.sign_in_failed': 'Operator kira olmadi',
  'flag.updated': "Bayroq o'zgardi",
  'community.post_hidden': 'Post yashirildi',
  'community.report_resolved': 'Shikoyat yopildi',
  'rewards.coins_adjusted': "Gul to'g'rilandi",
  'share.created': 'Shifokor sahifasi yaratildi',
  'share.viewed': "Shifokor sahifasi ko'rildi",
}

export const PERIODS = [7, 14, 30, 90] as const
export type Period = (typeof PERIODS)[number]

export function PeriodPicker({ value, onChange }: { value: Period; onChange: (days: Period) => void }) {
  return (
    <div className="segmented" role="group" aria-label="Davr">
      {PERIODS.map((option) => (
        <button key={option} className={option === value ? 'active' : undefined} onClick={() => onChange(option)}>
          {option} kun
        </button>
      ))}
    </div>
  )
}

export function DashboardPage() {
  const { can } = useAuth()
  const [days, setDays] = useState<Period>(14)
  const stats = useStats()
  // Support has no analytics route; her dashboard is the counts and the feed.
  const seesAnalytics = can(['OWNER', 'ADMIN', 'ANALYST'])
  const analytics = useAnalytics(days, seesAnalytics)
  const events = useRecentEvents(10)

  if (stats.isLoading) return <Loading rows={6} />
  if (stats.error) return <ErrorNotice error={stats.error} />
  const data = stats.data!
  const report = analytics.data

  const dauOfMau = data.activeThisMonth ? Math.round((data.activeToday / data.activeThisMonth) * 100) : null

  return (
    <div className="grid" style={{ gap: 16 }}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <span className="faint live">
          Yangilangan {formatTime(data.generatedAt)} · har 30 soniyada
        </span>
        {seesAnalytics && <PeriodPicker value={days} onChange={setDays} />}
      </div>

      <div className="grid stat-row">
        <Stat
          label="Jami foydalanuvchi"
          value={data.totalUsers}
          hint={`Haftada +${data.newThisWeek}`}
          spark={report?.perDay.map((day) => day.signUps)}
        />
        <Stat
          label={`Ro'yxatdan o'tgan · ${days} kun`}
          value={report?.current.signUps ?? data.newToday}
          delta={report ? { previous: report.previous.signUps } : undefined}
          hint={report ? `bugun ${data.newToday}` : 'bugun'}
        />
        <Stat
          label="Kunlik faol (DAU)"
          value={data.activeToday}
          hint={dauOfMau !== null ? `MAU ${data.activeThisMonth} · ${dauOfMau}% qaytadi` : undefined}
          spark={report?.perDay.map((day) => day.activeUsers)}
          sparkColor="var(--c3)"
        />
        <Stat
          label="Aktiv obuna"
          value={data.premiumUsers}
          hint={`${data.expiringWithinWeek} tasi hafta ichida tugaydi`}
          spark={report?.perDay.map((day) => day.premiumStarted)}
          sparkColor="var(--c2)"
        />
        <Stat
          label={`Tushum · ${days} kun`}
          value={report ? `${som(report.current.revenueMinor)} so'm` : '—'}
          hint={report ? <Delta current={report.current.revenueMinor} previous={report.previous.revenueMinor} /> : undefined}
          spark={report?.perDay.map((day) => day.revenueMinor)}
          sparkColor="var(--c2)"
        />
        <Stat label="Bloklangan" value={data.blockedUsers} hint={`${data.deletionPending} ta o'chirish so'rovi`} />
      </div>

      {seesAnalytics && (
        <Card
          title={`Ro'yxatdan o'tish va faollik — ${days} kun`}
          action={
            <Link to="/analytics" className="faint">
              To'liq analitika →
            </Link>
          }
        >
          {analytics.isLoading && !report ? (
            <Loading rows={3} />
          ) : analytics.error ? (
            <ErrorNotice error={analytics.error} />
          ) : report ? (
            <GrowthChart report={report} />
          ) : null}
        </Card>
      )}

      <div className="two-col">
        <Card title="Chat va AI — 24 soat">
          <div className="grid stat-row tight">
            <Stat label="Chat — post" value={data.communityPostsToday} spark={report?.perDay.map((day) => day.posts)} sparkColor="var(--c6)" />
            <Stat
              label="Ochiq shikoyatlar"
              value={data.communityOpenReports}
              hint={
                data.communityOpenReports > 0 ? (
                  <Link to="/community">Moderatsiya sahifasida →</Link>
                ) : (
                  'Navbat bo‘sh'
                )
              }
            />
            <Stat
              label="AI javoblari"
              value={Object.values(data.aiUsageToday).reduce((sum, count) => sum + count, 0)}
              hint={
                <Link to="/ai">
                  Xarajat →
                </Link>
              }
              spark={report?.perDay.map((day) => day.aiCalls)}
              sparkColor="var(--c5)"
            />
            <Stat
              label="Shifokor tavsiyasi bilan"
              value={data.referredByDoctor}
              hint={data.totalUsers ? `${Math.round((data.referredByDoctor / data.totalUsers) * 100)}% foydalanuvchi` : undefined}
            />
          </div>
          {report && report.perDay.some((day) => day.aiCalls > 0) && (
            <div style={{ marginTop: 14 }}>
              <div className="faint" style={{ marginBottom: 4 }}>
                AI javoblari kun bo'yicha
              </div>
              <BarChart labels={report.perDay.map((day) => day.date)} values={report.perDay.map((day) => day.aiCalls)} height={110} color="var(--c5)" />
            </div>
          )}
        </Card>

        <Card title="Hayot bosqichi bo'yicha">
          <Donut
            slices={Object.entries(data.byLifeStage)
              .sort(([, a], [, b]) => b - a)
              .map(([stage, count]) => ({ key: stage, label: lifeStageLabels[stage] ?? stage, value: count }))}
            centerLabel="hisob"
            size={132}
          />
        </Card>
      </div>

      <div className="two-col reverse">
        <Card title="Til bo'yicha">
          <Donut
            slices={Object.entries(data.byLanguage)
              .sort(([, a], [, b]) => b - a)
              .map(([language, count]) => ({ key: language, label: languageLabels[language] ?? language.toUpperCase(), value: count }))}
            centerLabel="hisob"
            size={132}
          />
        </Card>

        <Card
          title="So'nggi hodisalar"
          action={
            can(['OWNER']) ? (
              <Link to="/audit" className="faint">
                Audit log →
              </Link>
            ) : undefined
          }
        >
          {events.isLoading ? (
            <Loading rows={4} />
          ) : (
            <div className="table-wrap">
              <table>
                <tbody>
                  {(events.data ?? []).map((event) => (
                    <tr key={event.id}>
                      <td className="faint" style={{ whiteSpace: 'nowrap' }}>
                        {formatDateTime(event.createdAt)}
                      </td>
                      <td>
                        <span className={`badge ${event.actorType === 'admin' ? 'premium' : 'free'}`}>{event.actorType}</span>
                      </td>
                      <td>
                        <div>{actionLabels[event.action] ?? event.action}</div>
                        <div className="mono faint">{event.action}</div>
                      </td>
                      <td className="muted">{event.reason ?? ''}</td>
                    </tr>
                  ))}
                  {!events.data?.length && (
                    <tr>
                      <td className="faint">Hodisa yo'q</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </div>

      <div className="notice">
        DAU va MAU — hisobning oxirgi so'rovi bo'yicha. Grafikdagi "faol" esa o'lchangan: kunlik
        check-in yozuvi va kirishlar bo'yicha, har bir hisob kuniga bir marta. Ekranlar va
        voronkalar uchun alohida hodisalar jadvali kerak; u yo'q, shuning uchun bu yerda
        o'lchanmagan raqam ko'rsatilmaydi.
      </div>
    </div>
  )
}

function GrowthChart({ report }: { report: AdminAnalytics }) {
  const labels = report.perDay.map((day) => day.date)
  return (
    <>
      <LineChart
        labels={labels}
        height={190}
        series={[
          { key: 'active', label: 'Ilovani ochganlar', values: report.perDay.map((day) => day.activeUsers), color: 'var(--c3)', area: true },
          { key: 'signups', label: "Ro'yxatdan o'tganlar", values: report.perDay.map((day) => day.signUps), color: 'var(--c1)', area: true },
          { key: 'premium', label: 'Premium boshlandi', values: report.perDay.map((day) => day.premiumStarted), color: 'var(--c2)' },
        ]}
      />
      <div className="chart-legend">
        <span>
          <span className="swatch" style={{ background: 'var(--c3)' }} /> Ilovani ochganlar
        </span>
        <span>
          <span className="swatch" style={{ background: 'var(--c1)' }} /> Ro'yxatdan o'tganlar
        </span>
        <span>
          <span className="swatch" style={{ background: 'var(--c2)' }} /> Premium boshlandi
        </span>
        <span className="faint" style={{ marginLeft: 'auto' }}>
          Jami: {report.current.activeUsers} ochish · {report.current.signUps} yangi · {report.current.premiumStarted} premium
        </span>
      </div>
    </>
  )
}
