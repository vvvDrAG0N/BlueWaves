param(
    [Parameter(Mandatory = $true)]
    [string]$DeviceSerial,
    [string]$PackageName = "com.epubreader",
    [int]$DelayedWaitSeconds = 15,
    [int]$Iterations = 3,
    [string]$OutputDir = "",
    [switch]$SmokeOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $false

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $folderName = if ($SmokeOnly) {
        "theme-perf-release-live-smoke-$timestamp"
    }
    else {
        "theme-perf-release-live-$timestamp"
    }
    $OutputDir = Join-Path $repoRoot "logs\$folderName"
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$scenarios = if ($SmokeOnly) {
    @(
        [pscustomobject]@{
            ScenarioKey = "appearance-open"
            StartupCondition = "immediate"
            DelaySeconds = 0
            Iteration = 1
        },
        [pscustomobject]@{
            ScenarioKey = "gallery-open-close"
            StartupCondition = "immediate"
            DelaySeconds = 0
            Iteration = 1
        }
    )
}
else {
    $scenarioKeys = @(
        "appearance-open",
        "appearance-pager-swipe-return",
        "gallery-open-close",
        "gallery-switch-return"
    )
    $startupModes = @(
        [pscustomobject]@{ Name = "immediate"; DelaySeconds = 0 },
        [pscustomobject]@{ Name = "delayed"; DelaySeconds = $DelayedWaitSeconds }
    )

    $generatedRuns = New-Object System.Collections.Generic.List[object]
    foreach ($scenarioKey in $scenarioKeys) {
        foreach ($startupMode in $startupModes) {
            foreach ($iteration in 1..$Iterations) {
                $generatedRuns.Add([pscustomobject]@{
                        ScenarioKey = $scenarioKey
                        StartupCondition = $startupMode.Name
                        DelaySeconds = $startupMode.DelaySeconds
                        Iteration = $iteration
                    })
            }
        }
    }
    @($generatedRuns.ToArray())
}

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

    $x1 = [int]$match.Groups[1].Value
    $y1 = [int]$match.Groups[2].Value
    $x2 = [int]$match.Groups[3].Value
    $y2 = [int]$match.Groups[4].Value
    [pscustomobject]@{
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

function Dismiss-DialogIfPresent {
    param(
        [Parameter(Mandatory = $true)]
        [xml]$Xml
    )

    $dialogSpecs = @(
        @{ Marker = "What's New"; Buttons = @("OK", "Done", "Close") },
        @{ Marker = "Welcome to Blue Waves"; Buttons = @("Start", "Continue", "OK") }
    )

    foreach ($spec in $dialogSpecs) {
        if (Ui-ContainsText -Xml $Xml -Text $spec.Marker) {
            foreach ($buttonText in $spec.Buttons) {
                $buttonNodes = @(Find-NodesByExactText -Xml $Xml -Text $buttonText)
                if ($buttonNodes.Count -gt 0) {
                    Tap-NodeCenter -Node $buttonNodes[0]
                    Start-Sleep -Seconds 2
                    return $true
                }
            }
        }
    }

    $doneNodes = @(Find-NodesByExactText -Xml $Xml -Text "Done")
    if ($doneNodes.Count -gt 0 -and (Ui-ContainsText -Xml $Xml -Text "Theme Gallery")) {
        return $false
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
    Wait-ForScreen -Condition {
        param($dump)
        (Ui-ContainsText -Xml $dump.Xml -Text "My Library") -and (Ui-ContainsText -Xml $dump.Xml -Text "Settings")
    } -Label "library"
}

function Wait-ForSettingsScreen {
    Wait-ForScreen -Condition {
        param($dump)
        (Ui-ContainsText -Xml $dump.Xml -Text "Settings") -and (Ui-ContainsText -Xml $dump.Xml -Text "Appearance")
    } -Label "settings"
}

function Wait-ForAppearanceScreen {
    Wait-ForScreen -Condition {
        param($dump)
        (Ui-ContainsText -Xml $dump.Xml -Text "Appearance") -and (Ui-ContainsText -Xml $dump.Xml -Text "Gallery") -and (Ui-ContainsText -Xml $dump.Xml -Text "Create")
    } -Label "appearance"
}

function Wait-ForGalleryScreen {
    Wait-ForScreen -Condition {
        param($dump)
        (Ui-ContainsText -Xml $dump.Xml -Text "Theme Gallery") -and (Ui-ContainsText -Xml $dump.Xml -Text "Done")
    } -Label "gallery"
}

function Wait-ForThemeVisible {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ThemeName,
        [int]$MaxAttempts = 12
    )

    Wait-ForScreen -MaxAttempts $MaxAttempts -Condition {
        param($dump)
        Ui-ContainsText -Xml $dump.Xml -Text $ThemeName
    } -Label "theme-$($ThemeName -replace '\s+', '-')"
}

function Get-FirstExactNode {
    param(
        [Parameter(Mandatory = $true)]
        [xml]$Xml,
        [Parameter(Mandatory = $true)]
        [string]$Text
    )

    $nodes = @(Find-NodesByExactText -Xml $Xml -Text $Text)
    if ($nodes.Count -eq 0) {
        return $null
    }
    $nodes[0]
}

function Open-SettingsFromLibrary {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$LibraryDump
    )

    $settingsNode = Get-FirstExactNode -Xml $LibraryDump.Xml -Text "Settings"
    if (-not $settingsNode) {
        throw "Could not find Settings button on library screen."
    }
    Tap-NodeCenter -Node $settingsNode
    $settingsDump = Wait-ForSettingsScreen
    Start-Sleep -Milliseconds 700
    $settingsDump
}

function Open-AppearanceFromSettings {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$SettingsDump
    )

    $appearanceNode = Get-FirstExactNode -Xml $SettingsDump.Xml -Text "Appearance"
    if (-not $appearanceNode) {
        throw "Could not find Appearance row on settings screen."
    }
    Tap-NodeCenter -Node $appearanceNode
    $appearanceDump = Wait-ForAppearanceScreen
    Start-Sleep -Milliseconds 1500
    $appearanceDump
}

