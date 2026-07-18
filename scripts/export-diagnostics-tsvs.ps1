param(
    [Parameter(Position = 0)]
    [string] $ReportPath = "project-diagnostics.txt",

    [string] $OutputPrefix = "project-diagnostics-errors",

    [switch] $IncludeWarningsAndNotes
)

$ErrorActionPreference = "Stop"

$resolvedReport = Resolve-Path -LiteralPath $ReportPath
$uniquePath = "$OutputPrefix-unique.tsv"
$byCodePath = "$OutputPrefix-by-code.tsv"
$summaryPath = "$OutputPrefix-summary.txt"
$byCodeDir = "$OutputPrefix-by-code"

$items = New-Object System.Collections.Generic.List[object]
$currentFile = $null
$diagnosticPattern = if ($IncludeWarningsAndNotes) {
    '^  \[(?<kind>[A-Z]+)\] line (?<line>\d+), column (?<column>\d+), offsets (?<start>\d+)-(?<end>\d+), (?<code>[^:]+): (?<message>.*)$'
} else {
    '^  \[(?<kind>ERROR)\] line (?<line>\d+), column (?<column>\d+), offsets (?<start>\d+)-(?<end>\d+), (?<code>[^:]+): (?<message>.*)$'
}

foreach ($line in Get-Content -LiteralPath $resolvedReport) {
    if ($line -match '^(?<file>.+) \[(?<language>[^\]]+)\] \((?<count>\d+)\)$') {
        $currentFile = $matches.file
        continue
    }

    if ($line -match $diagnosticPattern) {
        $items.Add([pscustomobject]@{
            Kind = $matches.kind
            File = $currentFile
            Line = [int] $matches.line
            Column = [int] $matches.column
            Code = $matches.code
            Message = $matches.message
            Key = "$($matches.kind)`t$($matches.code)`t$($matches.message)"
        })
    }
}

$byMessage = $items |
    Group-Object Key |
    Sort-Object Count -Descending |
    ForEach-Object {
        $first = $_.Group[0]
        $examples = $_.Group |
            Select-Object -First 5 |
            ForEach-Object { "$($_.File):$($_.Line):$($_.Column)" }

        [pscustomobject]@{
            Count = $_.Count
            Kind = $first.Kind
            Code = $first.Code
            Message = $first.Message
            Examples = ($examples -join " | ")
        }
    }

$byCode = $items |
    Group-Object Code |
    Sort-Object Count -Descending |
    ForEach-Object {
        [pscustomobject]@{
            Count = $_.Count
            Code = $_.Name
            UniqueMessages = ($_.Group | Select-Object -ExpandProperty Message -Unique).Count
        }
    }

$byMessage |
    ForEach-Object { "{0}`t{1}`t{2}`t{3}`t{4}" -f $_.Count, $_.Kind, $_.Code, $_.Message, $_.Examples } |
    Set-Content -LiteralPath $uniquePath -Encoding UTF8

$byCode |
    ForEach-Object { "{0}`t{1}`t{2}" -f $_.Count, $_.Code, $_.UniqueMessages } |
    Set-Content -LiteralPath $byCodePath -Encoding UTF8

$summary = @(
    "Report: $resolvedReport"
    "Diagnostics parsed: $($items.Count)"
    "Unique code/message pairs: $($byMessage.Count)"
    "Unique codes: $($byCode.Count)"
    ""
    "Top codes:"
)
$summary += $byCode |
    Select-Object -First 50 |
    ForEach-Object { "{0}`t{1}`t{2}" -f $_.Count, $_.Code, $_.UniqueMessages }
$summary += ""
$summary += "Top unique messages:"
$summary += $byMessage |
    Select-Object -First 100 |
    ForEach-Object { "{0}`t{1}`t{2}`t{3}" -f $_.Count, $_.Kind, $_.Code, $_.Message }
$summary | Set-Content -LiteralPath $summaryPath -Encoding UTF8

New-Item -ItemType Directory -Force -Path $byCodeDir | Out-Null
Get-ChildItem -LiteralPath $byCodeDir -File -Filter "*.tsv" | Remove-Item

$byMessage |
    Group-Object Code |
    ForEach-Object {
        $safeName = ($_.Name -replace '[^A-Za-z0-9_.-]', '_') + ".tsv"
        $path = Join-Path $byCodeDir $safeName
        $_.Group |
            Sort-Object Count -Descending |
            ForEach-Object { "{0}`t{1}`t{2}`t{3}`t{4}" -f $_.Count, $_.Kind, $_.Code, $_.Message, $_.Examples } |
            Set-Content -LiteralPath $path -Encoding UTF8
    }

Write-Host "Parsed diagnostics: $($items.Count)"
Write-Host "Unique rows: $($byMessage.Count)"
Write-Host "Unique codes: $($byCode.Count)"
Write-Host "Wrote: $uniquePath"
Write-Host "Wrote: $byCodePath"
Write-Host "Wrote: $summaryPath"
Write-Host "Wrote per-code TSVs under: $byCodeDir"
