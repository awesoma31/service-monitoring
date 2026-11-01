#!/usr/bin/env bash
set -euo pipefail

bootstrap=kafka-1:9092,kafka-2:9092,kafka-3:9092
topics=(
  checks.completed.v1
  incidents.events.v1
  resources.events.v1
  checks.completed.v1.dlt
  incidents.events.v1.dlt
  resources.events.v1.dlt
)

for topic in "${topics[@]}"; do
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$bootstrap" \
    --create --if-not-exists --topic "$topic" --partitions 3 --replication-factor 3 \
    --config min.insync.replicas=2

  # --if-not-exists does not check the existing topic's layout; fail explicitly if a
  # previously created topic has a different partition count or replication factor.
  description=$(/opt/kafka/bin/kafka-topics.sh --bootstrap-server "$bootstrap" \
    --describe --topic "$topic")
  if ! printf '%s\n' "$description" | grep -Eq 'PartitionCount:[[:space:]]*3[[:space:]]+ReplicationFactor:[[:space:]]*3([[:space:]]|$)'; then
    printf 'Topic %s must have exactly 3 partitions and replication factor 3\n' "$topic" >&2
    exit 1
  fi
  /opt/kafka/bin/kafka-configs.sh --bootstrap-server "$bootstrap" \
    --entity-type topics --entity-name "$topic" --alter --add-config min.insync.replicas=2
done
