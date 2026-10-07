import SwiftUI

@main struct PotatoSupervisorApp: App {
    @StateObject private var model = StudyModel()
    @StateObject private var diary = StudyDiary()
    @Environment(\.scenePhase) private var phase
    var body: some Scene {
        WindowGroup {
            StudyContentView(model: model, diary: diary)
                .task { model.refresh() }
                .onChange(of: phase) { _, value in
                    if value == .active { model.refresh() } else { model.stopSound() }
                }
        }
    }
}
