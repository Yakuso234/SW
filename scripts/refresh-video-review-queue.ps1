param(
    [string]$RabbitContainer = 'sw-rabbitmq-1'
)

$ErrorActionPreference = 'Stop'
$queue = 'video.review.queue'
$summary = docker exec $RabbitContainer rabbitmqctl list_queues name messages messages_unacknowledged --formatter json | ConvertFrom-Json
$target = $summary | Where-Object { $_.name -eq $queue }

if ($null -eq $target) {
    Write-Host "$queue does not exist. Start the updated Video or Processor service to declare it."
    exit 0
}

if ($target.messages -ne 0 -or $target.messages_unacknowledged -ne 0) {
    throw "$queue is not empty. Refuse to recreate it because messages could be lost."
}

docker exec $RabbitContainer rabbitmqctl delete_queue $queue
if ($LASTEXITCODE -ne 0) {
    throw "Failed to delete empty $queue."
}

Write-Host "$queue was removed safely. Restart the updated Video Service and Video Processor; they will declare the queue with its DLQ settings."
