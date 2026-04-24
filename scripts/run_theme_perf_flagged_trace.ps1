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
    $OutputDir = Join-Path $repoRoot "logs\theme-perf-trace-followup-$timestamp"
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$traceConfigPath = Join-Path $PSScriptRoot "reader_lag_trace_config.pbtxt"
$summaryScriptPath = Join-Path $PSScriptRoot "summarize_reader_lag_trace.py"
$remoteTraceConfigPath = "/data/misc/perfetto-configs/theme-perf-trace-config.pbtxt"

$runs = @(
    [pscustomobject]@{
        RunId = "appearance-open-immediate-trace"
        Label = "Appearance Open Immediate"
        ScenarioKey = "appearance-open"
        DelaySeconds = 0
    },
    [pscustomobject]@{
        RunId = "appearance-pager-delayed-trace"
        Label = "Appearance Pager Swipe Return Delayed"
        ScenarioKey = "appearance-pager-swipe-return"
        DelaySeconds = $DelayedWaitSeconds
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
        throw "adb failed ($($Arguments -join ' '))`n$text"
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

    @($matches.ToArray())
}

function Ui-ContainsText {
    param(
        [Parameter(Mandatory = $true)]
        [xml]$Xml,
        [Parameter(Mandatory = $true)]
        [string]$Text
    )

    @(Find-NodesByExactText -Xml $Xml -Text $Text).Count -gt 0
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

    [pscustomobject]@{
        X = [int](($match.Groups[1].Value + $match.Groups[3].Value) / 2)
        Y = [int](($match.Groups[2].Value + $match.Groups[4].Value) / 2)
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

function Get-FirstExactNode {
    param(
        [Parameter(Mandatory = $true)]
        [xml]$Xml,
        [Parameter(Mandatory = $true)]
        [string]$Text
    )

    $nodes = @(Find-NodesByExactText -Xml $Xml -Text $Text)
    if ($nodes.Count -eq 0) { return $null }
    $nodes[0]
}

function Dismiss-DialogIfPresent {
    param(
        [Parameter(Mandatory = $true)]
        [xml]$Xml
    )

    if (Ui-ContainsText -Xml $Xml -Text "What's New") {
        $okNode = Get-FirstExactNode -Xml $Xml -Text "OK"
        if ($okNode) {
            Tap-NodeCenter -Node $okNode
            Start-Sleep -Seconds 2
            return $true
        }
    }

    if (Ui-ContainsText -Xml $Xml -Text "Welcome to Blue Waves") {
        $startNode = Get-FirstExactNode -Xml $Xml -Text "Start"
        if ($startNode) {
            Tap-NodeCenter -Node $startNode
            Start-Sleep -Seconds 2
            return $true
        }
    }

    return $false
}

function Wait-ForScreen {
    param(
        [Parameter(Mandatory = $true)]
        [scriptblock]$Condition,
        [string]$Label = "screen",
        [int]$MaxAttempts = 20,
        [int]$SleepMilliseconds = 1200
    )

    $lastDump = $null
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        $dump = Get-TrimmedUiDump
        $lastDump = $dump
        if (Dismiss-DialogIfPresent -Xml $dump.Xml) {
            continue
        }
        if (& $Condition $dump) {
            return $dump
        }
        Start-Sleep -Milliseconds $SleepMilliseconds
    }

    if ($lastDump) {
        Save-TextFile -Path (Join-Path $OutputDir "failure-$Label.xml") -Content $lastDump.Raw
    }
    throw "Expected screen condition was not met for $Label."
}

function Wait-ForLibrary {
    Wait-ForScreen -Label "library" -Condition {
        param($dump)
        (Ui-ContainsText -Xml $dump.Xml -Text "My Library") -and (Ui-ContainsText -Xml $dump.Xml -Text "Settings")
    }
}

function Wait-ForSettingsScreen {
    Wait-ForScreen -Label "settings" -Condition {
        param($dump)
        (Ui-ContainsText -Xml $dump.Xml -Text "Settings") -and (Ui-ContainsText -Xml $dump.Xml -Text "Appearance")
    }
}

function Wait-ForAppearanceScreen {
    Wait-ForScreen -Label "appearance" -Condition {
        param($dump)
        (Ui-ContainsText -Xml $dump.Xml -Text "Appearance") -and (Ui-ContainsText -Xml $dump.Xml -Text "Gallery") -and (Ui-ContainsText -Xml $dump.Xml -Text "Create")
    }
}

function Wait-ForThemeVisible {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ThemeName
    )

    Wait-ForScreen -Label "theme-$($ThemeName -replace '\s+', '-')" -MaxAttempts 12 -Condition {
        param($dump)
        Ui-ContainsText -Xml $dump.Xml -Text $ThemeName
    }
}

function Open-SettingsFromLibrary {
    param([Parameter(Mandatory = $true)] [pscustomobject]$LibraryDump)
    $currentDump = $LibraryDump
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        Start-Sleep -Milliseconds 700
        $settingsNode = Get-FirstExactNode -Xml $currentDump.Xml -Text "Settings"
        if (-not $settingsNode) { throw "Could not find Settings on library." }
        Tap-NodeCenter -Node $settingsNode
        try {
            $settingsDump = Wait-ForScreen -Label "settings" -MaxAttempts 6 -Condition {
                param($dump)
                (Ui-ContainsText -Xml $dump.Xml -Text "Settings") -and (Ui-ContainsText -Xml $dump.Xml -Text "Appearance")
            }
            Start-Sleep -Milliseconds 700
            return $settingsDump
        }
        catch {
            if ($attempt -eq 3) {
                throw
            }
            $currentDump = Wait-ForLibrary
        }
    }

    throw "Settings screen did not open."
}

