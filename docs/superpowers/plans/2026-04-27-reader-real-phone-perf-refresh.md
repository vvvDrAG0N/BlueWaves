# Reader Real-Phone Perf Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refresh the book-opening and chapter-scrolling performance baselines on an actual phone after the major refactors, using fresh release-like numbers and excluding the Theme / Appearance flows.

**Architecture:** Reuse the existing live-phone harness family instead of inventing a new perf lane: one release-like build/install path, one book-open harness, one reader-scroll harness, and one targeted trace lane only for scenarios that still look meaningfully rough after the fresh release-like pass. Treat the April 24-25 reports as historical baselines only; first revalidate the current prepared book state and UI selectors, then run fresh measurements and write one new portable report that compares the new numbers against those older baselines.

**Tech Stack:** Gradle, PowerShell, adb, `uiautomator dump`, `dumpsys gfxinfo`, Perfetto, Python trace summarization, Markdown reports.

---

## Assumed Scope

- Refresh the same two report families the user just called out:
  - book opening
  - chapter scrolling
- Keep Theme / Appearance completely out of this pass.
- Use an actual phone as the final gate.
- Use a release-like build for final reported numbers.

If the user expands scope later, create a separate plan instead of widening this one mid-run.

## File Map

- `scripts/run_book_open_close_release_live.ps1`
  - Existing book-open/book-close live-phone harness. Reuse it for the opening refresh and only patch selectors or dialog handling if the smoke run proves drift.
- `scripts/run_reader_lag_release_live.ps1`
  - Existing release-like chapter-scroll harness for the real phone. Reuse it first for the fresh scroll sanity pass.
- `scripts/run_reader_lag_trace_matrix.ps1`
  - Existing trace capture harness. Use only if the refreshed release-like numbers still show a flagged scenario worth deeper diagnosis.
- `scripts/summarize_reader_lag_trace.py`
  - Existing Perfetto summarizer used by the trace harness.
- `logs/book-open-close-release-live-<timestamp>/`
  - Fresh open/close raw artifacts and summary.
- `logs/reader-lag-release-live-<timestamp>/`
  - Fresh chapter-scroll raw artifacts and summary.
- `logs/reader-lag-trace-matrix-<timestamp>/`
  - Fresh targeted traces, only if a flagged scenario survives the release-like pass.
- `logs/perf_report_book_open_and_chapter_scroll_<date>.md`
  - New portable report combining the refreshed open and chapter-scroll results with charts and comparison notes.
- `docs/agent_memory/step_history.md`
  - Append a structured planning/implementation record after the refresh work is done.
- `docs/agent_memory/next_steps.md`
  - Close or escalate the perf follow-up depending on the new phone results.

## Guardrails

- Do not treat the old April reports as current truth.
- Do not use emulator results as the final perf claim.
- Do not report debug-build numbers as the final user-facing outcome.
- For book opening, preserve the old measurement contract:
  - no scroll contamination during the measured open window
  - `Android system back` remains the default close path
- For chapter scrolling, make sure the heavy `TTEV6` sample is still on a real text-heavy chapter, not an image-only opener.
- Only run traces for flagged scenarios after the fresh release-like numbers are in hand.
- If the prepared books or progress markers have drifted too much, pause and redefine the dataset before touching the harness logic.

### Task 1: Re-establish The Release-Like Phone Lane

**Files:**
- Use: `app/build/outputs/apk/release/app-release-unsigned.apk`
- Create/refresh: `app/build/outputs/apk/release/app-release-aligned.apk`
- Create/refresh: `app/build/outputs/apk/release/app-release-debugsigned.apk`

- [ ] **Step 1: Confirm the phone is connected**

Run:

```powershell
adb devices
```

Expected: one physical-device serial in `device` state. Record that serial and reuse it in every later command.

- [ ] **Step 2: Build the release variant**

Run:

```powershell
.\gradlew.bat :app:assembleRelease --console=plain
```

Expected: `BUILD SUCCESSFUL` and a fresh `app/build/outputs/apk/release/app-release-unsigned.apk`.

