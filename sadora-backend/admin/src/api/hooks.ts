import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { query, request, requestBlob } from './client'
import { adminPhotoPath } from './photos'
import type {
  AdminAnalytics,
  AdminArticle,
  AdminRedemption,
  AdminRewardsCard,
  AdminShopProduct,
  CoinBalance,
  CoinRule,
  RedemptionStatus,
  RewardsOverview,
  SaveShopProductBody,
  AdminMe,
  AdminConsultationPage,
  AdminDoctorDetail,
  AdminDoctorQuality,
  AdminDoctorRow,
  CommissionView,
  ConsultationPayment,
  DoctorEarnings,
  DoctorPayoutView,
  EarningLine,
  DoctorCounts,
  DoctorReviewAction,
  DoctorStatus,
  AdminFlag,
  AdminPayment,
  AdminStats,
  AdminUserCard,
  AdminUserSummary,
  ArticleCategory,
  AuditEntry,
  BillingPlan,
  BillingSummary,
  AiUsageReport,
  CommunityStats,
  ReportContextView,
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
  TotpEnrolment,
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

/**
 * The analytics page's one request. Gated on the caller's role rather than on a 403,
 * so a Support operator opening the dashboard does not see a failed request for a card
 * she was never going to get.
 */
export const useAnalytics = (days: number, enabled = true) =>
  useQuery({
    queryKey: ['analytics', days],
    queryFn: () => request<AdminAnalytics>(`/v1/admin/stats/analytics${query({ days })}`),
    enabled,
    refetchInterval: 60_000,
    placeholderData: (previous) => previous,
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

/**
 * A post's thread for the moderation drawer, oldest first, read a page at a time.
 *
 * "Load more" rather than prev/next: the drawer reads like the thread does in the app,
 * and a moderator following a pile-on wants the earlier replies still on screen. Hiding a
 * comment invalidates ['community'], which refetches every page already loaded.
 */
export const useModerationComments = (postId: string | null, pageSize = 50) =>
  useInfiniteQuery({
    queryKey: ['community', 'comments', postId, pageSize],
    queryFn: ({ pageParam }) =>
      request<Page<ModerationComment>>(
        `/v1/admin/community/posts/${postId}/comments${query({ limit: pageSize, offset: pageParam })}`,
      ),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.offset + last.items.length < last.total ? last.offset + last.items.length : undefined),
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

export const reportContextPath = (reportId: string) => `/v1/admin/community/reports/${reportId}/context`
export const reportImagePath = (reportId: string) => `/v1/admin/community/reports/${reportId}/image`

/**
 * The lines around a reported private message. Every fetch is written to the audit log
 * on the server, so it runs only while the modal is open, is never refetched behind the
 * moderator's back, and is dropped from the cache as soon as the modal closes. It lives
 * outside the `['community']` root on purpose: a moderation write invalidates that root,
 * and must not quietly read the thread again.
 */
export const useReportContext = (reportId: string | null) =>
  useQuery({
    queryKey: ['report-context', reportId],
    queryFn: () => request<ReportContextView>(reportContextPath(reportId!)),
    enabled: Boolean(reportId),
    staleTime: Infinity,
    gcTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
  })

/**
 * The reported photo. Not a query: it is fetched only when the moderator presses the
 * button (that request is audited too), and the caller owns the blob's object URL.
 */
export const useReportImage = () =>
  useMutation({
    mutationFn: (reportId: string) => requestBlob(reportImagePath(reportId)),
  })

/** Restricts whoever sent the reported message; the panel never learns who that is. */
export const useRestrictSender = () =>
  useCommunityMutation(({ reportId, reason, days }: { reportId: string; reason: string; days?: number | null }) =>
    request(`/v1/admin/community/reports/${reportId}/restrict-sender`, { method: 'POST', body: { reason, days } }),
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

export const useAiUsage = (days: number) =>
  useQuery({
    queryKey: ['ai', 'usage', days],
    queryFn: () => request<AiUsageReport>(`/v1/admin/ai/usage${query({ days: String(days) })}`),
    refetchInterval: 60_000,
  })

export const useBillingSummary = (days: number) =>
  useQuery({
    queryKey: ['billing', 'summary', days],
    queryFn: () => request<BillingSummary>(`/v1/admin/billing/summary${query({ days: String(days) })}`),
    refetchInterval: 60_000,
  })

export const useBillingPlans = () =>
  useQuery({
    queryKey: ['billing', 'plans'],
    queryFn: () => request<BillingPlan[]>('/v1/admin/billing/plans'),
  })

export const usePayments = (state: string | undefined, limit: number, offset: number) =>
  useQuery({
    queryKey: ['billing', 'payments', state ?? 'all', limit, offset],
    queryFn: () => request<Page<AdminPayment>>(`/v1/admin/billing/payments${query({ state, limit, offset })}`),
    placeholderData: (previous) => previous,
  })

// ---------------------------------------------------------------- the operator's own account

export const useAdminMe = () =>
  useQuery({
    queryKey: ['me'],
    queryFn: () => request<AdminMe>('/v1/admin/me'),
  })

export const useStartTotpEnrolment = () =>
  useMutation({
    mutationFn: () => request<TotpEnrolment>('/v1/admin/me/totp/start', { method: 'POST' }),
  })

/** Both of these change whether the account is protected, so the card is refetched. */
const useTotpMutation = <T,>(mutationFn: (input: T) => Promise<unknown>) => {
  const client = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => client.invalidateQueries({ queryKey: ['me'] }),
  })
}

export const useConfirmTotp = () =>
  useTotpMutation((code: string) =>
    request('/v1/admin/me/totp/confirm', { method: 'POST', body: { code } }),
  )

export const useDisableTotp = () =>
  useTotpMutation((input: { password: string; code: string }) =>
    request('/v1/admin/me/totp/disable', { method: 'POST', body: input }),
  )

// ---------------------------------------------------------------- Gul

// Both are Owner/Admin/Analyst on the server; Support reaches the page for the
// redemption table only, so its session never asks for what it would be refused.
export const useRewardsOverview = (enabled = true) =>
  useQuery({
    queryKey: ['rewards-overview'],
    queryFn: () => request<RewardsOverview>('/v1/admin/rewards/overview'),
    refetchInterval: 60_000,
    enabled,
  })

export const useCoinRules = (enabled = true) =>
  useQuery({
    queryKey: ['coin-rules'],
    queryFn: () => request<CoinRule[]>('/v1/admin/rewards/rules'),
    enabled,
  })

export const useSaveCoinRule = () => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ reason, amount, dailyCap, enabled }: CoinRule) =>
      request<CoinRule>(`/v1/admin/rewards/rules/${reason}`, {
        method: 'PUT',
        body: { amount, dailyCap: dailyCap ?? null, enabled },
      }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['coin-rules'] })
      void client.invalidateQueries({ queryKey: ['rewards-overview'] })
    },
  })
}

