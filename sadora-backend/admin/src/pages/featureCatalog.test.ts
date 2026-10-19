import { describe, expect, it } from 'vitest'
import type { AdminFlag, FeatureDefinition, FlagRule } from '../api/types'
import {
  audienceOf,
  buildFeatureGroups,
  cardState,
  catalog,
  flagState,
  matchesSearch,
  planSwitchOff,
  planSwitchOn,
  showsLimits,
} from './featureCatalog'

function flag(key: string, enabled = true, defaultValue = true, rules: Partial<FlagRule>[] = []): AdminFlag {
  return {
    key,
    description: `${key} description`,
    enabled,
    defaultValue,
    rules: rules.map((rule, index) => ({ id: `r${index}`, rolloutPercentage: 100, value: true, priority: 100, ...rule })),
  }
}

function feature(key: string, freeEnabled: boolean, premiumEnabled: boolean, limits: Partial<FeatureDefinition> = {}): FeatureDefinition {
  return { key, description: `${key} description`, freeEnabled, premiumEnabled, ...limits }
}

describe('catalog', () => {
  it('names each server row in one card only', () => {
    const owner = new Map<string, string>()
    for (const group of catalog) {
      for (const entry of group.entries) {
        const rows = [
          entry.flag && `flag:${entry.flag}`,
          entry.entitlement && `entitlement:${entry.entitlement}`,
          ...(entry.platforms ?? []).map((platform) => `flag:${platform.flag}`),
        ].filter((row): row is string => Boolean(row))
        for (const row of rows) {
          expect(owner.get(row), `${row} is in «${owner.get(row)}» and «${entry.title}»`).toBeUndefined()
          owner.set(row, entry.title)
        }
      }
    }
  })
})

describe('buildFeatureGroups', () => {
  it('pairs a flag and an entitlement into one card', () => {
    const groups = buildFeatureGroups([flag('ai_chat_enabled')], [feature('ai_chat', true, true)])
    const card = groups.flatMap((group) => group.cards).find((c) => c.entry.title === 'AI chat')!
    expect(card.flag?.key).toBe('ai_chat_enabled')
    expect(card.feature?.key).toBe('ai_chat')
  })

  it('leaves out a catalogue entry the server does not have', () => {
    const groups = buildFeatureGroups([], [feature('ai_chat', true, true)])
    const titles = groups.flatMap((group) => group.cards.map((card) => card.entry.title))
    expect(titles).toEqual(['AI chat'])
  })

  it('puts a key the panel does not know under "Boshqa", by its description', () => {
    const groups = buildFeatureGroups([flag('brand_new_thing')], [feature('another_new', false, true)])
    const other = groups.find((group) => group.title === 'Boshqa')!
    expect(other.cards.map((card) => card.entry.title)).toEqual(['brand_new_thing description', 'another_new description'])
  })

  it('hangs platform flags on their feature, not under "Boshqa"', () => {
    const groups = buildFeatureGroups([flag('healthkit_sync'), flag('health_connect_sync', false)], [feature('wearable_sync', true, true)])
    expect(groups.find((group) => group.title === 'Boshqa')).toBeUndefined()
    const card = groups.flatMap((group) => group.cards).find((c) => c.feature?.key === 'wearable_sync')!
    expect(card.platforms.map((p) => [p.label, p.flag.enabled])).toEqual([
      ['iPhone', true],
      ['Android', false],
    ])
  })
})

describe('flagState', () => {
  it('is off at the kill switch whatever the rules say', () => {
    expect(flagState(flag('x', false, true, [{ value: true }]))).toBe('off')
  })

  it('follows the default when no rule disagrees with it', () => {
    expect(flagState(flag('x', true, true))).toBe('on')
    expect(flagState(flag('x', true, false))).toBe('off')
    expect(flagState(flag('x', true, true, [{ value: true, rolloutPercentage: 20 }]))).toBe('on')
  })

  it('is partial when a rule opens or closes it for some', () => {
    expect(flagState(flag('x', true, false, [{ value: true, rolloutPercentage: 20 }]))).toBe('partial')
    expect(flagState(flag('x', true, true, [{ value: false, platform: 'IOS' }]))).toBe('partial')
  })
})