- [ ] **Step 3: Align the unsigned APK**

Run:

```powershell
& 'C:\Users\Amon\AppData\Local\Android\Sdk\build-tools\37.0.0\zipalign.exe' -f -p 4 `
  'app\build\outputs\apk\release\app-release-unsigned.apk' `
  'app\build\outputs\apk\release\app-release-aligned.apk'
```

Expected: exit code `0` and a refreshed `app-release-aligned.apk`.

- [ ] **Step 4: Sign the aligned APK with the local debug keystore**

Run:

```powershell
& 'C:\Users\Amon\AppData\Local\Android\Sdk\build-tools\37.0.0\apksigner.bat' sign `
  --ks 'C:\Users\Amon\.android\debug.keystore' `
  --ks-pass pass:android `
  --key-pass pass:android `
  --out 'app\build\outputs\apk\release\app-release-debugsigned.apk' `
  'app\build\outputs\apk\release\app-release-aligned.apk'
```

Expected: exit code `0` and a refreshed `app-release-debugsigned.apk`.

- [ ] **Step 5: Install the release-like APK over the existing app data**

Run:

```powershell
adb -s <DEVICE_SERIAL> install -r -d app/build/outputs/apk/release/app-release-debugsigned.apk
```

Expected: `Success`. The library data and saved progress should remain intact.

- [ ] **Step 6: Capture a quick install proof**

Run:

```powershell
adb -s <DEVICE_SERIAL> shell dumpsys package com.epubreader | findstr versionName
```

Expected: one `versionName` line proving the installed package is the refreshed build you are about to measure.

### Task 2: Lock The Current Prepared-State Dataset Before Measuring

**Files:**
- Create: `logs/perf-refresh-preflight-<timestamp>/library-preflight.xml`
- Modify only if drift is proven: `scripts/run_book_open_close_release_live.ps1`
- Modify only if drift is proven: `scripts/run_reader_lag_release_live.ps1`

- [ ] **Step 1: Force-stop, relaunch, and dump the current library UI**

Run:

```powershell
adb -s <DEVICE_SERIAL> shell am force-stop com.epubreader
adb -s <DEVICE_SERIAL> shell monkey -p com.epubreader -c android.intent.category.LAUNCHER 1
adb -s <DEVICE_SERIAL> exec-out uiautomator dump /dev/tty > logs\perf-refresh-preflight-library-raw.xml
```

Expected: the app returns to `My Library`, and the raw UI dump is captured locally.

- [ ] **Step 2: Confirm the book dataset still exists**

Check the live library UI for:

```text
Shadow Slave
The Saga of Tanya the Evil, Vol. 6 (light novel)
```

Expected: both titles are still present on the phone.

- [ ] **Step 3: Confirm or refresh the saved progress markers**

Check whether the old prepared markers are still visible:

```text
Shadow Slave -> 1435 / 2927 ch
The Saga of Tanya the Evil, Vol. 6 (light novel) -> 11 / 45 ch
```

Expected: both markers still match.  
If they do not match, stop and update the expected progress strings in the live harness scripts before running the real matrix.

- [ ] **Step 4: Confirm the heavy `TTEV6` sample is still a real text chapter**

Manual phone action:

```text
Open TTEV6 -> verify the saved location is still a real text-heavy chapter, not an image-only opener.
If it drifted, use the in-app TOC jump to return to CH 11, then back out to the library so the new position persists.
```

Expected: the heavy-book scroll sample is still anchored to real text content.

- [ ] **Step 5: Decide whether to continue or redefine the dataset**

Gate:

```text
Continue only if both books still exist and the heavy TTEV6 sample is on real text content.
If not, redefine the dataset first and record the new titles/progress strings in the report.
```

Expected: no measurement starts against stale or mismatched prepared-state assumptions.

### Task 3: Smoke The Book-Opening Harness And Repair Only Proven Drift

**Files:**
- Use: `scripts/run_book_open_close_release_live.ps1`
- Create: `logs/book-open-close-release-live-<timestamp>/...`
- Modify only if smoke proves drift: `scripts/run_book_open_close_release_live.ps1`

- [ ] **Step 1: Run a one-iteration smoke pass**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run_book_open_close_release_live.ps1 `
  -DeviceSerial "<DEVICE_SERIAL>" `
  -Iterations 1
```

