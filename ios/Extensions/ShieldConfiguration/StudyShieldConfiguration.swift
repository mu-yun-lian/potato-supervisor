import UIKit
import ManagedSettings
import ManagedSettingsUI

final class StudyShieldConfiguration: ShieldConfigurationDataSource {
    override func configuration(shielding application: Application) -> ShieldConfiguration {
        let session = (try? SharedStorage.read())?.session
        let name = session?.phase == .cooldown ? "potato-mine-angry" : "potato-mine"
        let icon = CharacterImage.load(name)
        let cooled = session?.phase == .cooldown
        return ShieldConfiguration(backgroundBlurStyle: .systemMaterial,
            backgroundColor: UIColor(red: 0.96, green: 0.97, blue: 0.89, alpha: 1), icon: icon,
            title: .init(text: cooled ? "额度用完了，回去学习" : "先把你写下的任务想清楚", color: .label),
            subtitle: .init(text: StudyDialogue.text(for: session), color: .secondaryLabel),
            primaryButtonLabel: .init(text: "回去学习", color: .white),
            primaryButtonBackgroundColor: UIColor(red: 0.26, green: 0.42, blue: 0.22, alpha: 1),
            secondaryButtonLabel: .init(text: cooled ? "查看学习任务" : "打开土豆申请短暂使用", color: .label))
    }
}
