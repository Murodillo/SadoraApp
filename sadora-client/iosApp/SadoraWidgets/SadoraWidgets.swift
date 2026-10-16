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
                    StageTile(content: content, bigSize: 52)
                        .padding(14)
                        .widgetURL(URL(string: "sadora://open/journey"))
                        .containerBackground(for: .widget) { SadoraPalette.stageFill(content) }
                case .systemMedium:
                    HStack(spacing: 8) {
                        StageTile(content: content, bigSize: 40)
                            .padding(12)
                            .background(SadoraPalette.stageFill(content), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                        VStack(spacing: 8) { SideBlocks(content: content, compact: true) }
                    }
                    .padding(10)
                    .containerBackground(.white, for: .widget)
                default:
                    VStack(spacing: 8) {
                        StageTile(content: content, bigSize: 40)
                            .padding(12)
                            .frame(height: 96)
                            .background(SadoraPalette.stageFill(content), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                        SideBlocks(content: content, compact: false)
                    }
                    .padding(10)
                    .containerBackground(.white, for: .widget)
                }
            } else {
                Text(host.signInText())
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(SadoraPalette.muted)
                    .multilineTextAlignment(.center)
                    .containerBackground(.white, for: .widget)
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
            ValueTile(title: "🔒 " + content.lockText, value: "", tint: Color(hex: 0xF7F5FF), accent: SadoraPalette.dose) { EmptyView() }
        } else {
            if let water = content.water {
                ValueTile(title: water.title, value: water.label, tint: SadoraPalette.waterTint, accent: SadoraPalette.water) {
                    Button(intent: AddWaterIntent(ml: Int(water.stepMl))) {
                        PillLabel(text: compact ? "+\(water.stepMl)" : water.stepLabel, color: SadoraPalette.water)
                    }
                    .buttonStyle(.plain)
                }
            }
            if let doses = content.doses {
                if compact || doses.rows.isEmpty {
                    let next = doses.next
                    ValueTile(
                        title: next.map { "\($0.name) · \($0.time)" } ?? doses.title,
                        value: doses.label,
                        tint: SadoraPalette.doseTint,
                        accent: SadoraPalette.dose
                    ) {
                        if let next { DoseButton(line: next) }
                    }
                } else {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("\(doses.title) · \(doses.label)").font(.system(size: 11, weight: .bold)).foregroundStyle(SadoraPalette.dose)
                        ForEach(Array(doses.rows.prefix(3).enumerated()), id: \.offset) { _, row in
                            HStack {
                                Text("\(row.name) · \(row.time)").font(.system(size: 13, weight: .bold)).foregroundStyle(SadoraPalette.ink).lineLimit(1)
                                Spacer(minLength: 0)
                                if row.status == ContractDoseStatus.taken {
                                    Image(systemName: "checkmark.circle.fill").foregroundStyle(SadoraPalette.taken).font(.system(size: 22))
                                } else {
                                    DoseButton(line: row)
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 10).padding(.vertical, 6)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                    .background(SadoraPalette.doseTint, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
            }
            if let health = content.health, !(compact && content.water != nil && content.doses != nil) {
                if health.connectPrompt {
                    ValueTile(title: health.connectText, value: "", tint: SadoraPalette.healthTint, accent: SadoraPalette.ink) { EmptyView() }
                        .widgetURL(URL(string: "sadora://open/devices"))
                } else {
                    HStack(spacing: 8) {
                        ForEach(Array(health.items.enumerated()), id: \.offset) { _, item in
                            ValueTile(title: item.label, value: item.value, tint: SadoraPalette.healthTint, accent: SadoraPalette.muted) { EmptyView() }
                        }
                    }
                }
            }
        }
    }
}

struct DoseButton: View {
    let line: DoseLine

    var body: some View {
        Button(intent: MarkDoseIntent(medicationId: line.medicationId, day: "\(line.dueOn)", time: String("\(line.dueAt)".prefix(5)))) {
            PillLabel(text: "✓", color: SadoraPalette.dose)
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
