param(
    [int]$WarmupSamples = 1,
    [int]$MeasuredSamples = 5,
    [int]$TimeoutSeconds = 120,
    [string]$VideoServiceUrl = 'http://localhost:10091/video/api',
    [string]$MysqlContainer = 'sw-dev-mysql-1',
    [string]$MysqlPassword,
    [string]$FfmpegPath
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$verifyScript = Join-Path $PSScriptRoot 'verify-video-e2e.ps1'

if ($WarmupSamples -lt 0) {
    throw 'WarmupSamples cannot be negative.'
}
if ($MeasuredSamples -lt 3) {
    throw 'MeasuredSamples must be at least 3 so the result is not a single-run anecdote.'
}

function Get-NearestRankPercentile([long[]]$SortedValues, [double]$Percentile) {
    $index = [Math]::Ceiling($SortedValues.Count * $Percentile) - 1
    return $SortedValues[[Math]::Max(0, [Math]::Min($index, $SortedValues.Count - 1))]
}

function Invoke-VideoScenario([int]$Sequence, [string]$Phase) {
    $traceId = "sw-e2e-benchmark-$Phase-$Sequence-" + [Guid]::NewGuid().ToString('N')
    $arguments = @{
        VideoServiceUrl = $VideoServiceUrl
        MysqlContainer = $MysqlContainer
        TimeoutSeconds = $TimeoutSeconds
        TraceId = $traceId
    }
    if (-not [string]::IsNullOrWhiteSpace($MysqlPassword)) { $arguments.MysqlPassword = $MysqlPassword }
    if (-not [string]::IsNullOrWhiteSpace($FfmpegPath)) { $arguments.FfmpegPath = $FfmpegPath }
    return & $verifyScript @arguments
}

for ($index = 1; $index -le $WarmupSamples; $index++) {
    Write-Host "Running warmup sample $index/$WarmupSamples..."
    Invoke-VideoScenario -Sequence $index -Phase 'warmup' | Out-Null
}

$samples = [System.Collections.Generic.List[object]]::new()
for ($index = 1; $index -le $MeasuredSamples; $index++) {
    Write-Host "Running measured sample $index/$MeasuredSamples..."
    $result = Invoke-VideoScenario -Sequence $index -Phase 'measured'
    $samples.Add([PSCustomObject]@{
            Sequence = $index
            VideoId = $result.VideoId
            TraceId = $result.TraceId
            SubmissionToPublishedMillis = [long]$result.SubmissionToPublishedMillis
        })
}

$sorted = [long[]]($samples.SubmissionToPublishedMillis | Sort-Object)
[PSCustomObject]@{
    Scenario = 'Valid video upload: presign to PUBLISHED/SUCCEEDED/Outbox SUCCESS'
    Concurrency = 1
    WarmupSamples = $WarmupSamples
    MeasuredSamples = $MeasuredSamples
    MinMillis = $sorted[0]
    P50Millis = Get-NearestRankPercentile -SortedValues $sorted -Percentile 0.50
    P95Millis = Get-NearestRankPercentile -SortedValues $sorted -Percentile 0.95
    MaxMillis = $sorted[$sorted.Count - 1]
    Samples = $samples
}
