import WidgetKit
import WatchConnectivity
import Shared

/// WidgetKit and the watch, for the shared code (`IosWidgets`): both are Swift-only APIs.
final class WidgetKitBridgeImpl: NSObject, WidgetKitBridge, WCSessionDelegate {
    private let session: WCSession? = WCSession.isSupported() ? WCSession.default : nil

    override init() {
        super.init()
        session?.delegate = self
        session?.activate()
    }

    func reloadWidgets() {
        WidgetCenter.shared.reloadAllTimelines()
    }

    func watchPaired() -> Bool {
        guard let session, session.activationState == .activated else { return false }
        return session.isPaired && session.isWatchAppInstalled
    }

    /// The session goes as user info, which is queued and delivered every time; the
    /// snapshot as application context, of which only the latest arrives — what a widget
    /// wants, and why the session cannot ride along with it: a later snapshot would
    /// replace the context that carried it before the watch ever saw it.
    func sendToWatch(tokensJson: String?, snapshotJson: String?, baseUrl: String) {
        guard watchPaired(), let session else { return }
        let now = Date().timeIntervalSince1970
        if tokensJson == nil && snapshotJson == nil {
            session.transferUserInfo(["signOut": true, "sentAt": now])
            try? session.updateApplicationContext(["signOut": true, "sentAt": now])
            return
        }
        if let tokensJson {
            session.transferUserInfo(["tokens": tokensJson, "baseUrl": baseUrl, "sentAt": now])
        }
        if let snapshotJson {
            try? session.updateApplicationContext(["snapshot": snapshotJson, "baseUrl": baseUrl, "sentAt": now])
        }
    }

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {}
    func sessionDidBecomeInactive(_ session: WCSession) {}
    func sessionDidDeactivate(_ session: WCSession) { session.activate() }

    /// The watch answered a dose, or asks for a fresh picture.
    func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        if let link = message["dose"] as? String {
            IosWidgets.shared.answerDose(link: link, taken: (message["taken"] as? Bool) ?? true) {}
        } else if message["refresh"] != nil {
            IosWidgets.shared.refresh {}
        }
    }
}
