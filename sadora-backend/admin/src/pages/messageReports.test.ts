import { describe, expect, it } from 'vitest'
import type { ReportContextView } from '../api/types'
import {
  canOpenImage,
  contextLineText as mediaLine,
  formatDuration,
  mediaSummary,
  openableMedia,
  canViewMessageContext,
  contextLineAuthor,
  contextLineText,
  IMAGE_PLACEHOLDER,
  isDoctorLine,
  LAB_LINE,
  RECORD_LINE,
  REPORT_LINE,
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
  it('never shows a record, a lab sheet or a report, and shows a photo as a placeholder with its caption', () => {
    expect(contextLineText({ kind: 'record', body: 'anything the wire sent' })).toBe(RECORD_LINE)
    expect(contextLineText({ kind: 'lab', body: 'Gemoglobin: 104 g/L' })).toBe(LAB_LINE)
    expect(contextLineText({ kind: 'report', body: '📄 Shifokor uchun hisobot · 6 oy (PDF)' })).toBe(REPORT_LINE)
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

describe('photos and videos of a reported line', () => {
  const items = [
    { id: 'a', kind: 'photo' as const, width: 10, height: 10, sizeBytes: 1 },
    { id: 'b', kind: 'video' as const, width: 10, height: 10, sizeBytes: 1, durationMs: 65_400 },
    { id: 'c', kind: 'photo' as const, width: 10, height: 10, sizeBytes: 1 },
  ]

  it('names the line by a placeholder, with its caption', () => {
    expect(mediaLine({ kind: 'media', body: '' })).toBe('[rasm/video]')
    expect(mediaLine({ kind: 'media', body: 'natija' })).toBe('[rasm/video] natija')
  })

  it('opens only the reported line’s items', () => {
    expect(openableMedia({ kind: 'media', reported: true, media: items })).toHaveLength(3)
    expect(openableMedia({ kind: 'media', reported: false, media: items })).toEqual([])
    expect(openableMedia({ kind: 'text', reported: true, media: items })).toEqual([])
  })

  it('says what the button opens and how long a video is', () => {
    expect(mediaSummary(items)).toBe('2 ta rasm, 1 ta video')
    expect(mediaSummary(items.slice(1, 2))).toBe('1 ta video')
    expect(formatDuration(65_400)).toBe('1:05')
    expect(formatDuration(null)).toBe('0:00')
  })
})
