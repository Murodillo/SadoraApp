import { useRecentEvents, useSignUps, useStats } from '../api/hooks'
import type { SignUpPoint } from '../api/types'
import { Card, ErrorNotice, formatDateTime, Loading, Stat } from '../components/ui'

/** Expands a sparse series into one entry per day, oldest first. */
function fillDays(points: SignUpPoint[], days: number): SignUpPoint[] {
  const counts = new Map(points.map((point) => [point.date, point.signUps]))
  const today = new Date()
  return Array.from({ length: days }, (_, index) => {
    const date = new Date(today)
    date.setUTCDate(today.getUTCDate() - (days - 1 - index))
    const key = date.toISOString().slice(0, 10)
    return { date: key, signUps: counts.get(key) ?? 0 }
  })
}

const lifeStageLabels: Record<string, string> = {
  cycle: 'Sikl',
  trying_to_conceive: 'Rejalashtirish',
  pregnancy: 'Homiladorlik',
  postpartum: "Tug'ruqdan keyin",
  perimenopause: 'Perimenopauza',
  menopause: 'Menopauza',
}

export function DashboardPage() {
  const stats = useStats()
  const signUps = useSignUps(14)
  const events = useRecentEvents(10)

  if (stats.isLoading) return <Loading rows={6} />
  if (stats.error) return <ErrorNotice error={stats.error} />
  const data = stats.data!

  // The endpoint returns only days that had a sign-up. Rendering those alone would put
  // one bar across the whole width and read as "every day was busy", so the empty days
  // are filled back in to make a real 14-day axis.
  const series = fillDays(signUps.data ?? [], 14)
  const peak = Math.max(1, ...series.map((point) => point.signUps))

  return (
    <div className="grid" style={{ gap: 16 }}>
      <div className="grid stat-row">
        <Stat label="Jami foydalanuvchi" value={data.totalUsers} hint={`Haftada +${data.newThisWeek}`} />
        <Stat label="Bugun ro'yxatdan o'tgan" value={data.newToday} />
        <Stat label="24 soatda faol" value={data.activeToday} />
        <Stat label="Aktiv obuna" value={data.premiumUsers} hint={`${data.expiringWithinWeek} tasi hafta ichida tugaydi`} />
        <Stat label="Bloklangan" value={data.blockedUsers} />
        <Stat label="O'chirish so'rovi" value={data.deletionPending} />
      </div>

      <div className="grid" style={{ gridTemplateColumns: 'minmax(0, 2fr) minmax(0, 1fr)', gap: 16 }}>
        <Card title="Ro'yxatdan o'tish — 14 kun">
          {signUps.isLoading ? (
            <Loading rows={1} />
          ) : (
            <>
              <div className="bars">
                {series.map((point) => (
                  <div
                    key={point.date}
                    className="bar"
                    data-empty={point.signUps === 0}
                    style={{ height: `${Math.max((point.signUps / peak) * 100, point.signUps ? 6 : 2)}%` }}
                    title={`${point.date}: ${point.signUps}`}
                  />
                ))}
              </div>
              <div className="row" style={{ justifyContent: 'space-between', marginTop: 6 }}>
                <span className="faint">{series[0]?.date}</span>
                <span className="faint">
                  Eng yuqori: {peak} · jami {series.reduce((sum, point) => sum + point.signUps, 0)}
                </span>
                <span className="faint">{series[series.length - 1]?.date}</span>
              </div>
            </>
          )}
        </Card>

        <Card title="Bugungi AI foydalanish">
          {Object.keys(data.aiUsageToday).length === 0 ? (
            <p className="faint" style={{ margin: 0 }}>
              Bugun AI so'rovlari bo'lmagan. Xarajat hisobi AI Gateway bilan birga 3-sprintda
              qo'shiladi.
            </p>
          ) : (
            <table>
              <tbody>
                {Object.entries(data.aiUsageToday).map(([key, count]) => (
                  <tr key={key}>
                    <td className="mono">{key}</td>
                    <td style={{ textAlign: 'right', fontWeight: 600 }}>{count}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Card>
      </div>

      <div className="grid" style={{ gridTemplateColumns: 'minmax(0, 1fr) minmax(0, 2fr)', gap: 16 }}>
        <Card title="Hayot bosqichi bo'yicha">
          <table>
            <tbody>
              {Object.entries(data.byLifeStage).map(([stage, count]) => (
                <tr key={stage}>
                  <td>{lifeStageLabels[stage] ?? stage}</td>
                  <td style={{ textAlign: 'right', fontWeight: 600 }}>{count}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>

        <Card title="So'nggi hodisalar">
          {events.isLoading ? (
            <Loading rows={4} />
          ) : (
            <div className="table-wrap">
              <table>
                <tbody>
                  {(events.data ?? []).map((event) => (
                    <tr key={event.id}>
                      <td className="faint" style={{ whiteSpace: 'nowrap' }}>{formatDateTime(event.createdAt)}</td>
                      <td>
                        <span className="badge free">{event.actorType}</span>
                      </td>
                      <td className="mono">{event.action}</td>
                      <td className="muted">{event.reason ?? ''}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </div>

      <div className="notice">
        DAU/MAU va AI xarajat dinamikasi bu yerda emas: ular hodisalar jadvali va AI Gateway'ning
        xarajat logini talab qiladi, ikkalasi ham 3-sprintda. O'lchanmagan raqamni o'lchangandek
        ko'rsatgandan ko'ra ko'rsatmagan ma'qul.
      </div>
    </div>
  )
}
