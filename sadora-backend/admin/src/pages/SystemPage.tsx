import { useSystemHealth } from '../api/hooks'
import type { ServiceHealth } from '../api/types'
import { Card, ErrorNotice, formatDateTime, formatTime, Loading } from '../components/ui'
import { ago, serviceCopy, sortServices, statusLabels, uptime } from './system'

/**
 * "Is anything broken?" for the Owner and Admin: the server itself, its database, and every
 * outside service the product depends on, the broken ones first. Every number comes from
 * rows the product writes anyway, or from the ops counters where there are none.
 */
export function SystemPage() {
  const health = useSystemHealth()
  if (health.isLoading) return <Loading rows={6} />
  if (health.error) return <ErrorNotice error={health.error} />
  const data = health.data!
  const services = sortServices(data.services)
  const broken = services.filter((service) => service.status === 'error').length

  return (
    <div className="grid" style={{ gap: 16 }}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <span className="faint live">Yangilangan {formatTime(data.generatedAt)} · har daqiqada</span>
        {broken > 0 ? (
          <span className="badge danger">{broken} ta xizmatda xato</span>
        ) : (
          <span className="badge ok">Xato yo'q</span>
        )}
      </div>

      <Card title="Server">
        <div className="system-facts">
          <Fact label="Muhit" value={data.environment.toUpperCase()} />
          <Fact label="Versiya" value={`${data.version} · ${data.release.slice(0, 12)}`} mono />
          <Fact label="Ishlab turibdi" value={uptime(data.startedAt)} hint={formatDateTime(data.startedAt)} />
          <Fact
            label="Ma'lumotlar bazasi"
            value={data.database.ok ? `Ulangan · ${data.database.latencyMs ?? '—'} ms` : "Ulanib bo'lmadi"}
            tone={data.database.ok ? undefined : 'danger'}
          />
          <Fact label="Sxema" value={data.database.schemaVersion ? `V${data.database.schemaVersion}` : '—'} mono />
        </div>
      </Card>

      <div className="system-grid">
        {services.map((service) => (
          <ServiceCard key={service.key} service={service} />
        ))}
      </div>

      <p className="faint" style={{ margin: 0 }}>
        "Bugun" — Toshkent kuni. To'lov xizmatlarida bekor qilingan to'lov xato hisoblanmaydi: bu foydalanuvchining
        qarori. Sozlanmagan xizmat — serverda uning kaliti yo'q.
      </p>
    </div>
  )
}

function ServiceCard({ service }: { service: ServiceHealth }) {
  const words = serviceCopy(service.key)
  const status = statusLabels[service.status]
  return (
    <section className={`card system-card ${service.status}`}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <strong>{words.name}</strong>
        <span className={`badge ${status.tone}`}>{status.text}</span>
      </div>
      {service.configured && (
        <div className="system-metrics">
          {words.okLabel && service.okToday != null && (
            <div>
              <span className="system-number">{service.okToday.toLocaleString('ru-RU')}</span> {words.okLabel}
            </div>
          )}
          {words.failLabel && service.failedToday != null && service.failedToday > 0 && (
            <div className="system-failed">
              <span className="system-number">{service.failedToday.toLocaleString('ru-RU')}</span> {words.failLabel}
            </div>
          )}
          <div className="faint" title={service.lastOkAt ? formatDateTime(service.lastOkAt) : undefined}>
            {words.lastOkLabel}: {ago(service.lastOkAt)}
          </div>
          {service.lastError && service.lastErrorAt && (
            <div className="system-error" title={formatDateTime(service.lastErrorAt)}>
              <span className="faint">Oxirgi xato, {ago(service.lastErrorAt)}:</span>
              <code>{service.lastError}</code>
            </div>
          )}
        </div>
      )}
    </section>
  )
}

function Fact({ label, value, hint, mono, tone }: { label: string; value: string; hint?: string; mono?: boolean; tone?: string }) {
  return (
    <div className="system-fact" title={hint}>
      <div className="faint">{label}</div>
      <div className={`${mono ? 'mono' : ''}${tone ? ` badge ${tone}` : ''}`}>{value}</div>
    </div>
  )
}
