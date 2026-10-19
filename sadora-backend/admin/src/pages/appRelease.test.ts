import { describe, expect, it } from 'vitest'
import type { AppReleasePolicy } from '../api/types'
import { releaseWarnings } from './appRelease'

const base: AppReleasePolicy = {
  android: { storeUrl: 'https://play.google.com/store/apps/details?id=uz.sadora.app' },
  ios: {},
  maintenance: { active: false, message: {} },
}

describe('releaseWarnings', () => {
  it('says nothing for a change that stops nobody', () => {
    expect(releaseWarnings(base, { ...base, android: { ...base.android, latestBuild: 14 } })).toEqual([])
    expect(releaseWarnings({ ...base, android: { ...base.android, minBuild: 9 } }, { ...base, android: { ...base.android, minBuild: 5 } })).toEqual([])
  })

  it('warns when a minimum goes up, and when the update button would lead nowhere', () => {
    const warnings = releaseWarnings(base, { ...base, ios: { minBuild: 7 } })
    expect(warnings).toHaveLength(2)
    expect(warnings[0]).toContain('iOS: 7 dan past')
    expect(warnings[1]).toContain("do'kon havolasi yo'q")
  })

  it('warns when maintenance is switched on, not when it stays on', () => {
    const on = { ...base, maintenance: { active: true, message: { uz: 'Ishlar' } } }
    expect(releaseWarnings(base, on)).toHaveLength(1)
    expect(releaseWarnings(on, on)).toEqual([])
  })
})
