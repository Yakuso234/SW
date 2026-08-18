param(
    [switch]$Observability
)

$ErrorActionPreference = 'Stop'
$projectName = 'sw-dev'
$baseServices = @('mysql', 'redis', 'rabbitmq', 'minio', 'nacos')

 $dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerCommand) {
    $dockerBin = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin'
    $dockerPath = Join-Path $dockerBin 'docker.exe'
    if (Test-Path -LiteralPath $dockerPath) {
        $env:Path = "$dockerBin;$env:Path"
        $dockerCommand = Get-Command $dockerPath
    } else {
        throw '未找到 Docker CLI。请先安装并启动 Docker Desktop。'
    }
}

$docker = $dockerCommand.Source
$composeArgs = @('--project-name', $projectName, 'up', '-d') + $baseServices

Write-Host "启动 SW 独立 Compose 项目: $projectName"
& $docker compose @composeArgs
if ($LASTEXITCODE -ne 0) {
    throw "SW 中间件启动失败，退出码: $LASTEXITCODE"
}

if ($Observability) {
    & $docker compose --project-name $projectName --profile observability up -d prometheus grafana
    if ($LASTEXITCODE -ne 0) {
        throw "SW 可观测组件启动失败，退出码: $LASTEXITCODE"
    }
}

& $docker compose --project-name $projectName ps
