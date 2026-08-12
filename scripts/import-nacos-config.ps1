param(
    [string]$NacosUrl = 'http://localhost:8848',
    [string]$NamespaceId = 'dev'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$configDirectory = Join-Path $projectRoot '.file\config'

for ($attempt = 1; $attempt -le 30; $attempt++) {
    try {
        Invoke-RestMethod -Uri "$NacosUrl/nacos/v1/console/health/readiness" -TimeoutSec 3 | Out-Null
        break
    } catch {
        if ($attempt -eq 30) { throw 'Nacos was not ready in time.' }
        Start-Sleep -Seconds 2
    }
}

Get-ChildItem -LiteralPath $configDirectory -Filter '*.yaml' | ForEach-Object {
    $body = @{
        dataId  = $_.Name
        group   = 'DEFAULT_GROUP'
        type    = 'yaml'
        content = [System.IO.File]::ReadAllText($_.FullName)
    }
    Invoke-RestMethod -Method Post -Uri "$NacosUrl/nacos/v1/cs/configs?tenant=$NamespaceId" -ContentType 'application/x-www-form-urlencoded' -Body $body | Out-Null
    Write-Host "Imported $($_.Name)"
}
