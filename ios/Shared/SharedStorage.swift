import Foundation
import FamilyControls
import Darwin

struct StudyEnvelope: Codable {
    var schema = 1
    var settings = StudyConfig()
    var selection = FamilyActivitySelection()
    var session: StudySession?
}

// Host and extensions are separate processes. Every read/update uses the same file lock.
// Do not nest transaction calls; do not silently replace corrupt data with a new active session.
enum SharedStorage {
    static var groupID: String {
        Bundle.main.object(forInfoDictionaryKey: "PotatoAppGroup") as? String ?? ""
    }
    static func directory() throws -> URL {
        guard !groupID.isEmpty,
              let url = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupID) else {
            throw StudyError.message("App Group 尚未配置或未获得签名权限。")
        }
        return url
    }
    static func transaction<T>(_ body: (inout StudyEnvelope, (StudyEnvelope) throws -> Void) throws -> T) throws -> T {
        let root = try directory()
        let lock = open(root.appendingPathComponent("study.lock").path, O_CREAT | O_RDWR, S_IRUSR | S_IWUSR)
        guard lock >= 0 else { throw StudyError.message("无法打开共享数据锁。") }
        defer { close(lock) }
        guard flock(lock, LOCK_EX) == 0 else { throw StudyError.message("无法锁定共享状态。") }
        defer { flock(lock, LOCK_UN) }
        let file = root.appendingPathComponent("study-v1.json")
        var state: StudyEnvelope
        if FileManager.default.fileExists(atPath: file.path) {
            let data = try Data(contentsOf: file)
            guard data.count <= 1_048_576 else { throw StudyError.message("共享状态文件过大，请结束并清理数据。") }
            state = try JSONDecoder().decode(StudyEnvelope.self, from: data)
            guard state.schema == 1 else { throw StudyError.message("共享数据版本不受支持。") }
        } else { state = StudyEnvelope() }
        let stopped = try? String(contentsOf: root.appendingPathComponent("stop-session.txt"), encoding: .utf8)
        if let id = state.session?.id, stopped == id { state.session?.end("已请求结束") }
        let save: (StudyEnvelope) throws -> Void = { value in
            try JSONEncoder().encode(value).write(to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        }
        return try body(&state, save)
    }
    static func read() throws -> StudyEnvelope { try transaction { state, _ in state } }
    static func markStopped(_ id: String) throws {
        let file = try directory().appendingPathComponent("stop-session.txt")
        try Data(id.utf8).write(to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }
}
