import DeviceActivity

final class StudyMonitor: DeviceActivityMonitor {
    override func eventDidReachThreshold(_ event: DeviceActivityEvent.Name, activity: DeviceActivityName) {
        guard event == ScreenTimeCoordinator.threshold else { return }
        ScreenTimeCoordinator.handle(activity: activity, thresholdReached: true)
    }
    override func intervalDidEnd(for activity: DeviceActivityName) {
        ScreenTimeCoordinator.handle(activity: activity, thresholdReached: false)
    }
}
