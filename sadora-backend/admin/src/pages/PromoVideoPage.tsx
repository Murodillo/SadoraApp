import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { API_BASE } from '../api/client'
import { usePromoVideo, useRemovePromoVideo } from '../api/hooks'
import { promoVideoProblem, uploadPromoVideo } from '../api/promoVideo'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/toast'
import { Card, ConfirmDialog, Empty, ErrorNotice, formatDateTime, Loading, Spinner } from '../components/ui'
import { formatBytes } from './doctorReview'

/**
 * The video the public page plays — sadora.app/video. One at a time: uploading another
 * replaces it. The file goes up in pieces, so the bar is real progress and a dropped
 * connection costs one piece, not the upload.
 */
export function PromoVideoPage() {
  const { can } = useAuth()
  const { notify } = useToast()
  const client = useQueryClient()
  const editable = can(['OWNER', 'ADMIN'])
  const promo = usePromoVideo()
  const remove = useRemovePromoVideo()

  const picker = useRef<HTMLInputElement>(null)
  const running = useRef<AbortController | null>(null)
  const [file, setFile] = useState<File | null>(null)
  const [sent, setSent] = useState<number | null>(null)
  const [failure, setFailure] = useState<unknown>(null)
  const [removing, setRemoving] = useState(false)

  // Leaving the page stops the upload; the server drops what arrived at the next one.
  useEffect(() => () => running.current?.abort(), [])

  if (promo.isLoading) return <Loading rows={5} />
  if (promo.error || !promo.data) return <ErrorNotice error={promo.error} />

  const { video, pageUrl, maxBytes, chunkBytes } = promo.data
  const problem = file ? promoVideoProblem(file, maxBytes) : null
  const uploading = sent !== null
  const percent = file && uploading ? Math.round((sent / file.size) * 100) : 0

  function choose(next: File | null) {
    setFile(next)
    setFailure(null)
  }

  async function upload() {
    if (!file || problem) return
    const controller = new AbortController()
    running.current = controller
    setFailure(null)
    setSent(0)
    try {
      const view = await uploadPromoVideo(file, chunkBytes, setSent, controller.signal)
      client.setQueryData(['promo-video'], view)
      notify(video ? 'Video yangilandi' : 'Video joylandi')
      setFile(null)
      if (picker.current) picker.current.value = ''
    } catch (cause) {
      if (!controller.signal.aborted) setFailure(cause)
    } finally {
      running.current = null
      setSent(null)
    }
  }

  async function copyLink() {
    try {
      await navigator.clipboard.writeText(pageUrl)
      notify('Havola nusxalandi')
    } catch {
      notify("Nusxalab bo'lmadi — havolani qo'lda belgilab oling", 'error')
    }
  }

  return (
    <div className="grid" style={{ gap: 16 }}>
      <Card
        title="Hozirgi video"
        action={
          editable &&
          video && (
            <button className="btn small danger" disabled={uploading} onClick={() => setRemoving(true)}>
              Olib tashlash
            </button>
          )
        }
      >
        <div className="row" style={{ marginBottom: 12 }}>
          <span className="muted">Ochiq sahifa:</span>
          <a href={pageUrl} target="_blank" rel="noreferrer" className="mono">
            {pageUrl}
          </a>
          <button className="btn small" onClick={copyLink}>
            Nusxalash
          </button>
        </div>
        {video ? (
          <>
            {/* Keyed on the address so a new video is a new player, not the old one's buffer. */}
            <video key={video.url} className="promo-player" controls playsInline preload="metadata" src={API_BASE + video.url} />
            <div className="faint" style={{ marginTop: 8 }}>
              {video.mimeType === 'video/webm' ? 'WebM' : 'MP4'} · {formatBytes(video.sizeBytes)} · yuklangan:{' '}
              {formatDateTime(video.updatedAt)}
            </div>
          </>
        ) : (
          <Empty>Hali video joylanmagan. Sahifa hozircha “Video tez orada” deb turibdi.</Empty>
        )}
      </Card>

      {editable && (
        <Card title={video ? 'Videoni yangilash' : 'Video yuklash'}>
          <div className="notice" style={{ marginBottom: 12 }}>
            MP4 (H.264) yoki WebM, eng ko'pi {formatBytes(maxBytes)}. Yangi video eskisining o'rnini bosadi va sahifada
            darhol ko'rinadi.
          </div>
          <input
            ref={picker}
            type="file"
            accept="video/mp4,video/webm,.mp4,.webm"
            aria-label="Video fayl"
            disabled={uploading}
            onChange={(event) => choose(event.target.files?.[0] ?? null)}
          />
          {file && (
            <div className="faint" style={{ marginTop: 8 }}>
              {file.name} · {formatBytes(file.size)}
            </div>
          )}
          {problem && (
            <div className="notice" role="alert" style={{ marginTop: 12 }}>
              {problem}
            </div>
          )}
          {failure != null && (
            <div style={{ marginTop: 12 }}>
              <ErrorNotice error={failure} />
            </div>
          )}
          {uploading && (
            <div style={{ marginTop: 12 }}>
              <div
                className="progress"
                role="progressbar"
                aria-label="Yuklanmoqda"
                aria-valuemin={0}
                aria-valuemax={100}
                aria-valuenow={percent}
              >
                <span style={{ width: `${percent}%` }} />
              </div>
              <div className="faint" style={{ marginTop: 6 }}>
                {percent}% · {formatBytes(sent)} / {formatBytes(file?.size ?? 0)}
              </div>
            </div>
          )}
          <div className="row" style={{ marginTop: 14 }}>
            <button className="btn primary" disabled={!file || Boolean(problem) || uploading} onClick={upload}>
              {uploading && <Spinner />}
              {uploading ? 'Yuklanmoqda…' : video ? 'Yangilash' : 'Yuklash'}
            </button>
            {uploading && (
              <button className="btn ghost" onClick={() => running.current?.abort()}>
                Bekor qilish
              </button>
            )}
          </div>
        </Card>
      )}

      {removing && (
        <ConfirmDialog
          title="Videoni olib tashlash"
          confirmLabel="Olib tashlash"
          pendingLabel="Olib tashlanmoqda…"
          danger
          pending={remove.isPending}
          onClose={() => setRemoving(false)}
          onConfirm={() =>
            remove.mutate(undefined, {
              onSuccess: () => {
                notify('Video olib tashlandi')
                setRemoving(false)
              },
            })
          }
        >
          <p className="muted">
            Video sahifadan olinadi va serverdan o'chiriladi. Sahifa yangisi yuklanmaguncha “Video tez orada” deb turadi.
          </p>
          {remove.error && <ErrorNotice error={remove.error} />}
        </ConfirmDialog>
      )}
    </div>
  )
}
