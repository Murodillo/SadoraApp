import SwiftUI
import SadoraKit

/// The palette the app's widgets share with the app, in its clay finish: every surface a
/// gradient with a bright band on top and a soft shade at the bottom.
enum SadoraPalette {
    static let warmInk = Color(hex: 0x2A1145)
    static let waterInk = Color(hex: 0x146A94)
    static let doseInk = Color(hex: 0x6247E0)
    static let stepsInk = Color(hex: 0x7F5100)
    static let ink = Color(hex: 0x1A1630)
    static let muted = Color(hex: 0x66617F)

    static func gradient(_ colors: [UInt32], angle: Bool = true) -> LinearGradient {
        LinearGradient(colors: colors.map { Color(hex: $0) }, startPoint: angle ? .topLeading : .top, endPoint: angle ? .bottomTrailing : .bottom)
    }

    static let card = gradient([0xFFFFFF, 0xF6F3FF], angle: false)
    static let waterTile = gradient([0xEAF8FF, 0xC9EDFF])
    static let doseTile = gradient([0xF3EFFF, 0xDCD3FF])
    static let stepsTile = gradient([0xFFF6E4, 0xFFDDB0])
    static let sleepTile = gradient([0xF1EDFF, 0xD6CCFF])
    static let premiumTile = gradient([0xFFF8E6, 0xFFE7B8])
    static let waterButton = gradient([0x6FD3FF, 0x1C88C8])
    static let doseButton = gradient([0xA592FF, 0x6247E0])
    static let takenButton = gradient([0x4FD8A0, 0x2BA57A])

    static func stage(_ look: StageLook) -> LinearGradient {
        switch look {
        case .period: return gradient([0xD42A78, 0xE8478F, 0xFF6FB8])
        case .ttc: return gradient([0x7B61FF, 0xB565DE, 0xFF6FB8])
        case .pregnancy: return gradient([0xFFC54D, 0xFFA36B, 0xFF8E92])
        case .postpartum: return gradient([0xFF8AA3, 0xFF9E72, 0xFFB020])
        case .perimenopause: return gradient([0x7B61FF, 0x6F9CF0, 0x63D8FF])
        case .menopause: return gradient([0x4FB8FF, 0x40BCC6, 0x2FBF8F])
        case .discreet: return gradient([0x6A4FF0, 0x7C66F8, 0x8E7BFF])
        default: return gradient([0x7B61FF, 0xA14FD0, 0xE0559B])
        }
    }

    /// The clay icon each stage is drawn with; discreet mode gets the neutral heart.
    static func stageIcon(_ look: StageLook) -> String {
        switch look {
        case .period: return "clay_period"
        case .ttc: return "clay_ttc"
        case .pregnancy: return "clay_pregnancy"
        case .postpartum: return "clay_postpartum"
        case .perimenopause: return "clay_perimenopause"
        case .menopause: return "clay_menopause"
        case .discreet: return "clay_heart"
        default: return "clay_cycle"
        }
    }

    /// Pregnancy and postpartum are too light for white type, as in the app.
    static func stageInk(_ look: StageLook) -> Color {
        look == .pregnancy || look == .postpartum ? warmInk : .white
    }
}

/// The clay finish over any shape: light from above, a soft shade below.
struct ClayFinish: ViewModifier {
    var radius: CGFloat

    func body(content: Content) -> some View {
        content.overlay(
            RoundedRectangle(cornerRadius: radius, style: .continuous)
                .fill(LinearGradient(colors: [.white.opacity(0.28), .clear, .black.opacity(0.07)], startPoint: .top, endPoint: .bottom))
                .allowsHitTesting(false)
        )
    }
}

extension View {
    /// A clay surface: [fill] with its gloss, clipped to the app's rounded corners.
    func clay<S: ShapeStyle>(_ fill: S, radius: CGFloat) -> some View {
        background(fill, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
            .modifier(ClayFinish(radius: radius))
    }
}

extension WidgetContent {
    var stageFill: LinearGradient { SadoraPalette.stage(stage.look) }
    var stageInk: Color { SadoraPalette.stageInk(stage.look) }
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

/// The stage: the one number, what it counts, and its clay icon. Every size and the watch
/// draw it the same way; only [bigSize] changes.
struct StageTile: View {
    let content: WidgetContent
    var bigSize: CGFloat = 52

