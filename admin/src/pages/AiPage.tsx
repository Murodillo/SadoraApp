import { useState } from 'react'
import { useAiUsage, useFlags, useUpdateFlag } from '../api/hooks'
import type { AiUsageDay } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Card, Empty, ErrorNotice, Loading, Stat } from '../components/ui'

const MODEL_FLAG = 'ai_model_enabled'

/**
 * A day with no spend is still a day.
 *
 * Without this a quiet window draws one bar across the whole card and reads as "every
 * day cost the maximum", which is the opposite of what happened.
 */
function fillDays(points: AiUsageDay[], days: number): AiUsageDay[] {
  const byDate = new Map(points.map((point) => [point.date, point]))
  const today = new Date()
  return Array.from({ length: days }, (_, index) => {
    const date = new Date(today)
    date.setDate(today.getDate() - (days - 1 - index))
    const key = date.toISOString().slice(0, 10)
    return (
      byDate.get(key) ?? {
        date: key,
        calls: 0,
        modelCalls: 0,
        fallbacks: 0,
        promptTokens: 0,
        completionTokens: 0,
        costMicros: 0,
      }
    )
  })
}

/** USD micros to something an operator reads: "$1.24" or "$0.0031" while it is still small. */
function money(micros: number): string {
  const usd = micros / 1_000_000
  if (usd === 0) return '$0'
  return usd >= 0.01 ? `$${usd.toFixed(2)}` : `$${usd.toFixed(4)}`
}

/**
 * What the AI costs.
 *
 * There is nothing on this page about what anyone asked or was told — the log has no
 * such column. What it answers is the operator's question: is the model answering, how
 * often does it fail over to the rules, and what is it costing.
 */
export function AiPage() {
  const { can } = useAuth()
  const [days, setDays] = useState(14)
  const usage = useAiUsage(days)
  const flags = useFlags()
  const updateFlag = useUpdateFlag()

  const mayToggle = can(['OWNER', 'ADMIN'])
  const flag = flags.data?.find((it) => it.key === MODEL_FLAG)
  const report = usage.data
  const series = report ? fillDays(report.perDay, days) : []
  const peak = Math.max(1, ...series.map((point) => point.costMicros))

  return (
    <div className="grid" style={{ gap: 16 }}>
      {report && !report.modelConfigured && (
        <div className="notice">
          Model kaliti sozlanmagan (`GEMINI_API_KEY`) — javoblarni qoidalar yozmoqda. Bu
          xato emas, sozlama.
        </div>
      )}

      {usage.isLoading && <Loading />}
      <ErrorNotice error={usage.error} />

      {report && (
        <>
          <div className="grid stat-row">
            <Stat label="Javoblar" value={report.calls} hint={`${days} kun`} />
            <Stat
              label="Model yozgan"
              value={report.modelCalls}
              hint={report.model ?? 'model yo‘q'}
            />
            <Stat
              label="Qoidalarga tushgan"
              value={report.fallbacks}
              hint={report.fallbacks > 0 ? 'model javob bera olmadi' : 'nosozlik yo‘q'}
            />
            <Stat label="Xarajat" value={money(report.costMicros)} hint={`${report.promptTokens + report.completionTokens} token`} />
            <Stat
              label="O'rtacha kechikish"
              value={report.averageLatencyMs === null ? '—' : `${report.averageLatencyMs} ms`}
              hint="model javoblari bo'yicha"
            />
          </div>

          <Card
            title="Model kalitlari"
            action={
              <div className="row" style={{ gap: 6 }}>
                {[7, 14, 30].map((option) => (
                  <button
                    key={option}
                    className={`btn small${option === days ? '' : ' ghost'}`}
                    onClick={() => setDays(option)}
                  >
                    {option} kun
                  </button>
                ))}
              </div>
            }
          >
            <div className="row" style={{ justifyContent: 'space-between' }}>
              <div>
                <div>Javoblarni model yozadi</div>
                <div className="faint">
                  O'chirilsa chat ochiq qoladi va qoidalar javob beradi — bu chatni yopish
                  emas, uni arzonlashtirish.
                </div>
              </div>
              <button
                className={`btn small${flag?.enabled ? ' danger' : ''}`}
                disabled={!mayToggle || !flag || updateFlag.isPending}
                onClick={() =>
                  flag &&
                  updateFlag.mutate({
                    key: MODEL_FLAG,
                    enabled: !flag.enabled,
                    defaultValue: flag.defaultValue,
                  })
                }
              >
                {flag?.enabled ? "O'chirish" : 'Yoqish'}
              </button>
            </div>
            <ErrorNotice error={updateFlag.error} />
          </Card>

          <Card title="Kunlik xarajat">
            {report.perDay.length === 0 ? (
              <Empty>Bu oraliqda birorta ham javob yo'q.</Empty>
            ) : (
              <>
                <div className="bars">
                  {series.map((point) => (
                    <div
                      key={point.date}
                      className="bar"
                      data-empty={point.costMicros === 0}
                      style={{
                        height: `${Math.max((point.costMicros / peak) * 100, point.costMicros ? 6 : 2)}%`,
                      }}
                      title={`${point.date}: ${money(point.costMicros)} · ${point.calls} javob`}
                    />
                  ))}
                </div>
                <div className="row" style={{ justifyContent: 'space-between', marginTop: 6 }}>
                  <span className="faint">{series[0]?.date}</span>
                  <span className="faint">Eng qimmat kun: {money(peak)}</span>
                  <span className="faint">{series[series.length - 1]?.date}</span>
                </div>
              </>
            )}
          </Card>

          <Card title="Nosozliklar">
            {report.failures.length === 0 ? (
              <Empty>Model hech bir javobda qoqilmadi.</Empty>
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>Kod</th>
                    <th>Soni</th>
                    <th>Ma'nosi</th>
                  </tr>
                </thead>
                <tbody>
                  {report.failures.map((failure) => (
                    <tr key={failure.code}>
                      <td className="mono">{failure.code}</td>
                      <td>{failure.count}</td>
                      <td className="faint">{failureLabel(failure.code)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </Card>
        </>
      )}
    </div>
  )
}

/** The codes the gateway records. An unknown one is shown as itself, not guessed at. */
function failureLabel(code: string): string {
  if (code === 'timeout') return 'Model vaqtida javob bermadi'
  if (code === 'transport') return 'Tarmoq yoki provayder xatosi'
  if (code === 'no_key') return 'API kalit sozlanmagan'
  if (code === 'decode') return "Javobni o'qib bo'lmadi"
  if (code === 'safety') return 'Model javobni bloklagan'
  if (code.startsWith('http_')) return `Provayder ${code.slice(5)} qaytardi`
  return code
}
