import Foundation
import Darwin

struct StudyConfig: Codable, Equatable {
    var goal = "完成今天的学习计划"
    var task = "先完成一道题"
    var note = ""
    var studyMinutes = 60
    var grantMinutes = 2
    var maxGrants = 3
    var budgetMinutes = 6
    var cooldownMinutes = 20
    var tone = "snark"
    var sound = false

    func validate() throws {
        guard !goal.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !task.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              goal.count <= 200, task.count <= 500, note.count <= 2000 else {
            throw StudyError.message("请填写目标与任务，目标最多 200 字、任务 500 字、寄语 2000 字。")
        }
        guard (15...720).contains(studyMinutes), (1...5).contains(grantMinutes),
              (1...20).contains(maxGrants), (1...100).contains(budgetMinutes),
              (15...720).contains(cooldownMinutes), ["gentle", "snark", "roast"].contains(tone) else {
            throw StudyError.message("时间、次数或语气设置超出范围。")
        }
    }
}

enum StudyError: LocalizedError {
    case message(String)
    var errorDescription: String? { if case .message(let text) = self { return text }; return nil }
}

struct StudyClock {
    var wall: Date
    var uptime: TimeInterval
    // Includes sleep; an awake-only clock would misclassify normal screen locking.
    static var now: StudyClock {
        StudyClock(wall: Date(), uptime: Double(clock_gettime_nsec_np(CLOCK_MONOTONIC_RAW)) / 1_000_000_000)
    }
}

enum StudyPhase: String, Codable { case gate, arming, allowance, cooldown, interrupted, ended }
enum GrantReason: String, Codable { case study, rest }

struct StudySession: Codable, Equatable {
    let id: String
    let config: StudyConfig
    let startedAt: Date
    let startedUptime: TimeInterval
    let deadline: Date
    var phase: StudyPhase = .gate
    var round = 1
    var grants = 0
    var grantedSeconds = 0
    var totalGrants = 0
    var segmentID: String?
    var segmentSeconds = 0
    var segmentWindowUntil: Date?
    var reason: GrantReason?
    var cooldownUntil: Date?
    var lastMessage = ""
    var confirmedThresholds = 0

    init(config: StudyConfig, clock: StudyClock) throws {
        try config.validate()
        self.id = UUID().uuidString
        self.config = config
        self.startedAt = clock.wall
        self.startedUptime = clock.uptime
        self.deadline = clock.wall.addingTimeInterval(Double(config.studyMinutes * 60))
    }
    var active: Bool { phase != .ended && phase != .interrupted }
    var shieldsRequired: Bool { active && phase != .allowance }
    var nextGrantSeconds: Int { min(config.grantMinutes * 60, max(0, config.budgetMinutes * 60 - grantedSeconds)) }
    var canGrant: Bool { phase == .gate && grants < config.maxGrants && nextGrantSeconds > 0 }

    mutating func reconcile(_ clock: StudyClock) {
        guard active else { return }
        let wallElapsed = clock.wall.timeIntervalSince(startedAt)
        let uptimeElapsed = clock.uptime - startedUptime
        guard uptimeElapsed >= 0, abs(wallElapsed - uptimeElapsed) <= 90 else {
            phase = .interrupted; segmentID = nil
            lastMessage = "设备重启或系统时间发生变化，请重新开始。"; return
        }
        if clock.wall >= deadline { end("学习时段结束"); return }
        if phase == .allowance, let until = segmentWindowUntil, clock.wall >= until {
            completeSegment(thresholdReached: false, clock: clock)
        }
        if phase == .cooldown, let until = cooldownUntil, clock.wall >= until {
            phase = .gate; round += 1; grants = 0; grantedSeconds = 0
            segmentID = nil; segmentSeconds = 0; segmentWindowUntil = nil; cooldownUntil = nil; reason = nil
            lastMessage = "冷却结束，打开目标应用仍先显示学习提醒。"
        }
    }
    mutating func reserveGrant(reason: GrantReason, clock: StudyClock) throws -> String {
        reconcile(clock)
        guard canGrant else { throw StudyError.message("当前不能继续放行，请回到学习任务。") }
        phase = .arming; segmentID = UUID().uuidString
        segmentWindowUntil = clock.wall.addingTimeInterval(900)
        segmentSeconds = nextGrantSeconds; self.reason = reason
        return segmentID!
    }
    mutating func commitGrant(id: String) throws {
        guard phase == .arming, segmentID == id else { throw StudyError.message("放行请求已失效。") }
        grants += 1; totalGrants += 1; grantedSeconds += segmentSeconds
        phase = .allowance; lastMessage = "已放行，系统按选定应用的使用量监测；这里不显示未经确认的实时余额。"
    }
    mutating func cancelReservation(id: String) {
        guard phase == .arming, segmentID == id else { return }
        phase = .gate; segmentID = nil; segmentSeconds = 0; segmentWindowUntil = nil; reason = nil
        lastMessage = "监测未建立，未扣放行次数，也未解除拦截。"
    }
    @discardableResult mutating func finishSegment(id: String, thresholdReached: Bool, clock: StudyClock) -> Bool {
        reconcile(clock)
        guard phase == .allowance, segmentID == id else { return false }
        completeSegment(thresholdReached: thresholdReached, clock: clock)
        return true
    }
    private mutating func completeSegment(thresholdReached: Bool, clock: StudyClock) {
        if thresholdReached { confirmedThresholds += 1 }
        segmentID = nil; segmentWindowUntil = nil; phase = .gate
        lastMessage = thresholdReached ? "系统报告本段使用达到门槛。" : "十五分钟监测窗口已结束；未确认的实际用量不作推测，已发放额度不返还。"
        if grants >= config.maxGrants || grantedSeconds >= config.budgetMinutes * 60 {
            phase = .cooldown
            cooldownUntil = clock.wall.addingTimeInterval(Double(config.cooldownMinutes * 60))
        }
    }
    mutating func end(_ message: String = "主动结束") {
        phase = .ended; segmentID = nil; segmentWindowUntil = nil; cooldownUntil = nil; lastMessage = message
    }
}
