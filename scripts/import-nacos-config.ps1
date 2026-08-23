param(
    [string]$NacosUrl = "http://localhost:$($env:SW_NACOS_PORT ?? '28848')",
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
    $localContent = [System.IO.File]::ReadAllText($_.FullName)
    $body = @{
        dataId  = $_.Name
        group   = 'DEFAULT_GROUP'
        type    = 'yaml'
        content = $localContent
    }
    $published = Invoke-RestMethod -Method Post -Uri "$NacosUrl/nacos/v1/cs/configs?tenant=$NamespaceId" -ContentType 'application/x-www-form-urlencoded' -Body $body
    if ($published -ne $true -and "$published" -ne 'true') {
        throw "Nacos rejected $($_.Name): $published"
    }

    $verified = $false
    for ($attempt = 1; $attempt -le 10; $attempt++) {
        $remoteContent = (Invoke-WebRequest -UseBasicParsing -Uri "$NacosUrl/nacos/v1/cs/configs?dataId=$($_.Name)&group=DEFAULT_GROUP&tenant=$NamespaceId" -TimeoutSec 5).Content
        if ($remoteContent -eq $localContent) {
            $verified = $true
            break
        }
        Start-Sleep -Milliseconds 300
    }
    if (-not $verified) {
        throw "Nacos content verification failed for $($_.Name)"
    }
    Write-Host "Imported and verified $($_.Name)"
}
