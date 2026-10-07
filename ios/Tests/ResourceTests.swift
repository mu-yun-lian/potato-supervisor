import XCTest
import UIKit
import AVFoundation
@testable import PotatoSupervisor

final class ResourceTests: XCTestCase {
    func testSpritesDecodeInHostAndShieldExtension() throws {
        for name in ["potato-mine", "potato-mine-blink", "potato-mine-angry", "potato-mine-explosion"] {
            let image = try XCTUnwrap(CharacterImage.load(name), "Host sprite missing or undecodable: \(name)")
            XCTAssertGreaterThan(image.size.width, 0)
            XCTAssertGreaterThan(image.size.height, 0)
        }
        let plugins = try XCTUnwrap(Bundle.main.builtInPlugInsURL)
        let shield = try XCTUnwrap(Bundle(url: plugins.appendingPathComponent("StudyShieldConfigurationExtension.appex")))
        for name in ["potato-mine", "potato-mine-angry"] {
            XCTAssertNotNil(CharacterImage.load(name, bundle: shield), "Shield sprite missing: \(name)")
        }
    }
    func testAudioFilesAreDecodableByApplePlayer() throws {
        for name in ["appear", "disappear", "warning", "explosion"] {
            let url = try XCTUnwrap(Bundle.main.url(forResource: name, withExtension: "wav"))
            let player = try AVAudioPlayer(contentsOf: url)
            XCTAssertGreaterThan(player.duration, 0.1)
        }
    }
    func testDialogueBankLoadsInHostAndShieldExtension() throws {
        let plugins = try XCTUnwrap(Bundle.main.builtInPlugInsURL)
        let shield = try XCTUnwrap(Bundle(url: plugins.appendingPathComponent("StudyShieldConfigurationExtension.appex")))
        for bundle in [Bundle.main, shield] {
            let url = try XCTUnwrap(bundle.url(forResource: "dialogue", withExtension: "json"))
            let book = try JSONDecoder().decode(StudyDialogue.Book.self, from: Data(contentsOf: url))
            XCTAssertEqual(book.lines.count, 257)
            XCTAssertEqual(Set(book.lines.map(\.id)).count, 257)
        }
    }
}
