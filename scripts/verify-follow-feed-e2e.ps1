param(
    [string]$GatewayUrl = 'http://localhost:10086',
    [string]$FfmpegPath,
    [int]$TimeoutSeconds = 150
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false

if ([string]::IsNullOrWhiteSpace($FfmpegPath)) {
    $ffmpegCommand = Get-Command ffmpeg -ErrorAction SilentlyContinue
    if ($null -ne $ffmpegCommand) {
        $FfmpegPath = $ffmpegCommand.Source
    }
}
if ([string]::IsNullOrWhiteSpace($FfmpegPath) -or -not (Test-Path -LiteralPath $FfmpegPath)) {
    throw 'ffmpeg was not found. Restart the terminal or provide -FfmpegPath.'
}

foreach ($port in 10086, 10088, 10091, 10092, 25672, 29000) {
    if (-not (Test-NetConnection -ComputerName localhost -Port $port -InformationLevel Quiet)) {
        throw "Local port $port is unavailable. Start Gateway, User, Video, Processor, and infrastructure first."
    }
}

function Invoke-SwJson {
    param(
        [string]$Method,
        [string]$Uri,
        [hashtable]$Headers,
        [object]$Body
    )
    $params = @{ Method = $Method; Uri = $Uri; TimeoutSec = 20 }
    if ($null -ne $Headers) { $params.Headers = $Headers }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json'
        $params.Body = ($Body | ConvertTo-Json -Compress)
    }
    $response = Invoke-RestMethod @params
    if ($response.code -ne 1) {
        throw "SW API failed: $Uri, code=$($response.code), msg=$($response.msg)"
    }
    return $response
}

function New-TestIdentity {
    param([string]$Role)
    $phone = '13' + (Get-Random -Minimum 100000000 -Maximum 999999999).ToString()
    $password = 'SwFollowE2E!2026'
    Invoke-SwJson -Method 'POST' -Uri "$GatewayUrl/user/api/public/auth/register" -Body @{ phoneNumber = $phone; password = $password } | Out-Null
    $login = Invoke-SwJson -Method 'POST' -Uri "$GatewayUrl/user/api/public/auth/login" -Body @{ phoneNumber = $phone; password = $password }
    $headers = @{ Authorization = "Bearer $($login.data)" }
    $profile = Invoke-SwJson -Method 'GET' -Uri "$GatewayUrl/user/api/me/profile/basic" -Headers $headers
    return [PSCustomObject]@{ Role = $Role; Id = [string]$profile.data.id; Headers = $headers }
}

$workDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("sw-follow-e2e-" + [Guid]::NewGuid())
New-Item -ItemType Directory -Path $workDirectory | Out-Null
$sourceVideo = Join-Path $workDirectory 'source.mp4'
$cover = Join-Path $workDirectory 'cover.jpg'

try {
    $creator = New-TestIdentity -Role 'creator'
    $follower = New-TestIdentity -Role 'follower'
    Invoke-SwJson -Method 'POST' -Uri "$GatewayUrl/user/api/me/follow/$($creator.Id)" -Headers $follower.Headers | Out-Null

    & $FfmpegPath -hide_banner -loglevel error -y -f lavfi -i 'testsrc=size=320x240:rate=24' -f lavfi -i 'sine=frequency=1000:sample_rate=44100' -t 2 -c:v libx264 -pix_fmt yuv420p -c:a aac $sourceVideo 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $sourceVideo)) { throw 'Test MP4 generation failed.' }
    & $FfmpegPath -hide_banner -loglevel error -y -f lavfi -i 'testsrc=size=320x240:rate=1' -frames:v 1 $cover 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $cover)) { throw 'Test cover generation failed.' }

    $traceId = 'sw-follow-e2e-' + [Guid]::NewGuid().ToString('N')
    $creator.Headers['X-Trace-Id'] = $traceId
    $presign = Invoke-SwJson -Method 'POST' -Uri "$GatewayUrl/video/api/me/presign-put-object" -Headers $creator.Headers
    $taskId = [string]$presign.data.taskId
    Invoke-WebRequest -UseBasicParsing -Uri $presign.data.url -Method Put -InFile $sourceVideo -Headers @{ 'Content-Type' = 'video/mp4' } -TimeoutSec 30 | Out-Null
    $ended = Invoke-SwJson -Method 'POST' -Uri "$GatewayUrl/video/api/me/end?taskId=$taskId" -Headers $creator.Headers
    $videoId = [string]$ended.data.videoId

    $submit = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$GatewayUrl/video/api/me" -Headers $creator.Headers -Form @{
        videoId = $videoId
        description = 'SW follow-feed end-to-end verification video'
        addedTagList = 'sw-follow-e2e'
        cover = Get-Item -LiteralPath $cover
    } -TimeoutSec 30
    $submitBody = $submit.Content | ConvertFrom-Json
    if ($submitBody.code -ne 1) { throw "Video submit failed: $($submitBody.msg)" }

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $feed = Invoke-SwJson -Method 'GET' -Uri "$GatewayUrl/video/api/me/follow-feed?pageSize=10" -Headers $follower.Headers
        $matched = @($feed.data.items | Where-Object { [string]$_.id -eq $videoId })
        if ($matched.Count -eq 1) {
            $firstView = Invoke-SwJson -Method 'POST' -Uri "$GatewayUrl/video/api/me/view/$videoId" -Headers $follower.Headers
            $secondView = Invoke-SwJson -Method 'POST' -Uri "$GatewayUrl/video/api/me/view/$videoId" -Headers $follower.Headers
            if ($firstView.data -ne $true -or $secondView.data -ne $false) {
                throw "Unexpected daily view results: first=$($firstView.data), second=$($secondView.data)."
            }
            $analytics = Invoke-SwJson -Method 'GET' -Uri "$GatewayUrl/video/api/me/analytics" -Headers $creator.Headers
            if ([int64]$analytics.data.views -ne 1) {
                throw "Expected exactly one daily unique view, actual=$($analytics.data.views)."
            }
            [PSCustomObject]@{
                CreatorId = $creator.Id
                FollowerId = $follower.Id
                VideoId = $videoId
                UploadTaskId = $taskId
                TraceId = $traceId
                FollowFeedContainsPublishedVideo = $true
                IsFollowed = $matched[0].isFollowed
                CreatorName = $matched[0].creatorName
                UniqueDailyViews = [int64]$analytics.data.views
                FirstViewCounted = $firstView.data
                SecondViewDeduplicated = -not $secondView.data
                TrendDays = @($analytics.data.trends).Count
            }
            return
        }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)

    throw "Follow feed did not receive video $videoId within $TimeoutSeconds seconds."
} finally {
    Remove-Item -LiteralPath $workDirectory -Recurse -Force -ErrorAction SilentlyContinue
}
