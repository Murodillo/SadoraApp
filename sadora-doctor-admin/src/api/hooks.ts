import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { QueryClient } from '@tanstack/react-query'
import { ApiFailure, query, request } from './client'
import type {
  Ack,
  CommunityComment,
  CommunityPost,
  CommunityTopic,
  Conversation,
  ConversationThread,
  CreatePostRequest,
  DirectMessage,
  DoctorAccount,
  DoctorProfile,
  DoctorSummary,
  ReportRequest,
  SendMessageRequest,
  UpdateDoctorProfileRequest,
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
}

/** How often the conversation list and an open thread ask the server again. */
export const CONVERSATIONS_POLL_MS = 10_000
export const THREAD_POLL_MS = 3_000

/** The questions list asks for the server's ceiling; a doctor works down one list, not pages. */
export const QUESTIONS_LIMIT = 100

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
 * Questions no doctor has answered yet, newest first. Polled, because this is the page a
 * doctor leaves open: a question asked while she reads another should appear by itself.
 */
export const useQuestions = (topic?: CommunityTopic) =>
  useQuery({
    queryKey: keys.questionsFor(topic),
    queryFn: () => request<CommunityPost[]>(`/v1/doctor/questions${query({ limit: QUESTIONS_LIMIT, topic })}`),
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

/** The doctor ends the consultation early. The answer is the thread, closed. */
export const useCloseConsultation = (conversationId: string) => {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => request<ConversationThread>(`${conversationPath(conversationId)}/close`, { method: 'POST', body: {} }),
    onSuccess: (thread) => {
      client.setQueryData(keys.thread(conversationId), thread)
      patchConversation(client, { ...thread.conversation, unread: 0 })
      void client.invalidateQueries({ queryKey: keys.conversations })
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
