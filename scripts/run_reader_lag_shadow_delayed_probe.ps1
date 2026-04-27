param(
    [Parameter(Mandatory = $true)]
    [string]$DeviceSerial,
    [ValidateSet("release", "trace")]
    [string]$Mode = "release",
    [string]$PackageName = "com.epubreader",
    [string]$BookTitle = "Shadow Slave",
    [string]$ExpectedLibraryProgress = "1435 / 2927 ch",
    [int]$DelaySeconds = 15,
    [int]$RepeatCount = 3,
    [string]$PythonExe = "python",
    [string]$SnapshotTarPath = "",
    [string]$OutputDir = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $false

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputDir = Join-Path $repoRoot "logs\reader-lag-shadow-delayed-$Mode-$timestamp"
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$runLabel = "Shadow Slave Delayed"
$traceConfigPath = Join-Path $PSScriptRoot "reader_lag_trace_config.pbtxt"
$summaryScriptPath = Join-Path $PSScriptRoot "summarize_reader_lag_trace.py"
if ([string]::IsNullOrWhiteSpace($SnapshotTarPath)) {
    $SnapshotTarPath = Join-Path $repoRoot "logs\reader-lag-two-book-reset-20260424-0802\pristine-state-cmd.tar"
}
$remoteTraceConfigPath = "/data/misc/perfetto-configs/reader-lag-trace-config.pbtxt"
$remoteShadowSnapshotPath = "/data/local/tmp/pristine-state-cmd.tar"

function Invoke-Adb {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,
        [switch]$AllowFailure
    )

    $quotedArgs = foreach ($argument in @("-s", $DeviceSerial) + $Arguments) {
        if ($argument -match '[\s"]') {
            '"' + ($argument -replace '"', '\"') + '"'
        }
        else {
            $argument
        }
    }

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = "adb"
    $startInfo.Arguments = $quotedArgs -join " "
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    $null = $process.Start()
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()

    $text = ($stdout + $stderr).TrimEnd("`r", "`n")
    if (-not $AllowFailure -and $process.ExitCode -ne 0) {
        $joined = $Arguments -join " "
        throw "adb failed ($joined)`n$text"
    }

    return $text
}

function Save-TextFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [Parameter(Mandatory = $true)]
        [string]$Content
    )

    [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
}

function Get-TrimmedUiDump {
    $raw = Invoke-Adb -Arguments @("exec-out", "uiautomator", "dump", "/dev/tty")
    $endTag = "</hierarchy>"
    $endIndex = $raw.IndexOf($endTag)
    if ($endIndex -lt 0) {
        throw "Could not find </hierarchy> in UI dump."
    }

    $trimmed = $raw.Substring(0, $endIndex + $endTag.Length)
    return [pscustomobject]@{
        Raw = $trimmed
        Xml = [xml]$trimmed
    }
}

function Find-NodesByExactText {
    param(
        [Parameter(Mandatory = $true)]
        [xml]$Xml,
        [Parameter(Mandatory = $true)]
        [string]$Text
    )

    $matches = New-Object System.Collections.Generic.List[System.Xml.XmlElement]
    foreach ($node in $Xml.SelectNodes("//*")) {
        if ($node.GetAttribute("text") -eq $Text -or $node.GetAttribute("content-desc") -eq $Text) {
            $matches.Add($node)
        }
    }

    return @($matches.ToArray())
}

function Ui-ContainsText {
    param(
        [Parameter(Mandatory = $true)]
        [xml]$Xml,
        [Parameter(Mandatory = $true)]
        [string]$Text
    )

    $matches = @(Find-NodesByExactText -Xml $Xml -Text $Text)
    return $matches.Count -gt 0
}