export const useShopProducts = () =>
  useQuery({
    queryKey: ['shop-products'],
    queryFn: () => request<AdminShopProduct[]>('/v1/admin/shop/products'),
  })

export const useCreateShopProduct = () => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ slug, product }: { slug: string; product: SaveShopProductBody }) =>
      request<AdminShopProduct>('/v1/admin/shop/products', { method: 'POST', body: { slug, product } }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['shop-products'] }),
  })
}

export const useUpdateShopProduct = () => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, product }: { id: string; product: SaveShopProductBody }) =>
      request<AdminShopProduct>(`/v1/admin/shop/products/${id}`, { method: 'PUT', body: product }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['shop-products'] }),
  })
}

export const useDeleteShopProduct = () => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => request(`/v1/admin/shop/products/${id}`, { method: 'DELETE' }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['shop-products'] }),
  })
}

export const useRedemptions = (limit: number, offset: number) =>
  useQuery({
    queryKey: ['redemptions', limit, offset],
    queryFn: () => request<Page<AdminRedemption>>(`/v1/admin/shop/redemptions${query({ limit, offset })}`),
    placeholderData: (previous) => previous,
  })

export const useUpdateRedemption = () => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: RedemptionStatus }) =>
      request(`/v1/admin/shop/redemptions/${id}`, { method: 'PUT', body: { status } }),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['redemptions'] }),
  })
}

