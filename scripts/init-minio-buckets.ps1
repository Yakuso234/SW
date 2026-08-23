param(
    [string]$ContainerName = 'sw-dev-minio-1',
    [string[]]$Buckets = @('video', 'user', 'product', 'live', 'chat')
)

$ErrorActionPreference = 'Stop'

$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerCommand) {
    $dockerPath = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'
    if (-not (Test-Path -LiteralPath $dockerPath)) {
        throw '未找到 Docker CLI。请先安装并启动 Docker Desktop。'
    }
    $dockerCommand = Get-Command $dockerPath
}
$docker = $dockerCommand.Source

for ($attempt = 1; $attempt -le 30; $attempt++) {
    & $docker exec $ContainerName sh -lc 'mc alias set sw-local http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null' 2>$null
    if ($LASTEXITCODE -eq 0) { break }
    if ($attempt -eq 30) { throw 'MinIO was not ready in time.' }
    Start-Sleep -Seconds 2
}

foreach ($bucket in $Buckets) {
    if ($bucket -notmatch '^[a-z0-9][a-z0-9-]+$') {
        throw "非法 Bucket 名称: $bucket"
    }
    & $docker exec $ContainerName sh -lc "mc mb --ignore-existing sw-local/$bucket >/dev/null"
    if ($LASTEXITCODE -ne 0) {
        throw "MinIO Bucket 初始化失败: $bucket"
    }
    Write-Host "Initialized MinIO bucket: $bucket"
}