    var body: some View {
        let stage = content.stage
        VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .top) {
                Text(stage.title).font(.system(size: 12, weight: .heavy, design: .rounded)).lineLimit(1)
                Spacer(minLength: 0)
                Image(SadoraPalette.stageIcon(stage.look)).resizable().scaledToFit().frame(width: 38, height: 38)
                    .shadow(color: .black.opacity(0.2), radius: 4, y: 3)
            }
            Spacer(minLength: 0)
            HStack(alignment: .lastTextBaseline, spacing: 4) {
                Text(stage.big).font(.system(size: bigSize, weight: .black, design: .rounded)).minimumScaleFactor(0.6)
                    .shadow(color: .black.opacity(0.15), radius: 4, y: 3)
                if let unit = stage.unit { Text(unit).font(.system(size: 13, weight: .heavy, design: .rounded)) }
            }
            Spacer(minLength: 0)
            if let line1 = stage.line1 { Text(line1).font(.system(size: 12, weight: .heavy, design: .rounded)).lineLimit(1) }
            if let line2 = stage.line2 { Text(line2).font(.system(size: 11, weight: .semibold, design: .rounded)).lineLimit(1) }
        }
        .foregroundStyle(content.stageInk)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

/// The large widget's banner: the clay icon, the words, and the number on the right.
struct WideStageTile: View {
    let content: WidgetContent

    var body: some View {
        let stage = content.stage
        HStack(alignment: .center, spacing: 10) {
            Image(SadoraPalette.stageIcon(stage.look)).resizable().scaledToFit().frame(width: 46, height: 46)
                .shadow(color: .black.opacity(0.2), radius: 4, y: 3)
            VStack(alignment: .leading, spacing: 2) {
                Text(stage.title).font(.system(size: 12, weight: .heavy, design: .rounded)).lineLimit(1)
                if let line1 = stage.line1 { Text(line1).font(.system(size: 13, weight: .heavy, design: .rounded)).lineLimit(1) }
                if let line2 = stage.line2 { Text(line2).font(.system(size: 12, weight: .semibold, design: .rounded)).lineLimit(1) }
            }
            Spacer(minLength: 0)
            Text(stage.big).font(.system(size: 40, weight: .black, design: .rounded))
            if let unit = stage.unit { Text(unit).font(.system(size: 13, weight: .heavy, design: .rounded)) }
        }
        .foregroundStyle(content.stageInk)
        .accessibilityElement(children: .combine)
    }
}

/// A clay tile: its icon, a title, a value and, optionally, the button that changes it.
struct ValueTile<Action: View, Fill: ShapeStyle>: View {
    let icon: String?
    let title: String
    let value: String
    let fill: Fill
    let accent: Color
    @ViewBuilder var action: () -> Action

    var body: some View {
        HStack(spacing: 7) {
            if let icon { Image(icon).resizable().scaledToFit().frame(width: 30, height: 30) }
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.system(size: 11, weight: .heavy, design: .rounded)).foregroundStyle(accent).lineLimit(1)
                if !value.isEmpty {
                    Text(value).font(.system(size: 15, weight: .black, design: .rounded)).foregroundStyle(SadoraPalette.ink).lineLimit(1).minimumScaleFactor(0.7)
                }
            }
            Spacer(minLength: 0)
            action()
        }
        .padding(.horizontal, 9)
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .clay(fill, radius: 18)
    }
}

/// A raised clay pill, 44pt so a thumb finds it.
struct PillLabel: View {
    let text: String
    let fill: LinearGradient

    var body: some View {
        Text(text)
            .font(.system(size: 13, weight: .black, design: .rounded))
            .foregroundStyle(.white)
            .frame(minWidth: 44, minHeight: 38)
            .padding(.horizontal, 4)
            .clay(fill, radius: 19)
            .shadow(color: .black.opacity(0.18), radius: 4, y: 3)
    }
}
