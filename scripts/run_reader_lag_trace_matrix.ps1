param(
    [Parameter(Mandatory = $true)]
    [string]$DeviceSerial,
    [string]$PackageName = "com.epubreader",
    [int]$DelayedWaitSeconds = 15,
    [string]$PythonExe = "python",
    [string]$OutputDir = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $false

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputDir = Join-Path $repoRoot "logs\reader-lag-trace-matrix-$timestamp"
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$traceConfigPath = Join-Path $PSScriptRoot "reader_lag_trace_config.pbtxt"
$summaryScriptPath = Join-Path $PSScriptRoot "summarize_reader_lag_trace.py"
$shadowSnapshotPath = Join-Path $repoRoot "logs\reader-lag-two-book-reset-20260424-0802\pristine-state-cmd.tar"
$ttev6Ch11SnapshotPath = Join-Path $repoRoot "logs\reader-lag-two-book-reset-20260424-0802\pristine-ch11-device.tar"

$remoteTraceConfigPath = "/data/misc/perfetto-configs/reader-lag-trace-config.pbtxt"
$remoteShadowSnapshotPath = "/data/local/tmp/pristine-state-cmd.tar"
$remoteTtev6SnapshotPath = "/data/local/tmp/pristine-ch11-device.tar"

$runs = @(
    [pscustomobject]@{
        RunId = "shadow-immediate-trace"
        Label = "Shadow Slave Immediate"
        BookTitle = "Shadow Slave"
        DelaySeconds = 0
        SnapshotPath = $shadowSnapshotPath
        RemoteSnapshotPath = $remoteShadowSnapshotPath
    },
    [pscustomobject]@{
        RunId = "shadow-delayed-trace"
        Label = "Shadow Slave Delayed"
        BookTitle = "Shadow Slave"
        DelaySeconds = $DelayedWaitSeconds
        SnapshotPath = $shadowSnapshotPath
        RemoteSnapshotPath = $remoteShadowSnapshotPath
    },
    [pscustomobject]@{
        RunId = "ttev6-ch11-immediate-trace"
        Label = "ttev6 Chapter 11 Immediate"
        BookTitle = "The Saga of Tanya the Evil, Vol. 6 (light novel)"
        DelaySeconds = 0
        SnapshotPath = $ttev6Ch11SnapshotPath
        RemoteSnapshotPath = $remoteTtev6SnapshotPath
    }
)

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
    param(
        [int]$MaxAttempts = 20
    )

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
    param(
        [int]$MaxAttempts = 16
    )

    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        Start-Sleep -Milliseconds 500
        $dump = Get-TrimmedUiDump
        if (-not (Ui-ContainsText -Xml $dump.Xml -Text "My Library")) {
            return $dump
        }
    }

    throw "Reader screen did not appear after tapping the book."
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
    $lines.Add("# Reader Lag Trace Matrix")
    $lines.Add("")
    $lines.Add("Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
    $lines.Add("Device: ``$DeviceSerial``")
    $lines.Add("")
    $lines.Add("| Run | Book | Delay Seconds | High Input Latency | Janky Frames | Janky % | P95 | P99 | Slow UI | Frame Deadline Missed | Trace Summary |")
    $lines.Add("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |")
    foreach ($row in $Results) {
        $traceSummaryName = Split-Path -Leaf $row.TraceSummaryPath
        $lines.Add("| $($row.RunId) | $($row.BookTitle) | $($row.DelaySeconds) | $($row.HighInputLatency) | $($row.JankyFrames) | $($row.JankyPercent) | $($row.P95) | $($row.P99) | $($row.SlowUiThread) | $($row.FrameDeadlineMissed) | $traceSummaryName |")
    }

    Save-TextFile -Path $Path -Content ($lines -join "`r`n")
}

if (-not (Test-Path $traceConfigPath)) {
    throw "Trace config not found: $traceConfigPath"
}
if (-not (Test-Path $summaryScriptPath)) {
    throw "Trace summary script not found: $summaryScriptPath"
}

