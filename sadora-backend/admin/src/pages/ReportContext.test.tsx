import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import type { ModerationReport, ReportContextView } from '../api/types'
import { ToastProvider } from '../components/toast'
import { IMAGE_PLACEHOLDER, PRIVACY_LINE, RECORD_LINE } from './messageReports'
import { ReportContextModal, RestrictSenderDialog } from './ReportContext'

const report: ModerationReport = {
  id: 'r1',
  messageId: 'm3',
  reason: 'abuse',
  excerpt: 'Yomon gap',
  targetHidden: false,
  createdAt: '2026-09-29T10:00:00Z',
}

const view: ReportContextView = {
  reportId: 'r1',
  consultation: true,
  reporter: 'Oydin',
  reported: 'Nodira Karimova ✓',
  reportedIsDoctor: true,
  messages: [
    { id: 'm1', fromReported: false, kind: 'text', body: 'Salom, doktor', createdAt: '2026-09-29T09:00:00Z', reported: false, hidden: false },
    { id: 'm2', fromReported: false, kind: 'record', body: '', createdAt: '2026-09-29T09:01:00Z', reported: false, hidden: false },
    { id: 'm3', fromReported: true, kind: 'image', body: '', createdAt: '2026-09-29T09:02:00Z', reported: true, hidden: false },
    { id: 'm4', fromReported: true, kind: 'text', body: 'Olib tashlangan', createdAt: '2026-09-29T09:03:00Z', reported: false, hidden: true },
  ],
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status })
}

/** Routes by path, and records every call so a test can assert what was sent. */
function stubServer(routes: Record<string, () => Response>) {
  const fetchMock = vi.fn((path: string) => {
    const route = routes[path]
    return Promise.resolve(route ? route() : json({ error: { code: 'not_found', message: 'yo‘q' } }, 404))
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function wrap(children: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>{children}</ToastProvider>
    </QueryClientProvider>,
  )
}

describe('ReportContextModal', () => {
  it('shows the thread as bubbles with the privacy line, labels and the reported line marked', async () => {
    stubServer({ '/v1/admin/community/reports/r1/context': () => json(view) })
    wrap(<ReportContextModal report={report} moderate={false} onClose={() => undefined} />)

    expect(screen.getByText(PRIVACY_LINE)).toBeInTheDocument()
    const thread = await screen.findByRole('list', { name: 'Suhbat parchasi' })
    const lines = within(thread).getAllByRole('listitem')
    expect(lines).toHaveLength(4)

    // The reporter writes on the right, the doctor — who was reported — on the left.
    expect(lines[0]).toHaveClass('from-reporter')
    expect(lines[0]).toHaveTextContent('Oydin')
    expect(lines[2]).toHaveClass('from-reported', 'doctor', 'target')
    expect(lines[2]).toHaveTextContent('Nodira Karimova ✓')
    expect(lines[2]).toHaveAttribute('aria-current', 'true')
    expect(lines[2]).toHaveTextContent('Shikoyat qilingan xabar')

    // A record is announced, never shown; a photo is a placeholder; a hidden line is greyed.
    expect(lines[1]).toHaveTextContent(RECORD_LINE)
    expect(lines[2]).toHaveTextContent(IMAGE_PLACEHOLDER)
    expect(lines[3]).toHaveClass('hidden')
    expect(lines[3]).toHaveTextContent('Yashirilgan')

    expect(screen.getAllByText('Konsultatsiya').length).toBeGreaterThan(0)
  })

  it('offers the photo only on the reported line, and fetches it on request', async () => {
    const fetchMock = stubServer({
      '/v1/admin/community/reports/r1/context': () => json(view),
      '/v1/admin/community/reports/r1/image': () => new Response(new Uint8Array([0xff, 0xd8, 0xff]), { status: 200, headers: { 'Content-Type': 'image/jpeg' } }),
    })
    // jsdom has no object URLs; each test file runs isolated, so this does not leak.
    Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:photo'), revokeObjectURL: vi.fn() })
    wrap(<ReportContextModal report={report} moderate={false} onClose={() => undefined} />)

    const open = await screen.findAllByRole('button', { name: 'Rasmni ochish' })
    expect(open).toHaveLength(1)
    // Opening the dialog reads the thread; the photo is not fetched until asked for.
    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual(['/v1/admin/community/reports/r1/context'])

    await userEvent.click(open[0]!)
    expect(await screen.findByRole('img', { name: 'Shikoyat qilingan rasm' })).toHaveAttribute('src', 'blob:photo')
    expect(fetchMock).toHaveBeenLastCalledWith('/v1/admin/community/reports/r1/image', expect.anything())
  })

  it('gives Support no moderation buttons', async () => {
    stubServer({ '/v1/admin/community/reports/r1/context': () => json(view) })
    wrap(<ReportContextModal report={report} moderate={false} onClose={() => undefined} />)
    await screen.findByRole('list', { name: 'Suhbat parchasi' })
    expect(screen.queryByRole('button', { name: 'Yashirish' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Rad etish' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Yuboruvchini cheklash' })).not.toBeInTheDocument()
  })

  it('hides the reported message and closes for a moderator', async () => {
    const fetchMock = stubServer({
      '/v1/admin/community/reports/r1/context': () => json(view),
      '/v1/admin/community/reports/r1/resolve': () => json({ ok: true }),
    })
    const onClose = vi.fn()
    wrap(<ReportContextModal report={report} moderate onClose={onClose} />)
    await screen.findByRole('list', { name: 'Suhbat parchasi' })

    await userEvent.click(screen.getByRole('button', { name: 'Yashirish' }))
    await waitFor(() => expect(onClose).toHaveBeenCalled())
    const [path, init] = fetchMock.mock.calls.at(-1) as unknown as [string, RequestInit]
    expect(path).toBe('/v1/admin/community/reports/r1/resolve')
    expect(JSON.parse(String(init.body))).toEqual({ action: 'hide', reason: 'Shikoyat: Haqorat' })
    expect(screen.getByRole('status')).toHaveTextContent('Xabar yashirildi')
  })

  it('says why when the thread cannot be read', async () => {
    stubServer({})
    wrap(<ReportContextModal report={report} moderate={false} onClose={() => undefined} />)
    expect(await screen.findByText('yo‘q')).toBeInTheDocument()
  })
})

describe('RestrictSenderDialog', () => {
  it('sends the reason and the chosen days, and cheksiz as no end', async () => {
    const fetchMock = stubServer({ '/v1/admin/community/reports/r1/restrict-sender': () => json({ ok: true }) })
    const onClose = vi.fn()
    wrap(<RestrictSenderDialog report={report} onClose={onClose} />)

    const submit = screen.getByRole('button', { name: 'Cheklash' })
    expect(submit).toBeDisabled()
    await userEvent.type(screen.getByLabelText('Sabab (majburiy)'), '  Haqorat  ')
    await userEvent.selectOptions(screen.getByLabelText('Muddat'), 'Cheksiz')
    await userEvent.click(submit)

    await waitFor(() => expect(onClose).toHaveBeenCalled())
    const [path, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(path).toBe('/v1/admin/community/reports/r1/restrict-sender')
    expect(init.method).toBe('POST')
    expect(JSON.parse(String(init.body))).toEqual({ reason: 'Haqorat', days: null })
    expect(screen.getByRole('status')).toHaveTextContent('Yuboruvchi muddatsiz cheklandi')
  })
})
