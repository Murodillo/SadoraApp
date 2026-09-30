/*
 * The wire types this panel reads and writes, mirrored from `sadora-backend/contract`
 * (Common.kt, Auth.kt, Profile.kt, Doctors.kt, Community.kt, Share.kt, DoctorWork.kt).
 * TypeScript cannot read the Kotlin, so these are written down by hand under the same
 * names; a field the server adds is simply ignored until it is added here. Instants arrive as ISO-8601 strings.
 */

// ---------------------------------------------------------------- common

export type Language = 'uz' | 'ru' | 'en'

export type Platform = 'ios' | 'android' | 'web'

/** Errors are always this shape, whatever went wrong. */
export interface ApiError {
  code: string
  message: string
  details?: Record<string, string>
  requestId?: string
}

/** Returned by endpoints whose only job is to succeed. */
export interface Ack {
  ok: boolean
}

// ---------------------------------------------------------------- auth

export interface DeviceInfo {
  deviceId: string
  platform: Platform
  osVersion?: string
  appVersion?: string
  model?: string
  pushToken?: string
  timezone?: string
  /** Which app this is, so a doctor's pushes ring the doctor app and not the women's one. */
  app?: ClientApp
}

export type ClientApp = 'client' | 'doctor'

export interface OtpRequest {
  phone: string
  language: Language
}

/** `devCode` is only present on a dev or stage server that exposes the code. */
export interface OtpChallenge {
  challengeId: string
  expiresAt: string
  resendAfterSeconds: number
  attemptsLeft: number
  devCode?: string | null
}

export interface OtpVerifyRequest {
  challengeId: string
  code: string
  device: DeviceInfo
}

export interface RefreshRequest {
  refreshToken: string
}

export interface LogoutRequest {
  refreshToken?: string
  allDevices?: boolean
}

export interface TokenPair {
  accessToken: string
  refreshToken: string
  accessExpiresAt: string
  refreshExpiresAt: string
  tokenType?: string
}

/** Only the fields of `UserProfile` the panel reads. */
export interface UserProfile {
  id: string
  phone?: string | null
  name: string
}

export interface AuthSession {
  tokens: TokenPair
  user: UserProfile
  /** The app's entitlement snapshot; the panel has no use for it. */
  entitlements: unknown
  isNewUser: boolean
}

// ---------------------------------------------------------------- doctors

export type DoctorSpecialty =
  | 'gynecologist'
  | 'obstetrician'
  | 'reproductologist'
  | 'endocrinologist'
  | 'mammologist'
  | 'psychologist'
  | 'nutritionist'
  | 'pediatrician'
  | 'general'
  | 'other'

export type DoctorStatus = 'none' | 'pending' | 'approved' | 'rejected' | 'suspended'

/** The byline of a post or comment a verified doctor wrote. */
export interface DoctorAuthor {
  id: string
  fullName: string
  specialty: DoctorSpecialty
}

/** The caller's own doctor account: the status, and what she sent. */
export interface DoctorAccount {
  status: DoctorStatus
  profileId?: string | null
  fullName?: string | null
  specialty?: DoctorSpecialty | null
  workplace?: string | null
  experienceYears?: number | null
  licenseNumber?: string | null
  bio?: string | null
  documentCount: number
  reviewNote?: string | null
  submittedAt?: string | null
  reviewedAt?: string | null
  /** Whether patients may open a consultation with her now; she switches it herself. Defaults to true. */
  acceptsConsultations?: boolean
}

/** What an approved doctor may change without a new review. Omitted leaves it as it is. */
export interface UpdateDoctorProfileRequest {
  workplace?: string
  bio?: string
  acceptsConsultations?: boolean
}

/** A verified doctor's public page. */
export interface DoctorProfile {
  id: string
  fullName: string
  specialty: DoctorSpecialty
  workplace: string
  experienceYears: number
  bio?: string | null
  verifiedSince: string
  postCount: number
  answerCount: number
  isMe?: boolean
  posts: CommunityPost[]
  canMessage?: boolean
  conversationId?: string | null
  /** Her price in tiyin; 0 is free. */
  priceMinor?: number
  rating?: number | null
  ratingCount?: number
  availability?: DoctorAvailability | null
  paymentProviders?: PaymentProvider[]
}

// ---------------------------------------------------------------- community

export type CommunityTopic = 'cycle' | 'pregnancy' | 'wellbeing' | 'body'

export type ReportReason = 'spam' | 'abuse' | 'misinformation' | 'personal_data' | 'other'

export interface ReportRequest {
  reason: ReportReason
  note?: string
}

export type CommunityBadge = 'newcomer' | 'early' | 'writer' | 'helper' | 'loved' | 'veteran'

export interface CommunityPost {
  id: string
  topic: CommunityTopic
  alias: string
  tint: number
  body: string
  createdAt: string
  likeCount: number
  commentCount: number
  liked?: boolean
  saved?: boolean
  isMine?: boolean
  badges?: CommunityBadge[]
  /** Set when a verified doctor wrote it; `alias` then holds her name. */
  doctor?: DoctorAuthor | null
  /** How many verified doctors have answered under it. */
  doctorAnswers: number
}

