import WidgetKit
import SwiftUI
import AppIntents
import SadoraKit

@main
struct SadoraWidgetBundle: WidgetBundle {
    var body: some Widget {
        SadoraHomeWidget()
        SadoraLockWidget()
    }
}

// MARK: - settings

/// What she picks under "Edit Widget": the blocks, the water step, and whether the lock
/// screen may show words. Free accounts get the stage whatever is picked here.
struct SadoraWidgetIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Sadora"
    static var description = IntentDescription("Sikl kuni, suv va dorilar")

    @Parameter(title: "Suv", default: true) var water: Bool
    @Parameter(title: "Dorilar", default: true) var doses: Bool
    @Parameter(title: "Soat ma'lumoti", default: false) var health: Bool
    @Parameter(title: "Suv tugmasi (ml)", default: 250, controlStyle: .field, inclusiveRange: (100, 500)) var waterStep: Int
    @Parameter(title: "Lock screen'da so'z bilan", default: false) var reveal: Bool
}

// MARK: - timeline

struct SadoraEntry: TimelineEntry {
    let date: Date
    let intent: SadoraWidgetIntent
}

struct SadoraProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> SadoraEntry { SadoraEntry(date: .now, intent: SadoraWidgetIntent()) }

    func snapshot(for intent: SadoraWidgetIntent, in context: Context) async -> SadoraEntry {
        SadoraEntry(date: .now, intent: intent)
    }

    /// Refreshes a snapshot older than half an hour within the seconds iOS allows, then
    /// draws now and again at midnight, when the cycle day turns without a fetch.
    func timeline(for intent: SadoraWidgetIntent, in context: Context) async -> Timeline<SadoraEntry> {
        let host = SadoraKitHost.shared
        if !context.isPreview, host.isStale(minutes: 30) {
            _ = try? await host.sync.refresh()
        }
        let now = Date.now
        let midnight = Calendar.current.startOfDay(for: now.addingTimeInterval(86_400))
        return Timeline(
            entries: [SadoraEntry(date: now, intent: intent), SadoraEntry(date: midnight, intent: intent)],
            policy: .after(now.addingTimeInterval(30 * 60))
        )
    }
}

// MARK: - home screen

struct SadoraHomeWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "SadoraHome", intent: SadoraWidgetIntent.self, provider: SadoraProvider()) { entry in
            HomeWidgetView(entry: entry)
        }
        .configurationDisplayName("Sadora")
        .description("Sikl kuni, suv va dorilar bir qarashda")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
        .contentMarginsDisabled()
    }
}

struct HomeWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: SadoraEntry

    var body: some View {
        let host = SadoraKitHost.shared
        let size: WidgetSize = family == .systemSmall ? .small : (family == .systemMedium ? .medium : .large)
        let config = host.config(
            size: size,
            water: entry.intent.water,
            doses: entry.intent.doses,
            health: entry.intent.health,
            waterStepMl: Int32(entry.intent.waterStep),
            lockScreen: false,
            revealOnLockScreen: false
        )
        Group {
            if let content = host.contentAt(config: config, epochSeconds: Int64(entry.date.timeIntervalSince1970)) {
                switch family {
                case .systemSmall:
                    StageTile(content: content, bigSize: 54)
                        .padding(14)
                        .widgetURL(URL(string: "sadora://open/journey"))
                        .containerBackground(for: .widget) {
                            content.stageFill.overlay(LinearGradient(colors: [.white.opacity(0.28), .clear, .black.opacity(0.07)], startPoint: .top, endPoint: .bottom))
                        }
                case .systemMedium:
                    HStack(spacing: 8) {
                        StageTile(content: content, bigSize: 42)
                            .padding(12)
                            .clay(content.stageFill, radius: 22)
                            .shadow(color: Color(hex: 0x7B61FF).opacity(0.25), radius: 6, y: 4)
                        VStack(spacing: 8) { SideBlocks(content: content, compact: true) }
                    }
                    .padding(9)
                    .containerBackground(SadoraPalette.card, for: .widget)
                default:
                    VStack(spacing: 8) {
                        WideStageTile(content: content)
                            .padding(.horizontal, 12).padding(.vertical, 9)
                            .clay(content.stageFill, radius: 22)
                            .shadow(color: Color(hex: 0x7B61FF).opacity(0.25), radius: 6, y: 4)
                        SideBlocks(content: content, compact: false)
                        Spacer(minLength: 0)
                    }
                    .padding(9)
                    .containerBackground(SadoraPalette.card, for: .widget)
                }
            } else {
                VStack(spacing: 6) {
                    Image("clay_heart").resizable().scaledToFit().frame(width: 44, height: 44)
                    Text(host.signInText())
                        .font(.system(size: 13, weight: .heavy, design: .rounded))
                        .foregroundStyle(SadoraPalette.muted)
                        .multilineTextAlignment(.center)
                }
                .containerBackground(SadoraPalette.card, for: .widget)
            }
        }
    }
}

/// Water, doses and the wearable beside or under the stage, with their buttons.
struct SideBlocks: View {
    let content: WidgetContent
    let compact: Bool