function Get-CenterFromBounds {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Bounds
    )

    $match = [regex]::Match($Bounds, "\[(\d+),(\d+)\]\[(\d+),(\d+)\]")
    if (-not $match.Success) {
        throw "Could not parse bounds: $Bounds"
    }

    $x1 = [int]$match.Groups[1].Value
    $y1 = [int]$match.Groups[2].Value
    $x2 = [int]$match.Groups[3].Value
    $y2 = [int]$match.Groups[4].Value
    return [pscustomobject]@{
        X = [int](($x1 + $x2) / 2)
        Y = [int](($y1 + $y2) / 2)
    }
}

function Tap-NodeCenter {
    param(
        [Parameter(Mandatory = $true)]
        [System.Xml.XmlElement]$Node
    )

    $center = Get-CenterFromBounds -Bounds $Node.GetAttribute("bounds")
    Invoke-Adb -Arguments @("shell", "input", "tap", "$($center.X)", "$($center.Y)") | Out-Null
}

function Get-PreferredBookNode {
    param(
        [Parameter(Mandatory = $true)]
        [System.Xml.XmlElement[]]$Nodes
    )

    if ($Nodes.Count -eq 1) {
        return $Nodes[0]
    }

    return $Nodes |
        Sort-Object {
            (Get-CenterFromBounds -Bounds $_.GetAttribute("bounds")).Y
        } -Descending |
        Select-Object -First 1
}

function Dismiss-DialogIfPresent {
    param(
        [Parameter(Mandatory = $true)]
        [xml]$Xml
    )

    if (Ui-ContainsText -Xml $Xml -Text "What's New") {
        $okNodes = @(Find-NodesByExactText -Xml $Xml -Text "OK")
        if ($okNodes.Count -gt 0) {
            Tap-NodeCenter -Node $okNodes[0]
            Start-Sleep -Seconds 2
            return $true
        }
    }

    if (Ui-ContainsText -Xml $Xml -Text "Welcome to Blue Waves") {
        $startNodes = @(Find-NodesByExactText -Xml $Xml -Text "Start")
        if ($startNodes.Count -gt 0) {
            Tap-NodeCenter -Node $startNodes[0]
            Start-Sleep -Seconds 2
            return $true
        }
    }

    return $false
}

function Wait-ForLibrary {
    param([int]$MaxAttempts = 20)

    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        $dump = Get-TrimmedUiDump
        if (Dismiss-DialogIfPresent -Xml $dump.Xml) {
            continue
        }

        if (Ui-ContainsText -Xml $dump.Xml -Text "My Library") {
            return $dump
        }

        Start-Sleep -Seconds 2
    }

    throw "Library screen did not become visible."
}

function Wait-ForReaderScreen {
    param([int]$MaxAttempts = 16)

    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        Start-Sleep -Milliseconds 500
        $dump = Get-TrimmedUiDump
        if (-not (Ui-ContainsText -Xml $dump.Xml -Text "My Library")) {
            return $dump
        }
    }

    throw "Reader screen did not appear after tapping the book."
}

function Assert-LibraryState {
    param(
        [Parameter(Mandatory = $true)]
        [xml]$Xml,
        [Parameter(Mandatory = $true)]
        [string]$Context
    )

    if (-not (Ui-ContainsText -Xml $Xml -Text $BookTitle)) {
        throw "Expected book title '$BookTitle' was not visible during $Context."
    }

    if (-not (Ui-ContainsText -Xml $Xml -Text $ExpectedLibraryProgress)) {
        throw "Expected library progress '$ExpectedLibraryProgress' was not visible during $Context."
    }
}