export interface CommunityComment {
  id: string
  postId: string
  alias: string
  tint: number
  body: string
  createdAt: string
  isMine?: boolean
  badges?: CommunityBadge[]
  /** A verified doctor's answer; the server lists these first. */
  doctor?: DoctorAuthor | null
}

export interface CreatePostRequest {
  topic: CommunityTopic
  body: string
}

export interface CreateCommentRequest {
  body: string
}

// ---------------------------------------------------------------- messages (Community.kt)

export type LifeStage = 'cycle' | 'trying_to_conceive' | 'pregnancy' | 'postpartum' | 'perimenopause' | 'menopause'

export type MessageKind = 'text' | 'image' | 'record'

/** Who a doctor is consulting: her real name, and what a doctor needs at a glance. */
export interface ConsultationPatient {
  name: string
  age?: number | null
  lifeStage: LifeStage
}

/** A consultation's window. `open` is computed by the server when the answer was made. */
export interface Consultation {
  openedAt: string
  expiresAt: string
  closedAt?: string | null
  open: boolean
  /** The current window's session. */
  sessionId?: string | null
  /** Her price when this window opened, in tiyin; 0 when it was free. */
  priceMinor?: number
  payment?: ConsultationPayment
  /** Her advice from the last window she closed with one. */
  summary?: string | null
  canRate?: boolean
  rating?: number | null
  /** She has answered in the current window. */
  answered?: boolean
}

/**
 * One row of the conversation list. In a consultation where she is the doctor, `alias`
 * holds the patient's real name and `patient` is set.
 */
export interface Conversation {
  id: string
  alias: string
  tint: number
  badges?: CommunityBadge[]
  lastMessage?: string | null
  lastMessageAt: string
  unread: number
  blocked?: boolean
  doctor?: DoctorAuthor | null
  patient?: ConsultationPatient | null
  consultation?: Consultation | null
  lastMessageKind?: MessageKind
  /** The last line is hers and the other side has read it. */
  lastMessageRead?: boolean
}

export interface MessageImage {
  width: number
  height: number
  mimeType: string
}

export interface DirectMessage {
  id: string
  /** The text; a photo's caption; "" for an attached record. */
  body: string
  createdAt: string
  isMine: boolean
  kind?: MessageKind
  image?: MessageImage | null
  /** Hers, and read by the other side. */
  read?: boolean
}

/** A thread opened: reading it marks it read. Messages are oldest first, the last 200. */
export interface ConversationThread {
  conversation: Conversation
  messages: DirectMessage[]
  otherTyping?: boolean
  otherReadAt?: string | null
}

export interface MessageImageUpload {
  imageBase64: string
  mimeType: 'image/jpeg' | 'image/png'
}

export interface SendMessageRequest {
  body: string
  image?: MessageImageUpload
  attachRecord?: boolean
}

// ---------------------------------------------------------------- the patient record (Share.kt)

/* Dates without a time (`LocalDate`) arrive as `YYYY-MM-DD`, times (`LocalTime`) as `HH:MM[:SS]`. */

export type CyclePhase = 'period' | 'follicular' | 'fertile' | 'luteal'
export type FlowLevel = 'spotting' | 'light' | 'medium' | 'heavy'
export type SymptomSeverity = 'mild' | 'moderate' | 'severe'
export type SymptomCategory = 'bleeding' | 'pain' | 'digestion' | 'skin' | 'mood' | 'sleep' | 'energy' | 'other'
export type FoodRelation = 'any' | 'before' | 'with' | 'after'
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

export interface SharedPerson {
  name: string
  age?: number | null
  birthDate?: string | null
  heightCm?: number | null
  weightKg?: number | null
  lifeStage: LifeStage
  goals?: string[]
  memberSince: string
}

export interface CyclePrediction {
  confidence: string
  reason: string
  nextPeriodStart?: string | null
  nextPeriodEnd?: string | null
}

export interface CycleHistory {
  cycles: { startedOn: string; endedOn: string; cycleLength: number; periodLength?: number | null }[]
  averageCycleLength?: number | null
  averagePeriodLength?: number | null
  shortestCycle?: number | null
  longestCycle?: number | null
  prediction: CyclePrediction
}

export interface SharedCycle {
  today: string
  cycleDay?: number | null
  phase?: CyclePhase | null
  lastPeriodStart?: string | null
  history: CycleHistory
  periods?: { id: string; startedOn: string; endedOn?: string | null; createdAt: string }[]
}

export interface SharedPregnancy {
  dueDate?: string | null
  week?: number | null
  childBirthDate?: string | null
  lessMovementDays?: string[]
}

export interface SharedSymptom {
  key: string
  label: string
  severity: SymptomSeverity
}

export interface SharedDay {
  date: string
  flow?: FlowLevel | null
  mood?: string | null
  energy?: number | null
  stress?: number | null
  symptoms?: SharedSymptom[]
  fetalMovement?: string | null
}

export interface SharedSymptomCount {
  key: string
  label: string
  category: SymptomCategory
  days: number
}