Invoke-Adb -Arguments @("push", $traceConfigPath, $remoteTraceConfigPath) | Out-Null
Invoke-Adb -Arguments @("shell", "chmod", "666", $remoteTraceConfigPath) | Out-Null
Invoke-Adb -Arguments @("push", $shadowSnapshotPath, $remoteShadowSnapshotPath) | Out-Null
Invoke-Adb -Arguments @("shell", "chmod", "644", $remoteShadowSnapshotPath) | Out-Null
Invoke-Adb -Arguments @("push", $ttev6Ch11SnapshotPath, $remoteTtev6SnapshotPath) | Out-Null
Invoke-Adb -Arguments @("shell", "chmod", "644", $remoteTtev6SnapshotPath) | Out-Null

$results = New-Object System.Collections.Generic.List[object]

foreach ($run in $runs) {
    $prefix = Join-Path $OutputDir $run.RunId
    $remoteTracePath = "/data/misc/perfetto-traces/$($run.RunId).pftrace"
    $localTracePath = "$prefix-trace.pftrace"
    $traceSummaryMd = "$prefix-trace-summary.md"
    $traceSummaryJson = "$prefix-trace-summary.json"

    Invoke-Adb -Arguments @("shell", "rm", "-f", $remoteTracePath) -AllowFailure | Out-Null
    Invoke-Adb -Arguments @("shell", "am", "force-stop", $PackageName) | Out-Null
    Invoke-Adb -Arguments @("shell", "pm", "clear", $PackageName) | Out-Null
    Invoke-Adb -Arguments @("shell", "run-as", $PackageName, "tar", "-xf", $run.RemoteSnapshotPath, "-C", "/data/user/0/$PackageName") | Out-Null
    Invoke-Adb -Arguments @("logcat", "-c") | Out-Null

    $perfettoPid = Start-PerfettoTrace -RemoteTracePath $remoteTracePath
    Invoke-Adb -Arguments @("shell", "monkey", "-p", $PackageName, "-c", "android.intent.category.LAUNCHER", "1") | Out-Null

    $libraryDump = Wait-ForLibrary
    Save-TextFile -Path "$prefix-library.xml" -Content $libraryDump.Raw

    if ($run.DelaySeconds -gt 0) {
        Start-Sleep -Seconds $run.DelaySeconds
        $libraryDump = Wait-ForLibrary
        Save-TextFile -Path "$prefix-library-after-delay.xml" -Content $libraryDump.Raw
    }

    $gfxReset = Invoke-Adb -Arguments @("shell", "dumpsys", "gfxinfo", $PackageName, "reset")
    Save-TextFile -Path "$prefix-gfx-reset.txt" -Content $gfxReset

    $bookNodes = @(Find-NodesByExactText -Xml $libraryDump.Xml -Text $run.BookTitle)
    if ($bookNodes.Count -eq 0) {
        throw "Could not find library tile for '$($run.BookTitle)'."
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

    Stop-PerfettoTrace -TracePid $perfettoPid -RemoteTracePath $remoteTracePath
    Invoke-Adb -Arguments @("pull", $remoteTracePath, $localTracePath) | Out-Null
    Invoke-Adb -Arguments @("shell", "rm", "-f", $remoteTracePath) -AllowFailure | Out-Null

    & $PythonExe $summaryScriptPath `
        --trace $localTracePath `
        --label $run.Label `
        --package $PackageName `
        --output-md $traceSummaryMd `
        --output-json $traceSummaryJson
    if ($LASTEXITCODE -ne 0) {
        throw "Trace summary script failed for $($run.RunId)."
    }

    $metrics = Parse-GfxMetrics -Text $gfxText
    $results.Add([pscustomobject]@{
            RunId = $run.RunId
            BookTitle = $run.BookTitle
            DelaySeconds = $run.DelaySeconds
            HighInputLatency = $metrics.HighInputLatency
            JankyFrames = $metrics.JankyFrames
            JankyPercent = $metrics.JankyPercent
            P95 = $metrics.P95
            P99 = $metrics.P99
            SlowUiThread = $metrics.SlowUiThread
            FrameDeadlineMissed = $metrics.FrameDeadlineMissed
            TracePath = $localTracePath
            TraceSummaryPath = $traceSummaryMd
        })
}

$results | Export-Csv -Path (Join-Path $OutputDir "summary.csv") -NoTypeInformation
Write-MarkdownSummary -Results $results -Path (Join-Path $OutputDir "summary.md")

Write-Host "Completed reader lag trace matrix."
Write-Host "Output: $OutputDir"
