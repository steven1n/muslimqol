#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

FIFO="/tmp/server_fifo_$$"
rm -f "$FIFO"
mkfifo "$FIFO"

cleanup() {
    exec 3>&- 2>/dev/null || true
    rm -f "$FIFO" server_console.log
}
trap cleanup EXIT

echo "Starting server via FIFO: $FIFO"
# Open FIFO for writing first to prevent blocking read
exec 3<> "$FIFO"

./gradlew --console=plain runServer < "$FIFO" > server_console.log 2>&1 &
SERVER_PID=$!

echo "Server process PID: $SERVER_PID"

MAX_WAIT=120
ELAPSED=0
SUCCESS=false

while [ $ELAPSED -lt $MAX_WAIT ]; do
    if ! kill -0 $SERVER_PID 2>/dev/null; then
        echo "Server process ended unexpectedly."
        break
    fi

    if grep -q "Done (" server_console.log 2>/dev/null; then
        echo "Server is ready! Found 'Done (' in server_console.log"
        SUCCESS=true
        break
    fi

    sleep 2
    ELAPSED=$((ELAPSED + 2))
done

if [ "$SUCCESS" != true ]; then
    echo "ERROR: Server did not reach ready state in time."
    kill -9 $SERVER_PID 2>/dev/null || true
    exit 1
fi

echo "Running dedicated server command and reload checks..."
echo "muslimqol status" >&3
echo "muslimqol classify minecraft:porkchop" >&3
echo "muslimqol reload" >&3
echo "reload" >&3
sleep 4

echo "Sending 'stop' command to server..."
echo "stop" >&3
echo "Waiting up to 40s for server to shut down cleanly..."

STOP_WAIT=40
while [ $STOP_WAIT -gt 0 ] && kill -0 $SERVER_PID 2>/dev/null; do
    sleep 1
    STOP_WAIT=$((STOP_WAIT - 1))
done

if kill -0 $SERVER_PID 2>/dev/null; then
    echo "Server still running after 40s, killing..."
    kill -9 $SERVER_PID 2>/dev/null || true
    exit 1
else
    echo "Server shut down cleanly!"
fi

echo "Verifying dedicated server log invariants..."

if ! grep -q "Registered optional S2C payload muslimqol:classification_sync (protocol version 1)" server_console.log; then
    echo "ERROR: Missing S2C classification_sync payload registration in server_console.log"
    exit 1
fi
echo "  [PASS] S2C classification_sync payload registered (protocol version 1, optional)"

if ! grep -q "Applied .* datapack classification keys and .* user overrides transactionally" server_console.log; then
    echo "ERROR: Missing FoodClassificationReloadListener transactional apply log in server_console.log"
    exit 1
fi
echo "  [PASS] Server datapack reload listener applied classifications transactionally"

if ! grep -q "MuslimQoL Status:" server_console.log; then
    echo "ERROR: Missing '/muslimqol status' output in server_console.log"
    exit 1
fi
echo "  [PASS] '/muslimqol status' command executed on dedicated server"

if ! grep -q "Item: minecraft:porkchop | Resolved: RESTRICTED" server_console.log; then
    echo "ERROR: Missing '/muslimqol classify minecraft:porkchop' output in server_console.log"
    exit 1
fi
echo "  [PASS] '/muslimqol classify' command executed on dedicated server"

if ! grep -q "MuslimQoL configuration and overrides reloaded successfully." server_console.log; then
    echo "ERROR: Missing '/muslimqol reload' confirmation in server_console.log"
    exit 1
fi
echo "  [PASS] '/muslimqol reload' command executed on dedicated server"

if grep -E -q "ClassNotFoundException|NoClassDefFoundError|io\.github\.muslimqol\.client\.(ClientInit|ClientClassificationSyncHandler|FoodOverlayRenderer|FoodTooltipHandler|FoodClassificationTooltipFormatter)" server_console.log; then
    echo "ERROR: Client-only class reference or classloading error detected on dedicated server!"
    exit 1
fi
echo "  [PASS] Zero client-only classes or classloading errors on dedicated server"

echo "Dedicated server test completed successfully."
