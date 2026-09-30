import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { useDoctor, useReportContext, useReportImage, useRestrictSender } from './hooks'

function json(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200 })
}

function withClient() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>
  return { client, wrapper }
}

describe('private message moderation hooks', () => {
  it('read the context only for an open report, and keep it out of the community cache', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json({ reportId: 'r1', messages: [] }))
    vi.stubGlobal('fetch', fetchMock)
    const { client, wrapper } = withClient()

    const idle = renderHook(() => useReportContext(null), { wrapper })
    expect(idle.result.current.fetchStatus).toBe('idle')
    expect(fetchMock).not.toHaveBeenCalled()

    const { result } = renderHook(() => useReportContext('r1'), { wrapper })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/v1/admin/community/reports/r1/context')

    // A moderation write invalidates ['community']; that must not read the thread again.
    await act(() => client.invalidateQueries({ queryKey: ['community'] }))
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('fetch the reported photo as a blob on demand', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(new Uint8Array([1, 2, 3]), { status: 200, headers: { 'Content-Type': 'image/png' } }))
    vi.stubGlobal('fetch', fetchMock)
    const { wrapper } = withClient()

    const { result } = renderHook(() => useReportImage(), { wrapper })
    expect(fetchMock).not.toHaveBeenCalled()
    await act(() => result.current.mutateAsync('r1'))
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/v1/admin/community/reports/r1/image')
    await waitFor(() => expect(result.current.data?.size).toBe(3))
  })

  it('restrict the sender through the report and refresh the queue and counts', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json({ ok: true }))
    vi.stubGlobal('fetch', fetchMock)
    const { client, wrapper } = withClient()
    const invalidate = vi.spyOn(client, 'invalidateQueries')

    const { result } = renderHook(() => useRestrictSender(), { wrapper })
    await act(() => result.current.mutateAsync({ reportId: 'r1', reason: 'Spam', days: 7 }))

    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe('/v1/admin/community/reports/r1/restrict-sender')
    expect(init.method).toBe('POST')
    expect(JSON.parse(String(init.body))).toEqual({ reason: 'Spam', days: 7 })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['community'] })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['stats'] })
  })
})

describe('useDoctor', () => {
  it('passes the consultation counts through as the server sent them', async () => {
    const detail = {
      id: 'd1',
      acceptsConsultations: false,
      consultations: { total: 4, open: 1, messagesFromDoctor: 9, messagesFromPatients: 12, lastMessageAt: '2026-09-29T09:00:00Z' },
    }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(detail)))
    const { wrapper } = withClient()

    const { result } = renderHook(() => useDoctor('d1'), { wrapper })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.acceptsConsultations).toBe(false)
    expect(result.current.data?.consultations).toEqual(detail.consultations)
  })
})