Expected: a new `logs/book-open-close-release-live-<timestamp>/summary.md` plus per-run XML/logcat/gfxinfo artifacts.

- [ ] **Step 2: If the smoke pass fails, patch only the broken selectors**

Patch candidates in `scripts/run_book_open_close_release_live.ps1`:

```text
- dialog markers such as "What's New" or "Welcome to Blue Waves"
- the exact book title strings
- the exact expected progress strings
- any library-visible text used to confirm the app is on the correct screen
```

Expected: no behavior changes beyond selector/dialog drift repair.

- [ ] **Step 3: Re-run the same smoke command until it succeeds cleanly**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run_book_open_close_release_live.ps1 `
  -DeviceSerial "<DEVICE_SERIAL>" `
  -Iterations 1
```

Expected: the smoke summary is produced without manual rescue steps during the measured window.

### Task 4: Run The Full Book-Opening Refresh On The Phone

**Files:**
- Use: `scripts/run_book_open_close_release_live.ps1`
- Create: `logs/book-open-close-release-live-<timestamp>/summary.md`

- [ ] **Step 1: Run the full release-like matrix**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run_book_open_close_release_live.ps1 `
  -DeviceSerial "<DEVICE_SERIAL>"
```

Expected: the 12-run matrix completes:

```text
2 books x 2 startup modes x 3 iterations
```

- [ ] **Step 2: Verify the summary contains fresh opening averages for both books**

Check:

```text
logs/book-open-close-release-live-<timestamp>/summary.md
```

Expected: fresh `Open Averages` for:

```text
Shadow Slave immediate
Shadow Slave delayed
TTEV6 immediate
TTEV6 delayed
```

- [ ] **Step 3: Record the exact new output directory**

Write down:

```text
logs/book-open-close-release-live-<timestamp>/
```

Expected: the final report can point to the exact fresh evidence set instead of the April directory.

### Task 5: Refresh The Chapter-Scrolling Release-Live Pass On The Phone

**Files:**
- Use: `scripts/run_reader_lag_release_live.ps1`
- Create: `logs/reader-lag-release-live-<timestamp>/summary.md`
- Modify only if smoke proves drift: `scripts/run_reader_lag_release_live.ps1`

- [ ] **Step 1: Run the current release-like chapter-scroll harness**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run_reader_lag_release_live.ps1 `
  -DeviceSerial "<DEVICE_SERIAL>"
```

Expected: a new `logs/reader-lag-release-live-<timestamp>/summary.md` with fresh real-phone scroll metrics.

- [ ] **Step 2: If the harness fails, patch only verified drift**

Patch candidates in `scripts/run_reader_lag_release_live.ps1`:

```text
- dialog markers
- exact book title text
- TTEV6 expected progress text
- reader/library visible markers used to detect that the scroll run is on the intended screen
```

Expected: keep the existing scenario intent and only fix selector drift.

- [ ] **Step 3: Re-run the same command until a clean summary is produced**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run_reader_lag_release_live.ps1 `
  -DeviceSerial "<DEVICE_SERIAL>"
