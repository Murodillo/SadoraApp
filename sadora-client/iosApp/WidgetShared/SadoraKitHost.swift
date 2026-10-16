import Foundation
import SadoraKit

/// One `WidgetHost` per process, built from the target's own Info.plist: the App Group
/// its snapshot lives in, and the Keychain group holding the companion session.
enum SadoraKitHost {
    static let shared: WidgetHost = WidgetHost(
        appGroup: info("SadoraAppGroup") ?? "group.uz.sadora.app",
        keychainGroup: info("SadoraKeychainGroup")
    )

    private static func info(_ key: String) -> String? {
        (Bundle.main.object(forInfoDictionaryKey: key) as? String).flatMap { $0.isEmpty ? nil : $0 }
    }
}

extension WidgetContent {
    /// Pregnancy and postpartum draw on the warm palette, as the app does.
    var isWarm: Bool { stage.unit != nil && stage.phase == nil && !discreet }
}
