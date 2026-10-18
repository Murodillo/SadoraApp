import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiError, mockApi } from '../test/http'
import { signIn } from '../test/render'
import { formatFileSize, ReportCard } from './ReportDocument'

const PATH = '/v1/community/conversations/c1/messages/m1/document'
const file = { name: 'sadora-hisobot-6oy.pdf', mimeType: 'application/pdf', sizeBytes: 245_760 }

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('formatFileSize', () => {
  it('reads in KB under a megabyte and MB with one decimal over it', () => {
    expect(formatFileSize(10)).toBe('1 KB')
    expect(formatFileSize(240 * 1024)).toBe('240 KB')
    expect(formatFileSize(1024 * 1024)).toBe('1 MB')
    expect(formatFileSize(1_468_006)).toBe('1,4 MB')
  })
})

describe('ReportCard', () => {
  it('shows the file and opens it with the token in a new tab', async () => {
    signIn()
    const api = mockApi({ [`GET ${PATH}`]: new Response(new Uint8Array([0x25, 0x50, 0x44, 0x46]), { status: 200, headers: { 'Content-Type': 'application/pdf' } }) })
    const tab = { location: { href: '' }, close: vi.fn() }
    vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window)
    URL.createObjectURL = vi.fn(() => 'blob:report')
    URL.revokeObjectURL = vi.fn()

    render(<ReportCard conversationId="c1" messageId="m1" document={file} />)
    expect(screen.getByText('Hisobot (PDF)')).toBeInTheDocument()
    expect(screen.getByText('sadora-hisobot-6oy.pdf · 240 KB')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'PDF ochish' }))
    await vi.waitFor(() => expect(tab.location.href).toBe('blob:report'))
    expect(api.callsTo('GET', PATH)[0]?.headers.Authorization).toMatch(/^Bearer /)
  })

  it('says so when the file cannot be had, and closes the empty tab', async () => {
    signIn()
    mockApi({ [`GET ${PATH}`]: apiError(404, 'not_found', 'Topilmadi') })
    const tab = { location: { href: '' }, close: vi.fn() }
    vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window)

    render(<ReportCard conversationId="c1" messageId="m1" document={file} />)
    await userEvent.click(screen.getByRole('button', { name: 'PDF ochish' }))
    expect(await screen.findByRole('alert')).toHaveTextContent("Faylni ochib bo'lmadi")
    expect(tab.close).toHaveBeenCalled()
  })
})
