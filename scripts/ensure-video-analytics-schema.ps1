param(
    [string]$MysqlContainer = 'sw-dev-mysql-1',
    [string]$MysqlPassword
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($MysqlPassword)) {
    $line = Get-Content -LiteralPath (Join-Path $projectRoot '.env') | Where-Object { $_ -match '^MYSQL_ROOT_PASSWORD=' } | Select-Object -First 1
    if ($null -eq $line) { throw 'Missing MYSQL_ROOT_PASSWORD in local .env.' }
    $MysqlPassword = $line.Substring('MYSQL_ROOT_PASSWORD='.Length)
}

$columnExists = & docker exec -e "MYSQL_PWD=$MysqlPassword" $MysqlContainer mysql -uroot -D yh -N -s -e "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'video' AND COLUMN_NAME = 'views';"
if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect the video analytics schema.' }
if ([string]$columnExists -eq '0') {
    & docker exec -e "MYSQL_PWD=$MysqlPassword" $MysqlContainer mysql -uroot -D yh -e "ALTER TABLE video ADD COLUMN views INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '每日去重观看数' AFTER favorites;"
    if ($LASTEXITCODE -ne 0) { throw 'Unable to add the video.views column.' }
}

$sql = @"
CREATE TABLE IF NOT EXISTS video_view_event (
    id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    video_id BIGINT UNSIGNED NOT NULL,
    creator_id BIGINT UNSIGNED NOT NULL,
    viewer_id BIGINT UNSIGNED NOT NULL,
    viewed_date DATE NOT NULL,
    created_at DATETIME NULL,
    CONSTRAINT uk_video_view_event_daily UNIQUE (video_id, viewer_id, viewed_date),
    INDEX idx_video_view_event_creator_date (creator_id, viewed_date)
) CHARSET=utf8mb3;
"@

& docker exec -e "MYSQL_PWD=$MysqlPassword" $MysqlContainer mysql -uroot -D yh -e $sql
if ($LASTEXITCODE -ne 0) { throw 'Video analytics schema initialization failed.' }
Write-Host 'Video analytics schema is ready.'