function Parse-GfxMetrics {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Text
    )

    $patterns = @{
        TotalFrames = "Total frames rendered:\s+(\d+)"
        JankyFrames = "Janky frames:\s+(\d+)\s+\(([0-9.]+)%\)"
        P95 = "95th percentile:\s+(\d+)ms"
        P99 = "99th percentile:\s+(\d+)ms"
        HighInputLatency = "Number High input latency:\s+(\d+)"
        SlowUiThread = "Number Slow UI thread:\s+(\d+)"
        FrameDeadlineMissed = "Number Frame deadline missed:\s+(\d+)"
    }

    $result = [ordered]@{}
    foreach ($key in $patterns.Keys) {
        $match = [regex]::Match($Text, $patterns[$key])
        if ($match.Success) {
            if ($key -eq "JankyFrames") {
                $result[$key] = [int]$match.Groups[1].Value
                $result["JankyPercent"] = [double]$match.Groups[2].Value
            }
            else {
                $result[$key] = [int]$match.Groups[1].Value
            }
        }
        else {
            if ($key -eq "JankyFrames") {
                $result[$key] = $null
                $result["JankyPercent"] = $null
            }
            else {
                $result[$key] = $null
            }
        }
    }

    return [pscustomobject]$result
}

function Start-PerfettoTrace {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RemoteTracePath
    )

    $output = Invoke-Adb -Arguments @(
        "shell",
        "perfetto",
        "--background-wait",
        "--txt",
        "-c", $remoteTraceConfigPath,
        "-o", $RemoteTracePath
    )

    $match = [regex]::Match($output, "(\d+)")
    if (-not $match.Success) {
        throw "Perfetto did not return a background PID.`n$output"
    }

    return [int]$match.Groups[1].Value
}

function Stop-PerfettoTrace {
    param(
        [Parameter(Mandatory = $true)]
        [int]$TracePid,
        [Parameter(Mandatory = $true)]
        [string]$RemoteTracePath
    )

    Invoke-Adb -Arguments @("shell", "kill", "-TERM", "$TracePid") -AllowFailure | Out-Null
    for ($attempt = 1; $attempt -le 10; $attempt++) {
        Start-Sleep -Seconds 1
        $lsOutput = Invoke-Adb -Arguments @("shell", "ls", "-l", $RemoteTracePath) -AllowFailure
        if ($lsOutput -and $lsOutput -notmatch "No such file") {
            return
        }
    }

    throw "Perfetto trace file did not appear at $RemoteTracePath."
}

