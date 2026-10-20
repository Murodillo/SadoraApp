import { describe, expect, it } from 'vitest'
import { ApiFailure } from './client'
import { limits } from './limits'
import {
  admitFiles,
  albumLayout,
  classifyFile,
  durationWords,
  formatDuration,
  formatFileSize,
  mediaErrorText,
  mediaFileName,
  mediaPreview,
  mediaSummary,
  singleTileSize,
  tileLabel,
  videoMeta,
  videoProblem,
} from './media'
import type { MessageMedia } from './types'

const MB = 1024 * 1024

function photo(id = 'p1', overrides: Partial<MessageMedia> = {}): MessageMedia {
  return { id, kind: 'photo', width: 2048, height: 1536, sizeBytes: 900_000, hasThumbnail: true, ...overrides }
}

function video(id = 'v1', overrides: Partial<MessageMedia> = {}): MessageMedia {
  return { id, kind: 'video', width: 720, height: 1280, durationMs: 84_000, sizeBytes: 18 * MB, hasThumbnail: true, ...overrides }
}

describe('albumLayout', () => {
  it('draws one tile alone, two side by side and three as one big beside two', () => {
    expect(albumLayout(1)).toEqual({ shape: 'one', shown: 1, more: 0 })
    expect(albumLayout(2)).toEqual({ shape: 'two', shown: 2, more: 0 })
    expect(albumLayout(3)).toEqual({ shape: 'three', shown: 3, more: 0 })
  })

  it('draws four or more as a square of four, the rest counted on the last tile', () => {
    expect(albumLayout(4)).toEqual({ shape: 'grid', shown: 4, more: 0 })
    expect(albumLayout(7)).toEqual({ shape: 'grid', shown: 4, more: 3 })
    expect(albumLayout(limits.messageMediaMax)).toEqual({ shape: 'grid', shown: 4, more: 6 })
  })

  it('draws nothing for nothing', () => {
    expect(albumLayout(0)).toEqual({ shape: 'one', shown: 0, more: 0 })
  })
})

describe('singleTileSize', () => {
  it('keeps the proportions inside the album width and the height cap', () => {
    expect(singleTileSize(2048, 1536)).toEqual({ width: 300, height: 225 })
    // An upright video is held by the height cap, not the width.
    expect(singleTileSize(720, 1280)).toEqual({ width: 191, height: 340 })
    expect(singleTileSize(1000, 1000)).toEqual({ width: 300, height: 300 })
  })

  it('holds a panorama or a long screenshot to a usable tile', () => {
    expect(singleTileSize(8000, 500)).toEqual({ width: 300, height: 90 })
    expect(singleTileSize(400, 6000)).toEqual({ width: 140, height: 340 })
  })

  it('falls back to 4:3 when the size is unknown', () => {
    expect(singleTileSize(0, 0)).toEqual({ width: 300, height: 225 })
  })
})

describe('durations and sizes', () => {
  it('writes a video length as a clock', () => {
    expect(formatDuration(42_000)).toBe('0:42')
    expect(formatDuration(84_400)).toBe('1:24')
    expect(formatDuration(180_000)).toBe('3:00')
    expect(formatDuration(3_723_000)).toBe('1:02:03')
    expect(formatDuration(0)).toBe('0:00')
  })

  it('words the same length for a screen reader', () => {
    expect(durationWords(84_000)).toBe('1 daqiqa 24 soniya')
    expect(durationWords(42_000)).toBe('42 soniya')
    expect(durationWords(120_000)).toBe('2 daqiqa')
    expect(durationWords(400)).toBe('0 soniya')
  })

  it('puts the length and the weight under a video tile', () => {
    expect(videoMeta({ durationMs: 84_000, sizeBytes: 18 * MB })).toBe('1:24 · 18 MB')
    expect(videoMeta({ durationMs: null, sizeBytes: 1_468_006 })).toBe('1,4 MB')
  })

  it('weighs a file in KB under a megabyte and in MB above', () => {
    expect(formatFileSize(10)).toBe('1 KB')
    expect(formatFileSize(240 * 1024)).toBe('240 KB')
    expect(formatFileSize(100 * MB)).toBe('100 MB')
  })
})

