import SwiftUI

@main
struct iOSApp: App {
    // Push needs UIKit's application delegate, which a SwiftUI App does not have by itself.
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