function Open-AppearanceFromSettings {
    param([Parameter(Mandatory = $true)] [pscustomobject]$SettingsDump)
    $appearanceNode = Get-FirstExactNode -Xml $SettingsDump.Xml -Text "Appearance"
    if (-not $appearanceNode) { throw "Could not find Appearance on settings screen." }
    Tap-NodeCenter -Node $appearanceNode
    $appearanceDump = Wait-ForAppearanceScreen
    Start-Sleep -Milliseconds 1500
    $appearanceDump
}

function Open-AppearanceFromLibrary {
    param([Parameter(Mandatory = $true)] [pscustomobject]$LibraryDump)
    $settingsDump = Open-SettingsFromLibrary -LibraryDump $LibraryDump
    Open-AppearanceFromSettings -SettingsDump $settingsDump
}

function Open-AppearanceFixedFromLibrary {
    Invoke-Adb -Arguments @("shell", "input", "tap", "1125", "229") | Out-Null
    Start-Sleep -Seconds 2
    $settingsDump = Wait-ForSettingsScreen
    Start-Sleep -Milliseconds 700
    Invoke-Adb -Arguments @("shell", "input", "tap", "599", "450") | Out-Null
    Start-Sleep -Seconds 3
    $appearanceDump = Wait-ForAppearanceScreen
    Start-Sleep -Milliseconds 1500
    return [pscustomobject]@{
        SettingsDump = $settingsDump
        AppearanceDump = $appearanceDump
    }
}

function Swipe-AppearancePager {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Direction,
        [Parameter(Mandatory = $true)]
        [pscustomobject]$AppearanceDump
    )

    $xStart = if ($Direction -eq "left") { 980 } else { 180 }
    $xEnd = if ($Direction -eq "left") { 140 } else { 1040 }
    $y = 700
    Invoke-Adb -Arguments @("shell", "input", "swipe", "$xStart", "$y", "$xEnd", "$y", "320") | Out-Null
}

