param(
    [string]$GatewayUrl = 'http://127.0.0.1:10086'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot

function Invoke-SwResult {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Token,
        [object]$Body,
        [switch]$AllowBusinessError
    )
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $parameters = @{
        Method = $Method
        Uri = "$GatewayUrl$Path"
        Headers = $headers
        TimeoutSec = 15
    }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 8
    }
    try {
        $result = Invoke-RestMethod @parameters
    } catch {
        if (-not $AllowBusinessError -or -not $_.ErrorDetails.Message) { throw }
        $result = $_.ErrorDetails.Message | ConvertFrom-Json
    }
    if (-not $AllowBusinessError -and $null -ne $result.code -and $result.code -ne 1) {
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

$suffix = Get-Random -Minimum 10000000 -Maximum 99999999
$password = 'SwTest123456!'
$creatorToken = New-TestUser -Phone "188$suffix" -Password $password
$buyerToken = New-TestUser -Phone "189$suffix" -Password $password
$creatorId = Get-JwtUserId $creatorToken
$buyerId = Get-JwtUserId $buyerToken
$videoId = [long]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() * 1000 + (Get-Random -Minimum 1 -Maximum 999))

# 该脚本只聚焦电商闭环，使用隔离的已发布视频事实作为前置，不重复执行媒体转码 E2E。
$sql = @"
INSERT INTO video(id, creator_id, url, cover_url, description, likes, comments, favorites, views, status, updated_at, published_at, created_at)
VALUES ($videoId, $creatorId, 'e2e/commerce-placeholder.mp4', NULL, 'commerce e2e isolated video', 0, 0, 0, 0, 5, NOW(), NOW(), NOW());
"@
$sql | & docker compose --project-name sw-dev exec -T mysql sh -lc 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'
if ($LASTEXITCODE -ne 0) { throw 'Failed to seed isolated published video.' }

$product = (Invoke-SwResult -Method Post -Path '/video/api/me/commerce/products' -Token $creatorToken -Body @{
    videoId = $videoId
    name = 'E2E 限量周边'
    description = '短视频带货闭环测试商品'
    imageUrl = $null
    priceCent = 1290
    stock = 5
}).data

$startsAt = (Get-Date).AddMinutes(-1).ToString('yyyy-MM-ddTHH:mm:ss')
$endsAt = (Get-Date).AddMinutes(20).ToString('yyyy-MM-ddTHH:mm:ss')
$saleProduct = (Invoke-SwResult -Method Post -Path '/video/api/me/commerce/flash-sales' -Token $creatorToken -Body @{
    productId = $product.productId
    salePriceCent = 990
    totalStock = 3
    perUserLimit = 1
    startsAt = $startsAt
    endsAt = $endsAt
}).data

$couponTemplate = (Invoke-SwResult -Method Post -Path '/video/api/me/commerce/coupon-templates' -Token $creatorToken -Body @{
    name = 'E2E 满减券'
    thresholdCent = 500
    discountCent = 100
    totalStock = 10
    startsAt = $startsAt
    endsAt = $endsAt
}).data

Invoke-SwResult -Method Post -Path "/video/api/me/commerce/coupons/$($couponTemplate.templateId)/claim" -Token $buyerToken | Out-Null
$buyerCoupon = ((Invoke-SwResult -Method Get -Path '/video/api/me/commerce/coupons' -Token $buyerToken).data | Select-Object -First 1)
$publicProduct = (Invoke-SwResult -Method Get -Path "/video/api/public/commerce/videos/$videoId/product").data

$order = (Invoke-SwResult -Method Post -Path '/video/api/me/commerce/orders' -Token $buyerToken -Body @{
    flashSaleId = $saleProduct.flashSaleId
    userCouponId = $buyerCoupon.id
    receiverName = 'E2E 用户'
    receiverPhone = '13800000000'
    receiverAddress = '测试隔离地址'
}).data

$duplicate = Invoke-SwResult -Method Post -Path '/video/api/me/commerce/orders' -Token $buyerToken -AllowBusinessError -Body @{
    flashSaleId = $saleProduct.flashSaleId
    receiverName = 'E2E 用户'
    receiverPhone = '13800000000'
    receiverAddress = '测试隔离地址'
}
if ($duplicate.code -ne 0) { throw 'Duplicate flash-sale order was not rejected.' }

Invoke-SwResult -Method Post -Path "/video/api/me/commerce/orders/$($order.id)/pay" -Token $buyerToken | Out-Null
Invoke-SwResult -Method Post -Path "/video/api/me/commerce/creator/orders/$($order.id)/ship" -Token $creatorToken | Out-Null
Invoke-SwResult -Method Post -Path "/video/api/me/commerce/orders/$($order.id)/complete" -Token $buyerToken | Out-Null
$refund = (Invoke-SwResult -Method Post -Path "/video/api/me/commerce/orders/$($order.id)/refund" -Token $buyerToken -Body @{
    reason = 'E2E 售后流程验证'
}).data
Invoke-SwResult -Method Post -Path "/video/api/me/commerce/creator/refunds/$($refund.id)/review" -Token $creatorToken -Body @{
    approved = $true
    reply = 'E2E 审核通过'
} | Out-Null
$finalOrder = ((Invoke-SwResult -Method Get -Path '/video/api/me/commerce/orders' -Token $buyerToken).data |
    Where-Object id -eq $order.id | Select-Object -First 1)

$memory = Invoke-RestMethod -Method Post -Uri "$GatewayUrl/ai/api/creator-assistant/memories" `
    -Headers @{ Authorization = "Bearer $creatorToken" } -ContentType 'application/json' `
    -Body (@{ type = 'STYLE'; content = '标题偏好简短直接，避免夸张承诺' } | ConvertTo-Json) -TimeoutSec 20
$memoryList = Invoke-RestMethod -Method Get -Uri "$GatewayUrl/ai/api/creator-assistant/memories" `
    -Headers @{ Authorization = "Bearer $creatorToken" } -TimeoutSec 15
if (-not ($memoryList | Where-Object id -eq $memory.id)) { throw 'Saved creator memory was not returned.' }
Invoke-RestMethod -Method Delete -Uri "$GatewayUrl/ai/api/creator-assistant/memories/$($memory.id)" `
    -Headers @{ Authorization = "Bearer $creatorToken" } -TimeoutSec 15 | Out-Null
$memoryAfterDelete = Invoke-RestMethod -Method Get -Uri "$GatewayUrl/ai/api/creator-assistant/memories" `
    -Headers @{ Authorization = "Bearer $creatorToken" } -TimeoutSec 15
if ($memoryAfterDelete | Where-Object id -eq $memory.id) { throw 'Deleted creator memory is still visible.' }

[pscustomobject]@{
    CreatorId = $creatorId
    BuyerId = $buyerId
    VideoId = $videoId
    ProductVisible = ($publicProduct.productId -eq $product.productId)
    DuplicateOrderRejected = ($duplicate.code -eq 0)
    CouponDiscountCent = $order.discountAmountCent
    FinalOrderStatus = $finalOrder.status
    MemoryCreateListDelete = $true
}
