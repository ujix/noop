# Android: interactive charts, Sleep overhaul, Explore redesign, axis labels, battery/streak strip, live-HR notification sparkline

## What this PR does

**Executive summary:** A broad Android UX pass covering interactive chart inspection across all screens, a fully redesigned Sleep page with night-by-night browsing and in-app time editing, Y/X axis labels on every line chart, a redesigned Explore metric picker, a live HR sparkline in the foreground notification, and a battery/streak status strip on Today.

**Details:**

**Charts & data visualization**
- **Added:** `selectionEnabled` on every `LineChart` across Today (`HeartRateTrendCard`), Sleep (metric detail), Stress (`StressTrendSection`), Trends (`HeroChartCard`), Explore, and Vital Signs — users can tap or smoothly drag to read exact values on any trend
- **Added:** Y-axis labels (max / avg / min) and an X-axis date row to all line charts in Explore, Trends, and Vital Signs screens
- **Fixed:** `HeartRateTrendCard` X-axis: labels now show correct `HH:mm` wall-clock times with an interpolated midpoint; Y-axis shows avg as the middle label

**Today screen**
- **Updated:** `ThreeDaySelectorBar` replaced with `DayNavBar` — left/right chevrons flanking an accent-tinted center block; tapping the block opens a `DatePickerDialog` for any past date
- **Added:** Compact status strip (top-right of content area) shows live strap battery % and total-nights-recorded streak with a fire icon (red when streak ≥ 2, gray otherwise)

**Sleep screen — night browsing**
- **Added:** `◀ / ▶` chevron navigation walks every recorded sleep block (including naps); a `DatePickerDialog` jumps to any night by calendar date
- **Updated:** Night nav header redesigned to match `DayNavBar` style — accent center block shows the night label ("Last night") and the date ("Wed 4 Jun"); the time range ("22:50–06:48") sits below with an edit icon
- **Fixed:** `nightOffset` no longer resets to 0 on optimistic state updates — it only resets on a real sync or import (`LaunchedEffect(days)`), so the user stays on the night they navigated to

**Sleep screen — time editing**
- **Added:** Tapping the edit icon opens a Strand-styled `AlertDialog` with two rows (Bedtime / Wake-up), each showing the current time and an accent edit icon; choosing one opens its own independent `TimePickerDialog`
- **Fixed:** After confirming a change the header clock re-renders immediately via an optimistic in-memory `sleeps` update; no DB round-trip wait
- **Fixed:** Stage Breakdown subtitle derives "X in bed" from `session.endTs − session.startTs` so it reflects the edited window instantly
- **Fixed:** All metrics (Rest %, Hours vs Needed, Sleep Debt, trend chart) recompute against the edited window: `buildSleepModel` now builds a `metricsWindow` that substitutes `sessionDurationMin` for the selected night's `totalSleepMin`, while keeping `typicalTotalMin` from the unmodified historical window

**Sleep screen — analytics cards**
- **Added:** "Hours vs Needed" card — score %, trend arrow, gradient progress bar, stacked Healthy/Strain/Debt component bar, slept/needed/debt footer
- **Added:** "Sleep Consistency" card — Canvas-drawn vertical bar chart with bed-time at top / wake-time at bottom, Y-axis time labels, X-axis day labels, dashed typical overlay lines; score counts nights where both bed and wake fall within 45 min of the user's typical (previous SD-based formula always returned 0 %)
- **Updated:** Consistency card Y-axis flipped to match natural night flow (early evening → morning top-to-bottom)

**Sleep screen — metric detail**
- **Updated:** Tapping any metric tile in Night Detail slides up a `ModalBottomSheet` (W/M/3M/6M/1Y/ALL range selector, Y-axis, line chart, X-axis dates, min/avg/max footer) instead of pushing a new screen

**Explore screen**
- **Updated:** Metric selector fully redesigned as an `ExposedDropdownMenuBox` with category headers, accent dot per metric, selected-item checkmark, and dividers between groups — replaces the horizontal chip row

**Notification**
- **Added:** Foreground-service notification accumulates a rolling 60-sample HR history and renders it as a `Bitmap` sparkline (800×200, accent fill + stroke on dark background) attached via `NotificationCompat.BigPictureStyle`; expanded notification shows an HR trend instead of just a number

**Intelligence screen**
- **Added:** W / M / 3M / 6M / 1Y / ALL `SegmentedPillControl` to the "By Day" section; list is filtered to the chosen window (defaults to M) with a day-count footnote and an empty-window nudge card

**Bug fixes**
- **Fixed:** `String?` vs `String` compile errors in `HealthScreen.kt` and `TrendsScreen.kt` x-axis date label paths (`.getOrNull()` returning `String?` passed to `LocalDate.parse(String)`)
- **Fixed:** `SleepConsistencyCard` — `typicalBed` / `typicalWake` declared after the block that referenced them; moved before the `consistentNights` count

**Validation:** `:app:assembleFullDebug`, `testFullDebugUnitTest`, and `testDemoDebugUnitTest` all green after every commit.

## Type of change

- [x] Bug fix
- [x] New feature
- [ ] Refactor / cleanup
- [ ] Documentation
- [ ] CI / tooling

## How it was tested

Android 16. Real Samsung device. WHOOP 4.0. Tested: night navigation and date-jump picker, time editing (bed and wake independently), navigation position preservation after editing, metric tile bottom sheets, consistency card with real multi-night data, HR sparkline in expanded notification, all chart drag interactions, Explore dropdown, Intelligence range filter, Today battery/streak strip.

## Checklist

- [ ] Swift package tests pass for any package I touched (`swift test` in `Packages/<name>`)
- [x] Android unit tests pass if I touched `android/` (`./gradlew testFullDebugUnitTest`)
- [x] No new build warnings introduced
- [x] UI changes use only `StrandDesign` tokens — no hardcoded colors, fonts, or spacing
- [x] No hardcoded hex frame bytes; protocol facts live in the schema / decoders
- [x] Follows the conventions in [`docs/CONTRIBUTING.md`](../docs/CONTRIBUTING.md)
- [x] I did not commit generated output (`Strand.xcodeproj/`) or any secrets/keystores

## Related issues

<!-- Closes #N -->
