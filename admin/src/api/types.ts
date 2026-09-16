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

export interface AdminMe {
  id: string
  name: string
  email: string
  role: AdminRole
  totpEnabled: boolean
}

export interface TotpEnrolment {
  secret: string
  otpauthUri: string
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
  /** Accounts seen in the last 30 days — MAU as the product can honestly measure it. */
  activeThisMonth: number
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
  messageId?: string | null
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
  | 'sleep_light'
  | 'sleep_awake'
  | 'sleep_performance'
  | 'sleep_efficiency'
  | 'weight'
  | 'recovery'
  | 'strain'
  | 'spo2'
  | 'skin_temperature'

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

// ---------------------------------------------------------------- content

export type ArticleKind = 'article' | 'course' | 'video'

/**
 * One block of an article body. The shape mirrors `ArticleBlock` in the contract, and
 * the discriminator is `type` because that is kotlinx-serialization's default — a
 * rename on either side is a compile error there and a parse failure here, so it is
 * pinned in both.
 */
export type ArticleBlock =
  | { type: 'heading'; text: string }
  | { type: 'paragraph'; text: string }
  | { type: 'bullets'; items: string[] }
  | { type: 'note'; text: string }

export interface ArticleCategory {
  key: string
  label: string
  count: number
}

export interface AdminArticle {
  slug: string
  kind: ArticleKind
  categoryKey: string
  title: string
  excerpt: string
  readMinutes: number
  premium: boolean
  published: boolean
  reviewedBy?: string | null
  author?: string | null
  authorRole?: string | null
  disclaimer?: string | null
  blocks: ArticleBlock[]
  publishedAt?: string | null
  updatedAt?: string | null
}

export interface SaveArticleBody {
  kind: ArticleKind
  categoryKey: string
  title: string
  excerpt: string
  blocks: ArticleBlock[]
  premium: boolean
  reviewedBy?: string | null
  author?: string | null
  authorRole?: string | null
  disclaimer?: string | null
  readMinutes?: number | null
}

// ---------------------------------------------------------------- ai

export interface AiUsageDay {
  date: string
  calls: number
  modelCalls: number
  fallbacks: number
  promptTokens: number
  completionTokens: number
  costMicros: number
}

/**
 * What the AI cost. There is deliberately nothing here about what was asked or
 * answered — the server's log has no column for it.
 */
export interface AiUsageReport {
  days: number
  calls: number
  modelCalls: number
  fallbacks: number
  ruleCalls: number
  costMicros: number
  promptTokens: number
  completionTokens: number
  averageLatencyMs: number | null
  perDay: AiUsageDay[]
  failures: { code: string; count: number }[]
  modelConfigured: boolean
  modelEnabled: boolean
  model: string | null
}

// ---------------------------------------------------------------- billing

export type PaymentProvider = 'payme' | 'click' | 'app_store' | 'google_play'
export type PaymentState = 'pending' | 'paid' | 'cancelled' | 'failed'

export interface BillingPlan {
  id: string
  title: string
  period: 'month' | 'year'
  priceMinor: number
  currency: string
  monthlyEquivalentMinor?: number | null
  trialDays: number
  highlighted: boolean
  appStoreProductId?: string | null
  googlePlayProductId?: string | null
}

export interface AdminPayment {
  id: string
  userId: string
  planId: string
  provider: PaymentProvider
  amountMinor: number
  currency: string
  state: PaymentState
  externalId?: string | null
  paidAt?: string | null
  createdAt: string
}

export interface BillingSummary {
  days: number
  paidCount: number
  pendingCount: number
  failedCount: number
  revenueMinor: number
  byProvider: { provider: PaymentProvider; paidCount: number; revenueMinor: number }[]
  activeSubscriptions: number
}

// ---------------------------------------------------------------- Gul

/** What each action pays, as the rewards page edits it. */
export interface CoinRule {
  reason: string
  amount: number
  dailyCap: number | null
  enabled: boolean
  description: string
}

export interface RewardsOverview {
  coinsOutstanding: number
  coinsEarnedTotal: number
  coinsSpentTotal: number
  redemptionsIssued: number
  activeStreaks: number
  longestStreak: number
  referralsAccepted: number
}

export type ShopKind = 'premium' | 'vitamin' | 'device'
export type RedemptionStatus = 'issued' | 'used' | 'expired' | 'cancelled'

export interface AdminShopProduct {
  id: string
  slug: string
  kind: ShopKind
  title: string
  brand?: string | null
  description?: string | null
  emoji?: string | null
  priceUzs?: number | null
  discountPercent: number
  coinCost: number
  premiumDays?: number | null
  stock?: number | null
  active: boolean
  position: number
  redeemed: number
  updatedAt?: string | null
}

/** What the panel writes. The slug is set once at creation and never edited. */
export interface SaveShopProductBody {
  kind: ShopKind
  title: string
  brand?: string | null
  description?: string | null
  emoji?: string | null
  priceUzs?: number | null
  discountPercent: number
  coinCost: number
  premiumDays?: number | null
  stock?: number | null
  active: boolean
  position: number
}

export interface AdminRedemption {
  id: string
  userId: string
  userName: string
  productTitle: string
  kind: ShopKind
  code: string
  coinCost: number
  discountPercent: number
  status: RedemptionStatus
  createdAt: string
  usedAt?: string | null
}

export interface StreakStatus {
  current: number
  longest: number
  lastOpenOn?: string | null
  openedToday: boolean
  totalDays: number
}

export interface CoinBalance {
  balance: number
  earned: number
  spent: number
}

export interface CoinEntry {
  id: string
  amount: number
  reason: string
  title: string
  createdAt: string
}

export interface ReferralStatus {
  code: string
  link: string
  invited: number
  coinsEarned: number
  rewardPerJoin: number
  welcomeReward: number
}

/** The reward half of a user card. */
export interface AdminRewardsCard {
  streak: StreakStatus
  coins: CoinBalance
  referral?: ReferralStatus | null
  history: CoinEntry[]
}
