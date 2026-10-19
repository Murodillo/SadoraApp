import { describe, expect, it } from 'vitest'
import type { AuditEntry } from '../api/types'
import { collapseTimeline, providerName } from './userCard'

function entry(id: string, action: string, createdAt: string, actorType: AuditEntry['actorType'] = 'user'): AuditEntry {
  return { id, action, createdAt, actorType, metadata: {} }
}

describe('collapseTimeline', () => {
  it('folds a run of the same event into one line, newest first', () => {
    const runs = collapseTimeline([
      entry('1', 'user.signed_in', '2026-10-20T10:00:00Z'),
      entry('2', 'user.signed_in', '2026-10-19T10:00:00Z'),
      entry('3', 'user.signed_in', '2026-10-18T10:00:00Z'),
      entry('4', 'subscription.granted', '2026-10-17T10:00:00Z', 'admin'),
      entry('5', 'user.signed_in', '2026-10-16T10:00:00Z'),
    ])
    expect(runs.map((run) => [run.action, run.count, run.latest, run.earliest])).toEqual([
      ['user.signed_in', 3, '2026-10-20T10:00:00Z', '2026-10-18T10:00:00Z'],
      ['subscription.granted', 1, '2026-10-17T10:00:00Z', '2026-10-17T10:00:00Z'],
      ['user.signed_in', 1, '2026-10-16T10:00:00Z', '2026-10-16T10:00:00Z'],
    ])
  })

  it('keeps the same action apart when a different kind of actor did it', () => {
    const runs = collapseTimeline([
      entry('1', 'user.blocked', '2026-10-20T10:00:00Z', 'admin'),
      entry('2', 'user.blocked', '2026-10-19T10:00:00Z', 'system'),
    ])
    expect(runs).toHaveLength(2)
  })

  it('is empty for an empty list', () => {
    expect(collapseTimeline([])).toEqual([])
  })
})

describe('providerName', () => {
  it('names the known providers and capitalises the rest', () => {
    expect(providerName('whoop')).toBe('WHOOP')
    expect(providerName('garmin')).toBe('Garmin')
    expect(providerName('polar')).toBe('Polar')
  })
})
