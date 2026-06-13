import XCTest
@testable import StrandAnalytics
import WhoopProtocol

final class DaytimeStressTests: XCTestCase {

    /// Fill one local hour-of-day with `n` 1 Hz HR samples at `bpm` (UTC, tz offset 0).
    private func hourHR(_ hour: Int, bpm: Int, n: Int = DaytimeStress.minHourHRSamples) -> [HRSample] {
        let base = hour * 3_600
        return (0..<n).map { HRSample(ts: base + $0, bpm: bpm) }
    }

    func testEmptyWhenNoHR() {
        XCTAssertEqual(DaytimeStress.analyze(hr: [], rr: []), .empty)
    }

    func testHourBelowGateIsUnscored() {
        // One waking hour with too few HR samples → present but unscored (honest gap).
        let hr = hourHR(9, bpm: 70, n: DaytimeStress.minHourHRSamples - 1)
        let r = DaytimeStress.analyze(hr: hr, rr: [])
        XCTAssertTrue(r.scored.isEmpty, "an under-gate hour must not be scored")
    }

    func testScoresMapOntoZeroToThree() {
        // Three calm hours + one tense hour (high HR). All scored values stay within 0…3.
        var hr: [HRSample] = []
        hr += hourHR(8, bpm: 62)
        hr += hourHR(9, bpm: 60)
        hr += hourHR(10, bpm: 61)
        hr += hourHR(11, bpm: 95)   // the spike
        let r = DaytimeStress.analyze(hr: hr, rr: [])
        XCTAssertFalse(r.scored.isEmpty)
        for p in r.scored {
            let lvl = p.level!
            XCTAssertGreaterThanOrEqual(lvl, 0)
            XCTAssertLessThanOrEqual(lvl, 3)
        }
        // The high-HR hour must be the day's peak and read above the calm hours.
        XCTAssertEqual(r.peak?.hour, 11)
        let calm = r.scored.first { $0.hour == 9 }!.level!
        let tense = r.scored.first { $0.hour == 11 }!.level!
        XCTAssertGreaterThan(tense, calm)
    }

    func testNonWakingHoursAreExcluded() {
        // A 3 am hour (outside 06:00–22:00) is never placed on the waking timeline.
        let hr = hourHR(3, bpm: 80) + hourHR(9, bpm: 60)
        let r = DaytimeStress.analyze(hr: hr, rr: [])
        XCTAssertFalse(r.hours.contains { $0.hour == 3 })
        XCTAssertTrue(r.hours.contains { $0.hour == 9 })
    }

    func testSustainedHighFlagsAfterThreeConsecutiveHighHours() {
        // A calm morning, then three increasingly tense afternoon hours that finish HIGH.
        var hr: [HRSample] = []
        for h in [8, 9, 10] { hr += hourHR(h, bpm: 58) }   // calm baseline hours
        hr += hourHR(13, bpm: 120)
        hr += hourHR(14, bpm: 125)
        hr += hourHR(15, bpm: 130)
        let r = DaytimeStress.analyze(hr: hr, rr: [])
        XCTAssertTrue(r.sustainedHigh, "three trailing HIGH hours should flag sustained stress")
        XCTAssertGreaterThanOrEqual(r.sustainedRun, DaytimeStress.sustainedHours)
    }

    func testFlatDayDoesNotFlagSustained() {
        // Every hour at the same HR → no hour is meaningfully elevated, no flag.
        var hr: [HRSample] = []
        for h in 8...16 { hr += hourHR(h, bpm: 64) }
        let r = DaytimeStress.analyze(hr: hr, rr: [])
        XCTAssertFalse(r.sustainedHigh)
        // A flat day sits around the baseline (≈1.5), not pinned high.
        if let mean = r.dayMean { XCTAssertLessThan(mean, DaytimeStress.highBandFloor) }
    }

    func testTimezoneOffsetShiftsWakingWindow() {
        // ts at UTC hour 4 with a +3 h offset lands at local hour 7 → inside waking hours.
        let hr = hourHR(4, bpm: 60)
        let r = DaytimeStress.analyze(hr: hr, rr: [], tzOffsetSeconds: 3 * 3_600)
        XCTAssertTrue(r.hours.contains { $0.hour == 7 })
    }

    func testRMSSDLowersStressDirectionMatchesDailyScore() {
        // Same HR across hours; the hour with the LOWEST HRV (RMSSD) should read more
        // stressed — the same directionality as the daily score (HRV down = stress).
        var hr: [HRSample] = []
        var rr: [RRInterval] = []
        for h in [8, 9, 10, 11] { hr += hourHR(h, bpm: 65) }
        // High-variability (relaxed) hours vs one low-variability (tense) hour.
        rr += hourRRVariable(8, rrMs: 900, jitter: 40)
        rr += hourRRVariable(9, rrMs: 900, jitter: 40)
        rr += hourRRVariable(10, rrMs: 900, jitter: 40)
        rr += hourRRVariable(11, rrMs: 900, jitter: 2)   // suppressed HRV
        let r = DaytimeStress.analyze(hr: hr, rr: rr)
        let relaxed = r.scored.first { $0.hour == 9 }!.level!
        let tense = r.scored.first { $0.hour == 11 }!.level!
        XCTAssertGreaterThan(tense, relaxed)
    }

    /// R-R for one hour with a controllable beat-to-beat jitter (drives RMSSD).
    private func hourRRVariable(_ hour: Int, rrMs: Int, jitter: Int, n: Int = 60) -> [RRInterval] {
        let base = hour * 3_600
        return (0..<n).map { RRInterval(ts: base + $0 * 50, rrMs: rrMs + ($0 % 2 == 0 ? jitter : -jitter)) }
    }
}
