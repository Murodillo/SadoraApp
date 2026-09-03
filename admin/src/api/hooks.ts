import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { query, request } from './client'
import type {
  AdminFlag,
  AdminStats,
  AdminUserCard,
  AdminUserSummary,
  AuditEntry,
  FeatureDefinition,
  Page,
  SignUpPoint,
} from './types'

export interface UserFilters {
  q?: string
  status?: string
  language?: string
  lifeStage?: string
  limit?: number
  offset?: number
}

export const useStats = () =>
  useQuery({
    queryKey: ['stats'],
    queryFn: () => request<AdminStats>('/v1/admin/stats'),
    refetchInterval: 30_000,
  })

export const useSignUps = (days = 14) =>
  useQuery({
    queryKey: ['signups', days],
    queryFn: () => request<SignUpPoint[]>(`/v1/admin/stats/signups${query({ days })}`),
  })

export const useRecentEvents = (limit = 12) =>
  useQuery({
    queryKey: ['events', limit],
    queryFn: () => request<AuditEntry[]>(`/v1/admin/stats/events${query({ limit })}`),
    refetchInterval: 30_000,
  })

export const useUsers = (filters: UserFilters) =>
  useQuery({
    queryKey: ['users', filters],
    queryFn: () => request<Page<AdminUserSummary>>(`/v1/admin/users${query({ ...filters })}`),
    placeholderData: (previous) => previous,
  })

export const useUserCard = (id: string | undefined) =>
  useQuery({
    queryKey: ['user', id],
    queryFn: () => request<AdminUserCard>(`/v1/admin/users/${id}`),
    enabled: Boolean(id),
  })

export const useFeatures = () =>
  useQuery({
    queryKey: ['features'],
    queryFn: () => request<FeatureDefinition[]>('/v1/admin/features'),
  })

export const useFlags = () =>
  useQuery({
    queryKey: ['flags'],
    queryFn: () => request<AdminFlag[]>('/v1/admin/flags'),
  })

export const useAudit = (params: { action?: string; entityId?: string; limit: number; offset: number }) =>
  useQuery({
    queryKey: ['audit', params],
    queryFn: () => request<Page<AuditEntry>>(`/v1/admin/audit${query({ ...params })}`),
    placeholderData: (previous) => previous,
  })

/**
 * Mutations invalidate rather than patch the cache. A limit change can move a user's
 * tier and a block revokes her sessions, so refetching is the honest thing to show.
 */
export const useGrantPremium = (userId: string) => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (body: { reason: string; expiresAt?: string | null }) =>
      request(`/v1/admin/users/${userId}/premium`, { method: 'POST', body }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['user', userId] })
      void client.invalidateQueries({ queryKey: ['users'] })
      void client.invalidateQueries({ queryKey: ['stats'] })
    },
  })
}

export const useSetBlocked = (userId: string) => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (body: { blocked: boolean; reason: string }) =>
      request(`/v1/admin/users/${userId}/block`, { method: 'POST', body }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['user', userId] })
      void client.invalidateQueries({ queryKey: ['users'] })
      void client.invalidateQueries({ queryKey: ['stats'] })
    },
  })
}

export const useUpdateFeature = () => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ key, ...body }: FeatureDefinition) =>
      request(`/v1/admin/features/${key}`, {
        method: 'PUT',
        body: {
          freeEnabled: body.freeEnabled,
          premiumEnabled: body.premiumEnabled,
          freeDailyLimit: body.freeDailyLimit ?? null,
          freeMonthlyLimit: body.freeMonthlyLimit ?? null,
          premiumDailyLimit: body.premiumDailyLimit ?? null,
          premiumMonthlyLimit: body.premiumMonthlyLimit ?? null,
        },
      }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['features'] }),
  })
}

export const useUpdateFlag = () => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ key, enabled, defaultValue }: { key: string; enabled: boolean; defaultValue: boolean }) =>
      request(`/v1/admin/flags/${key}`, { method: 'PUT', body: { enabled, defaultValue } }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['flags'] }),
  })
}

export const useAddFlagRule = () => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ key, ...body }: { key: string; environment?: string | null; rolloutPercentage: number; value: boolean; priority: number }) =>
      request(`/v1/admin/flags/${key}/rules`, { method: 'POST', body }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['flags'] }),
  })
}

export const useRemoveFlagRule = () => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ key, ruleId }: { key: string; ruleId: string }) =>
      request(`/v1/admin/flags/${key}/rules/${ruleId}`, { method: 'DELETE' }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['flags'] }),
  })
}
