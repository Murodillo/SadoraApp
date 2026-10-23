import type { AdminRole } from '../api/types'

/* The "Loyiha" section's wire shapes, as the server's admin/pm/PmModels.kt sends them. */

export type PmStatus = 'backlog' | 'todo' | 'in_progress' | 'review' | 'done'
export type PmTaskType = 'bug' | 'feature' | 'task'
export type PmPriority = 'critical' | 'high' | 'medium' | 'low'

export interface PmPerson {
  id: string
  name: string
  email: string
  role: AdminRole
}

export interface PmTaskSummary {
  id: string
  key: string
  title: string
  type: PmTaskType
  status: PmStatus
  priority: PmPriority
  components: string[]
  labels: string[]
  assigneeId?: string
  epicId?: string
  releaseId?: string
  parentKey?: string
  dueDate?: string
  position: number
  archived: boolean
  subtasks: number
  subtasksDone: number
  comments: number
  createdAt: string
  updatedAt: string
}

export interface PmComment {
  id: string
  authorId?: string
  body: string
  createdAt: string
}

export interface PmTaskEvent {
  kind: 'created' | 'status' | 'assignee' | 'archived' | 'restored' | string
  actorId?: string
  from?: string
  to?: string
  at: string
}

export interface PmCommit {
  sha: string
  branch: string
  message: string
  author: string
  url: string
  committedAt: string
}

export interface PmTaskDetail extends Omit<PmTaskSummary, 'subtasks' | 'subtasksDone' | 'comments'> {
  description: string
  reporterId?: string
  doneAt?: string
  subtasks: PmTaskSummary[]
  comments: PmComment[]
  events: PmTaskEvent[]
  commits: PmCommit[]
  /** Absent from a server older than the panel. */
  mentionedIn?: { id: string; title: string; snippet: string }[]
}

export interface CreatePmTask {
  title: string
  description?: string
  type?: PmTaskType
  status?: PmStatus
  priority?: PmPriority
  components?: string[]
  labels?: string[]
  assigneeId?: string
  epicId?: string
  releaseId?: string
  parentKey?: string
  dueDate?: string
}

export type PmClearable = 'assigneeId' | 'epicId' | 'releaseId' | 'parentKey' | 'dueDate'

export interface UpdatePmTask extends Partial<Omit<CreatePmTask, 'title'>> {
  title?: string
  position?: number
  clear?: PmClearable[]
}

export interface PmEpic {
  id: string
  title: string
  description: string
  status: 'open' | 'done'
  archived: boolean
  tasks: number
  done: number
  createdAt: string
  updatedAt: string
}

export interface PmRelease {
  id: string
  name: string
  description: string
  targetDate?: string
  status: 'planned' | 'released'
  releasedAt?: string
  archived: boolean
  tasks: number
  done: number
  createdAt: string
  updatedAt: string
}

export interface PmMeta {
  me: PmPerson
  people: PmPerson[]
  epics: PmEpic[]
  releases: PmRelease[]
  components: string[]
  commits: { enabled: boolean; repo: string; lastSuccessAt?: string; lastError?: string }
}

export interface PmDocNode {
  id: string
  parentId?: string
  title: string
  position: number
  version: number
  archived: boolean
  updatedBy?: string
  updatedAt: string
}

export interface PmDoc extends PmDocNode {
  body: string
  createdBy?: string
  createdAt: string
}

export interface PmDocVersionInfo {
  version: number
  title: string
  editedBy?: string
  createdAt: string
  size: number
}

export interface PmDocVersion {
  version: number
  title: string
  body: string
  editedBy?: string
  createdAt: string
}

export interface PmImageRef {
  id: string
  contentType: string
  size: number
}

export interface PmSearchResults {
  tasks: PmTaskSummary[]
  docs: { id: string; title: string; snippet: string }[]
}

export interface PmToken {
  id: string
  name: string
  adminId: string
  createdAt: string
  lastUsedAt?: string
  revoked: boolean
}

export interface PmTaskFilters {
  status?: string
  assignee?: string
  epic?: string
  release?: string
  component?: string
  label?: string
  type?: string
  priority?: string
  q?: string
  archived?: boolean
}
