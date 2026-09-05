import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { query, request } from './client'
import type {
  AdminArticle,
  AdminFlag,
  AdminStats,
  AdminUserCard,
  AdminUserSummary,
  ArticleCategory,
  AuditEntry,
  CommunityStats,
  FeatureDefinition,
  FrequencyCaps,
  MetricMapping,
  ModerationComment,
  ModerationPost,
  ModerationReport,
  NotificationTemplate,
  Page,
  ProviderHealth,
  SaveArticleBody,
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

// ---------------------------------------------------------------- secret chat

export interface ModerationFilters {
  hidden?: boolean
  topic?: string
  reported?: boolean
  limit: number
  offset: number
}

export const useCommunityStats = () =>
  useQuery({
    queryKey: ['community', 'stats'],
    queryFn: () => request<CommunityStats>('/v1/admin/community/stats'),
    refetchInterval: 30_000,
  })

export const useModerationPosts = (filters: ModerationFilters) =>
  useQuery({
    queryKey: ['community', 'posts', filters],
    queryFn: () =>
      request<Page<ModerationPost>>(
        `/v1/admin/community/posts${query({
          hidden: filters.hidden === undefined ? undefined : String(filters.hidden),
          topic: filters.topic,
          reported: filters.reported ? 'true' : undefined,
          limit: filters.limit,
          offset: filters.offset,
        })}`,
      ),
    placeholderData: (previous) => previous,
  })

export const useModerationComments = (postId: string | null) =>
  useQuery({
    queryKey: ['community', 'comments', postId],
    queryFn: () => request<ModerationComment[]>(`/v1/admin/community/posts/${postId}/comments`),
    enabled: Boolean(postId),
  })

export const useModerationReports = (open: boolean, limit: number, offset: number) =>
  useQuery({
    queryKey: ['community', 'reports', open, limit, offset],
    queryFn: () =>
      request<Page<ModerationReport>>(`/v1/admin/community/reports${query({ open: String(open), limit, offset })}`),
    placeholderData: (previous) => previous,
  })

/** Every moderation write invalidates the whole community cache: a hide moves counts everywhere. */
function useCommunityMutation<T>(mutationFn: (input: T) => Promise<unknown>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['community'] })
      void client.invalidateQueries({ queryKey: ['stats'] })
    },
  })
}

export const useHidePost = () =>
  useCommunityMutation(({ id, hidden, reason }: { id: string; hidden: boolean; reason?: string }) =>
    request(`/v1/admin/community/posts/${id}/hide`, { method: 'POST', body: { hidden, reason } }),
  )

export const useHideComment = () =>
  useCommunityMutation(({ id, hidden, reason }: { id: string; hidden: boolean; reason?: string }) =>
    request(`/v1/admin/community/comments/${id}/hide`, { method: 'POST', body: { hidden, reason } }),
  )

export const useResolveReport = () =>
  useCommunityMutation(({ id, action, reason }: { id: string; action: 'dismiss' | 'hide'; reason?: string }) =>
    request(`/v1/admin/community/reports/${id}/resolve`, { method: 'POST', body: { action, reason } }),
  )

export const useRestrictAuthor = () =>
  useCommunityMutation(({ postId, reason, days }: { postId: string; reason: string; days?: number | null }) =>
    request(`/v1/admin/community/posts/${postId}/restrict-author`, { method: 'POST', body: { reason, days } }),
  )

// ---------------------------------------------------------------- notifications

export const useTemplates = () =>
  useQuery({
    queryKey: ['notifications', 'templates'],
    queryFn: () => request<NotificationTemplate[]>('/v1/admin/notifications/templates'),
  })

export const useSaveTemplate = () => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (template: NotificationTemplate) =>
      request('/v1/admin/notifications/templates', { method: 'PUT', body: template }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['notifications', 'templates'] }),
  })
}

export const useCaps = () =>
  useQuery({
    queryKey: ['notifications', 'caps'],
    queryFn: () => request<FrequencyCaps>('/v1/admin/notifications/caps'),
  })

export const useUpdateCaps = () => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (caps: FrequencyCaps) => request<FrequencyCaps>('/v1/admin/notifications/caps', { method: 'PUT', body: caps }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['notifications', 'caps'] }),
  })
}

// ---------------------------------------------------------------- wearables

export const useProviders = () =>
  useQuery({
    queryKey: ['wearables', 'providers'],
    queryFn: () => request<ProviderHealth[]>('/v1/admin/wearables/providers'),
    refetchInterval: 60_000,
  })

export const useMappings = () =>
  useQuery({
    queryKey: ['wearables', 'mappings'],
    queryFn: () => request<MetricMapping[]>('/v1/admin/wearables/mappings'),
  })

export const useSaveMapping = () => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (mapping: MetricMapping) => request('/v1/admin/wearables/mappings', { method: 'PUT', body: mapping }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['wearables', 'mappings'] }),
  })
}

// ---------------------------------------------------------------- content

const useContentMutation = <T>(fn: (input: T) => Promise<unknown>) => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => client.invalidateQueries({ queryKey: ['content'] }),
  })
}

export const useArticles = () =>
  useQuery({
    queryKey: ['content', 'articles'],
    queryFn: () => request<AdminArticle[]>('/v1/admin/content/articles'),
  })

export const useArticleCategories = () =>
  useQuery({
    queryKey: ['content', 'categories'],
    queryFn: () => request<ArticleCategory[]>('/v1/admin/content/categories'),
  })

export const useCreateArticle = () =>
  useContentMutation(({ slug, article }: { slug: string; article: SaveArticleBody }) =>
    request('/v1/admin/content/articles', { method: 'POST', body: { slug, article } }),
  )

export const useUpdateArticle = () =>
  useContentMutation(({ slug, article }: { slug: string; article: SaveArticleBody }) =>
    request(`/v1/admin/content/articles/${slug}`, { method: 'PUT', body: article }),
  )

export const usePublishArticle = () =>
  useContentMutation(({ slug, published }: { slug: string; published: boolean }) =>
    request(`/v1/admin/content/articles/${slug}/published`, { method: 'PUT', body: { published } }),
  )

export const useDeleteArticle = () =>
  useContentMutation((slug: string) =>
    request(`/v1/admin/content/articles/${slug}`, { method: 'DELETE' }),
  )