export interface SharedMind {
  windowDays: number
  daysLogged: number
  averageMood?: number | null
  averageEnergy?: number | null
  averageStress?: number | null
  journalEntries?: number
  practiceMinutes?: number
}

export interface SharedMedication {
  name: string
  dosage?: string | null
  unit?: string | null
  schedule: { kind?: string; times?: string[]; weekdays?: string[]; intervalDays?: number | null }
  foodRelation: FoodRelation
  startedOn: string
  endedOn?: string | null
  active: boolean
  takenCount?: number
  skippedCount?: number
  adherencePercent?: number | null
}

export interface Appointment {
  id: string
  title: string
  scheduledOn: string
  scheduledAt?: string | null
  place?: string | null
  note?: string | null
  completedAt?: string | null
  createdAt: string
}

export interface SharedNutrition {
  windowDays: number
  daysLogged: number
  averageKcal?: number | null
  averageWaterMl?: number | null
  goals: { calorieGoal?: number; waterGoalMl?: number }
}

export interface DailyMetric {
  metric: HealthMetric
  value: number
  unit: string
  sampleCount: number
  providers?: HealthProvider[]
}

export interface SharedWearable {
  providers: HealthProvider[]
  days: { date: string; metrics?: DailyMetric[] }[]
  averages?: DailyMetric[]
}

/** The patient record — the same document her QR share opens. Nothing in it is a verdict. */
export interface DoctorSummary {
  generatedAt: string
  language: Language
  person: SharedPerson
  cycle?: SharedCycle | null
  pregnancy?: SharedPregnancy | null
  days?: SharedDay[]
  symptomCounts?: SharedSymptomCount[]
  mind?: SharedMind | null
  medications?: SharedMedication[]
  appointments?: Appointment[]
  nutrition?: SharedNutrition | null
  wearable?: SharedWearable | null
}

// ---------------------------------------------------------------- the doctor's workplace (DoctorWork.kt)

export type PaymentProvider = 'payme' | 'click' | 'app_store' | 'google_play'

/** Where a consultation's money stands. */
export type ConsultationPayment = 'free' | 'pending' | 'paid' | 'refund_due' | 'refunded'

/** One weekday's hours in minutes from midnight, in her own time zone. 1 is Monday. */
export interface DoctorHours {
  weekday: number
  startMinute: number
  endMinute: number
}

export interface DoctorAvailability {
  onlineNow: boolean
  busy?: boolean
  nextAvailableAt?: string | null
  hours?: DoctorHours[]
  timezone?: string
}

export interface DoctorSettings {
  /** In tiyin; 0 keeps her consultations free. */
  priceMinor: number
  busy: boolean
  hours: DoctorHours[]
  timezone: string
  acceptsConsultations: boolean
  /** Sadora's share of a paid consultation. */
  commissionPercent: number
}

/** An omitted field is left as it is; `hours` replaces the whole week. */
export interface UpdateDoctorSettingsRequest {
  priceMinor?: number
  busy?: boolean
  hours?: DoctorHours[]
}

export interface QuickReply {
  id: string
  title: string
  body: string
  position: number
}

export interface SaveQuickReplyRequest {
  title: string
  body: string
  position?: number
}

/** Her note on a patient: hers alone. */
export interface PatientNote {
  body: string
  updatedAt?: string | null
}

export interface SavePatientNoteRequest {
  body: string
}

/** One 24-hour window of a consultation. */
export interface ConsultationSession {
  id: string
  openedAt?: string | null
  expiresAt?: string | null
  closedAt?: string | null
  /** `doctor`, `expired` or `refund`. */
  closedReason?: string | null
  priceMinor: number
  payment: ConsultationPayment
  firstReplyAt?: string | null
  summary?: string | null
  rating?: number | null
  review?: string | null
  /** The records the patient attached in this window, by message id. */
  recordMessageIds: string[]
}

/** Every window she has had with one patient, oldest first. */
export interface PatientHistory {
  patient?: ConsultationPatient | null
  sessions: ConsultationSession[]
}

export interface CloseConsultationRequest {
  summary?: string
}

export interface TopicCount {
  topic: CommunityTopic
  count: number
}

export interface DoctorStats {
  consultationsWeek: number
  consultationsMonth: number
  consultationsTotal: number
  openNow: number
  avgFirstReplyMinutes?: number | null
  unansweredTotal: number
  rating?: number | null
  ratingCount: number
  topTopics: TopicCount[]
  answersTotal: number
}

export interface EarningLine {
  sessionId: string
  patientName: string
  openedAt?: string | null
  priceMinor: number
  commissionMinor: number
  netMinor: number
  payment: ConsultationPayment
}

export interface DoctorPayoutView {
  id: string
  amountMinor: number
  note?: string | null
  paidAt: string
}

export interface DoctorEarnings {
  currency: string
  grossMinor: number
  commissionMinor: number
  netMinor: number
  paidOutMinor: number
  balanceMinor: number
  refundDueMinor: number
  lines: EarningLine[]
  payouts: DoctorPayoutView[]
}
