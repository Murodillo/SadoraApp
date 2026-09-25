import { describe, expect, it } from 'vitest'
import { limits } from '../api/limits'
import { allowedReviewActions, formatBytes, reviewNeedsNote, reviewNoteValid } from './doctorReview'

describe('allowedReviewActions', () => {
  it('follows the server transitions for a reviewer', () => {
    expect(allowedReviewActions('pending', 'ADMIN')).toEqual(['approve', 'reject'])
    expect(allowedReviewActions('approved', 'OWNER')).toEqual(['suspend'])
    expect(allowedReviewActions('suspended', 'ADMIN')).toEqual(['reinstate'])
    expect(allowedReviewActions('rejected', 'OWNER')).toEqual([])
  })

  it('gives support and analysts nothing to press', () => {
    for (const status of ['pending', 'approved', 'suspended'] as const) {
      expect(allowedReviewActions(status, 'SUPPORT')).toEqual([])
      expect(allowedReviewActions(status, 'ANALYST')).toEqual([])
      expect(allowedReviewActions(status, undefined)).toEqual([])
    }
  })
})

describe('review notes', () => {
  it('are required only where the doctor is told why', () => {
    expect(reviewNeedsNote('reject')).toBe(true)
    expect(reviewNeedsNote('suspend')).toBe(true)
    expect(reviewNeedsNote('approve')).toBe(false)
    expect(reviewNeedsNote('reinstate')).toBe(false)
  })

  it('refuse a blank note for reject and suspend', () => {
    expect(reviewNoteValid('reject', '   ')).toBe(false)
    expect(reviewNoteValid('suspend', '')).toBe(false)
    expect(reviewNoteValid('reject', 'Diplom o‘qilmaydi')).toBe(true)
    expect(reviewNoteValid('approve', '')).toBe(true)
  })

  it('refuse a note longer than the server takes', () => {
    expect(reviewNoteValid('reject', 'x'.repeat(limits.reasonMax))).toBe(true)
    expect(reviewNoteValid('reject', 'x'.repeat(limits.reasonMax + 1))).toBe(false)
  })
})

describe('formatBytes', () => {
  it('picks a readable unit', () => {
    expect(formatBytes(512)).toBe('512 B')
    expect(formatBytes(1536)).toBe('1.5 KB')
    expect(formatBytes(2 * 1024 * 1024)).toBe('2.0 MB')
    expect(formatBytes(-1)).toBe('—')
  })
})
