param(
    [string]$VideoBaseUrl = 'http://127.0.0.1:10091/video',
    [string]$ApplicationUtcOffset = '+08:00'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

function Invoke-MySql {
    param(
        [Parameter(Mandatory = $true)][string]$Sql
    )

    $result = $Sql | & docker compose --project-name sw-dev exec -T mysql sh -lc 'mysql -N -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'
    if ($LASTEXITCODE -ne 0) {
        throw 'MySQL command failed.'
    }
    return @($result | Where-Object { $_ -and $_ -notmatch '^mysql: \[Warning\]' })
}

function Invoke-RecoveryRequest {
    param(
        [Parameter(Mandatory = $true)][System.Net.Http.HttpClient]$Client,
        [Parameter(Mandatory = $true)][long]$VideoId,
        [Parameter(Mandatory = $true)][string]$IdempotencyKey,
        [Parameter(Mandatory = $true)][string]$TraceId,
        [ValidateSet('POST', 'GET')][string]$Method = 'POST'
    )

    $path = if ($Method -eq 'POST') {
        "$VideoBaseUrl/api/private/processing/$VideoId/recover-expired"
    } else {
        "$VideoBaseUrl/api/private/processing/$VideoId/recovery-status"
    }
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::$Method, $path)
    $request.Headers.Add('Idempotency-Key', $IdempotencyKey)
    $request.Headers.Add('X-Trace-Id', $TraceId)
    $request.Headers.Add('X-FlowPilot-Service', 'flowpilot')
    return $Client.SendAsync($request)
}

function Read-RecoveryResponse {
    param([System.Net.Http.HttpResponseMessage]$Response)

    $body = $Response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    try {
        return [pscustomobject]@{
            HttpStatus = [int]$Response.StatusCode
            Body = $body | ConvertFrom-Json
        }
    } catch {
        throw "Recovery endpoint returned non-JSON response: $body"
    }
}

function Assert-Equal {
    param([object]$Actual, [object]$Expected, [string]$Message)
    if ("$Actual" -ne "$Expected") {
        throw "$Message Expected=[$Expected], Actual=[$Actual]"
    }
}

function Seed-ProcessingVideo {
    param(
        [long]$VideoId,
        [long]$TaskId,
        [int]$VideoStatus = 3,
        [int]$TaskStatus = 1,
        [string]$LeaseExpression
    )

    if ([string]::IsNullOrWhiteSpace($LeaseExpression)) {
        $LeaseExpression = "DATE_SUB(CONVERT_TZ(NOW(), '+00:00', '$ApplicationUtcOffset'), INTERVAL 5 MINUTE)"
    }

    Invoke-MySql @"
INSERT INTO video(id, creator_id, url, description, likes, comments, favorites, views, status, updated_at, created_at)
VALUES ($VideoId, 900000000000000001, 'e2e/recovery-$VideoId.mp4', 'recovery receipt isolated test', 0, 0, 0, 0, $VideoStatus, NOW(), NOW());
INSERT INTO video_processing_task(id, video_id, status, retry_count, lease_expire_at, updated_at, created_at)
VALUES ($TaskId, $VideoId, $TaskStatus, 1, $LeaseExpression, NOW(), NOW());
"@ | Out-Null
}

$runSeed = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() * 1000 + (Get-Random -Minimum 1 -Maximum 900)
$sameKeyVideoId = [long]$runSeed
$sameKeyTaskId = $sameKeyVideoId + 1
$differentKeyVideoId = $sameKeyVideoId + 10
$differentKeyTaskId = $sameKeyVideoId + 11
$rejectedVideoId = $sameKeyVideoId + 20
$rejectedTaskId = $sameKeyVideoId + 21
$rollbackVideoId = $sameKeyVideoId + 30
$rollbackTaskId = $sameKeyVideoId + 31
$allVideoIds = @($sameKeyVideoId, $differentKeyVideoId, $rejectedVideoId, $rollbackVideoId)
$allTaskIds = @($sameKeyTaskId, $differentKeyTaskId, $rejectedTaskId, $rollbackTaskId)
$sameKey = "e2e-recovery-${sameKeyVideoId}:same-key"
$differentKeyA = "e2e-recovery-${differentKeyVideoId}:key-a"
$differentKeyB = "e2e-recovery-${differentKeyVideoId}:key-b"
$rejectedKey = "e2e-recovery-${rejectedVideoId}:rejected"
$rollbackKey = "e2e-recovery-${rollbackVideoId}:rollback"
$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(20)

