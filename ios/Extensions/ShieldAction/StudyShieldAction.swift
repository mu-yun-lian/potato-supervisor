import FamilyControls
import ManagedSettings
import UserNotifications

final class StudyShieldAction: ShieldActionDelegate {
    override func handle(action: ShieldAction, for application: ApplicationToken,
                         completionHandler: @escaping (ShieldActionResponse) -> Void) {
        switch action {
        case .primaryButtonPressed: completionHandler(.close)
        case .secondaryButtonPressed:
            if #available(iOS 26.5, *) {
                completionHandler(.openParentalControlsApp)
            } else {
                // A notification is a user-operated fallback, not a background app-launch trick.
                let content = UNMutableNotificationContent()
                content.title = "回到土豆监督员"
                content.body = "打开土豆监督员，在当前学习时段申请搜资料或休息。未申请成功前，目标应用保持拦截。"
                UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "potato-grant-request", content: content, trigger: nil)) { _ in
                    completionHandler(.close)
                }
            }
        default: completionHandler(.close)
        }
    }
}
