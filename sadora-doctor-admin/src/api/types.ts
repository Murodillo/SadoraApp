/*
 * The wire types this panel reads and writes, mirrored from `sadora-backend/contract`
 * (Common.kt, Auth.kt, Profile.kt, Doctors.kt, Community.kt). TypeScript cannot read the
 * Kotlin, so these are written down by hand under the same names; a field the server
 * adds is simply ignored until it is added here. Instants arrive as ISO-8601 strings.
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
}

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
}

/** What an approved doctor may change without a new review. Omitted leaves it as it is. */
export interface UpdateDoctorProfileRequest {
  workplace?: string
  bio?: string
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
}

// ---------------------------------------------------------------- community

export type CommunityTopic = 'cycle' | 'pregnancy' | 'wellbeing' | 'body'

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