try {
    Invoke-MySql "SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'video_processing_recovery_request';" | Out-Null
    Seed-ProcessingVideo -VideoId $sameKeyVideoId -TaskId $sameKeyTaskId
    Seed-ProcessingVideo -VideoId $differentKeyVideoId -TaskId $differentKeyTaskId
    Seed-ProcessingVideo -VideoId $rejectedVideoId -TaskId $rejectedTaskId -LeaseExpression "DATE_ADD(CONVERT_TZ(NOW(), '+00:00', '$ApplicationUtcOffset'), INTERVAL 5 MINUTE)"
    # 任务满足恢复条件，但 Video 不是 PROCESSING：用于验证任务重置后的异常会整体回滚。
    Seed-ProcessingVideo -VideoId $rollbackVideoId -TaskId $rollbackTaskId -VideoStatus 1

    $sameA = Invoke-RecoveryRequest -Client $client -VideoId $sameKeyVideoId -IdempotencyKey $sameKey -TraceId 'e2e-same-a'
    $sameB = Invoke-RecoveryRequest -Client $client -VideoId $sameKeyVideoId -IdempotencyKey $sameKey -TraceId 'e2e-same-b'
    $sameAResult = Read-RecoveryResponse ($sameA.GetAwaiter().GetResult())
    $sameBResult = Read-RecoveryResponse ($sameB.GetAwaiter().GetResult())
    Assert-Equal $sameAResult.HttpStatus 200 'Same-key request A must succeed.'
    Assert-Equal $sameBResult.HttpStatus 200 'Same-key request B must succeed.'
    Assert-Equal $sameAResult.Body.data.status 'ACCEPTED' 'Same-key request A must be accepted.'
    Assert-Equal $sameBResult.Body.data.status 'ACCEPTED' 'Same-key request B must replay accepted receipt.'
    Assert-Equal $sameAResult.Body.data.recoveryId $sameBResult.Body.data.recoveryId 'Same key must return one recovery receipt.'
    Assert-Equal $sameAResult.Body.data.outboxId $sameBResult.Body.data.outboxId 'Same key must return one Outbox.'

    $sameGet = Read-RecoveryResponse ((Invoke-RecoveryRequest -Client $client -VideoId $sameKeyVideoId -IdempotencyKey $sameKey -TraceId 'e2e-same-get' -Method GET).GetAwaiter().GetResult())
    Assert-Equal $sameGet.HttpStatus 200 'Recovery GET must return persisted receipt.'
    Assert-Equal $sameGet.Body.data.replayed $true 'Recovery GET must be marked replayed.'

    $differentA = Invoke-RecoveryRequest -Client $client -VideoId $differentKeyVideoId -IdempotencyKey $differentKeyA -TraceId 'e2e-different-a'
    $differentB = Invoke-RecoveryRequest -Client $client -VideoId $differentKeyVideoId -IdempotencyKey $differentKeyB -TraceId 'e2e-different-b'
    $differentAResult = Read-RecoveryResponse ($differentA.GetAwaiter().GetResult())
    $differentBResult = Read-RecoveryResponse ($differentB.GetAwaiter().GetResult())
    $differentResults = @($differentAResult, $differentBResult)
    Assert-Equal (@($differentResults | Where-Object { $_.Body.data.status -eq 'ACCEPTED' }).Count) 1 'Different keys must accept exactly one recovery.'
    Assert-Equal (@($differentResults | Where-Object { $_.Body.data.status -eq 'REJECTED' }).Count) 1 'Different keys must reject the losing recovery.'

    $rejected = Read-RecoveryResponse ((Invoke-RecoveryRequest -Client $client -VideoId $rejectedVideoId -IdempotencyKey $rejectedKey -TraceId 'e2e-rejected').GetAwaiter().GetResult())
    Assert-Equal $rejected.HttpStatus 200 'Precondition rejection is a persisted business receipt.'
    Assert-Equal $rejected.Body.data.status 'REJECTED' 'Unexpired lease must be rejected.'
    Assert-Equal $rejected.Body.data.reason 'PRECONDITION_NOT_MET' 'Rejection reason must be stable.'

    $rollback = Read-RecoveryResponse ((Invoke-RecoveryRequest -Client $client -VideoId $rollbackVideoId -IdempotencyKey $rollbackKey -TraceId 'e2e-rollback').GetAwaiter().GetResult())
    Assert-Equal $rollback.HttpStatus 500 'Video/task mismatch must fail instead of producing a partial receipt.'

    $sql = @"
SELECT
  (SELECT COUNT(*) FROM video_processing_recovery_request WHERE idempotency_key = '$sameKey'),
  (SELECT COUNT(*) FROM message_outbox WHERE business_id = $sameKeyVideoId),
  (SELECT status FROM video_processing_task WHERE id = $sameKeyTaskId),
  (SELECT status FROM video WHERE id = $sameKeyVideoId),
  (SELECT COUNT(*) FROM video_processing_recovery_request WHERE video_id = $differentKeyVideoId),
  (SELECT COUNT(*) FROM message_outbox WHERE business_id = $differentKeyVideoId),
  (SELECT COUNT(*) FROM message_outbox WHERE business_id = $rejectedVideoId),
  (SELECT COUNT(*) FROM video_processing_recovery_request WHERE video_id = $rollbackVideoId),
  (SELECT COUNT(*) FROM message_outbox WHERE business_id = $rollbackVideoId),
  (SELECT status FROM video_processing_task WHERE id = $rollbackTaskId),
  (SELECT status FROM video WHERE id = $rollbackVideoId);
"@
    $verification = (Invoke-MySql $sql | Select-Object -Last 1).Split("`t")
    Assert-Equal $verification[0] 1 'Same key must persist one receipt.'
    Assert-Equal $verification[1] 1 'Same key must create one Outbox.'
    Assert-Equal $verification[2] 0 'Accepted recovery must reset task to PENDING.'
    Assert-Equal $verification[3] 1 'Accepted recovery must reset video to PENDING_REVIEW.'
    Assert-Equal $verification[4] 2 'Different keys must each have a durable receipt.'
    Assert-Equal $verification[5] 1 'Different keys must create only one Outbox.'
    Assert-Equal $verification[6] 0 'Precondition rejection must not create an Outbox.'
    Assert-Equal $verification[7] 0 'Rollback must remove the pending receipt.'
    Assert-Equal $verification[8] 0 'Rollback must not create an Outbox.'
    Assert-Equal $verification[9] 1 'Rollback must keep task PROCESSING.'
    Assert-Equal $verification[10] 1 'Rollback must keep video at original status.'

    [pscustomobject]@{
        SameKeyRecoveryId = $sameAResult.Body.data.recoveryId
        SameKeyOutboxId = $sameAResult.Body.data.outboxId
        SameKeyReplay = $sameBResult.Body.data.replayed
        DifferentKeys = '1 ACCEPTED / 1 REJECTED / 1 Outbox'
        Precondition = $rejected.Body.data.status
        Rollback = 'receipt=0,outbox=0,task=PROCESSING,video=PENDING_REVIEW'
    }
} finally {
    $videoIdList = $allVideoIds -join ','
    $taskIdList = $allTaskIds -join ','
    Invoke-MySql "DELETE FROM message_outbox WHERE business_id IN ($videoIdList); DELETE FROM video_processing_recovery_request WHERE video_id IN ($videoIdList); DELETE FROM video_processing_task WHERE id IN ($taskIdList); DELETE FROM video WHERE id IN ($videoIdList);" | Out-Null
    $client.Dispose()
}
