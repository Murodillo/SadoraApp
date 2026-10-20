import { describe, expect, it } from 'vitest'
import {
  base64Of,
  fitWithin,
  ImageProblem,
  PHOTO_ACCEPT,
  PHOTO_MAX_EDGE,
  photoSizeProblem,
  photoTypeProblem,
  preparePhoto,
} from './image'
import { limits } from './limits'

describe('fitWithin', () => {
  it('shrinks the longest edge to the limit and keeps the proportions', () => {
    expect(fitWithin(4032, 3024, PHOTO_MAX_EDGE)).toEqual({ width: 1600, height: 1200 })
    expect(fitWithin(3024, 4032, PHOTO_MAX_EDGE)).toEqual({ width: 1200, height: 1600 })
    expect(fitWithin(5000, 5000, PHOTO_MAX_EDGE)).toEqual({ width: 1600, height: 1600 })
  })

  it('never enlarges a picture already small enough', () => {
    expect(fitWithin(800, 600, PHOTO_MAX_EDGE)).toEqual({ width: 800, height: 600 })
    expect(fitWithin(1600, 900, PHOTO_MAX_EDGE)).toEqual({ width: 1600, height: 900 })
  })

  it('rounds to whole pixels and never reaches zero', () => {
    expect(fitWithin(10_000, 3, PHOTO_MAX_EDGE)).toEqual({ width: 1600, height: 1 })
    expect(fitWithin(333.4, 200.6, PHOTO_MAX_EDGE)).toEqual({ width: 333, height: 201 })
  })

  it("keeps the message photos' own limit, the server's 2048px, as the default", () => {
    expect(limits.mediaPhotoPx).toBe(2048)
    expect(fitWithin(4096, 2304)).toEqual({ width: 2048, height: 1152 })
    expect(fitWithin(3024, 4032)).toEqual({ width: 1536, height: 2048 })
    expect(fitWithin(2048, 1000)).toEqual({ width: 2048, height: 1000 })
  })
})

describe('photoTypeProblem', () => {
  it('takes JPEG and PNG, in any case', () => {
    expect(photoTypeProblem('image/jpeg')).toBeNull()
    expect(photoTypeProblem('image/png')).toBeNull()
    expect(photoTypeProblem('IMAGE/JPEG')).toBeNull()
  })

  it('refuses everything else the server cannot read', () => {
    for (const type of ['image/heic', 'image/webp', 'image/gif', 'application/pdf', '']) {
      expect(photoTypeProblem(type)).toBe('Faqat JPEG yoki PNG rasm')
    }
  })

  it('matches what the picker offers', () => {
    expect(PHOTO_ACCEPT).toBe('image/jpeg,image/png')
  })
})

describe('photoSizeProblem', () => {
  it('refuses a picture with either side under 64 pixels, as the server does', () => {
    expect(photoSizeProblem(63, 500)).toMatch(/juda kichik/)
    expect(photoSizeProblem(500, 63)).toMatch(/juda kichik/)
    expect(photoSizeProblem(64, 64)).toBeNull()
    expect(photoSizeProblem(4032, 3024)).toBeNull()
  })
})

describe('base64Of', () => {
  it('drops the data: prefix and keeps the payload', () => {
    expect(base64Of('data:image/jpeg;base64,/9j/4AAQSk==')).toBe('/9j/4AAQSk==')
    expect(base64Of('data:;base64,')).toBe('')
  })
})

describe('preparePhoto', () => {
  it('refuses a file that is not JPEG or PNG before reading it', async () => {
    const file = new File(['GIF89a'], 'a.gif', { type: 'image/gif' })
    await expect(preparePhoto(file)).rejects.toBeInstanceOf(ImageProblem)
    await expect(preparePhoto(file)).rejects.toThrow('Faqat JPEG yoki PNG rasm')
  })
})
