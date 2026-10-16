import SwiftUI
import SadoraKit

/// The palette the app's widgets share with the app: the hero gradient, the warm stage
/// gradient, and the tints of the water and dose tiles.
enum SadoraPalette {
    static let hero = LinearGradient(colors: [Color(hex: 0x6A4FF0), Color(hex: 0xC93C88)], startPoint: .topLeading, endPoint: .bottomTrailing)
    static let warm = LinearGradient(colors: [Color(hex: 0xFFB020), Color(hex: 0xFF8E92)], startPoint: .topLeading, endPoint: .bottomTrailing)
    static let period = LinearGradient(colors: [Color(hex: 0xD42A78), Color(hex: 0xFF6FB8)], startPoint: .topLeading, endPoint: .bottomTrailing)
    static let warmInk = Color(hex: 0x2A1145)
    static let waterTint = Color(hex: 0xE8F6FF)
    static let water = Color(hex: 0x1C88C8)
    static let doseTint = Color(hex: 0xF1EDFF)
    static let dose = Color(hex: 0x6247E0)
    static let healthTint = Color(hex: 0xFFE9F4)
    static let ink = Color(hex: 0x1A1630)
    static let muted = Color(hex: 0x66617F)
    static let taken = Color(hex: 0x2BA57A)

    static func stageFill(_ content: WidgetContent) -> LinearGradient {
        if content.isWarm { return warm }
        if content.stage.phase == ContractCyclePhase.period { return period }
        return hero
    }
}

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

/// The stage: the one number, what it counts, and the line under it. Every size and
/// the watch draw it the same way; only [bigSize] changes.
struct StageTile: View {
    let content: WidgetContent
    var bigSize: CGFloat = 52

    var body: some View {
        let stage = content.stage
        let ink = content.isWarm ? SadoraPalette.warmInk : .white
        VStack(alignment: .leading, spacing: 2) {
            Text(stage.title).font(.system(size: 12, weight: .bold)).lineLimit(1)
            Spacer(minLength: 0)
            HStack(alignment: .lastTextBaseline, spacing: 4) {
                Text(stage.big).font(.system(size: bigSize, weight: .black, design: .rounded)).minimumScaleFactor(0.6)
                if let unit = stage.unit { Text(unit).font(.system(size: 13, weight: .bold)) }
            }
            Spacer(minLength: 0)
            if let line1 = stage.line1 { Text(line1).font(.system(size: 12, weight: .bold)).lineLimit(1) }
            if let line2 = stage.line2 { Text(line2).font(.system(size: 11)).lineLimit(1) }
        }
        .foregroundStyle(ink)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

/// A tile with a title, a value and, optionally, the button that changes it.
struct ValueTile<Action: View>: View {
    let title: String
    let value: String
    let tint: Color
    let accent: Color
    @ViewBuilder var action: () -> Action

    var body: some View {
        HStack(spacing: 6) {
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.system(size: 11, weight: .bold)).foregroundStyle(accent).lineLimit(1)
                if !value.isEmpty {
                    Text(value).font(.system(size: 15, weight: .heavy)).foregroundStyle(SadoraPalette.ink).lineLimit(1).minimumScaleFactor(0.7)
                }
            }
            Spacer(minLength: 0)
            action()
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(tint, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

/// A round button label, 44pt so a thumb finds it.
struct PillLabel: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text)
            .font(.system(size: 13, weight: .heavy))
            .foregroundStyle(.white)
            .frame(minWidth: 44, minHeight: 36)
            .padding(.horizontal, 4)
            .background(color, in: Capsule())
    }
}
