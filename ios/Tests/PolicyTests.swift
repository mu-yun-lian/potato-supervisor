import XCTest

final class PolicyTests: XCTestCase {
    private let origin = Date(timeIntervalSince1970: 1_800_000_000)
    private func clock(_ seconds: Double) -> StudyClock { StudyClock(wall: origin.addingTimeInterval(seconds), uptime: 1000 + seconds) }
    private func fresh(_ config: StudyConfig = StudyConfig()) throws -> StudySession { try StudySession(config: config, clock: clock(0)) }
    private func grant(_ s: inout StudySession, at time: Double) throws -> String {
        let id = try s.reserveGrant(reason: .rest, clock: clock(time)); try s.commitGrant(id: id); return id
    }
    func testThreeGrantsThenCooldownAndRapidReopenStaysShielded() throws {
        var s = try fresh()
        for i in 0..<3 {
            let start = Double(i * 130)
            let id = try grant(&s, at: start)
            XCTAssertFalse(s.shieldsRequired)
            XCTAssertTrue(s.finishSegment(id: id, thresholdReached: true, clock: clock(start + 120)))
        }
        XCTAssertEqual(s.phase, .cooldown); XCTAssertTrue(s.shieldsRequired)
        let until = s.cooldownUntil
        for time in [381.0, 382, 383, 384] {
            s.reconcile(clock(time)); XCTAssertTrue(s.shieldsRequired)
            XCTAssertThrowsError(try s.reserveGrant(reason: .rest, clock: clock(time)))
            XCTAssertEqual(s.cooldownUntil, until); XCTAssertEqual(s.grants, 3)
        }
    }
    func testFailedReservationDoesNotChargeAndDuplicateCommitRejected() throws {
        var s = try fresh()
        let id = try s.reserveGrant(reason: .study, clock: clock(0))
        XCTAssertTrue(s.shieldsRequired); XCTAssertEqual(s.grants, 0)
        s.cancelReservation(id: id); XCTAssertEqual(s.grantedSeconds, 0)
        XCTAssertThrowsError(try s.commitGrant(id: id))
        let next = try grant(&s, at: 1)
        XCTAssertThrowsError(try s.commitGrant(id: next)); XCTAssertEqual(s.grants, 1)
    }
    func testStaleThresholdDoesNotFinishNewSegment() throws {
        var s = try fresh(); let old = try grant(&s, at: 0)
        XCTAssertTrue(s.finishSegment(id: old, thresholdReached: true, clock: clock(120)))
        let current = try grant(&s, at: 121)
        XCTAssertFalse(s.finishSegment(id: old, thresholdReached: true, clock: clock(122)))
        XCTAssertEqual(s.segmentID, current); XCTAssertEqual(s.phase, .allowance)
        XCTAssertEqual(s.confirmedThresholds, 1)
    }
    func testFinalPartialGrantHonorsSharedBudget() throws {
        var config = StudyConfig(); config.budgetMinutes = 5
        var s = try fresh(config)
        for i in 0..<2 {
            let id = try grant(&s, at: Double(i * 121))
            _ = s.finishSegment(id: id, thresholdReached: true, clock: clock(Double(i * 121 + 120)))
        }
        _ = try grant(&s, at: 242); XCTAssertEqual(s.segmentSeconds, 60); XCTAssertEqual(s.grantedSeconds, 300)
    }
    func testWallWindowExpiryDoesNotInventConfirmedUsage() throws {
        var s = try fresh(); _ = try grant(&s, at: 0)
        s.reconcile(clock(900))
        XCTAssertEqual(s.phase, .gate); XCTAssertTrue(s.shieldsRequired)
        XCTAssertEqual(s.grantedSeconds, 120); XCTAssertEqual(s.confirmedThresholds, 0)
    }
    func testColdExpiryMakesOneNewRoundWithoutUnshielding() throws {
        var config = StudyConfig(); config.maxGrants = 1
        var s = try fresh(config); let id = try grant(&s, at: 0)
        _ = s.finishSegment(id: id, thresholdReached: true, clock: clock(120))
        s.reconcile(clock(1320)); s.reconcile(clock(1321))
        XCTAssertEqual(s.round, 2); XCTAssertEqual(s.grants, 0); XCTAssertTrue(s.shieldsRequired)
    }
    func testSessionEndWinsAndOldCallbackCannotReapply() throws {
        var config = StudyConfig(); config.studyMinutes = 15
        var s = try fresh(config); let id = try grant(&s, at: 890)
        XCTAssertFalse(s.finishSegment(id: id, thresholdReached: true, clock: clock(910)))
        XCTAssertEqual(s.phase, .ended); XCTAssertFalse(s.shieldsRequired)
    }
    func testClockChangeAndRebootInterruptInsteadOfInventingTime() throws {
        var s = try fresh()
        s.reconcile(StudyClock(wall: origin.addingTimeInterval(1000), uptime: 1001))
        XCTAssertEqual(s.phase, .interrupted); XCTAssertFalse(s.shieldsRequired)
        var rebooted = try fresh()
        rebooted.reconcile(StudyClock(wall: origin.addingTimeInterval(120), uptime: 10))
        XCTAssertEqual(rebooted.phase, .interrupted)
    }
    func testCodableRoundTripKeepsBalanceAndSegmentIdentity() throws {
        var s = try fresh(); _ = try grant(&s, at: 0)
        let restored = try JSONDecoder().decode(StudySession.self, from: JSONEncoder().encode(s))
        XCTAssertEqual(restored, s)
    }
}
