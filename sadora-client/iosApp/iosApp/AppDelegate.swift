import UIKit
import UserNotifications
import FirebaseCore
import FirebaseMessaging
import Shared

/// Firebase and APNs, which only UIKit's delegate callbacks can reach.
///
/// When to ask for permission and whom to register the token for is decided in Kotlin
/// (`IosPush`), so iOS and Android follow one rule; this class only carries the tokens
/// across.
final class AppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // GoogleService-Info.plist is gitignored. A build without it runs without push
        // rather than crashing inside FirebaseApp.configure().
        guard Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else {
            return true
        }
        FirebaseApp.configure()
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
        registerDoseActions()
        return true
    }

    /// "Taken" and "Skipped" on a medication reminder (the server sends it with category
    /// DOSE). A paired Apple Watch shows the same buttons on its copy of the notification.
    private func registerDoseActions() {
        let taken = UNNotificationAction(identifier: "DOSE_TAKEN", title: NSLocalizedString("dose.taken", value: "Ichdim", comment: ""))
        let skipped = UNNotificationAction(identifier: "DOSE_SKIPPED", title: NSLocalizedString("dose.skipped", value: "O'tkazdim", comment: ""))
        let dose = UNNotificationCategory(identifier: "DOSE", actions: [taken, skipped], intentIdentifiers: [])
        UNUserNotificationCenter.current().setNotificationCategories([dose])
    }

    /// A silent push: something a widget shows changed elsewhere. Fetch and redraw.
    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        guard (userInfo["type"] as? String) == "widget_refresh" else {
            completionHandler(.noData)
            return
        }
        IosWidgets.shared.refresh { completionHandler(.newData) }
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        // FCM issues its token only once it has the APNs one.
        guard FirebaseApp.app() != nil else { return }
        Messaging.messaging().apnsToken = deviceToken
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken else { return }
        IosPush.shared.onToken(value: fcmToken)
    }

    /// Without this iOS shows nothing for a notification that arrives while the app is open.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }

    /// A tapped notification. A message or consultation push carries `link`
    /// (`sadora://conversation/{id}`), which goes to the shared code like any other
    /// sadora:// link; it opens the thread once she is inside the app.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let link = response.notification.request.content.userInfo["link"] as? String
        // A dose answered from the notification: recorded without opening the app.
        if let link, response.actionIdentifier == "DOSE_TAKEN" || response.actionIdentifier == "DOSE_SKIPPED" {
            IosWidgets.shared.answerDose(link: link, taken: response.actionIdentifier == "DOSE_TAKEN") { completionHandler() }
            return
        }
        if let link {
            IosAppLinks.shared.offer(url: link)
        }
        completionHandler()
    }
}
