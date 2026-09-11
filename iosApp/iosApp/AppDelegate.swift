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
        return true
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
}