function Open-AppearanceFromLibrary {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$LibraryDump
    )

    $settingsDump = Open-SettingsFromLibrary -LibraryDump $LibraryDump
    Open-AppearanceFromSettings -SettingsDump $settingsDump
}

function Open-GalleryFromAppearance {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$AppearanceDump
    )

    $currentDump = $AppearanceDump
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        Start-Sleep -Milliseconds 1500
        $galleryNode = Get-FirstExactNode -Xml $currentDump.Xml -Text "Gallery"
        if (-not $galleryNode) {
            throw "Could not find Gallery button on Appearance screen."
        }

        Tap-NodeCenter -Node $galleryNode
        try {
            return (Wait-ForScreen -Label "gallery" -MaxAttempts 6 -Condition {
                    param($dump)
                    (Ui-ContainsText -Xml $dump.Xml -Text "Theme Gallery") -and (Ui-ContainsText -Xml $dump.Xml -Text "Done")
                })
        }
        catch {
            if ($attempt -eq 3) {
                throw
            }
            $currentDump = Wait-ForAppearanceScreen
        }
    }

    throw "Theme Gallery did not open."
}

function Close-Gallery {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$GalleryDump
    )

    $doneNode = Get-FirstExactNode -Xml $GalleryDump.Xml -Text "Done"
    if (-not $doneNode) {
        throw "Could not find Done button in Theme Gallery."
    }
    Tap-NodeCenter -Node $doneNode
    Wait-ForAppearanceScreen
}

function Select-ThemeInGallery {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$GalleryDump,
        [Parameter(Mandatory = $true)]
        [string]$ThemeName
    )

    $themeNode = Get-FirstExactNode -Xml $GalleryDump.Xml -Text "Theme $ThemeName"
    if (-not $themeNode) {
        throw "Could not find Theme $ThemeName in Theme Gallery."
    }
    Tap-NodeCenter -Node $themeNode
}

