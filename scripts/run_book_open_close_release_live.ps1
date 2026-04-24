param(
    [Parameter(Mandatory = $true)]
    [string]$DeviceSerial,
    [string]$PackageName = "com.epubreader",
    [int]$DelayedWaitSeconds = 15,
    [int]$Iterations = 3,
    [string]$OutputDir = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $false

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputDir = Join-Path $repoRoot "logs\book-open-close-release-live-$timestamp"
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$runs = New-Object System.Collections.Generic.List[object]
$bookConfigs = @(
    [pscustomobject]@{
        Key = "shadow"
        BookTitle = "Shadow Slave"
        ExpectedLibraryProgress = "1435 / 2927 ch"
    },
    [pscustomobject]@{
        Key = "ttev6"
        BookTitle = "The Saga of Tanya the Evil, Vol. 6 (light novel)"
        ExpectedLibraryProgress = "11 / 45 ch"
    }
)
$startupModes = @(
    [pscustomobject]@{ Name = "immediate"; DelaySeconds = 0 },
    [pscustomobject]@{ Name = "delayed"; DelaySeconds = $DelayedWaitSeconds }
)

foreach ($book in $bookConfigs) {
    foreach ($startupMode in $startupModes) {
        foreach ($iteration in 1..$Iterations) {
            $runs.Add([pscustomobject]@{
                    RunId = ("{0}-{1}-run{2:D2}" -f $book.Key, $startupMode.Name, $iteration)
                    BookKey = $book.Key
                    BookTitle = $book.BookTitle
                    ExpectedLibraryProgress = $book.ExpectedLibraryProgress
                    StartupCondition = $startupMode.Name
                    DelaySeconds = $startupMode.DelaySeconds
                    Iteration = $iteration
                })
        }
    }
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

function Ui-ContainsPackage {
    param(
        [Parameter(Mandatory = $true)]
        [xml]$Xml,
        [Parameter(Mandatory = $true)]
        [string]$PackageName
    )

    foreach ($node in $Xml.SelectNodes("//*")) {
        if ($node.GetAttribute("package") -eq $PackageName) {
            return $true
        }
    }
    return $false
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

function Get-PreferredBookNode {
    param(
        [Parameter(Mandatory = $true)]
        [System.Xml.XmlElement[]]$Nodes
    )

    if ($Nodes.Count -eq 1) {
        return $Nodes[0]
    }

    $Nodes |
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
        (Ui-ContainsPackage -Xml $dump.Xml -PackageName $PackageName) -and (Ui-ContainsText -Xml $dump.Xml -Text "My Library")
    }
}

function Wait-ForReaderScreen {
    Wait-ForScreen -Label "reader" -MaxAttempts 24 -SleepMilliseconds 500 -Condition {
        param($dump)
        (Ui-ContainsPackage -Xml $dump.Xml -PackageName $PackageName) -and (-not (Ui-ContainsText -Xml $dump.Xml -Text "My Library"))
    }
}

function Wait-ForLibraryState {
    param(
        [Parameter(Mandatory = $true)]
        [string]$BookTitle,
        [Parameter(Mandatory = $true)]
        [string]$ExpectedProgress
    )

    Wait-ForScreen -Label "library-$($BookTitle -replace '[^A-Za-z0-9]+', '-')" -Condition {
        param($dump)
        (Ui-ContainsPackage -Xml $dump.Xml -PackageName $PackageName) -and
        (Ui-ContainsText -Xml $dump.Xml -Text "My Library") -and
        (Ui-ContainsText -Xml $dump.Xml -Text $BookTitle) -and
        (Ui-ContainsText -Xml $dump.Xml -Text $ExpectedProgress)
    }
}

function Parse-GfxMetrics {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Text
    )

    $patterns = @{
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
    param($Value)

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

function Get-ComparisonRows {
    param(
        [Parameter(Mandatory = $true)]
        [object[]]$AverageRows,
        [Parameter(Mandatory = $true)]
        [string]$Phase
    )

    $rows = New-Object System.Collections.Generic.List[object]
    foreach ($book in $bookConfigs) {
        $immediate = $AverageRows | Where-Object {
            $_.BookTitle -eq $book.BookTitle -and $_.StartupCondition -eq "immediate"
        } | Select-Object -First 1
        $delayed = $AverageRows | Where-Object {
            $_.BookTitle -eq $book.BookTitle -and $_.StartupCondition -eq "delayed"
        } | Select-Object -First 1
        if (-not $immediate -or -not $delayed) {
            continue
        }

        $immediateValue = $immediate."${Phase}HighInputLatency"
        $delayedValue = $delayed."${Phase}HighInputLatency"
        $delta = if ($null -ne $immediateValue -and $null -ne $delayedValue) {
            $delayedValue - $immediateValue
        }
        else {
            $null
        }

        $improvementPercent = $null
        $classification = "n/a"
        if ($null -ne $immediateValue -and $immediateValue -gt 0 -and $null -ne $delayedValue) {
            $improvementPercent = (($immediateValue - $delayedValue) / $immediateValue) * 100.0
            if ($improvementPercent -gt 30) {
                $classification = "large gap"
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

        $jankyImmediate = $immediate."${Phase}JankyPercent"
        $jankyDelayed = $delayed."${Phase}JankyPercent"
        $jankyFlag = if ((($null -ne $jankyImmediate) -and ($jankyImmediate -gt 5)) -or (($null -ne $jankyDelayed) -and ($jankyDelayed -gt 5))) {
            "flag"
        }
        else {
            "-"
        }

        $rows.Add([pscustomobject]@{
                BookTitle = $book.BookTitle
                ImmediateHighInputLatency = $immediateValue
                DelayedHighInputLatency = $delayedValue
                Delta = $delta
                DelayedBetterPercent = $improvementPercent
                GapClassification = $classification
                AvgJankyFlag = $jankyFlag
            })
    }

    @($rows.ToArray())
}

function Write-MarkdownSummary {
    param(
        [Parameter(Mandatory = $true)]
        [object[]]$Results,
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# Book Open / Close Release-Live Audit")
    $lines.Add("")
    $lines.Add("Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
    $lines.Add("Device: ``$DeviceSerial``")
    $lines.Add("Package: ``$PackageName``")
    $lines.Add("")
    $lines.Add("| Run | Book | Startup | Delay Seconds | Expected Progress | Open High Input | Open Janky Frames | Open Janky % | Open P95 | Open P99 | Open Slow UI | Open Deadline Missed | Close High Input | Close Janky Frames | Close Janky % | Close P95 | Close P99 | Close Slow UI | Close Deadline Missed |")
    $lines.Add("| --- | --- | --- | ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
    foreach ($row in $Results) {
        $lines.Add("| $($row.RunId) | $($row.BookTitle) | $($row.StartupCondition) | $($row.DelaySeconds) | $($row.ExpectedLibraryProgress) | $($row.OpenHighInputLatency) | $($row.OpenJankyFrames) | $($row.OpenJankyPercent) | $($row.OpenP95) | $($row.OpenP99) | $($row.OpenSlowUiThread) | $($row.OpenFrameDeadlineMissed) | $($row.CloseHighInputLatency) | $($row.CloseJankyFrames) | $($row.CloseJankyPercent) | $($row.CloseP95) | $($row.CloseP99) | $($row.CloseSlowUiThread) | $($row.CloseFrameDeadlineMissed) |")
    }

    $lines.Add("")
    $lines.Add("## Open Averages")
    $lines.Add("")
    $lines.Add("| Book | Startup | Runs | High Input Latency | Janky Frames | Janky % | P95 | P99 | Slow UI | Frame Deadline Missed |")
    $lines.Add("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")

    $averageRows = New-Object System.Collections.Generic.List[object]
    foreach ($book in $bookConfigs) {
        foreach ($startupMode in $startupModes) {
            $groupRows = @($Results | Where-Object {
                    $_.BookTitle -eq $book.BookTitle -and $_.StartupCondition -eq $startupMode.Name
                })
            if ($groupRows.Count -eq 0) { continue }

            $averageRow = [pscustomobject]@{
                BookTitle = $book.BookTitle
                StartupCondition = $startupMode.Name
                Runs = $groupRows.Count
                OpenHighInputLatency = Get-Average -Rows $groupRows -PropertyName "OpenHighInputLatency"
                OpenJankyFrames = Get-Average -Rows $groupRows -PropertyName "OpenJankyFrames"
                OpenJankyPercent = Get-Average -Rows $groupRows -PropertyName "OpenJankyPercent"
                OpenP95 = Get-Average -Rows $groupRows -PropertyName "OpenP95"
                OpenP99 = Get-Average -Rows $groupRows -PropertyName "OpenP99"
                OpenSlowUiThread = Get-Average -Rows $groupRows -PropertyName "OpenSlowUiThread"
                OpenFrameDeadlineMissed = Get-Average -Rows $groupRows -PropertyName "OpenFrameDeadlineMissed"
                CloseHighInputLatency = Get-Average -Rows $groupRows -PropertyName "CloseHighInputLatency"
                CloseJankyFrames = Get-Average -Rows $groupRows -PropertyName "CloseJankyFrames"
                CloseJankyPercent = Get-Average -Rows $groupRows -PropertyName "CloseJankyPercent"
                CloseP95 = Get-Average -Rows $groupRows -PropertyName "CloseP95"
                CloseP99 = Get-Average -Rows $groupRows -PropertyName "CloseP99"
                CloseSlowUiThread = Get-Average -Rows $groupRows -PropertyName "CloseSlowUiThread"
                CloseFrameDeadlineMissed = Get-Average -Rows $groupRows -PropertyName "CloseFrameDeadlineMissed"
            }
            $averageRows.Add($averageRow)
            $lines.Add("| $($averageRow.BookTitle) | $($averageRow.StartupCondition) | $($averageRow.Runs) | $(Format-Number $averageRow.OpenHighInputLatency) | $(Format-Number $averageRow.OpenJankyFrames) | $(Format-Number $averageRow.OpenJankyPercent) | $(Format-Number $averageRow.OpenP95) | $(Format-Number $averageRow.OpenP99) | $(Format-Number $averageRow.OpenSlowUiThread) | $(Format-Number $averageRow.OpenFrameDeadlineMissed) |")
        }
    }

    $lines.Add("")
    $lines.Add("## Close Averages")
    $lines.Add("")
    $lines.Add("| Book | Startup | Runs | High Input Latency | Janky Frames | Janky % | P95 | P99 | Slow UI | Frame Deadline Missed |")
    $lines.Add("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
    foreach ($row in $averageRows) {
        $lines.Add("| $($row.BookTitle) | $($row.StartupCondition) | $($row.Runs) | $(Format-Number $row.CloseHighInputLatency) | $(Format-Number $row.CloseJankyFrames) | $(Format-Number $row.CloseJankyPercent) | $(Format-Number $row.CloseP95) | $(Format-Number $row.CloseP99) | $(Format-Number $row.CloseSlowUiThread) | $(Format-Number $row.CloseFrameDeadlineMissed) |")
    }

    $lines.Add("")
    $lines.Add("## Open Immediate vs Delayed")
    $lines.Add("")
    $lines.Add("| Book | Immediate Avg High Input Latency | Delayed Avg High Input Latency | Delta | Delayed Better % | Gap Classification | Avg Janky % Flag |")
    $lines.Add("| --- | ---: | ---: | ---: | ---: | --- | --- |")
    $openComparisons = Get-ComparisonRows -AverageRows @($averageRows.ToArray()) -Phase "Open"
    foreach ($row in $openComparisons) {
        $lines.Add("| $($row.BookTitle) | $(Format-Number $row.ImmediateHighInputLatency) | $(Format-Number $row.DelayedHighInputLatency) | $(Format-Number $row.Delta) | $(Format-Number $row.DelayedBetterPercent) | $($row.GapClassification) | $($row.AvgJankyFlag) |")
    }

    $lines.Add("")
    $lines.Add("## Close Immediate vs Delayed")
    $lines.Add("")
    $lines.Add("| Book | Immediate Avg High Input Latency | Delayed Avg High Input Latency | Delta | Delayed Better % | Gap Classification | Avg Janky % Flag |")
    $lines.Add("| --- | ---: | ---: | ---: | ---: | --- | --- |")
    $closeComparisons = Get-ComparisonRows -AverageRows @($averageRows.ToArray()) -Phase "Close"
    foreach ($row in $closeComparisons) {
        $lines.Add("| $($row.BookTitle) | $(Format-Number $row.ImmediateHighInputLatency) | $(Format-Number $row.DelayedHighInputLatency) | $(Format-Number $row.Delta) | $(Format-Number $row.DelayedBetterPercent) | $($row.GapClassification) | $($row.AvgJankyFlag) |")
    }

    $lines.Add("")
    $lines.Add("## Follow-Up Flags")
    $lines.Add("")
    $flagLines = New-Object System.Collections.Generic.List[string]
    foreach ($row in $openComparisons) {
        if ($row.GapClassification -eq "large gap") {
            $flagLines.Add("open: $($row.BookTitle) (delayed >30% better)")
        }
        if ($row.AvgJankyFlag -eq "flag") {
            $flagLines.Add("open: $($row.BookTitle) (avg janky % > 5)")
        }
    }
    foreach ($row in $closeComparisons) {
        if ($row.GapClassification -eq "large gap") {
            $flagLines.Add("close: $($row.BookTitle) (delayed >30% better)")
        }
        if ($row.AvgJankyFlag -eq "flag") {
            $flagLines.Add("close: $($row.BookTitle) (avg janky % > 5)")
        }
    }

    if ($flagLines.Count -eq 0) {
        $lines.Add("- None from metrics alone.")
    }
    else {
        foreach ($item in $flagLines | Select-Object -Unique) {
            $lines.Add("- $item")
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

Assert-ReleaseLikeInstall

$results = New-Object System.Collections.Generic.List[object]

foreach ($run in $runs) {
    $prefix = Join-Path $OutputDir $run.RunId

    Invoke-Adb -Arguments @("shell", "am", "force-stop", $PackageName) | Out-Null
    Invoke-Adb -Arguments @("logcat", "-c") | Out-Null
    Invoke-Adb -Arguments @("shell", "monkey", "-p", $PackageName, "-c", "android.intent.category.LAUNCHER", "1") | Out-Null

    $libraryDump = Wait-ForLibraryState -BookTitle $run.BookTitle -ExpectedProgress $run.ExpectedLibraryProgress
    Save-TextFile -Path "$prefix-library-before-open.xml" -Content $libraryDump.Raw

    if ($run.DelaySeconds -gt 0) {
        Start-Sleep -Seconds $run.DelaySeconds
        $libraryDump = Wait-ForLibraryState -BookTitle $run.BookTitle -ExpectedProgress $run.ExpectedLibraryProgress
        Save-TextFile -Path "$prefix-library-before-open-after-delay.xml" -Content $libraryDump.Raw
    }

    $bookNodes = @(Find-NodesByExactText -Xml $libraryDump.Xml -Text $run.BookTitle)
    if ($bookNodes.Count -eq 0) {
        throw "Could not find library tile for '$($run.BookTitle)'."
    }

    $openGfxReset = Invoke-Adb -Arguments @("shell", "dumpsys", "gfxinfo", $PackageName, "reset")
    Save-TextFile -Path "$prefix-open-gfx-reset.txt" -Content $openGfxReset

    Tap-NodeCenter -Node (Get-PreferredBookNode -Nodes $bookNodes)
    $readerDump = Wait-ForReaderScreen
    Start-Sleep -Seconds 1
    $readerDump = Wait-ForReaderScreen
    Save-TextFile -Path "$prefix-reader-after-open.xml" -Content $readerDump.Raw

    $openGfxText = Invoke-Adb -Arguments @("shell", "dumpsys", "gfxinfo", $PackageName)
    Save-TextFile -Path "$prefix-open-gfx.txt" -Content $openGfxText

    $openLogcatText = Invoke-Adb -Arguments @("logcat", "-d")
    Save-TextFile -Path "$prefix-open-logcat.txt" -Content $openLogcatText

    Invoke-Adb -Arguments @("logcat", "-c") | Out-Null
    $closeGfxReset = Invoke-Adb -Arguments @("shell", "dumpsys", "gfxinfo", $PackageName, "reset")
    Save-TextFile -Path "$prefix-close-gfx-reset.txt" -Content $closeGfxReset

    Invoke-Adb -Arguments @("shell", "input", "keyevent", "4") | Out-Null
    $libraryAfterCloseDump = Wait-ForLibraryState -BookTitle $run.BookTitle -ExpectedProgress $run.ExpectedLibraryProgress
    Start-Sleep -Seconds 1
    $libraryAfterCloseDump = Wait-ForLibraryState -BookTitle $run.BookTitle -ExpectedProgress $run.ExpectedLibraryProgress
    Save-TextFile -Path "$prefix-library-after-close.xml" -Content $libraryAfterCloseDump.Raw

    $closeGfxText = Invoke-Adb -Arguments @("shell", "dumpsys", "gfxinfo", $PackageName)
    Save-TextFile -Path "$prefix-close-gfx.txt" -Content $closeGfxText

    $closeLogcatText = Invoke-Adb -Arguments @("logcat", "-d")
    Save-TextFile -Path "$prefix-close-logcat.txt" -Content $closeLogcatText

    $openMetrics = Parse-GfxMetrics -Text $openGfxText
    $closeMetrics = Parse-GfxMetrics -Text $closeGfxText
    $results.Add([pscustomobject]@{
            RunId = $run.RunId
            BookTitle = $run.BookTitle
            StartupCondition = $run.StartupCondition
            DelaySeconds = $run.DelaySeconds
            ExpectedLibraryProgress = $run.ExpectedLibraryProgress
            OpenHighInputLatency = $openMetrics.HighInputLatency
            OpenJankyFrames = $openMetrics.JankyFrames
            OpenJankyPercent = $openMetrics.JankyPercent
            OpenP95 = $openMetrics.P95
            OpenP99 = $openMetrics.P99
            OpenSlowUiThread = $openMetrics.SlowUiThread
            OpenFrameDeadlineMissed = $openMetrics.FrameDeadlineMissed
            CloseHighInputLatency = $closeMetrics.HighInputLatency
            CloseJankyFrames = $closeMetrics.JankyFrames
            CloseJankyPercent = $closeMetrics.JankyPercent
            CloseP95 = $closeMetrics.P95
            CloseP99 = $closeMetrics.P99
            CloseSlowUiThread = $closeMetrics.SlowUiThread
            CloseFrameDeadlineMissed = $closeMetrics.FrameDeadlineMissed
        })
}

$results | Export-Csv -Path (Join-Path $OutputDir "summary.csv") -NoTypeInformation
Write-MarkdownSummary -Results @($results.ToArray()) -Path (Join-Path $OutputDir "summary.md")
Write-Output "Saved book open/close release-live data to $OutputDir"
