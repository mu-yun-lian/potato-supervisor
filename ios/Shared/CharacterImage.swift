import UIKit

enum CharacterImage {
    // Sprites are loose bundle resources, not named entries in an asset catalog.
    static func load(_ name: String, bundle: Bundle = .main) -> UIImage? {
        guard let url = bundle.url(forResource: name, withExtension: "png") else { return nil }
        return UIImage(contentsOfFile: url.path)
    }
}
