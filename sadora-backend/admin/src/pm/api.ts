import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { query, request, requestBlob } from '../api/client'
import type {
  CreatePmTask,
  PmComment,
  PmDoc,
  PmDocNode,
  PmDocVersion,
  PmDocVersionInfo,
  PmEpic,
  PmImageRef,
  PmMeta,
  PmRelease,
  PmSearchResults,
  PmTaskDetail,
  PmTaskFilters,
  PmTaskSummary,
  PmToken,
  UpdatePmTask,
} from './types'

const BASE = '/v1/admin/pm'

/** Every query of the section starts with 'pm', so one invalidation refreshes the board, lists and counts. */
function useRefresh() {
  const client = useQueryClient()
  return () => client.invalidateQueries({ queryKey: ['pm'] })
}

export const usePmMeta = () =>
  useQuery({ queryKey: ['pm', 'meta'], queryFn: () => request<PmMeta>(`${BASE}/meta`), staleTime: 30_000 })

export const usePmTasks = (filters: PmTaskFilters) =>
  useQuery({
    queryKey: ['pm', 'tasks', filters],
    queryFn: () =>
      request<PmTaskSummary[]>(
        `${BASE}/tasks${query({ ...filters, archived: filters.archived ? 'true' : undefined })}`,
      ),
    placeholderData: (previous) => previous,
  })

export const usePmTask = (key: string | undefined) =>
  useQuery({
    queryKey: ['pm', 'task', key],
    queryFn: () => request<PmTaskDetail>(`${BASE}/tasks/${encodeURIComponent(key!)}`),
    enabled: Boolean(key),
  })

export const useCreatePmTask = () => {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: (body: CreatePmTask) => request<PmTaskDetail>(`${BASE}/tasks`, { method: 'POST', body }),
    onSuccess: refresh,
  })
}

export const useUpdatePmTask = () => {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: ({ key, body }: { key: string; body: UpdatePmTask }) =>
      request<PmTaskDetail>(`${BASE}/tasks/${encodeURIComponent(key)}`, { method: 'PATCH', body }),
    onSuccess: refresh,
  })
}

export const useArchivePmTask = () => {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: ({ key, archived }: { key: string; archived: boolean }) =>
      request<PmTaskDetail>(`${BASE}/tasks/${encodeURIComponent(key)}/${archived ? 'archive' : 'restore'}`, { method: 'POST', body: {} }),
    onSuccess: refresh,
  })
}

export const useDeletePmTask = () => {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: (key: string) => request(`${BASE}/tasks/${encodeURIComponent(key)}`, { method: 'DELETE' }),
    onSuccess: refresh,
  })
}

export const useAddPmComment = () => {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: ({ key, body }: { key: string; body: string }) =>
      request<PmComment>(`${BASE}/tasks/${encodeURIComponent(key)}/comments`, { method: 'POST', body: { body } }),
    onSuccess: refresh,
  })
}

export const useDeletePmComment = () => {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: ({ key, id }: { key: string; id: string }) =>
      request(`${BASE}/tasks/${encodeURIComponent(key)}/comments/${id}`, { method: 'DELETE' }),
    onSuccess: refresh,
  })
}

// --- epics and releases ------------------------------------------------------

export const usePmEpics = (archived = false) =>
  useQuery({ queryKey: ['pm', 'epics', archived], queryFn: () => request<PmEpic[]>(`${BASE}/epics${query({ archived: archived ? 'true' : undefined })}`) })

export const usePmReleases = (archived = false) =>
  useQuery({
    queryKey: ['pm', 'releases', archived],
    queryFn: () => request<PmRelease[]>(`${BASE}/releases${query({ archived: archived ? 'true' : undefined })}`),
  })

export interface SaveEpic {
  title: string
  description: string
  status: PmEpic['status']
}

export interface SaveRelease {
  name: string
  description: string
  targetDate?: string
  status: PmRelease['status']
}

