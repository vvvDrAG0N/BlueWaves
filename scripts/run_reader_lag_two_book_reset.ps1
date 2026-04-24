param(
    [Parameter(Mandatory = $true)]
    [string]$DeviceSerial,
    [Parameter(Mandatory = $true)]
    [string]$SnapshotTarPath,
    [string]$RemoteSnapshotPath = "/data/local/tmp/pristine-state-cmd.tar",
    [string]$PackageName = "com.epubreader",
    [int]$DelayedWaitSeconds = 15,
    [string]$OutputDir = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $false

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputDir = Join-Path $repoRoot "logs\reader-lag-two-book-reset-$timestamp"
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$books = @(
    [pscustomobject]@{
        Key = "ttev6"
        Title = "The Saga of Tanya the Evil, Vol. 6 (light novel)"
    },
    [pscustomobject]@{
        Key = "shadow"
        Title = "Shadow Slave"
    }
)

$runs = foreach ($book in $books) {
    foreach ($scenario in @(
            [pscustomobject]@{ Key = "immediate"; DelaySeconds = 0 },
            [pscustomobject]@{ Key = "delayed"; DelaySeconds = $DelayedWaitSeconds }
        )) {
        foreach ($iteration in 1..3) {
            [pscustomobject]@{
                Book = $book
                Scenario = $scenario
                Iteration = $iteration
            }
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
        P90 = "90th percentile:\s+(\d+)ms"
        P95 = "95th percentile:\s+(\d+)ms"
        P99 = "99th percentile:\s+(\d+)ms"
        HighInputLatency = "Number High input latency:\s+(\d+)"
        SlowUiThread = "Number Slow UI thread:\s+(\d+)"
        SlowBitmapUploads = "Number Slow bitmap uploads:\s+(\d+)"
        SlowIssueDrawCommands = "Number Slow issue draw commands:\s+(\d+)"
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

function Get-GroupAverages {
    param(
        [Parameter(Mandatory = $true)]
        [object[]]$Rows
    )

    $grouped = $Rows | Group-Object { "$($_.BookKey)|$($_.Scenario)" }
    foreach ($group in $grouped) {
        $sample = $group.Group[0]
        [pscustomobject]@{
            BookKey = $sample.BookKey
            Scenario = $sample.Scenario
            Runs = $group.Count
            AvgHighInputLatency = [math]::Round((($group.Group | Measure-Object -Property HighInputLatency -Average).Average), 2)
            AvgJankyFrames = [math]::Round((($group.Group | Measure-Object -Property JankyFrames -Average).Average), 2)
            AvgJankyPercent = [math]::Round((($group.Group | Measure-Object -Property JankyPercent -Average).Average), 2)
            AvgP95 = [math]::Round((($group.Group | Measure-Object -Property P95 -Average).Average), 2)
            AvgP99 = [math]::Round((($group.Group | Measure-Object -Property P99 -Average).Average), 2)
            AvgSlowUiThread = [math]::Round((($group.Group | Measure-Object -Property SlowUiThread -Average).Average), 2)
            AvgFrameDeadlineMissed = [math]::Round((($group.Group | Measure-Object -Property FrameDeadlineMissed -Average).Average), 2)
        }
    }
}

function Write-MarkdownSummary {
    param(
        [Parameter(Mandatory = $true)]
        [object[]]$Results,
        [Parameter(Mandatory = $true)]
        [object[]]$Averages,
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# Reader Lag Two-Book Matrix")
    $lines.Add("")
    $lines.Add("Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
    $lines.Add("Device: ``$DeviceSerial``")
    $lines.Add("Snapshot: ``$SnapshotTarPath``")
    $lines.Add("")
    $lines.Add("## Grouped Averages")
    $lines.Add("")
    $lines.Add("| Book | Scenario | Runs | Avg High Input Latency | Avg Janky Frames | Avg Janky % | Avg P95 | Avg P99 | Avg Slow UI | Avg Frame Deadline Missed |")
    $lines.Add("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
    foreach ($row in $Averages | Sort-Object BookKey, Scenario) {
        $lines.Add("| $($row.BookKey) | $($row.Scenario) | $($row.Runs) | $($row.AvgHighInputLatency) | $($row.AvgJankyFrames) | $($row.AvgJankyPercent) | $($row.AvgP95) | $($row.AvgP99) | $($row.AvgSlowUiThread) | $($row.AvgFrameDeadlineMissed) |")
    }
    $lines.Add("")
    $lines.Add("## Per-Run Metrics")
    $lines.Add("")
    $lines.Add("| Run | Book | Scenario | Iteration | High Input Latency | Janky Frames | Janky % | P95 | P99 | Slow UI | Frame Deadline Missed |")
    $lines.Add("| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
    foreach ($row in $Results | Sort-Object BookKey, Scenario, Iteration) {
        $lines.Add("| $($row.RunId) | $($row.BookKey) | $($row.Scenario) | $($row.Iteration) | $($row.HighInputLatency) | $($row.JankyFrames) | $($row.JankyPercent) | $($row.P95) | $($row.P99) | $($row.SlowUiThread) | $($row.FrameDeadlineMissed) |")
    }

    Save-TextFile -Path $Path -Content ($lines -join "`r`n")
}

Invoke-Adb -Arguments @("push", $SnapshotTarPath, $RemoteSnapshotPath) | Out-Null
Invoke-Adb -Arguments @("shell", "chmod", "644", $RemoteSnapshotPath) | Out-Null

$results = New-Object System.Collections.Generic.List[object]

foreach ($run in $runs) {
    $runId = "{0}-{1}-{2}" -f $run.Book.Key, $run.Scenario.Key, $run.Iteration
    $prefix = Join-Path $OutputDir $runId

    Invoke-Adb -Arguments @("shell", "am", "force-stop", $PackageName) | Out-Null
    Invoke-Adb -Arguments @("shell", "pm", "clear", $PackageName) | Out-Null
    Invoke-Adb -Arguments @("shell", "run-as", $PackageName, "tar", "-xf", $RemoteSnapshotPath, "-C", "/data/user/0/$PackageName") | Out-Null
    Invoke-Adb -Arguments @("logcat", "-c") | Out-Null
    Invoke-Adb -Arguments @("shell", "monkey", "-p", $PackageName, "-c", "android.intent.category.LAUNCHER", "1") | Out-Null

    $libraryDump = Wait-ForLibrary
    Save-TextFile -Path "$prefix-library.xml" -Content $libraryDump.Raw

    if ($run.Scenario.DelaySeconds -gt 0) {
        Start-Sleep -Seconds $run.Scenario.DelaySeconds
        $libraryDump = Wait-ForLibrary
        Save-TextFile -Path "$prefix-library-after-delay.xml" -Content $libraryDump.Raw
    }

    $gfxReset = Invoke-Adb -Arguments @("shell", "dumpsys", "gfxinfo", $PackageName, "reset")
    Save-TextFile -Path "$prefix-gfx-reset.txt" -Content $gfxReset

    $bookNodes = @(Find-NodesByExactText -Xml $libraryDump.Xml -Text $run.Book.Title)
    if ($bookNodes.Count -eq 0) {
        throw "Could not find library tile for '$($run.Book.Title)'."
    }

    $targetBookNode = Get-PreferredBookNode -Nodes $bookNodes
    Tap-NodeCenter -Node $targetBookNode
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

    $metrics = Parse-GfxMetrics -Text $gfxText
    $results.Add([pscustomobject]@{
            RunId = $runId
            BookKey = $run.Book.Key
            BookTitle = $run.Book.Title
            Scenario = $run.Scenario.Key
            Iteration = $run.Iteration
            DelaySeconds = $run.Scenario.DelaySeconds
            TotalFrames = $metrics.TotalFrames
            HighInputLatency = $metrics.HighInputLatency
            JankyFrames = $metrics.JankyFrames
            JankyPercent = $metrics.JankyPercent
            P90 = $metrics.P90
            P95 = $metrics.P95
            P99 = $metrics.P99
            SlowUiThread = $metrics.SlowUiThread
            SlowBitmapUploads = $metrics.SlowBitmapUploads
            SlowIssueDrawCommands = $metrics.SlowIssueDrawCommands
            FrameDeadlineMissed = $metrics.FrameDeadlineMissed
        })
}

$results | Export-Csv -Path (Join-Path $OutputDir "summary.csv") -NoTypeInformation
$averages = Get-GroupAverages -Rows $results
$averages | Export-Csv -Path (Join-Path $OutputDir "summary-averages.csv") -NoTypeInformation
Write-MarkdownSummary -Results $results -Averages $averages -Path (Join-Path $OutputDir "summary.md")

Write-Host "Completed reader lag matrix."
Write-Host "Output: $OutputDir"
