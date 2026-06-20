import XCTest
@testable import Strand

/// Pins the WHOOP 4.0 SET_ALARM_TIME payload to the exact bytes captured from the official WHOOP
/// app via HCI btsnoop log (PR #535). The captured frame for epoch 1781912880 (02:48 local, UTC+3)
/// was: AA 10 00 57 23 29 42 01 30 D5 35 6A 00 00 00 00 86 3B 9C 9F — the 9-byte payload is
/// [01 30 D5 35 6A 00 00 00 00]. Without the trailing [00 00] haptic-mode field the strap ACKs
/// but never fires the haptic (the silent-alarm bug from issue #1).
final class SetAlarmPayloadTests: XCTestCase {

    func testWireCapture_epoch1781912880_matchesOfficialApp() {
        // 1781912880 = 0x6A35D530 → LE: 0x30, 0xD5, 0x35, 0x6A
        XCTAssertEqual(
            WhoopCommand.setAlarmPayload(epochSec: 1_781_912_880),
            [0x01, 0x30, 0xD5, 0x35, 0x6A, 0x00, 0x00, 0x00, 0x00]
        )
    }

    func testLength_isNineBytes() {
        XCTAssertEqual(WhoopCommand.setAlarmPayload(epochSec: 0).count, 9)
    }

    func testLeadingByte_isFormByte0x01() {
        XCTAssertEqual(WhoopCommand.setAlarmPayload(epochSec: 0)[0], 0x01)
    }

    func testEpochField_isU32LittleEndian() {
        // 0x11223344 → LE: 0x44, 0x33, 0x22, 0x11
        let p = WhoopCommand.setAlarmPayload(epochSec: 0x11223344)
        XCTAssertEqual(Array(p[1..<5]), [0x44, 0x33, 0x22, 0x11])
    }

    func testSubsecondsField_isAlwaysZero() {
        let p = WhoopCommand.setAlarmPayload(epochSec: 1_781_912_880)
        XCTAssertEqual(p[5], 0x00)
        XCTAssertEqual(p[6], 0x00)
    }

    func testHapticModeField_isAlwaysZero() {
        let p = WhoopCommand.setAlarmPayload(epochSec: 1_781_912_880)
        XCTAssertEqual(p[7], 0x00)
        XCTAssertEqual(p[8], 0x00)
    }
}
