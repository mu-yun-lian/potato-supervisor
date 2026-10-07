import Foundation
import FamilyControls
import DeviceActivity
import ManagedSettings

enum ScreenTimeCoordinator {
    static let prefix = "potato."
    static let threshold = DeviceActivityEvent.Name("grant-used")
    static var center: DeviceActivityCenter { DeviceActivityCenter() }
    static var store: ManagedSettingsStore { ManagedSettingsStore(named: .init("potato-study")) }
    static func sessionName(_ s: StudySession) -> DeviceActivityName { .init(prefix + s.id + ".session") }
    static func segmentName(_ s: StudySession, _ id: String) -> DeviceActivityName { .init(prefix + s.id + ".grant." + id) }
    static func cooldownName(_ s: StudySession) -> DeviceActivityName { .init(prefix + s.id + ".cooldown." + String(s.round)) }
    static func stopOwnedMonitors() {
        center.stopMonitoring(center.activities.filter { $0.rawValue.hasPrefix(prefix) })
    }
    static func schedule(start: Date, end: Date) throws -> DeviceActivitySchedule {
        guard end.timeIntervalSince(start) >= 900 else { throw StudyError.message("监测窗口至少十五分钟。") }
        let fields: Set<Calendar.Component> = [.year, .month, .day, .hour, .minute, .second]
        let calendar = Calendar.current
        return DeviceActivitySchedule(intervalStart: calendar.dateComponents(fields, from: start),
                                      intervalEnd: calendar.dateComponents(fields, from: end), repeats: false)
    }
    static func apply(_ state: StudyEnvelope) {
        if let s = state.session, s.shieldsRequired {
            store.shield.applications = state.selection.applicationTokens
        } else { store.clearAllSettings() }
    }
    private static func registerCooldown(state: inout StudyEnvelope, save: (StudyEnvelope) throws -> Void) {
        guard let s = state.session, s.phase == .cooldown, let until = s.cooldownUntil,
              !center.activities.contains(cooldownName(s)) else { return }
        do {
            let start = until.addingTimeInterval(-Double(s.config.cooldownMinutes * 60))
            try center.startMonitoring(cooldownName(s), during: schedule(start: start, end: until))
        } catch {
            state.session?.phase = .interrupted; state.session?.lastMessage = "冷却监测注册失败，监督已停止。"
            try? save(state); stopOwnedMonitors(); store.clearAllSettings()
        }
    }
    static func refresh(authorized: Bool? = nil) throws -> StudyEnvelope {
        try SharedStorage.transaction { state, save in
            let prior = state.session
            state.session?.reconcile(.now)
            if authorized == false {
                state.session?.end("屏幕使用时间授权已撤销，需要重新授权和选择应用。")
                state.selection = FamilyActivitySelection()
            }
            if let s = state.session, s.phase == .arming, let id = s.segmentID {
                center.stopMonitoring([segmentName(s, id)])
                state.session?.cancelReservation(id: id)
            }
            if let s = state.session, s.active, !center.activities.contains(sessionName(s)) {
                state.session?.phase = .interrupted
                state.session?.segmentID = nil
                state.session?.lastMessage = "系统监测不再存在，请结束后重新开始。"
            }
            if let s = state.session, s.phase == .allowance,
               s.segmentID.map({ center.activities.contains(segmentName(s, $0)) }) != true {
                state.session?.phase = .interrupted; state.session?.segmentID = nil
                state.session?.lastMessage = "本段使用监测不再存在，监督已停止。"
            }
            try save(state); apply(state)
            registerCooldown(state: &state, save: save)
            if let previous = prior, let id = previous.segmentID, state.session?.segmentID != id {
                center.stopMonitoring([segmentName(previous, id)])
            }
            if let previous = prior, previous.phase == .cooldown, state.session?.phase != .cooldown {
                center.stopMonitoring([cooldownName(previous)])
            }
            if state.session?.active != true { stopOwnedMonitors() }
            return state
        }
    }
    static func start(config: StudyConfig) throws {
        try SharedStorage.transaction { state, save in
            guard state.session?.active != true else { throw StudyError.message("请先结束当前时段。") }
            guard !state.selection.applicationTokens.isEmpty, state.selection.categoryTokens.isEmpty,
                  state.selection.webDomainTokens.isEmpty else { throw StudyError.message("首版请只选择具体应用，不选择分类或网站。") }
            let clock = StudyClock.now
            let s = try StudySession(config: config, clock: clock)
            stopOwnedMonitors(); state.session = s; state.settings = config
            try save(state)
            do {
                try center.startMonitoring(sessionName(s), during: schedule(start: clock.wall, end: s.deadline))
                apply(state)
            } catch {
                state.session?.phase = .interrupted; state.session?.lastMessage = "学习时段监测注册失败。"
                try? save(state); store.clearAllSettings(); stopOwnedMonitors(); throw error
            }
        }
    }
    static func grant(_ reason: GrantReason) throws {
        try SharedStorage.transaction { state, save in
            guard var s = state.session else { throw StudyError.message("请先开始学习。") }
            let previous = s
            let clock = StudyClock.now
            let id = try s.reserveGrant(reason: reason, clock: clock)
            state.session = s; try save(state) // Durable reservation, before touching system enforcement.
            let name = segmentName(s, id)
            do {
                let event = DeviceActivityEvent(applications: state.selection.applicationTokens,
                    threshold: DateComponents(minute: s.segmentSeconds / 60), includesPastActivity: false)
                try center.startMonitoring(name, during: schedule(start: clock.wall, end: clock.wall.addingTimeInterval(900)), events: [threshold: event])
                try s.commitGrant(id: id); state.session = s
                try save(state) // Quota is committed before clearing shields.
                apply(state)
            } catch {
                center.stopMonitoring([name]); state.session = previous
                state.session?.lastMessage = "监测或保存失败，未放行，额度不作新增。"
                try? save(state); store.shield.applications = state.selection.applicationTokens
                throw error
            }
        }
    }
    static func end() throws {
        // An explicit stop always clears our own settings, even if persistence fails.
        defer { stopOwnedMonitors(); store.clearAllSettings() }
        try SharedStorage.transaction { state, save in
            if let id = state.session?.id { try SharedStorage.markStopped(id) }
            state.session?.end(); try save(state)
        }
    }
    static func handle(activity: DeviceActivityName, thresholdReached: Bool) {
        do {
            try SharedStorage.transaction { state, save in
                guard var s = state.session, s.active else { store.clearAllSettings(); return }
                // Ignore callbacks from cancelled monitors, previous sessions and old grants.
                let rootName = sessionName(s)
                let grantName = s.segmentID.map { segmentName(s, $0) }
                let coolingName = cooldownName(s)
                s.reconcile(.now)
                if activity == rootName { s.end("学习时段结束") }
                else if activity == grantName {
                    if let id = s.segmentID { _ = s.finishSegment(id: id, thresholdReached: thresholdReached, clock: .now) }
                } else if activity == coolingName { s.reconcile(.now) }
                else { return }
                state.session = s; try save(state)
                // Reapply before stopping the grant monitor, closing rapid reopen windows.
                apply(state)
                registerCooldown(state: &state, save: save)
                if !s.active { stopOwnedMonitors() } else { center.stopMonitoring([activity]) }
            }
        } catch {
            // No fabricated state after I/O corruption. Only our store is cleared.
            stopOwnedMonitors(); store.clearAllSettings()
        }
    }
}