function Swipe-AppearancePager {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Direction,
        [Parameter(Mandatory = $true)]
        [pscustomobject]$AppearanceDump
    )

    $xStart = if ($Direction -eq "left") { 920 } else { 280 }
    $xEnd = if ($Direction -eq "left") { 296 } else { 936 }
    $referenceNode = $null
    foreach ($candidate in @("Paper White", "Sepia", "Midnight", "Onyx", "Deep Forest")) {
        $referenceNode = Get-FirstExactNode -Xml $AppearanceDump.Xml -Text $candidate
        if ($referenceNode) { break }
    }
    $y = if ($referenceNode) {
        (Get-CenterFromBounds -Bounds $referenceNode.GetAttribute("bounds")).Y + 60
    }
    else {
        650
    }
    Invoke-Adb -Arguments @("shell", "input", "swipe", "$xStart", "$y", "$xEnd", "$y", "220") | Out-Null
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

    [pscustomobject]$result
}

function Format-Number {
    param(
        [Parameter(Mandatory = $false)]
        $Value
    )

    if ($null -eq $Value) { return "-" }
    if ($Value -is [double] -or $Value -is [decimal]) {
        return ("{0:N2}" -f [double]$Value).TrimEnd('0').TrimEnd('.')
    }
    return [string]$Value
}

function Get-Average {
    param(
        [Parameter(Mandatory = $true)]
        [object[]]$Rows,
        [Parameter(Mandatory = $true)]
        [string]$PropertyName
    )

    $values = @($Rows | ForEach-Object { $_.$PropertyName } | Where-Object { $null -ne $_ })
    if ($values.Count -eq 0) { return $null }
    (($values | Measure-Object -Average).Average)
}

