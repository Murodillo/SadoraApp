import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { adminPhotoPath } from '../api/photos'
import { Avatar } from './Avatar'
import { initialsOf } from './initials'

describe('initialsOf', () => {
  it('takes the first letter of the first two words', () => {
    expect(initialsOf('Nilufar Karimova')).toBe('NK')
    expect(initialsOf('Malika')).toBe('M')
    expect(initialsOf('  Dilnoza   Rahimova  Anvarovna ')).toBe('DR')
  })

  it('skips a title and keeps Uzbek letters whole', () => {
    expect(initialsOf('Dr. Nilufar Karimova')).toBe('NK')
    expect(initialsOf("O'g'iloy G'aniyeva")).toBe('OG')
    expect(initialsOf('шахноза юсупова')).toBe('ШЮ')
  })

  it('never leaves the circle empty', () => {
    expect(initialsOf('')).toBe('?')
    expect(initialsOf(null)).toBe('?')
    expect(initialsOf('Dr.')).toBe('?')
  })
})

describe('adminPhotoPath', () => {
  it('accepts only a path on the panel API', () => {
    expect(adminPhotoPath('/v1/admin/doctors/d1/photo?v=1')).toBe('/v1/admin/doctors/d1/photo?v=1')
    expect(adminPhotoPath(null)).toBeNull()
    expect(adminPhotoPath('')).toBeNull()
    expect(adminPhotoPath('https://evil.example/v1/x')).toBeNull()
    expect(adminPhotoPath('//evil.example/v1/x')).toBeNull()
  })
})

describe('Avatar', () => {
  beforeEach(() => {
    Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:face'), revokeObjectURL: vi.fn() })
  })
  afterEach(() => vi.unstubAllGlobals())

  function wrap(children: ReactNode) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>
  }

  it('shows initials and fetches nothing without a photo', () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    render(wrap(<Avatar name="Nilufar Karimova" photoUrl={null} />))
    expect(screen.getByRole('img', { name: 'Nilufar Karimova' })).toHaveTextContent('NK')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('fetches the photo with the admin token and revokes it on unmount', async () => {
    sessionStorage.setItem('sadora.admin.token', 'tkn')
    const fetchMock = vi.fn().mockResolvedValue(new Response(new Uint8Array([1, 2, 3]), { status: 200, headers: { 'Content-Type': 'image/jpeg' } }))
    vi.stubGlobal('fetch', fetchMock)
    const { unmount } = render(wrap(<Avatar name="Nilufar Karimova" photoUrl="/v1/admin/doctors/d1/photo?v=7" />))

    const image = await screen.findByAltText('Nilufar Karimova')
    expect(image).toHaveAttribute('src', 'blob:face')
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe('/v1/admin/doctors/d1/photo?v=7')
    expect(init.headers).toMatchObject({ Authorization: 'Bearer tkn' })

    unmount()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:face')
  })

  it('falls back to initials when the photo cannot be fetched', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('{}', { status: 404 }))
    vi.stubGlobal('fetch', fetchMock)
    render(wrap(<Avatar name="Malika Sobirova" photoUrl="/v1/admin/doctors/d2/photo?v=1" />))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1))
    expect(screen.getByRole('img', { name: 'Malika Sobirova' })).toHaveTextContent('MS')
    expect(screen.queryByAltText('Malika Sobirova')).toBeNull()
  })
})