    var body: some View {
        if content.locked {
            ValueTile(icon: "clay_crown", title: content.lockText, value: "", fill: SadoraPalette.premiumTile, accent: SadoraPalette.stepsInk) { EmptyView() }
        } else {
            if let water = content.water {
                ValueTile(icon: "clay_water", title: water.title, value: water.label, fill: SadoraPalette.waterTile, accent: SadoraPalette.waterInk) {
                    Button(intent: AddWaterIntent(ml: Int(water.stepMl))) {
                        PillLabel(text: compact ? "+\(water.stepMl)" : water.stepLabel, fill: SadoraPalette.waterButton)
                    }
                    .buttonStyle(.plain)
                }
                .frame(maxHeight: compact ? .infinity : 52)
            }
            if let doses = content.doses {
                if compact || doses.rows.isEmpty {
                    let next = doses.next
                    ValueTile(
                        icon: "clay_meds",
                        title: next.map { "\($0.name) · \($0.time)" } ?? doses.title,
                        value: doses.label,
                        fill: SadoraPalette.doseTile,
                        accent: SadoraPalette.doseInk
                    ) {
                        if let next { DoseButton(line: next) }
                    }
                } else {
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(spacing: 6) {
                            Image("clay_meds").resizable().scaledToFit().frame(width: 24, height: 24)
                            Text("\(doses.title) · \(doses.label)").font(.system(size: 12, weight: .heavy, design: .rounded)).foregroundStyle(SadoraPalette.doseInk)
                        }
                        ForEach(Array(doses.rows.prefix(3).enumerated()), id: \.offset) { _, row in
                            HStack {
                                Text("\(row.name) · \(row.time)").font(.system(size: 13, weight: .heavy, design: .rounded)).foregroundStyle(SadoraPalette.ink).lineLimit(1)
                                Spacer(minLength: 0)
                                if row.status == ContractDoseStatus.taken {
                                    Image(systemName: "checkmark").font(.system(size: 13, weight: .black)).foregroundStyle(.white)
                                        .frame(width: 32, height: 32).clay(SadoraPalette.takenButton, radius: 16)
                                } else {
                                    DoseButton(line: row)
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 10).padding(.vertical, 7)
                    .frame(maxWidth: .infinity, alignment: .topLeading)
                    .clay(SadoraPalette.doseTile, radius: 18)
                }
            }
            if let health = content.health, !(compact && content.water != nil && content.doses != nil) {
                if health.connectPrompt {
                    ValueTile(icon: "clay_watch", title: health.connectText, value: "", fill: SadoraPalette.sleepTile, accent: SadoraPalette.ink) { EmptyView() }
                        .widgetURL(URL(string: "sadora://open/devices"))
                } else {
                    HStack(spacing: 8) {
                        ForEach(Array(health.items.enumerated()), id: \.offset) { _, item in
                            HealthTile(item: item)
                        }
                    }
                    .frame(maxHeight: compact ? .infinity : 52)
                }
            }
        }
    }
}

struct HealthTile: View {
    let item: HealthItem

    var body: some View {
        switch item.metric {
        case ContractHealthMetric.steps:
            ValueTile(icon: "clay_active", title: item.label, value: item.value, fill: SadoraPalette.stepsTile, accent: SadoraPalette.stepsInk) { EmptyView() }
        case ContractHealthMetric.sleepDuration:
            ValueTile(icon: "clay_sleep", title: item.label, value: item.value, fill: SadoraPalette.sleepTile, accent: SadoraPalette.doseInk) { EmptyView() }
        default:
            ValueTile(icon: "clay_heart", title: item.label, value: item.value, fill: SadoraPalette.doseTile, accent: SadoraPalette.doseInk) { EmptyView() }
        }
    }
}

struct DoseButton: View {
    let line: DoseLine

    var body: some View {
        Button(intent: MarkDoseIntent(medicationId: line.medicationId, day: "\(line.dueOn)", time: String("\(line.dueAt)".prefix(5)))) {
            PillLabel(text: "✓", fill: SadoraPalette.doseButton)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - lock screen

struct SadoraLockWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "SadoraLock", intent: SadoraWidgetIntent.self, provider: SadoraProvider()) { entry in
            LockWidgetView(entry: entry)
        }
        .configurationDisplayName("Sadora")
        .description("Sikl kuni va suv")
        .supportedFamilies([.accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}

/// The lock screen is public: discreet unless she turned that off for this widget.
struct LockWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: SadoraEntry

    var body: some View {
        let host = SadoraKitHost.shared
        let config = host.config(
            size: .small, water: true, doses: true, health: false,
            waterStepMl: Int32(entry.intent.waterStep),
            lockScreen: true, revealOnLockScreen: entry.intent.reveal
        )
        let content = host.contentAt(config: config, epochSeconds: Int64(entry.date.timeIntervalSince1970))
        Group {
            switch family {
            case .accessoryCircular:
                Gauge(value: Double(truncating: content?.stage.progress ?? 0)) {
                    Image(systemName: "sparkle")
                } currentValueLabel: {
                    Text(content?.stage.big ?? "—")
                }
                .gaugeStyle(.accessoryCircular)
            case .accessoryInline:
                Text("✿ \(content?.stage.big ?? "—")" + (content?.stage.line2.map { " · \($0)" } ?? ""))
            default:
                VStack(alignment: .leading, spacing: 2) {
                    Text(content?.stage.title ?? "Sadora").font(.headline)
                    Text("✿ \(content?.stage.big ?? "—")" + (content?.doses.map { " · \($0.label)" } ?? ""))
                    if let line = content?.stage.line2 { Text(line).font(.caption) }
                }
            }
        }
        .widgetURL(URL(string: "sadora://open/today"))
        .containerBackground(.clear, for: .widget)
    }
}
