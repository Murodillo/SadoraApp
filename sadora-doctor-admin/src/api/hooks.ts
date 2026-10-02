import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { QueryClient } from '@tanstack/react-query'
import { ApiFailure, query, request } from './client'
import type {
  Ack,
  CloseConsultationRequest,
  CommunityComment,
  CommunityPost,
  CommunityTopic,
  Conversation,
  ConversationThread,
  CreatePostRequest,
  DirectMessage,
  DoctorAccount,
  DoctorEarnings,
  DoctorPayoutView,
  DoctorProfile,
  DoctorSettings,
  DoctorStats,
  DoctorSummary,
  EarningLine,
  Page,
  PatientHistory,
  PatientNote,
  QuickReply,
  ReportRequest,
  SaveQuickReplyRequest,
  SendMessageRequest,
  UpdateDoctorProfileRequest,
  UpdateDoctorSettingsRequest,
} from './types'

/** Every query key in one place, so a write invalidates exactly what it changed. */
export const keys = {
  account: ['doctor', 'me'] as const,
  questions: ['doctor', 'questions'] as const,
  questionsFor: (topic: CommunityTopic | undefined) => ['doctor', 'questions', topic ?? 'all'] as const,
  comments: (postId: string) => ['community', 'comments', postId] as const,
  profile: (profileId: string) => ['doctors', profileId] as const,
  profiles: ['doctors'] as const,
  conversations: ['community', 'conversations', 'patients'] as const,
  thread: (conversationId: string) => ['community', 'thread', conversationId] as const,
  record: (conversationId: string, messageId: string, open: boolean) =>
    ['community', 'record', conversationId, messageId, open] as const,
  settings: ['doctor', 'settings'] as const,
  stats: ['doctor', 'stats'] as const,
  earnings: ['doctor', 'earnings'] as const,
  quickReplies: ['doctor', 'quick-replies'] as const,
  note: (conversationId: string) => ['doctor', 'patients', conversationId, 'note'] as const,
  history: (conversationId: string) => ['doctor', 'patients', conversationId, 'history'] as const,
}

/** How often the conversation list and an open thread ask the server again. */
export const CONVERSATIONS_POLL_MS = 10_000
export const THREAD_POLL_MS = 3_000

/** The questions list is read this many at a time; she asks for more at its end. */
export const QUESTIONS_PAGE = 50

/** The pages read so far as one list, in order; a row met twice — offsets shift as rows come and go — is kept once. */
export function uniqueRows<T>(pages: readonly (readonly T[])[] | undefined, keyOf: (item: T) => string): T[] {
  const seen = new Set<string>()
  const rows: T[] = []
  for (const page of pages ?? []) {
    for (const item of page) {
      const key = keyOf(item)
      if (!seen.has(key)) {
        seen.add(key)
        rows.push(item)
      }
    }
  }
  return rows
}

/** Her own doctor account — the status that decides what the panel shows at all. */
export const useDoctorAccount = () =>
  useQuery({
    queryKey: keys.account,
    queryFn: () => request<DoctorAccount>('/v1/doctor/me'),
  })

export const useUpdateDoctorProfile = () => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (body: UpdateDoctorProfileRequest) => request<DoctorAccount>('/v1/doctor/me', { method: 'PUT', body }),
    onSuccess: (account) => {
      // The answer is the new account; writing it in keeps the header and the form in
      // step without a second round trip.
      client.setQueryData(keys.account, account)
      void client.invalidateQueries({ queryKey: keys.profiles })
    },
  })
}

/**
 * Questions no doctor has answered yet, newest first, a page at a time. Polled, because
 * this is the page a doctor leaves open: a question asked while she reads another should
 * appear by itself.
 *
 * The server answers a plain list; a page shorter than asked for is the last. The next
 * offset counts what the server gave, page by page, and a refetch works it out again from
 * the fresh pages — so a question answered in between moves the rest up rather than
 * pushing one past the next page. Read the pages through [uniqueRows].
 */
