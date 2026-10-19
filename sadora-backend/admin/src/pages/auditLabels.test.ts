import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { actionLabel, actionLabels } from './auditLabels'

/** The server's own list of audit actions, read from its source so a new one cannot slip by. */
function serverActions(): string[] {
  // The tests run from the panel's own folder, locally and in CI; the server sits beside it.
  const source = readFileSync(resolve(process.cwd(), '../server/src/main/kotlin/uz/sadora/server/audit/AuditService.kt'), 'utf8')
  const body = source.slice(source.indexOf('object AuditActions'))
  return [...body.matchAll(/const val [A-Z_]+ = "([a-z_.]+)"/g)].map((match) => match[1]!)
}

describe('actionLabels', () => {
  it('names every action in the server’s AuditActions', () => {
    const actions = serverActions()
    expect(actions.length).toBeGreaterThan(40)
    expect(actions.filter((action) => !(action in actionLabels))).toEqual([])
  })

  it('shows an unknown action as its code rather than hiding it', () => {
    expect(actionLabel('user.signed_up')).toBe("Ro'yxatdan o'tdi")
    expect(actionLabel('something.new')).toBe('something.new')
  })
})