/** The reward half of one user's card. */
export const useUserRewards = (id: string) =>
  useQuery({
    queryKey: ['user-rewards', id],
    queryFn: () => request<AdminRewardsCard>(`/v1/admin/rewards/users/${id}`),
  })

export const useAdjustCoins = (id: string) => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ amount, note }: { amount: number; note: string }) =>
      request<CoinBalance>(`/v1/admin/rewards/users/${id}/adjust`, { method: 'POST', body: { amount, note } }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['user-rewards', id] })
      void client.invalidateQueries({ queryKey: ['rewards-overview'] })
    },
  })
}

// ---------------------------------------------------------------- doctors

export const useDoctorCounts = () =>
  useQuery({
    queryKey: ['doctors', 'counts'],
    queryFn: () => request<DoctorCounts>('/v1/admin/doctors/counts'),
    refetchInterval: 60_000,
  })

export const useDoctors = (status: DoctorStatus, limit: number, offset: number) =>
  useQuery({
    queryKey: ['doctors', 'list', status, limit, offset],
    queryFn: () => request<Page<AdminDoctorRow>>(`/v1/admin/doctors${query({ status, limit, offset })}`),
    placeholderData: (previous) => previous,
  })

export const useDoctor = (id: string | null) =>
  useQuery({
    queryKey: ['doctors', 'detail', id],
    queryFn: () => request<AdminDoctorDetail>(`/v1/admin/doctors/${id}`),
    enabled: Boolean(id),
  })

/**
 * A scan's bytes. Kept under its own root so a review, which invalidates `['doctors']`,
 * does not download every image again — a document never changes once uploaded.
 */
export const useDoctorDocument = (doctorId: string, documentId: string) =>
  useQuery({
    queryKey: ['doctor-document', doctorId, documentId],
    queryFn: ({ signal }) => requestBlob(`/v1/admin/doctors/${doctorId}/documents/${documentId}`, { signal }),
    staleTime: Infinity,
    gcTime: 5 * 60_000,
  })

/** A review moves the doctor between queues, so the list, counts and card all refetch. */
export const useReviewDoctor = () => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, action, note }: { id: string; action: DoctorReviewAction; note?: string }) =>
      request<{ ok: boolean }>(`/v1/admin/doctors/${id}/review`, { method: 'POST', body: { action, note } }),
    onSuccess: (_, { id }) => {
      void client.invalidateQueries({ queryKey: ['doctors', 'list'] })
      void client.invalidateQueries({ queryKey: ['doctors', 'counts'] })
      void client.invalidateQueries({ queryKey: ['doctors', 'detail', id] })
    },
  })
}

/**
 * A doctor's photo, by the versioned path the server hands out. A new photo is a new
 * `?v=`, so the bytes under one key never go stale; the image lives under its own root
 * so a review, which invalidates `['doctors']`, does not download every face again.
 * A path that is not the panel's own API is never fetched — the bearer header goes with it.
 */
export const useDoctorPhoto = (photoUrl: string | null | undefined) => {
  const path = adminPhotoPath(photoUrl)
  return useQuery({
    queryKey: ['doctor-photo', path],
    queryFn: ({ signal }) => requestBlob(path as string, { signal }),
    enabled: path !== null,
    staleTime: Infinity,
    gcTime: 10 * 60_000,
    // A missing photo is a 404, and the initials already stand in for it.
    retry: false,
  })
}

/**
 * Takes a doctor's photo down (Owner and Admin). She is told by push, with the reason;
 * the list, her card and the quality table all show her initials after.
 */
export const useRemoveDoctorPhoto = () => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, reason }: { id: string; reason?: string }) =>
      request<{ ok: boolean }>(`/v1/admin/doctors/${id}/photo`, { method: 'DELETE', body: { reason } }),
    onSuccess: (_, { id }) => {
      void client.invalidateQueries({ queryKey: ['doctors', 'list'] })
      void client.invalidateQueries({ queryKey: ['doctors', 'detail', id] })
      void client.invalidateQueries({ queryKey: ['doctor-quality'] })
    },
  })
}

