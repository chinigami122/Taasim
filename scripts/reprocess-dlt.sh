#!/usr/bin/env bash
# Read all messages from a DLT topic and republish them to the original topic.
# Usage: ./reprocess-dlt.sh trip.completed
set -euo pipefail

TOPIC="${1:-trip.completed}"
DLT="${TOPIC}.DLT"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-taasim-kafka}"

echo "=========================================================="
echo "  TaaSim - DLT Reprocessor for topic: $TOPIC"
echo "  Source DLT: $DLT"
echo "=========================================================="

docker exec -i "$KAFKA_CONTAINER" /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic "$DLT" \
  --from-beginning \
  --timeout-ms 5000 2>/dev/null |
while read -r msg; do
  if [ -n "$msg" ]; then
    echo "$msg" | docker exec -i "$KAFKA_CONTAINER" /opt/kafka/bin/kafka-console-producer.sh \
      --bootstrap-server localhost:9092 --topic "$TOPIC"
    echo "  ↻ requeued: $msg"
  fi
done

echo "DLT reprocessing finished."
