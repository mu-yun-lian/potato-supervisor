import Foundation
import SwiftUI

struct StudyEntry: Codable, Identifiable {
    var id: String
    var content: String
    var minutes: Int?
}

@MainActor final class StudyDiary: ObservableObject {
    @Published private(set) var entries: [StudyEntry] = []
    @Published var message = ""
    private var corrupted = false
    private var file: URL { FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("study-diary-v1.json") }
    init() {
        guard FileManager.default.fileExists(atPath: file.path) else { return }
        do {
            let data = try Data(contentsOf: file)
            guard data.count <= 1_048_576 else { throw StudyError.message("日记数据过大。") }
            entries = try JSONDecoder().decode([StudyEntry].self, from: data)
        } catch { corrupted = true; message = "学习记录无法读取，原文件已保留，不能继续覆盖保存。" }
    }
    func save(date: Date, content: String, minutes: String) {
        do {
            guard !corrupted else { throw StudyError.message(message) }
            let text = content.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !text.isEmpty, text.count <= 2000 else { throw StudyError.message("请填写学习内容，最多 2000 字。") }
            let value = minutes.trimmingCharacters(in: .whitespacesAndNewlines)
            guard value.isEmpty || (Int(value).map { (1...1440).contains($0) } == true) else { throw StudyError.message("分钟数可留空，或填写 1–1440。") }
            let formatter = DateFormatter(); formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.calendar = Calendar(identifier: .gregorian); formatter.dateFormat = "yyyy-MM-dd"
            let id = formatter.string(from: date)
            var next = entries.filter { $0.id != id }
            next.append(StudyEntry(id: id, content: text, minutes: Int(value)))
            try write(next); message = "当天记录已保存，同一天再次保存会更新。"
        } catch { message = error.localizedDescription }
    }
    func delete(_ offsets: IndexSet) {
        do {
            guard !corrupted else { throw StudyError.message(message) }
            var next = entries; next.remove(atOffsets: offsets); try write(next)
        } catch { message = error.localizedDescription }
    }
    func export() throws -> URL {
        guard !corrupted else { throw StudyError.message(message) }
        let target = FileManager.default.temporaryDirectory.appendingPathComponent("potato-study-diary.json")
        try JSONEncoder().encode(entries).write(to: target, options: .atomic); return target
    }
    private func write(_ next: [StudyEntry]) throws {
        let sorted = next.sorted { $0.id > $1.id }
        let data = try JSONEncoder().encode(sorted)
        guard data.count <= 1_048_576 else { throw StudyError.message("学习记录已达到当前原型的容量上限，请先导出。") }
        try data.write(to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        entries = sorted
    }
}
