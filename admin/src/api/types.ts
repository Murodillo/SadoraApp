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
  generatedAt: string
}

export interface SignUpPoint {
  date: string
  signUps: number
}