export const useSavePmEpic = () => {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: ({ id, body }: { id?: string; body: SaveEpic }) =>
      request<PmEpic>(id ? `${BASE}/epics/${id}` : `${BASE}/epics`, { method: id ? 'PUT' : 'POST', body }),
    onSuccess: refresh,
  })
}

export const useSavePmRelease = () => {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: ({ id, body }: { id?: string; body: SaveRelease }) =>
      request<PmRelease>(id ? `${BASE}/releases/${id}` : `${BASE}/releases`, { method: id ? 'PUT' : 'POST', body }),
    onSuccess: refresh,
  })
}

export const useArchivePmGroup = () => {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: ({ kind, id, archived }: { kind: 'epics' | 'releases'; id: string; archived: boolean }) =>
      request(`${BASE}/${kind}/${id}/${archived ? 'archive' : 'restore'}`, { method: 'POST', body: {} }),
    onSuccess: refresh,
  })
}

// --- docs ----------------------------------------------------------------------

export const usePmDocTree = (archived = false) =>
  useQuery({ queryKey: ['pm', 'docs', archived], queryFn: () => request<PmDocNode[]>(`${BASE}/docs${query({ archived: archived ? 'true' : undefined })}`) })

export const usePmDoc = (id: string | undefined) =>
  useQuery({ queryKey: ['pm', 'doc', id], queryFn: () => request<PmDoc>(`${BASE}/docs/${id}`), enabled: Boolean(id) })

export const usePmDocVersions = (id: string | undefined, enabled: boolean) =>
  useQuery({
    queryKey: ['pm', 'doc', id, 'versions'],
    queryFn: () => request<PmDocVersionInfo[]>(`${BASE}/docs/${id}/versions`),
    enabled: Boolean(id) && enabled,
  })

export const fetchPmDocVersion = (id: string, version: number) => request<PmDocVersion>(`${BASE}/docs/${id}/versions/${version}`)

export const useCreatePmDoc = () => {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: (body: { title: string; body?: string; parentId?: string }) => request<PmDoc>(`${BASE}/docs`, { method: 'POST', body }),
    onSuccess: refresh,
  })
}

export const useSavePmDoc = () => {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: { title: string; body: string; baseVersion: number; parentId?: string } }) =>
      request<PmDoc>(`${BASE}/docs/${id}`, { method: 'PUT', body }),
    onSuccess: refresh,
  })
}

export const useArchivePmDoc = () => {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: ({ id, archived }: { id: string; archived: boolean }) =>
      request<PmDoc>(`${BASE}/docs/${id}/${archived ? 'archive' : 'restore'}`, { method: 'POST', body: {} }),
    onSuccess: refresh,
  })
}

export const useDeletePmDoc = () => {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: (id: string) => request(`${BASE}/docs/${id}`, { method: 'DELETE' }),
    onSuccess: refresh,
  })
}

export const uploadPmImage = (file: Blob) => request<PmImageRef>(`${BASE}/images`, { method: 'POST', bytes: file })

// --- search, tokens, backup ---------------------------------------------------

export const usePmSearch = (q: string, enabled: boolean) =>
  useQuery({
    queryKey: ['pm', 'search', q],
    queryFn: () => request<PmSearchResults>(`${BASE}/search${query({ q })}`),
    enabled: enabled && q.trim().length >= 2,
    placeholderData: (previous) => previous,
    staleTime: 10_000,
  })

export const usePmTokens = () => useQuery({ queryKey: ['pm', 'tokens'], queryFn: () => request<PmToken[]>(`${BASE}/tokens`) })

export const useCreatePmToken = () => {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: (name: string) => request<{ token: string; view: PmToken }>(`${BASE}/tokens`, { method: 'POST', body: { name } }),
    onSuccess: refresh,
  })
}

export const useRevokePmToken = () => {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: (id: string) => request(`${BASE}/tokens/${id}`, { method: 'DELETE' }),
    onSuccess: refresh,
  })
}

export const downloadPmBackup = () => requestBlob(`${BASE}/backup`)
