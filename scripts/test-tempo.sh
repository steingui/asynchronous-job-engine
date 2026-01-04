#!/bin/bash
# ===========================================
# Continuous Stress Test Runner
# Executes stress-test.sh at regular intervals
# ===========================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Default values
INTERVAL=${1:-30}
JOBS=${2:-100}

# Validate inputs
if ! [[ "$INTERVAL" =~ ^[0-9]+$ ]] || [ "$INTERVAL" -lt 1 ]; then
    echo "Error: Interval must be a positive integer (seconds)"
    echo "Usage: $0 <interval_seconds> <jobs_per_mode>"
    echo "Example: $0 10 500"
    exit 1
fi

if ! [[ "$JOBS" =~ ^[0-9]+$ ]] || [ "$JOBS" -lt 1 ]; then
    echo "Error: Jobs must be a positive integer"
    echo "Usage: $0 <interval_seconds> <jobs_per_mode>"
    echo "Example: $0 10 500"
    exit 1
fi

echo "=========================================="
echo "  Continuous Stress Test"
echo "=========================================="
echo "Interval: ${INTERVAL}s"
echo "Jobs per cycle: ${JOBS} per mode ($(($JOBS * 3)) total)"
echo "Press Ctrl+C to stop"
echo "=========================================="
echo ""

CYCLE=1

# Trap Ctrl+C for clean exit
trap 'echo ""; echo "Stopped after $((CYCLE - 1)) cycles"; exit 0' INT

while true; do
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  Cycle #$CYCLE - $(date '+%H:%M:%S')"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    # Run stress test
    "$SCRIPT_DIR/stress-test.sh" "$JOBS"
    
    CYCLE=$((CYCLE + 1))
    
    echo ""
    echo "Next cycle in ${INTERVAL}s... (Ctrl+C to stop)"
    sleep "$INTERVAL"
    echo ""
done