function Parse-GfxMetrics {
    param([Parameter(Mandatory = $true)] [string]$Text)
    $patterns = @{
        HighInputLatency = "Number High input latency:\s+(\d+)"
        JankyFrames = "Janky frames:\s+(\d+)\s+\(([0-9.]+)%\)"
        P95 = "95th percentile:\s+(\d+)ms"
        P99 = "99th percentile:\s+(\d+)ms"
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
    [pscustomobject]$result
}

function Start-PerfettoTrace {
    param([Parameter(Mandatory = $true)] [string]$RemoteTracePath)
    $output = Invoke-Adb -Arguments @("shell", "perfetto", "--background-wait", "--txt", "-c", $remoteTraceConfigPath, "-o", $RemoteTracePath)
    $match = [regex]::Match($output, "(\d+)")
    if (-not $match.Success) {
        throw "Perfetto did not return a background PID.`n$output"
    }
    [int]$match.Groups[1].Value
}

function Stop-PerfettoTrace {
    param(
        [Parameter(Mandatory = $true)] [int]$TracePid,
        [Parameter(Mandatory = $true)] [string]$RemoteTracePath
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

function Normalize-ToPaperWhite {
    Invoke-Adb -Arguments @("shell", "am", "force-stop", $PackageName) | Out-Null
    Invoke-Adb -Arguments @("logcat", "-c") | Out-Null
    Invoke-Adb -Arguments @("shell", "monkey", "-p", $PackageName, "-c", "android.intent.category.LAUNCHER", "1") | Out-Null
    $libraryDump = Wait-ForLibrary
    Save-TextFile -Path (Join-Path $OutputDir "preflight-library.xml") -Content $libraryDump.Raw
    Start-Sleep -Seconds 4
    Invoke-Adb -Arguments @("shell", "input", "tap", "1125", "229") | Out-Null
    Start-Sleep -Seconds 2
    Invoke-Adb -Arguments @("shell", "input", "tap", "599", "450") | Out-Null
    Start-Sleep -Seconds 3
    Invoke-Adb -Arguments @("shell", "input", "tap", "973", "1172") | Out-Null
    Start-Sleep -Seconds 2
    $galleryDump = Wait-ForScreen -Label "preflight-gallery" -Condition {
        param($dump)
        (Ui-ContainsText -Xml $dump.Xml -Text "Theme Gallery") -and (Ui-ContainsText -Xml $dump.Xml -Text "Done")
    }
    Save-TextFile -Path (Join-Path $OutputDir "preflight-gallery.xml") -Content $galleryDump.Raw
    Invoke-Adb -Arguments @("shell", "input", "tap", "333", "614") | Out-Null
    Start-Sleep -Seconds 1
    Invoke-Adb -Arguments @("shell", "input", "tap", "1056", "252") | Out-Null
    Start-Sleep -Seconds 2
    $appearanceDump = Wait-ForThemeVisible -ThemeName "Paper White"
    Save-TextFile -Path (Join-Path $OutputDir "preflight-appearance.xml") -Content $appearanceDump.Raw
    Invoke-Adb -Arguments @("shell", "am", "force-stop", $PackageName) | Out-Null
}

function Write-MarkdownSummary {
    param(
        [Parameter(Mandatory = $true)] [object[]]$Results,
        [Parameter(Mandatory = $true)] [string]$Path
    )

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# Theme Performance Trace Follow-Up")
    $lines.Add("")
    $lines.Add("Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
    $lines.Add("Device: ``$DeviceSerial``")
    $lines.Add("")
    $lines.Add("| Run | Scenario | Delay Seconds | High Input Latency | Janky Frames | Janky % | P95 | P99 | Slow UI | Frame Deadline Missed | Trace Summary |")
    $lines.Add("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |")
    foreach ($row in $Results) {
        $lines.Add("| $($row.RunId) | $($row.ScenarioKey) | $($row.DelaySeconds) | $($row.HighInputLatency) | $($row.JankyFrames) | $($row.JankyPercent) | $($row.P95) | $($row.P99) | $($row.SlowUiThread) | $($row.FrameDeadlineMissed) | $(Split-Path -Leaf $row.TraceSummaryPath) |")
    }
    Save-TextFile -Path $Path -Content ($lines -join "`r`n")
}

if (-not (Test-Path $traceConfigPath)) { throw "Trace config not found: $traceConfigPath" }
if (-not (Test-Path $summaryScriptPath)) { throw "Trace summary script not found: $summaryScriptPath" }

Invoke-Adb -Arguments @("push", $traceConfigPath, $remoteTraceConfigPath) | Out-Null
Invoke-Adb -Arguments @("shell", "chmod", "666", $remoteTraceConfigPath) | Out-Null
Normalize-ToPaperWhite

$results = New-Object System.Collections.Generic.List[object]

foreach ($run in $runs) {
    $prefix = Join-Path $OutputDir $run.RunId
    $remoteTracePath = "/data/misc/perfetto-traces/$($run.RunId).pftrace"
    $localTracePath = "$prefix-trace.pftrace"
    $traceSummaryMd = "$prefix-trace-summary.md"
    $traceSummaryJson = "$prefix-trace-summary.json"

    Invoke-Adb -Arguments @("shell", "rm", "-f", $remoteTracePath) -AllowFailure | Out-Null
    Invoke-Adb -Arguments @("shell", "am", "force-stop", $PackageName) | Out-Null
    Invoke-Adb -Arguments @("logcat", "-c") | Out-Null
    Invoke-Adb -Arguments @("shell", "monkey", "-p", $PackageName, "-c", "android.intent.category.LAUNCHER", "1") | Out-Null

    $libraryDump = Wait-ForLibrary
    Save-TextFile -Path "$prefix-library.xml" -Content $libraryDump.Raw
    if ($run.DelaySeconds -gt 0) {
        Start-Sleep -Seconds $run.DelaySeconds
        $libraryDump = Wait-ForLibrary
        Save-TextFile -Path "$prefix-library-after-delay.xml" -Content $libraryDump.Raw
    }

    $perfettoPid = $null
    switch ($run.ScenarioKey) {
        "appearance-open" {
            $perfettoPid = Start-PerfettoTrace -RemoteTracePath $remoteTracePath
            $gfxReset = Invoke-Adb -Arguments @("shell", "dumpsys", "gfxinfo", $PackageName, "reset")
            Save-TextFile -Path "$prefix-gfx-reset.txt" -Content $gfxReset
            $openResult = Open-AppearanceFixedFromLibrary
            Save-TextFile -Path "$prefix-settings.xml" -Content $openResult.SettingsDump.Raw
            Save-TextFile -Path "$prefix-appearance.xml" -Content $openResult.AppearanceDump.Raw
        }
        "appearance-pager-swipe-return" {
            $openResult = Open-AppearanceFixedFromLibrary
            $appearanceDump = $openResult.AppearanceDump
            Save-TextFile -Path "$prefix-settings.xml" -Content $openResult.SettingsDump.Raw
            Save-TextFile -Path "$prefix-appearance-before.xml" -Content $appearanceDump.Raw
            $perfettoPid = Start-PerfettoTrace -RemoteTracePath $remoteTracePath
            $gfxReset = Invoke-Adb -Arguments @("shell", "dumpsys", "gfxinfo", $PackageName, "reset")
            Save-TextFile -Path "$prefix-gfx-reset.txt" -Content $gfxReset
            Swipe-AppearancePager -Direction "left" -AppearanceDump $appearanceDump
            $appearanceDump = Wait-ForThemeVisible -ThemeName "Sepia"
            Save-TextFile -Path "$prefix-appearance-sepia.xml" -Content $appearanceDump.Raw
            Start-Sleep -Milliseconds 600
            Swipe-AppearancePager -Direction "right" -AppearanceDump $appearanceDump
            $appearanceDump = Wait-ForThemeVisible -ThemeName "Paper White"
            Save-TextFile -Path "$prefix-appearance-after.xml" -Content $appearanceDump.Raw
        }
        default {
            throw "Unsupported scenario $($run.ScenarioKey)"
        }
    }

    Start-Sleep -Seconds 1
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
            ScenarioKey = $run.ScenarioKey
            DelaySeconds = $run.DelaySeconds
            HighInputLatency = $metrics.HighInputLatency
            JankyFrames = $metrics.JankyFrames
            JankyPercent = $metrics.JankyPercent
            P95 = $metrics.P95
            P99 = $metrics.P99
            SlowUiThread = $metrics.SlowUiThread
            FrameDeadlineMissed = $metrics.FrameDeadlineMissed
            TraceSummaryPath = $traceSummaryMd
        })
}

$results | Export-Csv -Path (Join-Path $OutputDir "summary.csv") -NoTypeInformation
Write-MarkdownSummary -Results @($results.ToArray()) -Path (Join-Path $OutputDir "summary.md")
Write-Host "Completed theme performance trace follow-up."
Write-Host "Output: $OutputDir"