describe('tileLabel', () => {
  it('names a photo by its place in the album', () => {
    expect(tileLabel(photo(), 0, 3)).toBe('Rasm 1 / 3')
    expect(tileLabel(photo(), 0, 1)).toBe('Rasm')
  })

  it('names a video with its length in words', () => {
    expect(tileLabel(video(), 0, 1)).toBe('Video, 1 daqiqa 24 soniya')
    expect(tileLabel(video(), 1, 3)).toBe('Video 2 / 3, 1 daqiqa 24 soniya')
    expect(tileLabel(video('v', { durationMs: null }), 0, 1)).toBe('Video')
  })

  it('says how many more the last tile hides', () => {
    expect(tileLabel(photo(), 3, 7, 3)).toBe('Rasm 4 / 7, yana 3 ta')
  })
})

describe('words for a line', () => {
  it('counts photos and videos', () => {
    expect(mediaSummary([photo()])).toBe('📷 Rasm')
    expect(mediaSummary([photo(), photo(), photo()])).toBe('📷 3 ta rasm')
    expect(mediaSummary([video('v', { durationMs: 42_000 })])).toBe('🎥 Video · 0:42')
    expect(mediaSummary([video('v', { durationMs: null })])).toBe('🎥 Video')
    expect(mediaSummary([video(), video()])).toBe('🎥 2 ta video')
    expect(mediaSummary([photo(), video(), photo()])).toBe('📷 2 ta rasm, 1 ta video')
  })

  it('words the list preview from the first item and the count, with the caption after it', () => {
    expect(mediaPreview(photo(), 1, '')).toBe('📷 Rasm')
    expect(mediaPreview(video('v', { durationMs: 42_000 }), 1, null)).toBe('🎥 Video · 0:42')
    expect(mediaPreview(photo(), 1, ' Mana tahlil ')).toBe('📷 Rasm · Mana tahlil')
  })

  it('calls several items an album, since the list does not say how many are videos', () => {
    expect(mediaPreview(photo(), 3, '')).toBe('📷 3 ta rasm')
    expect(mediaPreview(photo(), 3, '', 1)).toBe('📷 2 ta rasm, 1 ta video')
    expect(mediaPreview(video(), 2, 'Qarang', 2)).toBe('🎥 2 ta video · Qarang')
  })

  it('still says something for a count without its item', () => {
    expect(mediaPreview(null, 0, '')).toBe('📷 Rasm')
  })
})

describe('classifyFile', () => {
  it('takes any picture as a photo, to be redrawn as a JPEG', () => {
    expect(classifyFile('a.jpg', 'image/jpeg')).toEqual({ kind: 'photo' })
    expect(classifyFile('a.heic', 'image/heic')).toEqual({ kind: 'photo' })
    expect(classifyFile('scan.png', '')).toEqual({ kind: 'photo' })
  })

  it('takes MP4 and MOV videos, by type or by extension when the browser names none', () => {
    expect(classifyFile('a.mp4', 'video/mp4')).toEqual({ kind: 'video', mimeType: 'video/mp4' })
    expect(classifyFile('a.m4v', 'video/x-m4v')).toEqual({ kind: 'video', mimeType: 'video/mp4' })
    expect(classifyFile('IMG_1.MOV', 'video/quicktime')).toEqual({ kind: 'video', mimeType: 'video/quicktime' })
    expect(classifyFile('IMG_1.MOV', '')).toEqual({ kind: 'video', mimeType: 'video/quicktime' })
  })

  it('refuses other videos and anything else', () => {
    expect(classifyFile('a.webm', 'video/webm')).toEqual({ problem: 'Faqat MP4 yoki MOV video' })
    expect(classifyFile('a.pdf', 'application/pdf')).toEqual({ problem: 'Faqat rasm yoki video yuborish mumkin' })
    expect(classifyFile('notes', '')).toEqual({ problem: 'Faqat rasm yoki video yuborish mumkin' })
  })
})