export const useQuestions = (topic?: CommunityTopic) =>
  useInfiniteQuery({
    queryKey: keys.questionsFor(topic),
    queryFn: ({ pageParam }) =>
      request<CommunityPost[]>(
        `/v1/doctor/questions${query({ limit: QUESTIONS_PAGE, offset: pageParam || undefined, topic })}`,
      ),
    initialPageParam: 0,
    getNextPageParam: (last, _pages, lastOffset) => (last.length < QUESTIONS_PAGE ? undefined : lastOffset + last.length),
    refetchInterval: 60_000,
  })

export const useComments = (postId: string | null) =>
  useQuery({
    queryKey: keys.comments(postId ?? ''),
    queryFn: () => request<CommunityComment[]>(`/v1/community/posts/${encodeURIComponent(postId ?? '')}/comments`),
    enabled: Boolean(postId),
  })

/**
 * Her answer under a question. Afterwards the question leaves the work list (it has a
 * doctor's answer now), the thread shows the answer, and her answer count moves.
 */
export const useAnswer = () => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ postId, body }: { postId: string; body: string }) =>
      request<CommunityComment>(`/v1/community/posts/${encodeURIComponent(postId)}/comments`, {
        method: 'POST',
        body: { body },
      }),
    onSuccess: (_, { postId }) => {
      void client.invalidateQueries({ queryKey: keys.questions })
      void client.invalidateQueries({ queryKey: keys.comments(postId) })
      void client.invalidateQueries({ queryKey: keys.profiles })
    },
  })
}

/** Her public page as readers see it: the counts and her own posts. */
export const useDoctorProfile = (profileId: string | null | undefined) =>
  useQuery({
    queryKey: keys.profile(profileId ?? ''),
    queryFn: () => request<DoctorProfile>(`/v1/doctors/${encodeURIComponent(profileId ?? '')}`),
    enabled: Boolean(profileId),
  })

/** How many of her older posts one "more" brings. */
export const PROFILE_POSTS_PAGE = 20

/**
 * Her posts past the few [useDoctorProfile] carries, read on by offset from [from]. Off
 * until she asks for more. Under her profile's key, so a new post or answer that
 * refreshes the page refreshes these with it.
 */
export const useMoreDoctorPosts = (profileId: string | null | undefined, from: number, enabled: boolean) =>
  useInfiniteQuery({
    queryKey: [...keys.profile(profileId ?? ''), 'posts', from],
    queryFn: ({ pageParam }) =>
      request<Page<CommunityPost>>(
        `/v1/doctors/${encodeURIComponent(profileId ?? '')}/posts${query({ limit: PROFILE_POSTS_PAGE, offset: pageParam })}`,
      ),
    initialPageParam: from,
    getNextPageParam: (last) => (last.offset + last.items.length < last.total ? last.offset + last.items.length : undefined),
    enabled: enabled && Boolean(profileId),
  })

export const useCreatePost = () => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (body: CreatePostRequest) => request<CommunityPost>('/v1/community/posts', { method: 'POST', body }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: keys.profiles })
    },
  })
}

// ---------------------------------------------------------------- messages

const conversationPath = (conversationId: string) =>
  `/v1/community/conversations/${encodeURIComponent(conversationId)}`

/**
 * Her consultations: the conversations where she is the doctor, newest line first.
 * Polled, like the questions, because this page is left open — and faster, because a
 * patient waiting on an answer is waiting now.
 */
export const useConversations = () =>
  useQuery({
    queryKey: keys.conversations,
    queryFn: () => request<Conversation[]>(`/v1/community/conversations${query({ scope: 'patients' })}`),
    refetchInterval: CONVERSATIONS_POLL_MS,
  })

/**
 * One thread, polled every few seconds while it is open. Reading it marks it read on the
 * server, so the list's unread count for it is cleared here at once rather than on the
 * list's next poll — and the list row takes the thread's fresher consultation state.
 */
export const useThread = (conversationId: string | null) => {
  const client = useQueryClient()
  return useQuery({
    queryKey: keys.thread(conversationId ?? ''),
    queryFn: async () => {
      const thread = await request<ConversationThread>(conversationPath(conversationId ?? ''))
      patchConversation(client, { ...thread.conversation, unread: 0 })
      return thread
    },
    enabled: Boolean(conversationId),
    refetchInterval: THREAD_POLL_MS,
  })
}

