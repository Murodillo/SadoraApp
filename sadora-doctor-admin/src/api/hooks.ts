import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { query, request } from './client'
import type {
  CommunityComment,
  CommunityPost,
  CommunityTopic,
  CreatePostRequest,
  DoctorAccount,
  DoctorProfile,
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
}

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