describe('videoProblem', () => {
  it('refuses a video over 100 MB', () => {
    expect(videoProblem(100 * MB, 10_000)).toBeNull()
    expect(videoProblem(100 * MB + 1, 10_000)).toBe("Video juda katta — eng ko'pi 100 MB")
  })

  it('refuses a video over three minutes, with the server\'s second of grace', () => {
    expect(videoProblem(MB, 180_000)).toBeNull()
    expect(videoProblem(MB, 180_999)).toBeNull()
    expect(videoProblem(MB, 181_000)).toBe("Video 3 daqiqadan uzun bo'lmasin")
  })

  it('leaves an unknown length to the server', () => {
    expect(videoProblem(MB, null)).toBeNull()
  })
})

describe('admitFiles', () => {
  const p = { kind: 'photo' as const }
  const v = { kind: 'video' as const }

  it('takes up to ten in all when chat_media is on', () => {
    expect(admitFiles(0, [p, v, p], true)).toEqual({ take: [p, v, p], problems: [] })
    const picked = Array.from({ length: 4 }, () => p)
    const admitted = admitFiles(8, picked, true)
    expect(admitted.take).toHaveLength(2)
    expect(admitted.problems).toEqual(["Bitta xabarda eng ko'pi 10 ta rasm yoki video"])
  })

  it('takes one photo and no video when chat_media is off', () => {
    expect(admitFiles(0, [p], false)).toEqual({ take: [p], problems: [] })
    const admitted = admitFiles(0, [v, p, p], false)
    expect(admitted.take).toEqual([p])
    expect(admitted.problems).toEqual(['Video va albom hozircha yoqilmagan: bitta rasm yuborish mumkin'])
    expect(admitFiles(1, [p], false).take).toEqual([])
  })
})

describe('mediaErrorText', () => {
  it("says the day's allowance is spent on a 429", () => {
    expect(mediaErrorText(new ApiFailure('limit_reached', 'Limit tugadi', {}, undefined, 429))).toBe('Bugungi limit tugadi')
  })

  it('keeps the server words for a plain rate limit', () => {
    expect(mediaErrorText(new ApiFailure('rate_limited', "Juda ko'p so'rov yuborildi", {}, undefined, 429))).toBe(
      "Juda ko'p so'rov yuborildi",
    )
  })

  it('says videos and albums are off on a feature_disabled 403', () => {
    expect(mediaErrorText(new ApiFailure('feature_disabled', "Bu bo'lim hozircha yopiq", { flag: 'chat_media' }, undefined, 403))).toBe(
      'Video va albom hozircha yoqilmagan',
    )
  })

  it("gives a 400's field problem rather than its general headline", () => {
    const failure = new ApiFailure('validation_failed', "So'rov ma'lumotlari noto'g'ri", { file: "Video 3 daqiqadan uzun bo'lmasin" }, undefined, 400)
    expect(mediaErrorText(failure)).toBe("Video 3 daqiqadan uzun bo'lmasin")
    expect(mediaErrorText(new ApiFailure('validation_failed', 'Fayl buzilgan', {}, undefined, 400))).toBe('Fayl buzilgan')
  })

  it("keeps the server's message on a 503 and anything else", () => {
    expect(mediaErrorText(new ApiFailure('upstream_unavailable', "Server xotirasi to'lgan", {}, undefined, 503))).toBe(
      "Server xotirasi to'lgan",
    )
    expect(mediaErrorText(new Error('boom'))).toBe('boom')
  })
})

describe('mediaFileName', () => {
  it('names a download by kind, a short id and the type it came as', () => {
    expect(mediaFileName(photo('1a2b3c4d-5e6f-7a8b'), 'image/jpeg')).toBe('sadora-rasm-1a2b3c4d.jpg')
    expect(mediaFileName(video('9f8e7d6c-0000'), 'video/mp4')).toBe('sadora-video-9f8e7d6c.mp4')
    expect(mediaFileName(video('9f8e7d6c-0000'), 'video/quicktime')).toBe('sadora-video-9f8e7d6c.mov')
  })

  it('falls back on the kind when the type is unknown', () => {
    expect(mediaFileName(photo('abc'), '')).toBe('sadora-rasm-abc.jpg')
    expect(mediaFileName(video('abc'), null)).toBe('sadora-video-abc.mp4')
  })
})