/** Writes a conversation over its row in the list, when the list is loaded. */
function patchConversation(client: QueryClient, conversation: Conversation) {
  client.setQueryData<Conversation[]>(keys.conversations, (list) =>
    list?.map((item) => (item.id === conversation.id ? { ...item, ...conversation } : item)),
  )
}

/** A text, or a photo with an optional caption. The sent line is put in the thread at once. */
export const useSendMessage = (conversationId: string) => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (body: SendMessageRequest) =>
      request<DirectMessage>(`${conversationPath(conversationId)}/messages`, { method: 'POST', body }),
    onSuccess: (message) => {
      client.setQueryData<ConversationThread>(keys.thread(conversationId), (thread) =>
        thread && !thread.messages.some((item) => item.id === message.id)
          ? { ...thread, messages: [...thread.messages, message] }
          : thread,
      )
      void client.invalidateQueries({ queryKey: keys.thread(conversationId) })
      void client.invalidateQueries({ queryKey: keys.conversations })
    },
  })
}

/** "yozmoqda…" for the other side. Best effort: a lost ping only shortens the dots. */
export function sendTyping(conversationId: string): Promise<void> {
  return request<Ack>(`${conversationPath(conversationId)}/typing`, { method: 'POST', body: {} }).then(
    () => undefined,
    () => undefined,
  )
}

/**
 * The doctor ends the consultation early, with her advice for the patient if she writes
 * one. On a window that is already over the same call only adds the advice, when none
 * was written. The answer is the thread, closed.
 */
export const useCloseConsultation = (conversationId: string) => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (summary?: string) => {
      const body: CloseConsultationRequest = summary?.trim() ? { summary: summary.trim() } : {}
      return request<ConversationThread>(`${conversationPath(conversationId)}/close`, { method: 'POST', body })
    },
    onSuccess: (thread) => {
      client.setQueryData(keys.thread(conversationId), thread)
      patchConversation(client, { ...thread.conversation, unread: 0 })
      void client.invalidateQueries({ queryKey: keys.conversations })
      void client.invalidateQueries({ queryKey: keys.history(conversationId) })
      void client.invalidateQueries({ queryKey: keys.stats })
    },
  })
}

/** A report on the patient's latest message; a moderator reads it with a few lines around it. */
export const useReportConversation = (conversationId: string) =>
  useMutation({
    mutationFn: (body: ReportRequest) => request<Ack>(`${conversationPath(conversationId)}/report`, { method: 'POST', body }),
  })

/** The record read for a record message: the document, or the door closed with the consultation. */
export type RecordResult = { kind: 'record'; summary: DoctorSummary } | { kind: 'closed'; message: string }

/**
 * The record a patient attached, assembled by the server now. A doctor may read it only
 * while the consultation is open; the 403 after that is an answer, not a failure — it
 * is returned as one, so it neither shows as an error nor sends the gate to re-read her
 * account the way any other 403 does. Keyed on `open`, so closing re-asks at once.
 */
export const usePatientRecord = (conversationId: string | null, messageId: string | null, open: boolean) =>
  useQuery({
    queryKey: keys.record(conversationId ?? '', messageId ?? '', open),
    queryFn: async (): Promise<RecordResult> => {
      try {
        const summary = await request<DoctorSummary>(
          `${conversationPath(conversationId ?? '')}/messages/${encodeURIComponent(messageId ?? '')}/record${query({ lang: 'uz' })}`,
        )
        return { kind: 'record', summary }
      } catch (error) {
        if (error instanceof ApiFailure && error.status === 403) return { kind: 'closed', message: error.message }
        throw error
      }
    },
    enabled: Boolean(conversationId && messageId),
  })

// ---------------------------------------------------------------- her workplace

/** Her price, hours and "busy" switch, with Sadora's commission beside them. */
export const useDoctorSettings = () =>
  useQuery({
    queryKey: keys.settings,
    queryFn: () => request<DoctorSettings>('/v1/doctor/settings'),
  })