function Write-MarkdownSummary {
    param(
        [Parameter(Mandatory = $true)]
        [object[]]$Results,
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# Shadow Slave Delayed Reader Lag Probe")
    $lines.Add("")
    $lines.Add("Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
    $lines.Add("Device: ``$DeviceSerial``")
    $lines.Add("Mode: ``$Mode``")
    $lines.Add("Book title: ``$BookTitle``")
    $lines.Add("Expected library progress: ``$ExpectedLibraryProgress``")
    $lines.Add("Delay seconds: $DelaySeconds")
    $lines.Add("Repeat count: $RepeatCount")
    $lines.Add("")
    if ($Mode -eq "trace") {
        $lines.Add("| Run | High Input Latency | Janky Frames | Janky % | P95 | P99 | Slow UI | Frame Deadline Missed | Trace Summary |")
        $lines.Add("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |")
    }
    else {
        $lines.Add("| Run | High Input Latency | Janky Frames | Janky % | P95 | P99 | Slow UI | Frame Deadline Missed |")
        $lines.Add("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
    }

    foreach ($row in $Results) {
        if ($Mode -eq "trace") {
            $traceSummaryName = Split-Path -Leaf $row.TraceSummaryPath
            $lines.Add("| $($row.RunId) | $($row.HighInputLatency) | $($row.JankyFrames) | $($row.JankyPercent) | $($row.P95) | $($row.P99) | $($row.SlowUiThread) | $($row.FrameDeadlineMissed) | $traceSummaryName |")
        }
        else {
            $lines.Add("| $($row.RunId) | $($row.HighInputLatency) | $($row.JankyFrames) | $($row.JankyPercent) | $($row.P95) | $($row.P99) | $($row.SlowUiThread) | $($row.FrameDeadlineMissed) |")
        }
    }

    $avgHighInput = [math]::Round((($Results | Measure-Object -Property HighInputLatency -Average).Average), 2)
    $minHighInput = ($Results | Measure-Object -Property HighInputLatency -Minimum).Minimum
    $maxHighInput = ($Results | Measure-Object -Property HighInputLatency -Maximum).Maximum
    $avgJankPercent = [math]::Round((($Results | Measure-Object -Property JankyPercent -Average).Average), 2)
    $avgP99 = [math]::Round((($Results | Measure-Object -Property P99 -Average).Average), 2)

    $lines.Add("")
    $lines.Add("## Aggregate")
    $lines.Add("")
    $lines.Add("- High input latency avg/min/max: $avgHighInput / $minHighInput / $maxHighInput")
    $lines.Add("- Janky percent average: $avgJankPercent")
    $lines.Add("- P99 average: $avgP99")
    $lines.Add("- Per-run artifacts include UI dumps, gfxinfo, logcat, meminfo, and battery snapshots.")

    Save-TextFile -Path $Path -Content ($lines -join "`r`n")
}

if ($Mode -eq "trace") {
    if (-not (Test-Path $traceConfigPath)) {
        throw "Trace config not found: $traceConfigPath"
    }
    if (-not (Test-Path $summaryScriptPath)) {
        throw "Trace summary script not found: $summaryScriptPath"
    }
    if (-not (Test-Path $SnapshotTarPath)) {
        throw "Shadow snapshot not found: $SnapshotTarPath"
    }

    Invoke-Adb -Arguments @("push", $traceConfigPath, $remoteTraceConfigPath) | Out-Null
    Invoke-Adb -Arguments @("shell", "chmod", "666", $remoteTraceConfigPath) | Out-Null
    Invoke-Adb -Arguments @("push", $SnapshotTarPath, $remoteShadowSnapshotPath) | Out-Null
    Invoke-Adb -Arguments @("shell", "chmod", "644", $remoteShadowSnapshotPath) | Out-Null
}

$deviceFingerprint = Invoke-Adb -Arguments @("shell", "getprop", "ro.build.fingerprint")
Save-TextFile -Path (Join-Path $OutputDir "device-build-fingerprint.txt") -Content $deviceFingerprint

$results = New-Object System.Collections.Generic.List[object]

foreach ($iteration in 1..$RepeatCount) {
    $runId = "shadow-delayed-$Mode-run{0:D2}" -f $iteration
    $prefix = Join-Path $OutputDir $runId

    Invoke-Adb -Arguments @("shell", "am", "force-stop", $PackageName) | Out-Null
    if ($Mode -eq "trace") {
        $remoteTracePath = "/data/misc/perfetto-traces/$runId.pftrace"
        $localTracePath = "$prefix-trace.pftrace"
        $traceSummaryMd = "$prefix-trace-summary.md"
        $traceSummaryJson = "$prefix-trace-summary.json"

        Invoke-Adb -Arguments @("shell", "rm", "-f", $remoteTracePath) -AllowFailure | Out-Null
        Invoke-Adb -Arguments @("shell", "pm", "clear", $PackageName) | Out-Null
        Invoke-Adb -Arguments @("shell", "run-as", $PackageName, "tar", "-xf", $remoteShadowSnapshotPath, "-C", "/data/user/0/$PackageName") | Out-Null
    }

    Invoke-Adb -Arguments @("logcat", "-c") | Out-Null
    Save-TextFile -Path "$prefix-battery-before.txt" -Content (Invoke-Adb -Arguments @("shell", "dumpsys", "battery"))

    $perfettoPid = $null
    if ($Mode -eq "trace") {
        $perfettoPid = Start-PerfettoTrace -RemoteTracePath $remoteTracePath
    }

    Invoke-Adb -Arguments @("shell", "monkey", "-p", $PackageName, "-c", "android.intent.category.LAUNCHER", "1") | Out-Null

    $libraryDump = Wait-ForLibrary
    Save-TextFile -Path "$prefix-library.xml" -Content $libraryDump.Raw
    Assert-LibraryState -Xml $libraryDump.Xml -Context $runId

    if ($DelaySeconds -gt 0) {
        Start-Sleep -Seconds $DelaySeconds
        $libraryDump = Wait-ForLibrary
        Save-TextFile -Path "$prefix-library-after-delay.xml" -Content $libraryDump.Raw
        Assert-LibraryState -Xml $libraryDump.Xml -Context "$runId after delay"
    }

    $gfxReset = Invoke-Adb -Arguments @("shell", "dumpsys", "gfxinfo", $PackageName, "reset")
    Save-TextFile -Path "$prefix-gfx-reset.txt" -Content $gfxReset

    $bookNodes = @(Find-NodesByExactText -Xml $libraryDump.Xml -Text $BookTitle)
    if ($bookNodes.Count -eq 0) {
        throw "Could not find library tile for '$BookTitle'."
    }

    Tap-NodeCenter -Node (Get-PreferredBookNode -Nodes $bookNodes)
    $readerDump = Wait-ForReaderScreen
    Save-TextFile -Path "$prefix-reader-before-scroll.xml" -Content $readerDump.Raw

    foreach ($swipeIndex in 1..3) {
        Invoke-Adb -Arguments @("shell", "input", "swipe", "608", "2200", "608", "900", "180") | Out-Null
        Start-Sleep -Milliseconds 400
    }

    Start-Sleep -Seconds 1
    $readerAfterDump = Get-TrimmedUiDump
    Save-TextFile -Path "$prefix-reader-after-scroll.xml" -Content $readerAfterDump.Raw

    $gfxText = Invoke-Adb -Arguments @("shell", "dumpsys", "gfxinfo", $PackageName)
    Save-TextFile -Path "$prefix-gfx.txt" -Content $gfxText

    $logcatText = Invoke-Adb -Arguments @("logcat", "-d")
    Save-TextFile -Path "$prefix-logcat.txt" -Content $logcatText

    $meminfoText = Invoke-Adb -Arguments @("shell", "dumpsys", "meminfo", $PackageName)
    Save-TextFile -Path "$prefix-meminfo.txt" -Content $meminfoText

    Save-TextFile -Path "$prefix-battery-after.txt" -Content (Invoke-Adb -Arguments @("shell", "dumpsys", "battery"))

    if ($Mode -eq "trace") {
        Stop-PerfettoTrace -TracePid $perfettoPid -RemoteTracePath $remoteTracePath
        Invoke-Adb -Arguments @("pull", $remoteTracePath, $localTracePath) | Out-Null
        Invoke-Adb -Arguments @("shell", "rm", "-f", $remoteTracePath) -AllowFailure | Out-Null

        & $PythonExe $summaryScriptPath `
            --trace $localTracePath `
            --label "$runLabel run $iteration" `
            --package $PackageName `
            --output-md $traceSummaryMd `
            --output-json $traceSummaryJson
        if ($LASTEXITCODE -ne 0) {
            throw "Trace summary script failed for $runId."
        }
    }

    $metrics = Parse-GfxMetrics -Text $gfxText
    $row = [pscustomobject]@{
        RunId = $runId
        HighInputLatency = $metrics.HighInputLatency
        JankyFrames = $metrics.JankyFrames
        JankyPercent = $metrics.JankyPercent
        P95 = $metrics.P95
        P99 = $metrics.P99
        SlowUiThread = $metrics.SlowUiThread
        FrameDeadlineMissed = $metrics.FrameDeadlineMissed
    }
    if ($Mode -eq "trace") {
        $row | Add-Member -NotePropertyName TracePath -NotePropertyValue $localTracePath
        $row | Add-Member -NotePropertyName TraceSummaryPath -NotePropertyValue $traceSummaryMd
    }
    $results.Add($row)
}

$results | Export-Csv -Path (Join-Path $OutputDir "summary.csv") -NoTypeInformation
Write-MarkdownSummary -Results $results -Path (Join-Path $OutputDir "summary.md")

Write-Host "Completed shadow delayed $Mode probe."
Write-Host "Output: $OutputDir"
