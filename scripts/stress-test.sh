#!/bin/bash
#
# Stress Test Script for Job Engine
# Runs parallel requests against all execution modes simultaneously
#
# Usage: ./stress-test.sh [count] [base_url]
#   count    - Number of jobs per mode (default: 100)
#   base_url - API base URL (default: http://localhost:8080)
#

set -e

# Configuration
COUNT=${1:-100}
BASE_URL=${2:-http://localhost:8080}
SEQ_COUNT=$COUNT  # Same count for all modes (fair comparison)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
HEALTH_ENDPOINT="$BASE_URL/actuator/health"
MAX_WAIT_SECONDS=60

echo "=========================================="
echo "  Job Engine Stress Test"
echo "=========================================="
echo "Jobs per mode: $COUNT (total: $((COUNT * 3)))"
echo "Target: $BASE_URL"
echo ""

# ============================================
# Health Check & Auto-Start
# ============================================
check_health() {
    curl -s -o /dev/null -w "%{http_code}" "$HEALTH_ENDPOINT" 2>/dev/null
}

wait_for_startup() {
    local elapsed=0
    echo -n "Waiting for API to be ready"
    while [ $elapsed -lt $MAX_WAIT_SECONDS ]; do
        if [ "$(check_health)" = "200" ]; then
            echo " OK!"
            return 0
        fi
        echo -n "."
        sleep 2
        elapsed=$((elapsed + 2))
    done
    echo " TIMEOUT!"
    echo "ERROR: API did not start within $MAX_WAIT_SECONDS seconds"
    exit 1
}

echo "[0/6] Checking if API is running..."
if [ "$(check_health)" = "200" ]; then
    echo "     API is already running!"
else
    echo "     API is not running. Starting with gradlew bootRun..."
    cd "$PROJECT_DIR"
    
    # Start in background and redirect output to log file
    nohup ./gradlew bootRun --no-daemon > /tmp/job-engine.log 2>&1 &
    BOOT_PID=$!
    echo "     Started with PID: $BOOT_PID"
    echo "     Log: /tmp/job-engine.log"
    
    # Wait for startup
    wait_for_startup
    
    cd - > /dev/null
fi

echo ""

# ============================================
# Stress Test
# ============================================

# Clear previous jobs AND metrics
echo "[1/7] Clearing previous jobs and metrics..."
curl -s -X DELETE "$BASE_URL/api/metrics" > /dev/null

# Array to store curl PIDs (to avoid waiting for gradlew bootRun)
CURL_PIDS=()

# Function to submit jobs for a specific mode
submit_jobs() {
    local mode=$1
    local count=$2
    local prefix=$3
    
    for i in $(seq 1 $count); do
        curl -s -X POST "$BASE_URL/api/jobs" \
            -H "Content-Type: application/json" \
            -d "{\"name\":\"$prefix-$i\",\"payload\":\"stress-data-$i\",\"executionMode\":\"$mode\"}" \
            > /dev/null &
        CURL_PIDS+=($!)
    done
}

echo "[2/6] Submitting $COUNT ASYNC jobs..."
submit_jobs "ASYNC" $COUNT "stress-async"

echo "[3/6] Submitting $COUNT THREAD_POOL jobs..."
submit_jobs "THREAD_POOL" $COUNT "stress-pool"

echo "[4/6] Submitting $SEQ_COUNT SEQUENTIAL jobs..."
submit_jobs "SEQUENTIAL" $SEQ_COUNT "stress-seq"

echo "[5/7] Waiting for all HTTP requests to complete..."
wait "${CURL_PIDS[@]}"

echo ""
echo "[6/7] Waiting for all jobs to finish processing..."

# Function to get total active jobs
get_active_count() {
    local response=$(curl -s "$BASE_URL/api/metrics/active" 2>/dev/null)
    # Sum all active counts from JSON response
    echo "$response" | grep -oE '"[A-Z_]+"\s*:\s*[0-9]+' | grep -oE '[0-9]+' | awk '{sum+=$1} END {print sum+0}'
}

# Wait for all jobs to complete (active count = 0)
MAX_JOB_WAIT=120
elapsed=0
echo -n "     Processing jobs"
while true; do
    active=$(get_active_count)
    if [ "$active" = "0" ] || [ -z "$active" ]; then
        # Double check after a brief pause
        sleep 1
        active=$(get_active_count)
        if [ "$active" = "0" ] || [ -z "$active" ]; then
            echo " Done!"
            break
        fi
    fi
    
    if [ $elapsed -ge $MAX_JOB_WAIT ]; then
        echo " Timeout! ($active jobs still active)"
        break
    fi
    
    echo -n "."
    sleep 1
    elapsed=$((elapsed + 1))
done

echo ""
echo "[7/7] Fetching results..."
echo "=========================================="
echo "  Results"
echo "=========================================="
curl -s "$BASE_URL/api/metrics/compare" | python3 -m json.tool 2>/dev/null || \
    curl -s "$BASE_URL/api/metrics/compare"

echo ""
echo "=========================================="
echo "Done!"
echo ""
echo "Tips:"
echo "  - View logs: tail -f /tmp/job-engine.log"
echo "  - Stop API:  pkill -f 'gradlew bootRun'"
echo "  - Grafana:   http://localhost:3000"
echo "=========================================="
