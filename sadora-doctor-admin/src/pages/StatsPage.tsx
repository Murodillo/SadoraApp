import { useDoctorStats } from '../api/hooks'
import { durationLabel, ratingLabel } from '../api/work'
import { topicLabel } from '../components/labels'
import { Card, Empty, ErrorNotice, formatTime, Loading, Stat } from '../components/ui'

/**
 * "Statistika": her consultations by period, how fast she answers, what patients think,
 * and which rooms her answers go to. Hand-drawn bars; the panel carries no chart library.
 */
export function StatsPage() {
  const stats = useDoctorStats()

  if (stats.isPending) {
    return (
      <div className="grid" style={{ gap: 16 }}>
        <div className="grid stat-row">
          {Array.from({ length: 4 }, (_, index) => (
            <div key={index} className="card stat">
              <Loading rows={2} height={20} />
            </div>
          ))}
        </div>
        <Card>
          <Loading rows={4} height={24} />
        </Card>
      </div>
    )
  }
  if (!stats.data) return <ErrorNotice error={stats.error} onRetry={() => void stats.refetch()} />

  const data = stats.data
  const topMax = Math.max(1, ...data.topTopics.map((item) => item.count))

  return (
    <div className="grid" style={{ gap: 16 }}>
      <div className="grid stat-row">
        <Stat art="ic3d_calendar" label="Shu hafta" value={data.consultationsWeek} hint="konsultatsiya" />
        <Stat art="ic3d_calendar" label="Shu oy" value={data.consultationsMonth} hint="konsultatsiya" />
        <Stat art="badge_doctor" label="Jami" value={data.consultationsTotal} hint="konsultatsiya" />
        <Stat art="ic3d_message" label="Hozir ochiq" value={data.openNow} hint="javobingizni kutmoqda" />
      </div>

      <div className="grid stat-row">
        <Stat art="ic3d_clock" label="Birinchi javob" value={durationLabel(data.avgFirstReplyMinutes)} hint="o'rtacha" />
        <Stat
          label="Javobsiz qolgan"
          art="ic3d_bell"
          value={data.unansweredTotal}
          alert={data.unansweredTotal > 0}
          hint={data.unansweredTotal ? 'muddati javobsiz tugagan' : 'hammasiga javob bergansiz'}
        />
        <Stat
          label="Reyting"
          art="ic3d_star"
          value={data.rating != null ? `★ ${ratingLabel(data.rating)}` : '—'}
          hint={data.ratingCount ? `${data.ratingCount} ta baho` : "hali baho yo'q"}
        />
        <Stat art="ic3d_chats" label="Chatdagi javoblar" value={data.answersTotal} hint="savollarga" />
      </div>

      <Card
        title="Qaysi mavzularda javob berasiz"
        action={
          <span className={`faint live${stats.isError ? ' stale' : ''}`}>
            {stats.isError ? "Yangilab bo'lmadi" : `yangilangan ${formatTime(stats.dataUpdatedAt)}`}
          </span>
        }
      >
        {data.topTopics.length ? (
          <ul className="bars" aria-label="Mavzular">
            {data.topTopics.map((item) => (
              <li key={item.topic} className="bar-row">
                <span className="bar-label">{topicLabel(item.topic)}</span>
                <span className="bar-track" aria-hidden="true">
                  <span className="bar-fill" style={{ width: `${(item.count / topMax) * 100}%` }} />
                </span>
                <b className="bar-value">{item.count}</b>
              </li>
            ))}
          </ul>
        ) : (
          <Empty art="ic3d_insights">
            Hali chatdagi savollarga javob bermagansiz.
            <div className="faint">Javob berganingizda mavzular shu yerda chiqadi.</div>
          </Empty>
        )}
      </Card>
    </div>
  )
}