// ---------------------------------------------------------------- paid consultations

export const useAdminConsultations = (payment: ConsultationPayment, limit: number, offset: number) =>
  useQuery({
    queryKey: ['consultations', 'list', payment, limit, offset],
    queryFn: () =>
      request<AdminConsultationPage>(`/v1/admin/consultations${query({ payment, limit, offset })}`),
    placeholderData: (previous) => previous,
    refetchInterval: 60_000,
  })

/**
 * Marks a refund done after the operator has returned the money in the provider's
 * cabinet. The row changes tab and every doctor's balance may move with it.
 */
export const useMarkConsultationRefunded = () => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      request<{ ok: boolean }>(`/v1/admin/consultations/${id}/refunded`, { method: 'POST' }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['consultations'] })
      void client.invalidateQueries({ queryKey: ['doctor-quality'] })
      void client.invalidateQueries({ queryKey: ['doctor-earnings'] })
    },
  })
}

export const useCommission = () =>
  useQuery({
    queryKey: ['commission'],
    queryFn: () => request<CommissionView>('/v1/admin/settings/commission'),
  })

export const useSetCommission = () => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (percent: number) =>
      request<CommissionView>('/v1/admin/settings/commission', { method: 'PUT', body: { percent } }),
    onSuccess: (view) => {
      client.setQueryData(['commission'], view)
      void client.invalidateQueries({ queryKey: ['consultations'] })
    },
  })
}

/**
 * A page of the quality table, the busiest this month first. Outside the `['doctors']`
 * root so a review does not refetch every doctor's numbers.
 */
export const useDoctorQuality = (limit: number, offset: number, enabled = true) =>
  useQuery({
    queryKey: ['doctor-quality', limit, offset],
    queryFn: () => request<Page<AdminDoctorQuality>>(`/v1/admin/doctors/quality${query({ limit, offset })}`),
    enabled,
    refetchInterval: 60_000,
    placeholderData: (previous) => previous,
  })

/**
 * Owner and Admin only on the server; the card asks only when the caller may read it.
 * The totals cover everything; the lines and payouts are their first pages.
 */
export const useDoctorEarnings = (id: string, enabled = true) =>
  useQuery({
    queryKey: ['doctor-earnings', id],
    queryFn: () => request<DoctorEarnings>(`/v1/admin/doctors/${id}/earnings`),
    enabled,
  })

/** How many earnings lines or payouts one "more" brings. */
export const EARNINGS_PAGE = 50

/**
 * Her consultations or payouts past the first page the card already has, read on by
 * offset from [from]. Off until asked for, so opening a card costs one request. Under the
 * card's key: a payout recorded refreshes these with the totals.
 */
export function useMoreDoctorEarnings<T extends EarningLine | DoctorPayoutView>(
  id: string,
  list: 'lines' | 'payouts',
  from: number,
  enabled: boolean,
) {
  return useInfiniteQuery({
    queryKey: ['doctor-earnings', id, list, from],
    queryFn: ({ pageParam }) =>
      request<Page<T>>(`/v1/admin/doctors/${id}/earnings/${list}${query({ limit: EARNINGS_PAGE, offset: pageParam })}`),
    initialPageParam: from,
    getNextPageParam: (last) => (last.offset + last.items.length < last.total ? last.offset + last.items.length : undefined),
    enabled,
  })
}

export const useAddDoctorPayout = () => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, amountMinor, note }: { id: string; amountMinor: number; note?: string }) =>
      request<DoctorEarnings>(`/v1/admin/doctors/${id}/payouts`, {
        method: 'POST',
        body: { amountMinor, ...(note ? { note } : {}) },
      }),
    onSuccess: (earnings, { id }) => {
      client.setQueryData(['doctor-earnings', id], earnings)
      void client.invalidateQueries({ queryKey: ['doctor-earnings', id] })
      void client.invalidateQueries({ queryKey: ['doctor-quality'] })
    },
  })
}
