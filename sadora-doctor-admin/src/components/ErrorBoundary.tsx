import { Component } from 'react'
import type { ErrorInfo, ReactNode } from 'react'

/**
 * The last line of defence under the router.
 *
 * A render that throws — a page reading a field a query never filled, a status the
 * server added — used to unmount the whole tree and leave a white window with nothing
 * to click. This keeps the shell's place, says so in the operator's language and offers
 * the one repair that always works.
 */
export class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }

  static getDerivedStateFromError(): { failed: boolean } {
    return { failed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // The console is the only log a static panel has; the message names no user data.
    console.error('Panel render failed', error, info.componentStack)
  }

  render(): ReactNode {
    if (!this.state.failed) return this.props.children
    return (
      <div className="page" role="alert">
        <h1 className="page-title">Sahifa ochilmadi</h1>
        <p>Kutilmagan xatolik yuz berdi. Sahifani yangilang; takrorlansa, texnik jamoaga xabar bering.</p>
        <button className="btn primary" type="button" onClick={() => window.location.reload()}>
          Yangilash
        </button>
      </div>
    )
  }
}
