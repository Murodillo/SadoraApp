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
                        .frame(height: 100)
                        .clay(content.stageFill, radius: 20)
                    if content.locked {
                        HStack(spacing: 6) {
                            Image("clay_crown").resizable().scaledToFit().frame(width: 28, height: 28)
                            Text(content.lockText).font(.system(size: 13, weight: .heavy, design: .rounded))
                        }
                    }
                    if let water = content.water {
                        Button(action: model.addWater) {
                            HStack(spacing: 8) {
                                Image("clay_water").resizable().scaledToFit().frame(width: 30, height: 30)
                                VStack(alignment: .leading, spacing: 0) {
                                    Text(water.title).font(.system(size: 11, weight: .heavy, design: .rounded))
                                    Text(water.label).font(.system(size: 16, weight: .black, design: .rounded))
                                }
                                Spacer(minLength: 0)
                                Text("+\(water.stepMl)").font(.system(size: 13, weight: .black, design: .rounded))
                                    .padding(.horizontal, 8).padding(.vertical, 6)
                                    .clay(SadoraPalette.waterButton, radius: 14)
                            }
                        }
                        .buttonStyle(.plain)
                        .padding(8)
                        .clay(SadoraPalette.gradient([0x12405E, 0x0E2A40]), radius: 18)
                    }
                    if let doses = content.doses {
                        ForEach(Array(doses.rows.enumerated()), id: \.offset) { _, row in
                            let taken = row.status == ContractDoseStatus.taken
                            Button { model.markDose(row) } label: {
                                HStack(spacing: 8) {
                                    Image("clay_meds").resizable().scaledToFit().frame(width: 28, height: 28)
                                    VStack(alignment: .leading, spacing: 0) {
                                        Text(row.name).font(.system(size: 13, weight: .heavy, design: .rounded)).lineLimit(1)
                                        Text(row.time).font(.system(size: 11, weight: .semibold, design: .rounded))
                                    }
                                    Spacer(minLength: 0)
                                    Image(systemName: taken ? "checkmark" : "circle")
                                        .font(.system(size: 13, weight: .black))
                                        .frame(width: 30, height: 30)
                                        .clay(taken ? SadoraPalette.takenButton : SadoraPalette.doseButton, radius: 15)
                                }
                            }
                            .buttonStyle(.plain)
                            .disabled(taken)
                            .padding(8)
                            .clay(SadoraPalette.gradient([0x33296A, 0x221C48]), radius: 18)
                        }
                    }
                    if let health = content.health, !health.connectPrompt {
                        ForEach(Array(health.items.enumerated()), id: \.offset) { _, item in
                            HStack(spacing: 8) {
                                Image(item.metric == ContractHealthMetric.steps ? "clay_active" : (item.metric == ContractHealthMetric.sleepDuration ? "clay_sleep" : "clay_heart"))
                                    .resizable().scaledToFit().frame(width: 26, height: 26)
                                Text(item.label).font(.system(size: 12, weight: .heavy, design: .rounded))
                                Spacer()
                                Text(item.value).font(.system(size: 14, weight: .black, design: .rounded))
                            }
                            .padding(8)
                            .clay(SadoraPalette.gradient([0x2A2350, 0x1C1838]), radius: 16)
                        }
                    }
                }
            }
            .refreshable { await model.refresh() }
        } else {
            VStack(spacing: 8) {
                Image("clay_heart").resizable().scaledToFit().frame(width: 44, height: 44)
                Text("Sadora").font(.system(size: 17, weight: .black, design: .rounded))
                Text("iPhone'da Sadora'ni oching").font(.system(size: 13, weight: .semibold, design: .rounded)).multilineTextAlignment(.center)
            }
        }
    }
}
