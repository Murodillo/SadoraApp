import { useRef } from 'react'
import { useComments } from '../api/hooks'
import type { CommunityComment } from '../api/types'
import { specialtyLabel } from './labels'
import { Avatar, Empty, ErrorNotice, formatAgo, formatDateTime, Loading, VerifiedMark } from './ui'

/**
 * The comments under a post, as the app shows them: a doctor's answer leads and carries
 * her name, specialty and check mark; everyone else is an alias. The server already puts
 * doctors' answers first, so the order is kept as it came.
 *
 * A comment that turns up after the thread was first drawn — her own answer, a moment
 * after she sent it — arrives with a short glow, so she sees where it landed.
 */
export function CommentThread({ postId, emptyText }: { postId: string; emptyText?: string }) {
  const comments = useComments(postId)
  const firstSeen = useRef<ReadonlySet<string> | null>(null)
  if (comments.data && firstSeen.current === null) {
    firstSeen.current = new Set(comments.data.map((comment) => comment.id))
  }

  if (comments.isPending) return <Loading rows={2} height={48} />
  if (comments.isError) return <ErrorNotice error={comments.error} onRetry={() => void comments.refetch()} />
  if (!comments.data.length) return <Empty>{emptyText ?? "Hali izoh yo'q."}</Empty>

  return (
    <ol className="thread" aria-label="Izohlar">
      {comments.data.map((comment) => (
        <Comment key={comment.id} comment={comment} arrived={!firstSeen.current?.has(comment.id)} />
      ))}
    </ol>
  )
}

function Comment({ comment, arrived }: { comment: CommunityComment; arrived: boolean }) {
  const doctor = comment.doctor
  const classes = ['comment', doctor && 'doctor', comment.isMine && 'mine', arrived && 'arrived'].filter(Boolean).join(' ')
  return (
    <li className={classes}>
      <Avatar name={doctor?.fullName ?? comment.alias} tint={comment.tint} doctor={Boolean(doctor)} />
      <div className="comment-main">
        <div className="comment-head">
          <b>{doctor?.fullName ?? comment.alias}</b>
          {doctor && (
            <>
              <VerifiedMark />
              <span className="faint">{specialtyLabel(doctor.specialty)}</span>
              <span className="badge ok">Shifokor javobi</span>
            </>
          )}
          {comment.isMine && <span className="badge free">Siz</span>}
          <span className="faint comment-time" title={formatDateTime(comment.createdAt)}>
            {formatAgo(comment.createdAt)}
          </span>
        </div>
        <p className="comment-body">{comment.body}</p>
      </div>
    </li>
  )
}
