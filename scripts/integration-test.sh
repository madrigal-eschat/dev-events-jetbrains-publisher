#!/usr/bin/env bash
# Integration smoke test: starts a local MQTT broker, subscribes to all events,
# launches the sandbox IDE, and prints captured events on exit.
set -euo pipefail

cd "$(dirname "$0")/.."

# Prefer 'docker compose' (v2) over 'docker-compose' (v1)
if docker compose version &>/dev/null; then
    DC="docker compose"
elif command -v docker-compose &>/dev/null; then
    DC="docker-compose"
else
    echo "ERROR: docker compose not found" >&2
    exit 1
fi

cleanup() {
    echo ""
    echo "=== Captured MQTT events ==="
    if [[ -s "$CAPTURE" ]]; then
        cat "$CAPTURE"
    else
        echo "(none — were events enabled in plugin settings?)"
    fi
    rm -f "$CAPTURE"

    kill "$SUB_PID" 2>/dev/null || true

    printf "\nStop broker? [y/N] "
    read -r ans
    [[ "$ans" =~ ^[Yy]$ ]] && $DC down
}

echo "Starting MQTT broker..."
$DC up -d mosquitto

# Wait for broker to be ready
for i in $(seq 1 10); do
    docker compose exec -T mosquitto mosquitto_pub -t "test/ping" -m "" 2>/dev/null && break
    sleep 0.5
done

CAPTURE=$(mktemp)
docker compose exec -T mosquitto mosquitto_sub -t "ide-events/#" -v >"$CAPTURE" 2>&1 &
SUB_PID=$!

trap cleanup EXIT

cat <<EOF

Broker ready on localhost:1883. Events will be captured.

Configure plugin in the sandbox IDE:
  Settings → Tools → IDE Events
  Broker URL: tcp://localhost:1883
  Enable at least one event, click Apply

Launching sandbox IDE (first run is slow)...
EOF

./gradlew runIde
