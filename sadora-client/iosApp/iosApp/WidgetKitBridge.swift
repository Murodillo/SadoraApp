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

    /// The latest picture only: application context replaces what was there, which is
    /// what a widget wants — not a queue of every snapshot since the watch was last near.
    func sendToWatch(tokensJson: String?, snapshotJson: String?, baseUrl: String) {
        guard watchPaired(), let session else { return }
        var context: [String: Any] = ["baseUrl": baseUrl, "sentAt": Date().timeIntervalSince1970]
        if let tokensJson { context["tokens"] = tokensJson }
        if let snapshotJson { context["snapshot"] = snapshotJson }
        if tokensJson == nil && snapshotJson == nil { context["signOut"] = true }
        try? session.updateApplicationContext(context)
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
