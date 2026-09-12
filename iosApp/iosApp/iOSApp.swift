import SwiftUI
// IosAppLinks, the shared code's side of a sadora:// link.
import Shared

@main
struct iOSApp: App {
    // Push needs UIKit's application delegate, which a SwiftUI App does not have by itself.
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                // A sadora:// link: an invite code, or the browser sending her back
                // from a wearable provider's consent page. Read by the shared code.
                .onOpenURL { url in IosAppLinks.shared.offer(url: url.absoluteString) }
        }
    }
}
