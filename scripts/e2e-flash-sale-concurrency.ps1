param(
    [string]$GatewayUrl = 'http://127.0.0.1:10086',
    [int]$BuyerCount = 12,
    [int]$Stock = 3
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
if ($BuyerCount -lt $Stock -or $Stock -lt 1) {
    throw 'BuyerCount must be greater than or equal to Stock, and Stock must be positive.'
}

function Invoke-SwResult {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Token,
        [object]$Body
    )
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $parameters = @{
        Method = $Method
        Uri = "$GatewayUrl$Path"
        Headers = $headers
        TimeoutSec = 20
    }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 8
    }
    $result = Invoke-RestMethod @parameters
    if ($null -ne $result.code -and $result.code -ne 1) {
        throw "SW business request failed: $Path -> $($result.msg)"
    }
    return $result
}

function Get-JwtUserId([string]$Token) {
    $payload = $Token.Split('.')[1].Replace('-', '+').Replace('_', '/')
    while ($payload.Length % 4 -ne 0) { $payload += '=' }
    $json = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload))
    return [long](($json | ConvertFrom-Json).id)
}

function New-TestUser([string]$Phone, [string]$Password) {
    Invoke-SwResult -Method Post -Path '/user/api/public/auth/register' -Body @{
        phoneNumber = $Phone
        password = $Password
    } | Out-Null
    return (Invoke-SwResult -Method Post -Path '/user/api/public/auth/login' -Body @{
        phoneNumber = $Phone
        password = $Password
    }).data
}

$runId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$password = 'SwConcurrency123!'
$creatorPhone = "17$($runId.ToString().Substring(3, 9))"
$creatorToken = New-TestUser -Phone $creatorPhone -Password $password
$creatorId = Get-JwtUserId $creatorToken
$buyerTokens = for ($index = 0; $index -lt $BuyerCount; $index++) {
    $phone = "16$((($runId + $index + 1) % 1000000000).ToString('000000000'))"
    New-TestUser -Phone $phone -Password $password
}

$videoId = [long]($runId * 1000 + (Get-Random -Minimum 1 -Maximum 999))
$sql = @"
INSERT INTO video(id, creator_id, url, cover_url, description, likes, comments, favorites, views, status, updated_at, published_at, created_at)
VALUES ($videoId, $creatorId, 'e2e/concurrency-placeholder.mp4', NULL, 'flash sale concurrency isolated video', 0, 0, 0, 0, 5, NOW(), NOW(), NOW());
"@
$sql | & docker compose --project-name sw-dev exec -T mysql sh -lc 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'
if ($LASTEXITCODE -ne 0) { throw 'Failed to seed isolated published video.' }

$product = (Invoke-SwResult -Method Post -Path '/video/api/me/commerce/products' -Token $creatorToken -Body @{
    videoId = $videoId
    name = '并发测试限量商品'
    description = '固定规模并发下单验证'
    priceCent = 1990
    stock = $Stock
}).data
$startsAt = (Get-Date).AddMinutes(-1).ToString('yyyy-MM-ddTHH:mm:ss')
$endsAt = (Get-Date).AddMinutes(20).ToString('yyyy-MM-ddTHH:mm:ss')
$sale = (Invoke-SwResult -Method Post -Path '/video/api/me/commerce/flash-sales' -Token $creatorToken -Body @{
    productId = $product.productId
    salePriceCent = 990
    totalStock = $Stock
    perUserLimit = 1
    startsAt = $startsAt
    endsAt = $endsAt
}).data

$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(30)
$tasks = foreach ($token in $buyerTokens) {
    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::Post,
        "$GatewayUrl/video/api/me/commerce/orders"
    )
    $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $token)
    $body = @{
        flashSaleId = $sale.flashSaleId
        receiverName = '并发测试用户'
        receiverPhone = '13800000000'
        receiverAddress = '隔离测试地址'
    } | ConvertTo-Json -Compress
    $request.Content = [System.Net.Http.StringContent]::new($body, [Text.Encoding]::UTF8, 'application/json')
    $client.SendAsync($request)
}

$startedAt = [DateTimeOffset]::UtcNow
$results = foreach ($task in $tasks) {
    $response = $task.GetAwaiter().GetResult()
    $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    try {
        $json = $content | ConvertFrom-Json
        [pscustomobject]@{ StatusCode = [int]$response.StatusCode; Code = $json.code; Message = $json.msg }
    } catch {
        [pscustomobject]@{ StatusCode = [int]$response.StatusCode; Code = $null; Message = $content }
    }
}
$elapsedMs = ([DateTimeOffset]::UtcNow - $startedAt).TotalMilliseconds
$client.Dispose()

$successCount = @($results | Where-Object Code -eq 1).Count
$rejectedCount = $BuyerCount - $successCount
$dbSql = "SELECT COUNT(*) FROM video_commerce_order WHERE flash_sale_id=$($sale.flashSaleId);"
$dbOrderCount = @($dbSql | & docker compose --project-name sw-dev exec -T mysql sh -lc 'mysql -N -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"') |
    Where-Object { "$_" -match '^\d+$' } | Select-Object -Last 1
$redisStock = @(& docker compose --project-name sw-dev exec -T redis redis-cli HGET "sw:commerce:flash-sale:$($sale.flashSaleId)" stock) |
    Where-Object { "$_" -match '^\d+$' } | Select-Object -Last 1

if ($successCount -ne $Stock) { throw "Expected $Stock successful orders but got $successCount." }
if ([int]$dbOrderCount -ne $Stock) { throw "Expected $Stock database orders but got $dbOrderCount." }
if ([int]$redisStock -ne 0) { throw "Expected Redis stock 0 but got $redisStock." }

[pscustomobject]@{
    Scenario = "$BuyerCount concurrent buyers / stock $Stock"
    SuccessfulOrders = $successCount
    RejectedOrders = $rejectedCount
    DatabaseOrders = [int]$dbOrderCount
    RedisRemainingStock = [int]$redisStock
    BatchElapsedMs = [math]::Round($elapsedMs, 2)
    Oversold = $false
}