describe('audienceOf and cardState', () => {
  it('reads who gets a feature', () => {
    expect(audienceOf(feature('x', true, true))).toBe('all')
    expect(audienceOf(feature('x', false, true))).toBe('premium')
    expect(audienceOf(feature('x', true, false))).toBe('free')
    expect(audienceOf(feature('x', false, false))).toBe('none')
  })

  it('is off when any part is off, partial when a flag is split', () => {
    expect(cardState({ flag: flag('x'), feature: feature('x', false, true) })).toBe('on')
    expect(cardState({ flag: flag('x', false), feature: feature('x', true, true) })).toBe('off')
    expect(cardState({ flag: flag('x'), feature: feature('x', false, false) })).toBe('off')
    expect(cardState({ flag: flag('x', true, false, [{ value: true, rolloutPercentage: 5 }]) })).toBe('partial')
    expect(cardState({})).toBe('off')
  })
})

describe('planSwitchOff', () => {
  it('uses the kill switch and keeps the default, so on restores the setup', () => {
    expect(planSwitchOff({ flag: flag('sos', true, true), feature: feature('x', true, true) })).toEqual({
      flag: { key: 'sos', enabled: false, defaultValue: true },
    })
  })

  it('closes an entitlement-only feature to both tiers', () => {
    expect(planSwitchOff({ feature: feature('lab_scan', true, true, { freeMonthlyLimit: 3 }) })).toEqual({
      feature: feature('lab_scan', false, false, { freeMonthlyLimit: 3 }),
    })
  })
})

describe('planSwitchOn', () => {
  it('lifts the kill switch and leaves a "yes" default as it was', () => {
    expect(planSwitchOn({ flag: flag('sos', false, true) })).toEqual({ flag: { key: 'sos', enabled: true, defaultValue: true } })
  })

  it('turns a "no" default to "yes" when no rule opens it, or on would change nothing', () => {
    expect(planSwitchOn({ flag: flag('frame_sale', true, false) })).toEqual({
      flag: { key: 'frame_sale', enabled: true, defaultValue: true },
    })
  })

  it('keeps a "no" default when a rollout rule already opens it for some', () => {
    expect(planSwitchOn({ flag: flag('x', false, false, [{ value: true, rolloutPercentage: 10 }]) })).toEqual({
      flag: { key: 'x', enabled: true, defaultValue: false },
    })
  })

  it('opens a feature closed to everyone to Premium only', () => {
    const plan = planSwitchOn({ flag: flag('ai_chat_enabled'), feature: feature('ai_chat', false, false) })
    expect(plan.flag).toBeUndefined()
    expect(plan.feature).toEqual(feature('ai_chat', false, true))
  })

  it('touches nothing that is already on', () => {
    expect(planSwitchOn({ flag: flag('x'), feature: feature('x', true, true) })).toEqual({})
  })
})

describe('limits', () => {
  it('shows limit fields where the server counts uses, or where a limit is already set', () => {
    const metered = { title: 'AI chat', about: '', art: 'ic3d_ai' as const, metered: true }
    const plain = { title: 'Kegel', about: '', art: 'ic3d_kegel' as const }
    expect(showsLimits(metered, feature('ai_chat', true, true))).toBe(true)
    expect(showsLimits(plain, feature('kegel_advanced', false, true))).toBe(false)
    expect(showsLimits(plain, feature('kegel_advanced', false, true, { premiumDailyLimit: 2 }))).toBe(true)
  })
})

describe('matchesSearch', () => {
  const [card] = buildFeatureGroups([], [feature('lab_scan', true, true)]).flatMap((group) => group.cards)

  it('finds a card by its words or its key, ignoring case and apostrophes', () => {
    expect(matchesSearch(card!, 'skaner')).toBe(true)
    expect(matchesSearch(card!, 'VARAGINI')).toBe(true)
    expect(matchesSearch(card!, 'lab_scan')).toBe(true)
    expect(matchesSearch(card!, '  ')).toBe(true)
    expect(matchesSearch(card!, 'payme')).toBe(false)
  })
})
