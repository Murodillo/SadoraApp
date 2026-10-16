import SwiftUI
import WatchConnectivity
import WidgetKit
import UserNotifications
import SadoraKit

@main
struct SadoraWatchApp: App {
    @WKApplicationDelegateAdaptor(WatchDelegate.self) private var delegate
    @StateObject private var model = WatchModel.shared

    var body: some Scene {
        WindowGroup {
            WatchHome(model: model)
        }
    }
}

/// What the watch shows, redrawn whenever the phone sends a picture or a tap returns.
@MainActor
final class WatchModel: ObservableObject {
    static let shared = WatchModel()

    @Published private(set) var content: WidgetContent?
    @Published private(set) var signedOut = false

    private let host = SadoraKitHost.shared

    /// Everything on one screen: stage, water, every dose, the wearable.
    private var config: WidgetConfig {
        host.config(size: .large, water: true, doses: true, health: true, waterStepMl: host.defaultConfig().waterStepMl, lockScreen: false, revealOnLockScreen: false)
    }

    func redraw() {
        content = host.contentAt(config: config, epochSeconds: Int64(Date().timeIntervalSince1970))
        signedOut = content == nil
        WidgetCenter.shared.reloadAllTimelines()
    }

    func addWater() {
        guard let step = content?.water?.stepMl else { return }
        Task {
            _ = try? await host.tapWater(ml: step)
            redraw()
        }
        redraw()
    }

    func markDose(_ line: DoseLine, taken: Bool = true) {
        Task {
            _ = try? await host.tapDose(medicationId: line.medicationId, day: "\(line.dueOn)", time: String("\(line.dueAt)".prefix(5)), taken: taken)
            redraw()
        }
    }

    /// Straight to the server over Wi-Fi or LTE when the phone is not near; the phone's
    /// picture arrives on its own when it is.
    func refresh() async {
        _ = try? await host.sync.refresh()
        redraw()
    }
}

/// The phone's side of the conversation and the dose notification's buttons.
final class WatchDelegate: NSObject, WKApplicationDelegate, WCSessionDelegate, UNUserNotificationCenterDelegate {
    func applicationDidFinishLaunching() {
        if WCSession.isSupported() {
            WCSession.default.delegate = self
            WCSession.default.activate()
        }
        let taken = UNNotificationAction(identifier: "DOSE_TAKEN", title: "Ichdim")
        let skipped = UNNotificationAction(identifier: "DOSE_SKIPPED", title: "O'tkazdim")
        UNUserNotificationCenter.current().setNotificationCategories([UNNotificationCategory(identifier: "DOSE", actions: [taken, skipped], intentIdentifiers: [])])
        UNUserNotificationCenter.current().delegate = self
        Task { @MainActor in WatchModel.shared.redraw() }
    }

    func applicationDidBecomeActive() {
        Task { await WatchModel.shared.refresh() }
    }

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        if !session.receivedApplicationContext.isEmpty { take(session.receivedApplicationContext) }
    }

    func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        take(applicationContext)
    }

    /// The session (or a sign-out), queued by the phone so it is never lost.
    func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any] = [:]) {
        take(userInfo)
    }

    private func take(_ message: [String: Any]) {
        let host = SadoraKitHost.shared
        let baseUrl = message["baseUrl"] as? String ?? ""
        if message["signOut"] as? Bool == true {
            host.importFromPhone(tokensJson: nil, snapshotJson: nil, baseUrl: "")
        } else {
            if let tokens = message["tokens"] as? String {
                host.importFromPhone(tokensJson: tokens, snapshotJson: nil, baseUrl: baseUrl)
            }
            if let snapshot = message["snapshot"] as? String {
                host.importFromPhone(tokensJson: nil, snapshotJson: snapshot, baseUrl: baseUrl)
            }
        }
        Task { @MainActor in WatchModel.shared.redraw() }
    }

    /// "Taken" on the watch: through the phone when it is near (one queue, one session),
    /// else on the watch's own session.
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        guard response.actionIdentifier == "DOSE_TAKEN" || response.actionIdentifier == "DOSE_SKIPPED",
              let link = response.notification.request.content.userInfo["link"] as? String else { return }
        let taken = response.actionIdentifier == "DOSE_TAKEN"
        if WCSession.default.isReachable {
            WCSession.default.sendMessage(["dose": link, "taken": taken], replyHandler: nil)
            return
        }
        let parts = link.replacingOccurrences(of: "sadora://dose/", with: "").split(separator: "/").map(String.init)
        guard parts.count == 3 else { return }
        _ = try? await SadoraKitHost.shared.tapDose(medicationId: parts[0], day: parts[1], time: parts[2], taken: taken)
        await MainActor.run { WatchModel.shared.redraw() }
    }
}

struct WatchHome: View {
    @ObservedObject var model: WatchModel

    var body: some View {
        if let content = model.content {
            ScrollView {
                VStack(spacing: 8) {
                    StageTile(content: content, bigSize: 34)
                        .padding(10)
                        .frame(height: 96)
                        .background(SadoraPalette.stageFill(content), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    if content.locked {
                        Text(content.lockText).font(.footnote).multilineTextAlignment(.center)
                    }
                    if let water = content.water {
                        Button(action: model.addWater) {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(water.title).font(.caption2)
                                    Text(water.label).font(.headline)
                                }
                                Spacer()
                                Text(water.stepLabel).font(.footnote.bold())
                            }
                        }
                        .tint(SadoraPalette.water)
                    }
                    if let doses = content.doses {
                        ForEach(Array(doses.rows.enumerated()), id: \.offset) { _, row in
                            Button { model.markDose(row) } label: {
                                HStack {
                                    VStack(alignment: .leading) {
                                        Text(row.name).font(.footnote.bold()).lineLimit(1)
                                        Text(row.time).font(.caption2)
                                    }
                                    Spacer()
                                    Image(systemName: row.status == ContractDoseStatus.taken ? "checkmark.circle.fill" : "circle")
                                }
                            }
                            .tint(SadoraPalette.dose)
                            .disabled(row.status == ContractDoseStatus.taken)
                        }
                    }
                    if let health = content.health, !health.connectPrompt {
                        ForEach(Array(health.items.enumerated()), id: \.offset) { _, item in
                            HStack {
                                Text(item.label).font(.caption2)
                                Spacer()
                                Text(item.value).font(.footnote.bold())
                            }
                        }
                    }
                }
            }
            .refreshable { await model.refresh() }
        } else {
            VStack(spacing: 8) {
                Text("Sadora").font(.headline)
                Text("iPhone'da Sadora'ni oching").font(.footnote).multilineTextAlignment(.center)
            }
        }
    }
}