```

Expected: one clean release-like scroll summary from the actual phone.

- [ ] **Step 4: Decide whether the refreshed release-like scroll pass is enough**

Gate:

```text
If the new release-like summary clearly answers the question, stop here for the scroll lane.
If the numbers are noisy, contradictory, or still feel outdated relative to the current app, escalate to the trace lane in Task 6.
```

Expected: do not widen the perf work unless the refreshed phone pass still leaves ambiguity.

### Task 6: Run Targeted Traces Only For Flagged Scroll Scenarios

**Files:**
- Use: `scripts/run_reader_lag_trace_matrix.ps1`
- Use: `scripts/summarize_reader_lag_trace.py`
- Create: `logs/reader-lag-trace-matrix-<timestamp>/...`

- [ ] **Step 1: Choose the smallest flagged scenario set**

Trace only scenarios that meet at least one trigger:

```text
- noticeably worse than the April release-like baseline
- visibly hitchy on the phone even though the summary says "acceptable"
- contradictory metrics, such as high input latency moving opposite to janky-frame behavior
```

Expected: one or two trace targets, not a broad blind rerun.

- [ ] **Step 2: Run the existing trace harness**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run_reader_lag_trace_matrix.ps1 `
  -DeviceSerial "<DEVICE_SERIAL>" `
  -PythonExe python
```

Expected: fresh trace artifacts and `*-trace-summary.md` files under `logs/reader-lag-trace-matrix-<timestamp>/`.

- [ ] **Step 3: Treat the traces as diagnostic, not final user-facing perf numbers**

Interpretation rule:

```text
Use the traces to explain the remaining bottleneck shape.
Do not replace the release-like phone numbers with trace numbers in the final outcome section.
```

Expected: the report stays honest about what is release-like evidence versus deeper debug-style diagnosis.

### Task 7: Write The Updated Portable Report And Compare Against April Baselines

**Files:**
- Create: `logs/perf_report_book_open_and_chapter_scroll_2026-04-27_refresh.md`
- Read: `logs/book-open-close-release-live-20260425-023012/summary.md`
- Read: `logs/reader-lag-release-live-20260424-090627/summary.md`
- Read: `logs/reader-lag-two-book-reset-20260424-0830-ch11/summary.md`
- Read: fresh `logs/book-open-close-release-live-<timestamp>/summary.md`
- Read: fresh `logs/reader-lag-release-live-<timestamp>/summary.md`
- Read only if used: fresh `logs/reader-lag-trace-matrix-<timestamp>/*-trace-summary.md`

- [ ] **Step 1: Create one new report file for the refreshed pass**

Create:

```text
logs/perf_report_book_open_and_chapter_scroll_2026-04-27_refresh.md
```

Expected: one portable artifact for the user, not scattered chat-only notes.

- [ ] **Step 2: Include the fresh book-opening tables and chart**

Report content:

```text
- fresh opening averages
- immediate vs delayed comparison
- exact phone/run date
- explicit note that Theme / Appearance was excluded
```

Expected: the new report supersedes the April opening section.

- [ ] **Step 3: Include the fresh chapter-scroll tables and chart**

Report content:

```text
- fresh release-like scroll summary
- any trace-backed interpretation only for flagged cases
- explicit note whether the old April chapter-scroll conclusions still hold after the refactors
```

Expected: the new report answers whether the old scroll story still stands.

- [ ] **Step 4: Add a comparison section against the April baselines**

Compare against:

```text
logs/book-open-close-release-live-20260425-023012/summary.md
logs/reader-lag-release-live-20260424-090627/summary.md
logs/reader-lag-two-book-reset-20260424-0830-ch11/summary.md
```

Expected: the user can see clearly what changed after the refactors and what stayed effectively the same.

### Task 8: Record The Continuity Handoff

**Files:**
- Modify: `docs/agent_memory/step_history.md`
- Modify: `docs/agent_memory/next_steps.md`

- [ ] **Step 1: Append a structured step-history entry**

Include:

```text
- exact phone retest goal
- files/scripts touched
- whether any harness selector drift had to be fixed
- exact fresh log directories
- final read: acceptable / optional polish / needs deeper work
```

Expected: the next agent does not have to rediscover this run.

- [ ] **Step 2: Update next steps based on the new outcome**

If the refreshed release-like phone results are acceptable:

```text
Close or downgrade the old optional perf follow-up items.
```

If a real regression survives:

```text
Replace the old optional note with one exact flagged scenario and one exact verification path.
```

Expected: the repo continuity reflects the new phone truth, not the April assumptions.
