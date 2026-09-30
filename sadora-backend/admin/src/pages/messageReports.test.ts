import { describe, expect, it } from 'vitest'
import type { ReportContextView } from '../api/types'
import {
  canOpenImage,
  canViewMessageContext,
  contextLineAuthor,
  contextLineText,
  IMAGE_PLACEHOLDER,
  isDoctorLine,
  RECORD_LINE,
  reportTarget,
  restrictDone,
  restrictDurations,
} from './messageReports'

const consultation: Pick<ReportContextView, 'consultation' | 'reportedIsDoctor' | 'reporter' | 'reported'> = {
  consultation: true,
  reportedIsDoctor: true,
  reporter: 'Oydin',
  reported: 'Nodira Karimova ✓',
}

describe('canViewMessageContext', () => {
  it('opens a thread for Owner, Admin and Support only', () => {
    expect(canViewMessageContext('OWNER')).toBe(true)
    expect(canViewMessageContext('ADMIN')).toBe(true)
    expect(canViewMessageContext('SUPPORT')).toBe(true)
    expect(canViewMessageContext('ANALYST')).toBe(false)
    expect(canViewMessageContext(undefined)).toBe(false)
  })
})

describe('reportTarget', () => {
  it('names a private message apart from posts and comments', () => {
    expect(reportTarget({ messageId: 'm' }).text).toBe('Shaxsiy xabar')
    expect(reportTarget({ messageId: 'm', consultation: true }).text).toBe('Konsultatsiya')
    expect(reportTarget({ commentId: 'c' }).text).toBe('izoh')
    expect(reportTarget({}).text).toBe('post')
  })
})

describe('contextLineText', () => {
  it('never shows a record, and shows a photo as a placeholder with its caption', () => {
    expect(contextLineText({ kind: 'record', body: 'anything the wire sent' })).toBe(RECORD_LINE)
    expect(contextLineText({ kind: 'image', body: '' })).toBe(IMAGE_PLACEHOLDER)
    expect(contextLineText({ kind: 'image', body: 'toshma' })).toBe(`${IMAGE_PLACEHOLDER} toshma`)
    expect(contextLineText({ kind: 'text', body: 'Salom' })).toBe('Salom')
  })
})

describe('context sides', () => {
  it('labels each line by the side that wrote it', () => {
    expect(contextLineAuthor(consultation, { fromReported: true })).toBe('Nodira Karimova ✓')
    expect(contextLineAuthor(consultation, { fromReported: false })).toBe('Oydin')
  })

  it('marks the doctor on whichever side she is', () => {
    expect(isDoctorLine(consultation, { fromReported: true })).toBe(true)
    expect(isDoctorLine(consultation, { fromReported: false })).toBe(false)
    const doctorReported = { ...consultation, reportedIsDoctor: false }
    expect(isDoctorLine(doctorReported, { fromReported: false })).toBe(true)
    // An alias DM has no doctor at all.
    expect(isDoctorLine({ consultation: false, reportedIsDoctor: false }, { fromReported: false })).toBe(false)
  })

  it('opens only the reported photo', () => {
    expect(canOpenImage({ kind: 'image', reported: true })).toBe(true)
    expect(canOpenImage({ kind: 'image', reported: false })).toBe(false)
    expect(canOpenImage({ kind: 'text', reported: true })).toBe(false)
  })
})

describe('restrict durations', () => {
  it('offer one day, a week, a month and until lifted', () => {
    expect(restrictDurations.map((option) => option.days)).toEqual([1, 7, 30, null])
    expect(restrictDone(7)).toBe('Yuboruvchi 7 kunga cheklandi')
    expect(restrictDone(null)).toBe('Yuboruvchi muddatsiz cheklandi')
  })
})