export const useUpdateDoctorSettings = () => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (body: UpdateDoctorSettingsRequest) =>
      request<DoctorSettings>('/v1/doctor/settings', { method: 'PUT', body }),
    onSuccess: (settings) => {
      client.setQueryData(keys.settings, settings)
      void client.invalidateQueries({ queryKey: keys.profiles })
    },
  })
}

/** Her numbers; asked again every minute, since the page may stay open through a shift. */
export const useDoctorStats = () =>
  useQuery({
    queryKey: keys.stats,
    queryFn: () => request<DoctorStats>('/v1/doctor/stats'),
    refetchInterval: 60_000,
  })

/** The totals over everything, and the first page of her consultations and of her payouts. */
export const useDoctorEarnings = () =>
  useQuery({
    queryKey: keys.earnings,
    queryFn: () => request<DoctorEarnings>('/v1/doctor/earnings'),
  })

/** How many earnings lines or payouts one "more" brings. */
export const EARNINGS_PAGE = 50

/**
 * Her consultations or payouts past the first page [useDoctorEarnings] already has, read
 * on by offset from [from]. Off until she asks for more, so the page costs one request.
 * Under the earnings key: whatever refreshes the totals refreshes these with them.
 */
export function useMoreEarnings<T extends EarningLine | DoctorPayoutView>(
  list: 'lines' | 'payouts',
  from: number,
  enabled: boolean,
) {
  return useInfiniteQuery({
    queryKey: [...keys.earnings, list, from],
    queryFn: ({ pageParam }) =>
      request<Page<T>>(`/v1/doctor/earnings/${list}${query({ limit: EARNINGS_PAGE, offset: pageParam })}`),
    initialPageParam: from,
    getNextPageParam: (last) => (last.offset + last.items.length < last.total ? last.offset + last.items.length : undefined),
    enabled,
  })
}

/** Her ready answers, in her order: the page that keeps them and the composer read the same list. */
export const useQuickReplies = () =>
  useQuery({
    queryKey: keys.quickReplies,
    queryFn: () => request<QuickReply[]>('/v1/doctor/quick-replies'),
    staleTime: 60_000,
  })

export const useSaveQuickReply = () => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id?: string; body: SaveQuickReplyRequest }) =>
      id
        ? request<QuickReply>(`/v1/doctor/quick-replies/${encodeURIComponent(id)}`, { method: 'PUT', body })
        : request<QuickReply>('/v1/doctor/quick-replies', { method: 'POST', body }),
    onSuccess: (saved) => {
      client.setQueryData<QuickReply[]>(keys.quickReplies, (list) =>
        list ? (list.some((item) => item.id === saved.id) ? list.map((item) => (item.id === saved.id ? saved : item)) : [...list, saved]) : list,
      )
      void client.invalidateQueries({ queryKey: keys.quickReplies })
    },
  })
}

export const useDeleteQuickReply = () => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => request<Ack>(`/v1/doctor/quick-replies/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    onSuccess: (_, id) => {
      client.setQueryData<QuickReply[]>(keys.quickReplies, (list) => list?.filter((item) => item.id !== id))
      void client.invalidateQueries({ queryKey: keys.quickReplies })
    },
  })
}

const patientPath = (conversationId: string) => `/v1/doctor/patients/${encodeURIComponent(conversationId)}`

/** Her private note on the patient in this conversation. */
export const usePatientNote = (conversationId: string, enabled = true) =>
  useQuery({
    queryKey: keys.note(conversationId),
    queryFn: () => request<PatientNote>(`${patientPath(conversationId)}/note`),
    staleTime: Infinity,
    enabled,
  })

/** An empty body deletes the note, which is what the server does with it. */
export const useSavePatientNote = (conversationId: string) => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (body: string) =>
      request<PatientNote>(`${patientPath(conversationId)}/note`, { method: 'PUT', body: { body } }),
    onSuccess: (note) => client.setQueryData(keys.note(conversationId), note),
  })
}

/** Every window she has had with this patient, oldest first. */
export const usePatientHistory = (conversationId: string, enabled = true) =>
  useQuery({
    queryKey: keys.history(conversationId),
    queryFn: () => request<PatientHistory>(`${patientPath(conversationId)}/history`),
    refetchInterval: 60_000,
    enabled,
  })
