param(
    [string]$VideoServiceUrl = 'http://localhost:10091/video/api',
    [string]$MysqlContainer = 'sw-dev-mysql-1',
    [string]$MysqlPassword,
    [string]$FfmpegPath,
    [int]$TimeoutSeconds = 120,
    [string]$TraceId = ("sw-failure-e2e-" + [Guid]::NewGuid().ToString('N'))
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$projectRoot = Split-Path -Parent $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($MysqlPassword)) {
    $envFile = Join-Path $projectRoot '.env'
    if (-not (Test-Path -LiteralPath $envFile)) {
        throw 'Missing local .env. Provide -MysqlPassword explicitly.'
    }
    $line = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^MYSQL_ROOT_PASSWORD=' } | Select-Object -First 1
    if ($null -eq $line) {
        throw '.env is missing MYSQL_ROOT_PASSWORD.'
    }
    $MysqlPassword = $line.Substring('MYSQL_ROOT_PASSWORD='.Length)
}

if ([string]::IsNullOrWhiteSpace($FfmpegPath)) {
    $ffmpegCommand = Get-Command ffmpeg -ErrorAction SilentlyContinue
    if ($null -ne $ffmpegCommand) {
        $FfmpegPath = $ffmpegCommand.Source
    }
}
if ([string]::IsNullOrWhiteSpace($FfmpegPath) -or -not (Test-Path -LiteralPath $FfmpegPath)) {
    throw 'ffmpeg was not found. Restart the terminal or provide -FfmpegPath.'
}

foreach ($port in 10091, 10092, 25672, 29000) {
    if (-not (Test-NetConnection -ComputerName localhost -Port $port -InformationLevel Quiet)) {
        throw "Local port $port is unavailable. Start Video, Processor, and infrastructure first."
    }
}

$workDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("sw-failure-e2e-" + [Guid]::NewGuid())
New-Item -ItemType Directory -Path $workDirectory | Out-Null
$invalidSource = Join-Path $workDirectory 'invalid-source.mp4'
$cover = Join-Path $workDirectory 'cover.jpg'

try {
    # 保持 .mp4 文件名以通过上传校验，但内容故意不是媒体文件，真实触发 FFmpeg 失败路径。
    $invalidSourceExists = $false
    $invalidSourceLength = 0
    foreach ($attempt in 1..3) {
        [System.IO.File]::WriteAllText($invalidSource, 'SW isolated failure verification - invalid media payload')
        $invalidSourceExists = Test-Path -LiteralPath $invalidSource
        $invalidSourceLength = if ($invalidSourceExists) { (Get-Item -LiteralPath $invalidSource).Length } else { 0 }
        if ($invalidSourceExists -and $invalidSourceLength -gt 0) {
            break
        }
        Start-Sleep -Milliseconds 200
    }
    if (-not $invalidSourceExists -or $invalidSourceLength -eq 0) {
        throw "Invalid-media test payload creation failed at $invalidSource (exists=$invalidSourceExists, bytes=$invalidSourceLength)."
    }
    & $FfmpegPath -hide_banner -loglevel error -y -f lavfi -i 'testsrc=size=320x240:rate=1' -frames:v 1 $cover 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $cover)) {
        throw 'Test cover generation failed.'
    }
    if (-not (Test-Path -LiteralPath $invalidSource)) {
        throw 'Invalid-media test payload disappeared before upload.'
    }

    $headers = @{ id = '900001'; 'X-Trace-Id' = $TraceId }
    $presign = Invoke-RestMethod -Uri "$VideoServiceUrl/me/presign-put-object" -Method Post -Headers $headers -TimeoutSec 20
    if ($presign.code -ne 1 -or $null -eq $presign.data) {
        throw 'Presign API did not return a valid task.'
    }

    $uploadTaskId = [string]$presign.data.taskId
    Invoke-WebRequest -Uri $presign.data.url -Method Put -InFile $invalidSource -Headers @{ 'Content-Type' = 'video/mp4' } -UseBasicParsing -TimeoutSec 30 | Out-Null
    $completed = Invoke-RestMethod -Uri "$VideoServiceUrl/me/end?taskId=$uploadTaskId" -Method Post -Headers $headers -TimeoutSec 20
    if ($completed.code -ne 1 -or $null -eq $completed.data.videoId) {
        throw 'Upload completion API did not return videoId.'
    }

    $videoId = [string]$completed.data.videoId
    $submitResult = & curl.exe --silent --show-error --fail-with-body -X POST "$VideoServiceUrl/me" -H 'id: 900001' -H "X-Trace-Id: $TraceId" -F "videoId=$videoId" -F 'description=SW automated failure-path verification video' -F 'addedTagList=sw-failure-e2e' -F "cover=@$cover;type=image/jpeg"
    if ($LASTEXITCODE -ne 0) {
        throw 'Video submit request failed.'
    }

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $result = docker exec -e "MYSQL_PWD=$MysqlPassword" $MysqlContainer mysql -uroot -D yh -Nse "SELECT CONCAT(v.status, '|', t.status, '|', o.status, '|', COALESCE(t.error_message, '')) FROM video v JOIN video_processing_task t ON t.video_id=v.id JOIN message_outbox o ON o.business_id=v.id WHERE v.id=$videoId ORDER BY o.id DESC LIMIT 1;"
        $parts = $result -split '\|', 4
        if ($parts.Count -ge 3 -and $parts[0] -eq '4' -and $parts[1] -eq '3' -and $parts[2] -eq '1') {
            [PSCustomObject]@{
                VideoId = $videoId
                UploadTaskId = $uploadTaskId
                TraceId = $TraceId
                VideoStatus = 'REJECTED'
                ProcessingTaskStatus = 'FAILED'
                OutboxStatus = 'SUCCESS'
                FailureSummaryStored = $true
            }
            return
        }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)

    throw "Async processing did not reach REJECTED/FAILED/SUCCESS within $TimeoutSeconds seconds. Last status: $result"
} finally {
    Remove-Item -LiteralPath $workDirectory -Recurse -Force -ErrorAction SilentlyContinue
}
