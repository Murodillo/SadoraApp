/**
 * TypeScript mirrors of the Kotlin contract.
 *
 * The mobile apps get these types for free by compiling against `:contract`; the panel
 * is the one client that has to restate them, so they are kept in one file and named
 * after their Kotlin counterparts to make a drift obvious in review.
 */

export type Language = 'uz' | 'ru' | 'en'

export type LifeStage =
  | 'cycle'
  | 'trying_to_conceive'
  | 'pregnancy'
  | 'postpartum'
  | 'perimenopause'
  | 'menopause'

export type AccountStatus = 'active' | 'blocked' | 'deletion_pending'
export type SubscriptionTier = 'free' | 'premium'
export type SubscriptionSource = 'app_store' | 'google_play' | 'payme' | 'click' | 'manual'
export type AdminRole = 'OWNER' | 'ADMIN' | 'SUPPORT' | 'ANALYST'

export interface ApiError {
  code: string
  message: string
  details?: Record<string, string>
  requestId?: string
}

export interface Page<T> {
  items: T[]
  total: number
  limit: number
  offset: number
}

export interface AdminSession {
  accessToken: string
  expiresAt: string
  name: string
  email: string
  role: AdminRole
}

export interface AdminUserSummary {
  id: string
  name: string
  phone?: string
  email?: string
  language: Language
  lifeStage: LifeStage
  tier: SubscriptionTier
  status: AccountStatus
  registeredAt: string
  lastActiveAt?: string
}

export interface AdminSubscriptionHistoryItem {
  source: SubscriptionSource
  productId?: string
  startedAt: string
  expiresAt?: string
}

export interface AdminUserCard {
  general: AdminUserSummary
  subscription: {
    tier: SubscriptionTier
    source?: SubscriptionSource
    expiresAt?: string
    inGracePeriod: boolean
    history: AdminSubscriptionHistoryItem[]
  }
  technical: {
    timezone: string
    devices: {
      deviceId: string
      platform: string
      model?: string
      appVersion?: string
      lastSeenAt: string
    }[]
    featureUsage: { featureKey: string; usedToday: number; usedThisMonth: number }[]
  }
}

export interface FeatureDefinition {
  key: string
  description: string
  freeEnabled: boolean
  premiumEnabled: boolean
  freeDailyLimit?: number | null
  freeMonthlyLimit?: number | null
  premiumDailyLimit?: number | null
  premiumMonthlyLimit?: number | null
}

export interface FlagRule {
  id: string
  environment?: string | null
  country?: string | null
  language?: string | null
  lifeStage?: string | null
  platform?: string | null
  cohort?: string | null
  rolloutPercentage: number
  value: boolean
  priority: number
}

export interface AdminFlag {
  key: string
  description: string
  enabled: boolean
  defaultValue: boolean
  rules: FlagRule[]
}

export interface AuditEntry {
  id: string
  actorType: 'user' | 'admin' | 'system'
  actorId?: string
  actorLabel?: string
  action: string
  entityType?: string
  entityId?: string
  reason?: string
  metadata: Record<string, string>
  ip?: string
  createdAt: string
}

export interface AdminStats {
  totalUsers: number
  newToday: number
  newThisWeek: number
  activeToday: number
  premiumUsers: number
  blockedUsers: number
  deletionPending: number
  expiringWithinWeek: number
  byLifeStage: Record<string, number>
  byLanguage: Record<string, number>
  aiUsageToday: Record<string, number>
  referredByDoctor: number
  communityPostsToday: number
  communityOpenReports: number
  generatedAt: string
}

// ---- secret chat (moderation) ----
//
// Alias, text, counts — and no user id. The server never puts one on these types, so
// the moderation page cannot show who wrote a post even by mistake.

export type CommunityTopic = 'cycle' | 'pregnancy' | 'wellbeing' | 'body'
export type ReportReason = 'spam' | 'abuse' | 'misinformation' | 'personal_data' | 'other'

export interface ModerationPost {
  id: string
  alias: string
  tint: number
  topic: CommunityTopic
  body: string
  hidden: boolean
  hiddenReason?: string | null
  createdAt: string
  likeCount: number
  commentCount: number
  openReports: number
}

export interface ModerationComment {
  id: string
  postId: string
  alias: string
  tint: number
  body: string
  hidden: boolean
  hiddenReason?: string | null
  createdAt: string
  openReports: number
}

export interface ModerationReport {
  id: string
  postId?: string | null
  commentId?: string | null
  reason: ReportReason
  note?: string | null
  excerpt: string
  targetHidden: boolean
  createdAt: string
  resolvedAt?: string | null
  resolution?: string | null
}

export interface CommunityStats {
  postsTotal: number
  postsToday: number
  hiddenPosts: number
  openReports: number
}

// ---- notifications ----

export type NotificationCategory = 'med_reminder' | 'cycle' | 'daily_check_in' | 'water' | 'insight' | 'system'

export interface NotificationTemplate {
  key: string
  language: Language
  category: NotificationCategory
  title: string
  body: string
  active: boolean
}

export interface FrequencyCaps {
  maxPerDay: number
  maxPerWeek: number
}

// ---- wearables ----

export type HealthProvider =
  | 'apple_health'
  | 'health_connect'
  | 'oura'
  | 'garmin'
  | 'whoop'
  | 'fitbit'
  | 'samsung_health'
  | 'manual'

export type HealthMetric =
  | 'steps'
  | 'active_energy'
  | 'distance'
  | 'heart_rate'
  | 'resting_heart_rate'
  | 'hrv'
  | 'respiratory_rate'
  | 'body_temperature'
  | 'sleep_duration'
  | 'sleep_deep'
  | 'sleep_rem'
  | 'weight'

export interface ProviderHealth {
  provider: HealthProvider
  sampleCount: number
  lastSampleAt?: string | null
}

export interface MetricMapping {
  provider: HealthProvider
  providerMetric: string
  metric: HealthMetric
  providerUnit?: string | null
  scale: number
  active: boolean
}

export interface SignUpPoint {
  date: string
  signUps: number
}
