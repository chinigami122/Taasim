<#
.SYNOPSIS
  Read all messages from a Dead Letter Topic (DLT) and republish them to the target Kafka topic.
.EXAMPLE
  .\scripts\reprocess-dlt.ps1 -Topic "trip.completed"
#>
param (
    [string]$Topic = "trip.completed",
    [string]$KafkaContainer = "taasim-kafka",
    [int]$MaxMessages = 5
)

$DLT = "$Topic.DLT"
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  TaaSim - DLT Reprocessor for topic: $Topic" -ForegroundColor Cyan
Write-Host "  Source DLT: $DLT" -ForegroundColor Cyan
Write-Host "=========================================================="

Write-Host "Consuming up to $MaxMessages messages from $DLT..." -ForegroundColor Yellow

$prevErrorAction = $ErrorActionPreference
$ErrorActionPreference = "SilentlyContinue"

$rawOutput = & docker exec $KafkaContainer /opt/kafka/bin/kafka-console-consumer.sh `
    --bootstrap-server localhost:9092 `
    --topic $DLT `
    --from-beginning `
    --max-messages $MaxMessages 2>&1

$ErrorActionPreference = $prevErrorAction

$messages = @($rawOutput | ForEach-Object { $_.ToString().Trim() } | Where-Object { 
    $_.StartsWith("{") -and 
    -not $_.Contains("ERROR") -and 
    -not $_.Contains("Exception") -and
    -not $_.Contains("Processed a total")
})

if ($messages.Count -eq 0) {
    Write-Host "[INFO] No messages found in $DLT to reprocess." -ForegroundColor Green
    exit 0
}

$count = 0
foreach ($msg in $messages) {
    if ([string]::IsNullOrWhiteSpace($msg)) { continue }
    Write-Host "  ↻ Requeueing message to $Topic..." -ForegroundColor Gray
    $msg | docker exec -i $KafkaContainer /opt/kafka/bin/kafka-console-producer.sh `
        --bootstrap-server localhost:9092 `
        --topic $Topic
    $count++
}

Write-Host "`n[SUCCESS] Successfully reprocessed $count message(s) from $DLT to $Topic!" -ForegroundColor Green

