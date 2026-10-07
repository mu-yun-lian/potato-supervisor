import SwiftUI
import FamilyControls
import UserNotifications
import AVFoundation

@MainActor final class StudyModel: ObservableObject {
    @Published var config = StudyConfig()
    @Published var selection = FamilyActivitySelection()
    @Published var session: StudySession?
    @Published var authorized = false
    @Published var message = ""
    @Published var bursting = false
    private var loaded = false
    private var player: AVAudioPlayer?

    func refresh() {
        authorized = AuthorizationCenter.shared.authorizationStatus == .approved
        do {
            let previous = session?.phase
            let value = try ScreenTimeCoordinator.refresh(authorized: authorized)
            session = value.session; selection = value.selection
            if !loaded { config = value.settings; loaded = true }
            if previous != .cooldown, session?.phase == .cooldown {
                bursting = true; play("explosion")
                Task { try? await Task.sleep(nanoseconds: 900_000_000); bursting = false }
            }
        } catch { message = error.localizedDescription }
    }
    func authorize() async {
        do { try await AuthorizationCenter.shared.requestAuthorization(for: .individual); message = "授权成功，请选择需要监督的应用。" }
        catch { message = error.localizedDescription }
        refresh()
    }
    func allowNotifications() async {
        do {
            let ok = try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert])
            message = ok ? "已允许提示通知。" : "未允许通知，仍可手动打开土豆申请使用。"
        } catch { message = error.localizedDescription }
    }
    func saveSettings() {
        perform {
            try config.validate()
            try SharedStorage.transaction { state, save in state.settings = config; try save(state) }
            message = "已保存，规则和语气从下次学习生效。"
        }
    }
    func saveSelection() {
        perform {
            guard authorized else { throw StudyError.message("请先授权屏幕使用时间。") }
            guard !selection.applicationTokens.isEmpty, selection.categoryTokens.isEmpty,
                  selection.webDomainTokens.isEmpty else { throw StudyError.message("请只选择具体应用，不选择分类或网站。") }
            try SharedStorage.transaction { state, save in
                state.session?.reconcile(.now)
                guard state.session?.active != true else { throw StudyError.message("开始后不能修改监督应用，请先结束。") }
                state.selection = selection; try save(state)
            }
            message = "监督应用已保存。"
        }
    }
    func start() {
        perform {
            guard authorized else { throw StudyError.message("请先授权屏幕使用时间。") }
            try ScreenTimeCoordinator.start(config: config); message = "已开始学习。"; play("appear")
        }
    }
    func grant(_ reason: GrantReason) {
        perform {
            guard authorized else { throw StudyError.message("授权已撤销。") }
            try ScreenTimeCoordinator.grant(reason); message = "本段放行已建立。"; play("warning")
        }
    }
    func end() { perform { try ScreenTimeCoordinator.end(); message = "本次监督已结束。"; play("disappear") } }
    private func perform(_ action: () throws -> Void) {
        do { try action() } catch { message = error.localizedDescription }
        refresh()
    }
    private func play(_ cue: String) {
        guard config.sound, let url = Bundle.main.url(forResource: cue, withExtension: "wav") else { return }
        do {
            try AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: [.mixWithOthers])
            player = try AVAudioPlayer(contentsOf: url); player?.volume = 0.35; player?.play()
        } catch { message = "音效暂时无法播放。" }
    }
    func stopSound() { player?.stop() }
}
