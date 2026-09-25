#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

FIFO="/tmp/server_fifo_$$"
rm -f "$FIFO"
mkfifo "$FIFO"

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

if [ "$SUCCESS" = true ]; then
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
    else
        echo "Server shut down cleanly!"
    fi
else
    echo "Server did not reach ready state in time."
    kill -9 $SERVER_PID 2>/dev/null || true
    exit 1
fi

exec 3>&-
rm -f "$FIFO"
echo "Dedicated server test completed successfully."
