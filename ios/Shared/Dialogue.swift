import Foundation

enum StudyDialogue {
    struct Book: Decodable { let lines: [Line] }
    struct Line: Decodable { let id: String; let scene: String; let tone: String; let text: String }
    static func text(for s: StudySession?) -> String {
        guard let s else { return "打开土豆监督员，写下今天要完成的那件事。" }
        let scene = s.phase == .cooldown ? "cooldown" : "entry"
        guard let url = Bundle.main.url(forResource: "dialogue", withExtension: "json"),
              let data = try? Data(contentsOf: url), let book = try? JSONDecoder().decode(Book.self, from: data) else { return s.config.task }
        let lines = book.lines.filter { $0.scene == scene && $0.tone == s.config.tone }
        guard !lines.isEmpty else { return s.config.task }
        // A stable selection prevents extension refreshes from continuously changing the sentence.
        let seed = (s.id + String(s.round) + String(s.grants)).utf8.reduce(UInt64(14695981039346656037)) { ($0 ^ UInt64($1)) &* 1099511628211 }
        var text = lines[Int(seed % UInt64(lines.count))].text
        let values = ["goal": s.config.goal, "task": s.config.task, "note": s.config.note,
                      "grantDuration": "\(s.config.grantMinutes)分钟", "remainingDuration": "系统监测中的当前额度",
                      "cooldownDuration": "\(s.config.cooldownMinutes)分钟", "budgetDuration": "\(s.config.budgetMinutes)分钟",
                      "grants": String(s.grants), "maxGrants": String(s.config.maxGrants)]
        for (key, value) in values { text = text.replacingOccurrences(of: "{\(key)}", with: value) }
        while let range = text.range(of: "\\{[^{}]+\\}", options: .regularExpression) { text.replaceSubrange(range, with: "当前约定") }
        return text
    }
}
