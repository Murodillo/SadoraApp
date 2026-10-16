import AppIntents
import WidgetKit
import SadoraKit

/// The widget's water button. Runs in the extension: the glass is queued and drawn, then
/// sent with the widgets' own session; with no connection it waits for the next refresh.
struct AddWaterIntent: AppIntent {
    static var title: LocalizedStringResource = "Suv qo'shish"
    static var isDiscoverable = false

    @Parameter(title: "ml") var ml: Int

    init() {}
    init(ml: Int) { self.ml = ml }

    func perform() async throws -> some IntentResult {
        _ = try? await SadoraKitHost.shared.tapWater(ml: Int32(ml))
        return .result()
    }
}

/// A dose ticked on the widget.
struct MarkDoseIntent: AppIntent {
    static var title: LocalizedStringResource = "Dori ichildi"
    static var isDiscoverable = false

    @Parameter(title: "Dori") var medicationId: String
    @Parameter(title: "Kun") var day: String
    @Parameter(title: "Vaqt") var time: String

    init() {}
    init(medicationId: String, day: String, time: String) {
        self.medicationId = medicationId
        self.day = day
        self.time = time
    }

    func perform() async throws -> some IntentResult {
        _ = try? await SadoraKitHost.shared.tapDose(medicationId: medicationId, day: day, time: time, taken: true)
        return .result()
    }
}
