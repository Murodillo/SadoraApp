import WidgetKit
import SwiftUI
import SadoraKit

/// The watch face: the cycle day (or week) in a ring, and the water ring. Drawn from the
/// snapshot the watch app keeps; discreet by default, since a wrist is in public view.
@main
struct SadoraComplications: WidgetBundle {
    var body: some Widget {
        StageComplication()
        WaterComplication()
    }
}

struct ComplicationEntry: TimelineEntry {
    let date: Date
}

struct ComplicationProvider: TimelineProvider {
    func placeholder(in context: Context) -> ComplicationEntry { ComplicationEntry(date: .now) }
    func getSnapshot(in context: Context, completion: @escaping (ComplicationEntry) -> Void) { completion(ComplicationEntry(date: .now)) }

    func getTimeline(in context: Context, completion: @escaping (Timeline<ComplicationEntry>) -> Void) {
        let now = Date.now
        let midnight = Calendar.current.startOfDay(for: now.addingTimeInterval(86_400))
        completion(Timeline(entries: [ComplicationEntry(date: now), ComplicationEntry(date: midnight)], policy: .after(now.addingTimeInterval(30 * 60))))
    }
}

private func complicationContent(at date: Date, water: Bool) -> WidgetContent? {
    let host = SadoraKitHost.shared
    let config = host.config(size: .small, water: water, doses: false, health: false, waterStepMl: 250, lockScreen: true, revealOnLockScreen: false)
    return host.contentAt(config: config, epochSeconds: Int64(date.timeIntervalSince1970))
}

struct StageComplication: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "SadoraStage", provider: ComplicationProvider()) { entry in
            let content = complicationContent(at: entry.date, water: false)
            Gauge(value: Double(truncating: content?.stage.progress ?? 0)) {
                Image(systemName: "sparkle")
            } currentValueLabel: {
                Text(content?.stage.big ?? "—")
            }
            .gaugeStyle(.accessoryCircular)
            .tint(Color(hex: 0xFF63C8))
            .containerBackground(.clear, for: .widget)
        }
        .configurationDisplayName("Sadora")
        .supportedFamilies([.accessoryCircular, .accessoryCorner, .accessoryInline])
    }
}

struct WaterComplication: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "SadoraWater", provider: ComplicationProvider()) { entry in
            let water = complicationContent(at: entry.date, water: true)?.water
            Gauge(value: Double(water?.progress ?? 0)) {
                Image(systemName: "drop.fill")
            } currentValueLabel: {
                Text("\(Int((water?.progress ?? 0) * 100))%")
            }
            .gaugeStyle(.accessoryCircular)
            .tint(Color(hex: 0x4FC3FF))
            .containerBackground(.clear, for: .widget)
        }
        .configurationDisplayName("Suv")
        .supportedFamilies([.accessoryCircular, .accessoryCorner])
    }
}