function Write-MarkdownSummary {
    param(
        [Parameter(Mandatory = $true)]
        [object[]]$Results,
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# Appearance Performance Audit")
    $lines.Add("")
    $lines.Add("Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
    $lines.Add("Device: ``$DeviceSerial``")
    $lines.Add("Package: ``$PackageName``")
    $lines.Add("Mode: $(if ($SmokeOnly) { 'Smoke' } else { 'Full matrix' })")
    $lines.Add("")
    $lines.Add("| Run | Scenario | Startup | Delay Seconds | High Input Latency | Janky Frames | Janky % | P95 | P99 | Slow UI | Frame Deadline Missed |")
    $lines.Add("| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
    foreach ($row in $Results) {
        $lines.Add("| $($row.RunId) | $($row.ScenarioKey) | $($row.StartupCondition) | $($row.DelaySeconds) | $($row.HighInputLatency) | $($row.JankyFrames) | $($row.JankyPercent) | $($row.P95) | $($row.P99) | $($row.SlowUiThread) | $($row.FrameDeadlineMissed) |")
    }

    $lines.Add("")
    $lines.Add("## Per-Scenario Averages")
    $lines.Add("")
    $lines.Add("| Scenario | Startup | Runs | High Input Latency | Janky Frames | Janky % | P95 | P99 | Slow UI | Frame Deadline Missed |")
    $lines.Add("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")

    $grouped = $Results | Group-Object ScenarioKey, StartupCondition
    $scenarioAverages = @()
    foreach ($group in $grouped) {
        $first = $group.Group[0]
        $averageRow = [pscustomobject]@{
            ScenarioKey = $first.ScenarioKey
            StartupCondition = $first.StartupCondition
            Runs = $group.Count
            HighInputLatency = Get-Average -Rows $group.Group -PropertyName "HighInputLatency"
            JankyFrames = Get-Average -Rows $group.Group -PropertyName "JankyFrames"
            JankyPercent = Get-Average -Rows $group.Group -PropertyName "JankyPercent"
            P95 = Get-Average -Rows $group.Group -PropertyName "P95"
            P99 = Get-Average -Rows $group.Group -PropertyName "P99"
            SlowUiThread = Get-Average -Rows $group.Group -PropertyName "SlowUiThread"
            FrameDeadlineMissed = Get-Average -Rows $group.Group -PropertyName "FrameDeadlineMissed"
        }
        $scenarioAverages += $averageRow
        $lines.Add("| $($averageRow.ScenarioKey) | $($averageRow.StartupCondition) | $($averageRow.Runs) | $(Format-Number $averageRow.HighInputLatency) | $(Format-Number $averageRow.JankyFrames) | $(Format-Number $averageRow.JankyPercent) | $(Format-Number $averageRow.P95) | $(Format-Number $averageRow.P99) | $(Format-Number $averageRow.SlowUiThread) | $(Format-Number $averageRow.FrameDeadlineMissed) |")
    }

    if (-not $SmokeOnly) {
        $lines.Add("")
        $lines.Add("## Immediate vs Delayed")
        $lines.Add("")
        $lines.Add("| Scenario | Immediate Avg High Input Latency | Delayed Avg High Input Latency | Delta | Delayed Better % | Gap Classification | Avg Janky % Flag |")
        $lines.Add("| --- | ---: | ---: | ---: | ---: | --- | --- |")

        $flagged = New-Object System.Collections.Generic.List[string]
        foreach ($scenarioKey in @("appearance-open", "appearance-pager-swipe-return", "gallery-open-close", "gallery-switch-return")) {
            $immediate = $scenarioAverages | Where-Object { $_.ScenarioKey -eq $scenarioKey -and $_.StartupCondition -eq "immediate" } | Select-Object -First 1
            $delayed = $scenarioAverages | Where-Object { $_.ScenarioKey -eq $scenarioKey -and $_.StartupCondition -eq "delayed" } | Select-Object -First 1
            if (-not $immediate -or -not $delayed) { continue }

            $delta = if ($null -ne $immediate.HighInputLatency -and $null -ne $delayed.HighInputLatency) {
                $delayed.HighInputLatency - $immediate.HighInputLatency
            }
            else {
                $null
            }

            $improvementPercent = $null
            $classification = "n/a"
            if ($null -ne $immediate.HighInputLatency -and $immediate.HighInputLatency -gt 0 -and $null -ne $delayed.HighInputLatency) {
                $improvementPercent = (($immediate.HighInputLatency - $delayed.HighInputLatency) / $immediate.HighInputLatency) * 100.0
                if ($improvementPercent -gt 30) {
                    $classification = "large gap"
                    $flagged.Add("$scenarioKey (large immediate-vs-delayed gap)")
                }
                elseif ($improvementPercent -ge 10) {
                    $classification = "moderate gap"
                }
                elseif ($improvementPercent -ge 0) {
                    $classification = "small gap"
                }
                else {
                    $classification = "delayed worse"
                }
            }

            $jankyFlag = if ((($null -ne $immediate.JankyPercent) -and ($immediate.JankyPercent -gt 5)) -or (($null -ne $delayed.JankyPercent) -and ($delayed.JankyPercent -gt 5))) {
                if (-not ($flagged.Contains("$scenarioKey (avg janky % > 5)"))) {
                    $flagged.Add("$scenarioKey (avg janky % > 5)")
                }
                "flag"
            }
            else {
                "-"
            }

            $lines.Add("| $scenarioKey | $(Format-Number $immediate.HighInputLatency) | $(Format-Number $delayed.HighInputLatency) | $(Format-Number $delta) | $(Format-Number $improvementPercent) | $classification | $jankyFlag |")
        }

        $lines.Add("")
        $lines.Add("## Follow-Up Flags")
        $lines.Add("")
        if ($flagged.Count -eq 0) {
            $lines.Add("- None from metrics alone.")
        }
        else {
            foreach ($item in $flagged | Select-Object -Unique) {
                $lines.Add("- $item")
            }
        }
    }

    Save-TextFile -Path $Path -Content ($lines -join "`r`n")
}

function Assert-ReleaseLikeInstall {
    $packageDump = Invoke-Adb -Arguments @("shell", "dumpsys", "package", $PackageName)
    if ($packageDump -match "DEBUGGABLE") {
        throw "Package $PackageName appears debuggable. Install the release-like build before running this audit."
    }
}

function Launch-AppAndWaitForLibrary {
    Invoke-Adb -Arguments @("shell", "am", "force-stop", $PackageName) | Out-Null
    Invoke-Adb -Arguments @("logcat", "-c") | Out-Null
    Invoke-Adb -Arguments @("shell", "monkey", "-p", $PackageName, "-c", "android.intent.category.LAUNCHER", "1") | Out-Null
    Wait-ForLibrary
}

function Normalize-ToPaperWhite {
    $libraryDump = Launch-AppAndWaitForLibrary
    Save-TextFile -Path (Join-Path $OutputDir "preflight-library.xml") -Content $libraryDump.Raw
    Start-Sleep -Seconds 4
    Invoke-Adb -Arguments @("shell", "input", "tap", "1125", "229") | Out-Null
    Start-Sleep -Seconds 2
    Invoke-Adb -Arguments @("shell", "input", "tap", "599", "450") | Out-Null
    Start-Sleep -Seconds 3
    Invoke-Adb -Arguments @("shell", "input", "tap", "973", "1172") | Out-Null
    Start-Sleep -Seconds 2
    $galleryDump = Wait-ForGalleryScreen
    Save-TextFile -Path (Join-Path $OutputDir "preflight-gallery.xml") -Content $galleryDump.Raw
    Invoke-Adb -Arguments @("shell", "input", "tap", "333", "614") | Out-Null
    Start-Sleep -Seconds 1
    Invoke-Adb -Arguments @("shell", "input", "tap", "1056", "252") | Out-Null
    Start-Sleep -Seconds 2
    $appearanceDump = Wait-ForThemeVisible -ThemeName "Paper White"
    Save-TextFile -Path (Join-Path $OutputDir "preflight-appearance.xml") -Content $appearanceDump.Raw
    Invoke-Adb -Arguments @("shell", "am", "force-stop", $PackageName) | Out-Null
}

Assert-ReleaseLikeInstall
Normalize-ToPaperWhite

$results = New-Object System.Collections.Generic.List[object]

foreach ($run in $scenarios) {
    $runId = "{0}-{1}-run{2:D2}" -f $run.ScenarioKey, $run.StartupCondition, $run.Iteration
    $prefix = Join-Path $OutputDir $runId

    $libraryDump = Launch-AppAndWaitForLibrary
    Save-TextFile -Path "$prefix-library.xml" -Content $libraryDump.Raw

    if ($run.DelaySeconds -gt 0) {
        Start-Sleep -Seconds $run.DelaySeconds
        $libraryDump = Wait-ForLibrary
        Save-TextFile -Path "$prefix-library-after-delay.xml" -Content $libraryDump.Raw
    }

    switch ($run.ScenarioKey) {
        "appearance-open" {
            $gfxReset = Invoke-Adb -Arguments @("shell", "dumpsys", "gfxinfo", $PackageName, "reset")
            Save-TextFile -Path "$prefix-gfx-reset.txt" -Content $gfxReset

            $settingsDump = Open-SettingsFromLibrary -LibraryDump $libraryDump
            Save-TextFile -Path "$prefix-settings.xml" -Content $settingsDump.Raw
            $appearanceDump = Open-AppearanceFromSettings -SettingsDump $settingsDump
            Start-Sleep -Milliseconds 700
            Save-TextFile -Path "$prefix-appearance.xml" -Content $appearanceDump.Raw
        }
        "appearance-pager-swipe-return" {
            $appearanceDump = Open-AppearanceFromLibrary -LibraryDump $libraryDump
            Save-TextFile -Path "$prefix-appearance-before.xml" -Content $appearanceDump.Raw
            $gfxReset = Invoke-Adb -Arguments @("shell", "dumpsys", "gfxinfo", $PackageName, "reset")
            Save-TextFile -Path "$prefix-gfx-reset.txt" -Content $gfxReset

            Swipe-AppearancePager -Direction "left" -AppearanceDump $appearanceDump
            $appearanceDump = Wait-ForThemeVisible -ThemeName "Sepia"
            Save-TextFile -Path "$prefix-appearance-sepia.xml" -Content $appearanceDump.Raw
            Start-Sleep -Milliseconds 600
            Swipe-AppearancePager -Direction "right" -AppearanceDump $appearanceDump
            $appearanceDump = Wait-ForThemeVisible -ThemeName "Paper White"
            Start-Sleep -Milliseconds 700
            Save-TextFile -Path "$prefix-appearance-after.xml" -Content $appearanceDump.Raw
        }
        "gallery-open-close" {
            $appearanceDump = Open-AppearanceFromLibrary -LibraryDump $libraryDump
            Save-TextFile -Path "$prefix-appearance-before.xml" -Content $appearanceDump.Raw
            $gfxReset = Invoke-Adb -Arguments @("shell", "dumpsys", "gfxinfo", $PackageName, "reset")
            Save-TextFile -Path "$prefix-gfx-reset.txt" -Content $gfxReset

            $galleryDump = Open-GalleryFromAppearance -AppearanceDump $appearanceDump
            Save-TextFile -Path "$prefix-gallery.xml" -Content $galleryDump.Raw
            Start-Sleep -Milliseconds 500
            $appearanceDump = Close-Gallery -GalleryDump $galleryDump
            Start-Sleep -Milliseconds 700
            Save-TextFile -Path "$prefix-appearance-after.xml" -Content $appearanceDump.Raw
        }
        "gallery-switch-return" {
            $appearanceDump = Open-AppearanceFromLibrary -LibraryDump $libraryDump
            Save-TextFile -Path "$prefix-appearance-before.xml" -Content $appearanceDump.Raw
            $gfxReset = Invoke-Adb -Arguments @("shell", "dumpsys", "gfxinfo", $PackageName, "reset")
            Save-TextFile -Path "$prefix-gfx-reset.txt" -Content $gfxReset

            $galleryDump = Open-GalleryFromAppearance -AppearanceDump $appearanceDump
            Save-TextFile -Path "$prefix-gallery-open.xml" -Content $galleryDump.Raw
            Select-ThemeInGallery -GalleryDump $galleryDump -ThemeName "Sepia"
            Start-Sleep -Seconds 1
            $appearanceDump = Wait-ForThemeVisible -ThemeName "Sepia"
            Save-TextFile -Path "$prefix-appearance-sepia.xml" -Content $appearanceDump.Raw
            $galleryDump = Open-GalleryFromAppearance -AppearanceDump $appearanceDump
            Save-TextFile -Path "$prefix-gallery-reopen.xml" -Content $galleryDump.Raw
            Select-ThemeInGallery -GalleryDump $galleryDump -ThemeName "Paper White"
            Start-Sleep -Seconds 1
            $appearanceDump = Wait-ForThemeVisible -ThemeName "Paper White"
            Start-Sleep -Milliseconds 700
            Save-TextFile -Path "$prefix-appearance-after.xml" -Content $appearanceDump.Raw
        }
        default {
            throw "Unsupported scenario $($run.ScenarioKey)"
        }
    }

    $gfxText = Invoke-Adb -Arguments @("shell", "dumpsys", "gfxinfo", $PackageName)
    Save-TextFile -Path "$prefix-gfx.txt" -Content $gfxText

    $logcatText = Invoke-Adb -Arguments @("logcat", "-d")
    Save-TextFile -Path "$prefix-logcat.txt" -Content $logcatText

    $metrics = Parse-GfxMetrics -Text $gfxText
    $results.Add([pscustomobject]@{
            RunId = $runId
            ScenarioKey = $run.ScenarioKey
            StartupCondition = $run.StartupCondition
            DelaySeconds = $run.DelaySeconds
            HighInputLatency = $metrics.HighInputLatency
            JankyFrames = $metrics.JankyFrames
            JankyPercent = $metrics.JankyPercent
            P95 = $metrics.P95
            P99 = $metrics.P99
            SlowUiThread = $metrics.SlowUiThread
            FrameDeadlineMissed = $metrics.FrameDeadlineMissed
        })
}

Write-MarkdownSummary -Results @($results.ToArray()) -Path (Join-Path $OutputDir "summary.md")
Write-Output "Saved appearance performance data to $OutputDir"
