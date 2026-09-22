import { useEffect, useId, useLayoutEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { compact, labelIndexes, niceCeiling, share, shortDay } from './analytics'
import { useCountUp } from './motion'

/*
 * The panel's charts, drawn by hand in SVG.
 *
 * No charting dependency: the four shapes this tool needs — a line over days, bars over
 * days, a donut of shares and a funnel — are a few hundred lines, and owning them means
 * they animate on the app's own curves and read the same colour tokens as everything
 * else. Every chart is a measurement, so every chart can be hovered for the number.
 */

/** The series palette. Violet leads, pink answers, then the cooler tones. */
export const palette = ['var(--c1)', 'var(--c2)', 'var(--c3)', 'var(--c4)', 'var(--c5)', 'var(--c6)']

export interface Series {
  key: string
  label: string
  values: number[]
  color?: string
  /** Draw a soft fill under the line as well. */
  area?: boolean
  /** Dashed — for a trend or a comparison rather than a measurement. */
  dashed?: boolean
}

/** Width of an element, kept current as the panel is resized. */
function useWidth<T extends HTMLElement>(fallback = 600): [React.RefObject<T>, number] {
  const ref = useRef<T>(null!)
  const [width, setWidth] = useState(fallback)
  useLayoutEffect(() => {
    const node = ref.current
    if (!node) return
    const measure = () => setWidth(node.clientWidth || fallback)
    measure()
    if (typeof ResizeObserver === 'undefined') return
    const observer = new ResizeObserver(measure)
    observer.observe(node)
    return () => observer.disconnect()
  }, [fallback])
  return [ref, width]
}

interface TooltipState {
  index: number
  x: number
}

function Tooltip({ x, width, children }: { x: number; width: number; children: ReactNode }) {
  // Flip to the left of the cursor in the right third, so the box never leaves the card.
  const flip = x > width * 0.66
  return (
    <div
      className="chart-tip"
      style={{ left: x, transform: flip ? 'translate(calc(-100% - 12px), -50%)' : 'translate(12px, -50%)' }}
      role="status"
    >
      {children}
    </div>
  )
}

// ---------------------------------------------------------------- line

export function LineChart({
  labels,
  series,
  height = 180,
  format = (value: number) => value.toLocaleString('ru-RU'),
  formatLabel = shortDay,
}: {
  labels: string[]
  series: Series[]
  height?: number
  format?: (value: number) => string
  formatLabel?: (label: string) => string
}) {
  const [wrap, width] = useWidth<HTMLDivElement>()
  const [tip, setTip] = useState<TooltipState | null>(null)
  const id = useId()

  const pad = { top: 12, right: 12, bottom: 24, left: 36 }
  const innerWidth = Math.max(1, width - pad.left - pad.right)
  const innerHeight = height - pad.top - pad.bottom
  const count = labels.length
  const peak = niceCeiling(Math.max(0, ...series.flatMap((line) => line.values)))
  const x = (index: number) => pad.left + (count > 1 ? (index / (count - 1)) * innerWidth : innerWidth / 2)
  const y = (value: number) => pad.top + innerHeight - (value / peak) * innerHeight

  const paths = useMemo(
    () =>
      series.map((line) => {
        const points = line.values.map((value, index) => `${x(index).toFixed(1)},${y(value).toFixed(1)}`)
        const stroke = points.length ? `M${points.join('L')}` : ''
        const area = points.length
          ? `${stroke}L${x(count - 1).toFixed(1)},${(pad.top + innerHeight).toFixed(1)}L${x(0).toFixed(1)},${(pad.top + innerHeight).toFixed(1)}Z`
          : ''
        return { stroke, area }
      }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [series, width, height, peak, count],
  )

  const gridValues = [0, 0.25, 0.5, 0.75, 1].map((fraction) => fraction * peak)
  const axisIndexes = labelIndexes(count)

  function move(event: React.MouseEvent<SVGSVGElement>) {
    if (!count) return
    const rect = event.currentTarget.getBoundingClientRect()
    const localX = event.clientX - rect.left
    const fraction = (localX - pad.left) / innerWidth
    const index = Math.min(count - 1, Math.max(0, Math.round(fraction * (count - 1))))
    setTip({ index, x: x(index) })
  }

  return (
    <div className="chart" ref={wrap} style={{ height }}>
      <svg width={width} height={height} onMouseMove={move} onMouseLeave={() => setTip(null)} className="chart-svg">
        <defs>
          {series.map((line, index) => (
            <linearGradient key={line.key} id={`${id}-${index}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stopColor={line.color ?? palette[index % palette.length]} stopOpacity="0.32" />
              <stop offset="1" stopColor={line.color ?? palette[index % palette.length]} stopOpacity="0" />
            </linearGradient>
          ))}
        </defs>

        {gridValues.map((value) => (
          <g key={value}>
            <line x1={pad.left} x2={width - pad.right} y1={y(value)} y2={y(value)} className="chart-grid" />
            <text x={pad.left - 6} y={y(value) + 3} className="chart-axis" textAnchor="end">
              {compact(value)}
            </text>
          </g>
        ))}

        {axisIndexes.map((index) => (
          <text key={index} x={x(index)} y={height - 6} className="chart-axis" textAnchor="middle">
            {formatLabel(labels[index] ?? '')}
          </text>
        ))}

        {series.map((line, index) => {
          const color = line.color ?? palette[index % palette.length]
          const path = paths[index]
          if (!path?.stroke) return null
          return (
            <g key={line.key}>
              {line.area && <path d={path.area} className="chart-area" style={{ fill: `url(#${id}-${index})` }} />}
              <path
                d={path.stroke}
                pathLength={1}
                className={`chart-line${line.dashed ? ' dashed' : ''}`}
                style={{ stroke: color, animationDelay: `${index * 120}ms` }}
              />
            </g>
          )
        })}

        {tip && (
          <g className="chart-cursor">
            <line x1={tip.x} x2={tip.x} y1={pad.top} y2={pad.top + innerHeight} className="chart-crosshair" />
            {series.map((line, index) => (
              <circle
                key={line.key}
                cx={tip.x}
                cy={y(line.values[tip.index] ?? 0)}
                r={4}
                className="chart-dot"
                style={{ fill: line.color ?? palette[index % palette.length] }}
              />
            ))}
          </g>
        )}
      </svg>

      {tip && (
        <Tooltip x={tip.x} width={width}>
          <div className="chart-tip-title">{labels[tip.index]}</div>
          {series.map((line, index) => (
            <div key={line.key} className="chart-tip-row">
              <span className="swatch" style={{ background: line.color ?? palette[index % palette.length] }} />
              <span className="muted">{line.label}</span>
              <b>{format(line.values[tip.index] ?? 0)}</b>
            </div>
          ))}
        </Tooltip>
      )}
    </div>
  )
}

// ---------------------------------------------------------------- bars

export function BarChart({
  labels,
  values,
  height = 150,
  color = palette[0],
  format = (value: number) => value.toLocaleString('ru-RU'),
  formatLabel = shortDay,
}: {
  labels: string[]
  values: number[]
  height?: number
  color?: string
  format?: (value: number) => string
  formatLabel?: (label: string) => string
}) {
  const [wrap, width] = useWidth<HTMLDivElement>()
  const [tip, setTip] = useState<TooltipState | null>(null)
  const pad = { top: 8, right: 4, bottom: 22, left: 4 }
  const innerHeight = height - pad.top - pad.bottom
  const count = Math.max(1, values.length)
  const gap = count > 40 ? 1 : 3
  const slot = (width - pad.left - pad.right) / count
  const barWidth = Math.max(2, slot - gap)
  const peak = Math.max(1, ...values)
  const axisIndexes = labelIndexes(values.length)

  return (
    <div className="chart" ref={wrap} style={{ height }}>
      <svg width={width} height={height} className="chart-svg" onMouseLeave={() => setTip(null)}>
        {values.map((value, index) => {
          const barHeight = value > 0 ? Math.max(3, (value / peak) * innerHeight) : 2
          const left = pad.left + index * slot + gap / 2
          return (
            <rect
              key={labels[index] ?? index}
              x={left}
              y={pad.top + innerHeight - barHeight}
              width={barWidth}
              height={barHeight}
              rx={Math.min(3, barWidth / 2)}
              className={`chart-bar${value === 0 ? ' empty' : ''}${tip?.index === index ? ' hot' : ''}`}
              style={{ fill: value === 0 ? undefined : color, animationDelay: `${Math.min(index * 18, 500)}ms` }}
              onMouseEnter={() => setTip({ index, x: left + barWidth / 2 })}
            />
          )
        })}
        {axisIndexes.map((index) => (
          <text
            key={index}
            x={pad.left + index * slot + slot / 2}
            y={height - 6}
            className="chart-axis"
            textAnchor="middle"
          >
            {formatLabel(labels[index] ?? '')}
          </text>
        ))}
      </svg>
      {tip && (
        <Tooltip x={tip.x} width={width}>
          <div className="chart-tip-title">{labels[tip.index]}</div>
          <b>{format(values[tip.index] ?? 0)}</b>
        </Tooltip>
      )}
    </div>
  )
}

// ---------------------------------------------------------------- sparkline

/** A line with no axes, for the corner of a stat tile. */
export function Sparkline({ values, color = palette[0], width = 96, height = 28 }: { values: number[]; color?: string; width?: number; height?: number }) {
  const id = useId()
  const peak = Math.max(1, ...values)
  const count = values.length
  if (count < 2) return null
  const points = values.map((value, index) => {
    const x = (index / (count - 1)) * (width - 2) + 1
    const y = height - 2 - (value / peak) * (height - 4)
    return `${x.toFixed(1)},${y.toFixed(1)}`
  })
  const line = `M${points.join('L')}`
  const area = `${line}L${width - 1},${height}L1,${height}Z`
  return (
    <svg width={width} height={height} className="spark" aria-hidden="true">
      <defs>
        <linearGradient id={id} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor={color} stopOpacity="0.35" />
          <stop offset="1" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={area} style={{ fill: `url(#${id})` }} className="spark-area" />
      <path d={line} pathLength={1} className="spark-line" style={{ stroke: color }} />
    </svg>
  )
}

// ---------------------------------------------------------------- donut

export interface Slice {
  key: string
  label: string
  value: number
  color?: string
}

export function Donut({
  slices,
  size = 148,
  thickness = 16,
  centerLabel,
}: {
  slices: Slice[]
  size?: number
  thickness?: number
  centerLabel?: string
}) {
  const total = slices.reduce((sum, slice) => sum + slice.value, 0)
  const radius = (size - thickness) / 2
  const circumference = 2 * Math.PI * radius
  const shown = useCountUp(total)
  let offset = 0
  const [hot, setHot] = useState<string | null>(null)

  return (
    <div className="donut">
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="donut-svg">
        <circle cx={size / 2} cy={size / 2} r={radius} className="donut-track" strokeWidth={thickness} />
        {total > 0 &&
          slices.map((slice, index) => {
            const fraction = slice.value / total
            const length = fraction * circumference
            const dash = `${length} ${circumference - length}`
            const rotation = (offset / circumference) * 360 - 90
            offset += length
            return (
              <circle
                key={slice.key}
                cx={size / 2}
                cy={size / 2}
                r={radius}
                strokeWidth={hot === slice.key ? thickness + 4 : thickness}
                strokeDasharray={dash}
                transform={`rotate(${rotation} ${size / 2} ${size / 2})`}
                className="donut-arc"
                style={{ stroke: slice.color ?? palette[index % palette.length], animationDelay: `${index * 90}ms` }}
                onMouseEnter={() => setHot(slice.key)}
                onMouseLeave={() => setHot(null)}
              />
            )
          })}
        <text x="50%" y="50%" className="donut-total" textAnchor="middle" dy="0.1em">
          {shown.toLocaleString('ru-RU')}
        </text>
        {centerLabel && (
          <text x="50%" y="50%" className="donut-label" textAnchor="middle" dy="1.6em">
            {centerLabel}
          </text>
        )}
      </svg>
      <ul className="legend">
        {slices.map((slice, index) => (
          <li
            key={slice.key}
            className={hot === slice.key ? 'hot' : undefined}
            onMouseEnter={() => setHot(slice.key)}
            onMouseLeave={() => setHot(null)}
          >
            <span className="swatch" style={{ background: slice.color ?? palette[index % palette.length] }} />
            <span className="legend-label">{slice.label}</span>
            <span className="legend-value">
              {slice.value.toLocaleString('ru-RU')} <span className="faint">· {share(slice.value, total)}%</span>
            </span>
          </li>
        ))}
        {!slices.length && <li className="faint">Ma'lumot yo'q</li>}
      </ul>
    </div>
  )
}

// ---------------------------------------------------------------- ring

/** One share drawn as an arc — a retention rate, a conversion. */
export function Ring({ percent, size = 72, thickness = 7, color = palette[0], children }: { percent: number; size?: number; thickness?: number; color?: string; children?: ReactNode }) {
  const radius = (size - thickness) / 2
  const circumference = 2 * Math.PI * radius
  const clamped = Math.max(0, Math.min(100, percent))
  const [drawn, setDrawn] = useState(0)
  useEffect(() => {
    const frame = requestAnimationFrame(() => setDrawn(clamped))
    return () => cancelAnimationFrame(frame)
  }, [clamped])
  return (
    <div className="ring" style={{ width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
        <circle cx={size / 2} cy={size / 2} r={radius} className="donut-track" strokeWidth={thickness} />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          strokeWidth={thickness}
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - drawn / 100)}
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
          className="ring-arc"
          style={{ stroke: color }}
        />
      </svg>
      <div className="ring-center">{children}</div>
    </div>
  )
}

// ---------------------------------------------------------------- funnel

export function Funnel({ steps }: { steps: { key: string; label: string; value: number; hint?: string }[] }) {
  const first = steps[0]?.value ?? 0
  return (
    <ol className="funnel">
      {steps.map((step, index) => {
        const previous = steps[index - 1]?.value ?? step.value
        const ofFirst = share(step.value, first)
        const ofPrevious = index === 0 ? null : share(step.value, previous)
        return (
          <li key={step.key} style={{ animationDelay: `${index * 70}ms` }}>
            <div className="funnel-head">
              <span>{step.label}</span>
              <span className="funnel-numbers">
                <b>{step.value.toLocaleString('ru-RU')}</b>
                <span className="faint">
                  {ofFirst}%{ofPrevious !== null && ofPrevious !== ofFirst && ofPrevious <= 100 ? ` · oldingidan ${ofPrevious}%` : ''}
                </span>
              </span>
            </div>
            <div className="funnel-track">
              <div
                className="funnel-bar"
                style={{ width: `${Math.max(ofFirst, step.value > 0 ? 2 : 0)}%`, background: palette[index % palette.length], transitionDelay: `${index * 70}ms` }}
              />
            </div>
            {step.hint && <div className="faint funnel-hint">{step.hint}</div>}
          </li>
        )
      })}
    </ol>
  )
}

// ---------------------------------------------------------------- horizontal bars

/** Ranked categories — platforms, versions, buckets. */
export function RankedBars({ items, format = (value: number) => value.toLocaleString('ru-RU'), color }: { items: { key: string; label: string; value: number }[]; format?: (value: number) => string; color?: string }) {
  const peak = Math.max(1, ...items.map((item) => item.value))
  const total = items.reduce((sum, item) => sum + item.value, 0)
  return (
    <ul className="ranked">
      {items.map((item, index) => (
        <li key={item.key} style={{ animationDelay: `${index * 50}ms` }}>
          <span className="ranked-label" title={item.label}>
            {item.label}
          </span>
          <span className="ranked-track">
            <span
              className="ranked-bar"
              style={{ width: `${(item.value / peak) * 100}%`, background: color ?? palette[index % palette.length], transitionDelay: `${index * 50}ms` }}
            />
          </span>
          <span className="ranked-value">
            {format(item.value)} <span className="faint">{share(item.value, total)}%</span>
          </span>
        </li>
      ))}
      {!items.length && <li className="faint">Ma'lumot yo'q</li>}
    </ul>
  )
}
