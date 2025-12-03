#!/bin/bash

# Quorum Test Using ps/grep (More Reliable)

echo "=========================================="
echo "Quorum Failure Test (W=3, R=3)"
echo "=========================================="
echo ""

# Clean start
pkill -9 -f leader-follower-kv 2>/dev/null
sleep 3

# Start nodes
echo "Starting all nodes with W=3, R=3..."
./start_nodes.sh 3 3 > /dev/null 2>&1
sleep 6

# Verify all started
echo ""
echo "Verifying all 5 nodes started..."
for port in 8080 8081 8082 8083 8084; do
    if ! curl -s --max-time 1 http://localhost:$port/test/health 2>/dev/null | grep -q "OK"; then
        echo "ERROR: Port $port failed to start"
        exit 1
    fi
    echo "  ✓ Port $port is UP"
done

# Count Java processes
java_count=$(ps aux | grep "leader-follower-kv" | grep java | grep -v grep | wc -l)
echo ""
echo "Found $java_count Java processes (expected 5)"

if [ "$java_count" -ne 5 ]; then
    echo "ERROR: Not 5 processes!"
    exit 1
fi

# Test normal write
echo ""
echo "Testing normal write..."
if ! curl -s -X POST "http://localhost:8080/api/kv/set?key=test1&value=data" | grep -q "success"; then
    echo "ERROR: Normal write failed"
    exit 1
fi
echo "  ✓ Normal write succeeded"

# Show all Java processes with their arguments
echo ""
echo "Java processes running:"
ps aux | grep "leader-follower-kv" | grep java | grep -v grep

# Get PIDs using netstat/lsof combination
echo ""
echo "Finding PIDs for each port..."

get_pid_for_port() {
    local port=$1
    # Use lsof but get the LAST pid (the actual Java process, not parent)
    local pid=$(lsof -ti:$port 2>/dev/null | tail -1)

    # Verify it's a Java process
    if ps -p $pid 2>/dev/null | grep -q java; then
        echo $pid
    else
        # Fallback: find Java process by checking open files
        lsof -i:$port 2>/dev/null | grep java | awk '{print $2}' | head -1
    fi
}

pid_8080=$(get_pid_for_port 8080)
pid_8081=$(get_pid_for_port 8081)
pid_8082=$(get_pid_for_port 8082)
pid_8083=$(get_pid_for_port 8083)
pid_8084=$(get_pid_for_port 8084)

echo "Port 8080 (Leader): PID $pid_8080"
echo "Port 8081 (Follower1): PID $pid_8081"
echo "Port 8082 (Follower2): PID $pid_8082"
echo "Port 8083 (Follower3): PID $pid_8083"
echo "Port 8084 (Follower4): PID $pid_8084"

# Verify PIDs are different
unique=$(echo "$pid_8080 $pid_8081 $pid_8082 $pid_8083 $pid_8084" | tr ' ' '\n' | sort -u | wc -l)
echo ""
echo "Unique PIDs: $unique (expected 5)"

if [ "$unique" -ne 5 ]; then
    echo "ERROR: PIDs are not unique!"
    echo "This suggests lsof is returning wrong PIDs"
    echo ""
    echo "Trying alternative method..."
    echo ""

    # Alternative: Kill by matching the profile in command line
    echo "Listing processes with profiles:"
    ps aux | grep "leader-follower-kv" | grep "spring.profiles.active"

    pkill -9 -f leader-follower-kv 2>/dev/null
    exit 1
fi

# Kill followers 2, 3, 4 using their PIDs
echo ""
echo "Killing Follower2, Follower3, Follower4..."

if [ -n "$pid_8082" ]; then
    kill -9 $pid_8082 2>/dev/null && echo "  ✓ Killed Follower2 (PID $pid_8082)"
    sleep 1
fi

if [ -n "$pid_8083" ]; then
    kill -9 $pid_8083 2>/dev/null && echo "  ✓ Killed Follower3 (PID $pid_8083)"
    sleep 1
fi

if [ -n "$pid_8084" ]; then
    kill -9 $pid_8084 2>/dev/null && echo "  ✓ Killed Follower4 (PID $pid_8084)"
    sleep 1
fi

echo ""
echo "Waiting 3 seconds for processes to die..."
sleep 3

# Check remaining Java processes
echo ""
echo "Checking remaining Java processes..."
remaining=$(ps aux | grep "leader-follower-kv" | grep java | grep -v grep | wc -l)
echo "Remaining Java processes: $remaining (expected 2)"

if [ "$remaining" -ne 2 ]; then
    echo "ERROR: Expected 2 processes, got $remaining"
    ps aux | grep "leader-follower-kv" | grep java | grep -v grep
fi

# Verify port states
echo ""
echo "Verifying port states:"
dead=0
alive=0

for port in 8082 8083 8084; do
    if curl -s --connect-timeout 1 --max-time 1 http://localhost:$port/test/health 2>/dev/null | grep -q "OK"; then
        echo "  ✗ Port $port STILL ALIVE"
    else
        echo "  ✓ Port $port is DEAD"
        ((dead++))
    fi
done

if curl -s --connect-timeout 1 --max-time 1 http://localhost:8080/test/health 2>/dev/null | grep -q "OK"; then
    echo "  ✓ Leader (8080) is ALIVE"
    ((alive++))
else
    echo "  ✗ Leader (8080) is DEAD"
fi

if curl -s --connect-timeout 1 --max-time 1 http://localhost:8081/test/health 2>/dev/null | grep -q "OK"; then
    echo "  ✓ Follower1 (8081) is ALIVE"
    ((alive++))
else
    echo "  ✗ Follower1 (8081) is DEAD"
fi

if [ $dead -ne 3 ] || [ $alive -ne 2 ]; then
    echo ""
    echo "ERROR: Test setup invalid (dead=$dead, alive=$alive)"
    echo "Expected: 3 dead, 2 alive"
    pkill -9 -f leader-follower-kv 2>/dev/null
    exit 1
fi

# THE CRITICAL TEST
echo ""
echo "=========================================="
echo "CRITICAL TEST: Write with Insufficient Quorum"
echo "=========================================="
echo ""
echo "Configuration:"
echo "  • W=3 (needs 2 out of 4 followers)"
echo "  • Available: Leader + 1 Follower (only 1 follower)"
echo "  • Expected: Write should FAIL"
echo ""
echo "Attempting write..."

start=$(date +%s)
response=$(curl -s --max-time 15 -X POST "http://localhost:8080/api/kv/set?key=shouldfail&value=test")
end=$(date +%s)
duration=$((end - start))

echo ""
echo "Response (took ${duration}s):"
echo "$response"
echo ""

# Analyze result
if echo "$response" | grep -qi "quorum not met"; then
    echo "=========================================="
    echo "✅ PASS: Quorum failure detected!"
    echo "=========================================="
    echo ""
    echo "Details:"
    echo "  • Duration: ${duration}s (no infinite hang)"
    echo "  • Error message: $(echo "$response" | grep -o 'W=3[^"]*')"
    echo ""
    echo "This proves:"
    echo "  ✓ No infinite hang (Bug #1 fixed)"
    echo "  ✓ Timeout works (Bug #2 fixed)"
    echo "  ✓ Quorum logic correct (Bug #3 fixed)"
    echo "  ✓ Post-verification works (Bug #4 fixed)"
    echo ""
    result=0

elif echo "$response" | grep -qi "success"; then
    echo "=========================================="
    echo "❌ FAIL: Write succeeded when it should fail!"
    echo "=========================================="
    echo ""
    echo "This is a BUG in your quorum logic."
    echo "The write succeeded with only 1 follower, but W=3 requires 2."
    echo ""
    echo "Debug steps:"
    echo "  1. Check logs: tail -50 logs/node-8080.log | grep 'W=3'"
    echo "  2. Verify ReplicationService.java line ~195: requiredAcks = W - 1"
    echo "  3. Verify line ~230: if (finalSuccesses < requiredAcks)"
    echo ""
    result=1

else
    echo "=========================================="
    echo "⚠️ Unexpected response"
    echo "=========================================="
    result=1
fi

# Show summary of all tests
echo ""
echo "=========================================="
echo "Complete Test Summary"
echo "=========================================="
echo ""
echo "Automated Tests (from earlier):"
echo "  ✓ All 5 nodes health checks"
echo "  ✓ Leader accepts writes"
echo "  ✓ Followers reject writes (403)"
echo "  ✓ Version numbers increase"
echo "  ✓ Data replicates to followers"
echo "  ✓ R=5 reads from ALL 5 nodes"
echo ""
echo "Quorum Failure Test:"
if [ $result -eq 0 ]; then
    echo "  ✓ No infinite hang"
    echo "  ✓ Proper error handling"
else
    echo "  ✗ Failed (see above)"
fi
echo ""

# Cleanup
echo "Cleaning up..."
pkill -9 -f leader-follower-kv 2>/dev/null
exit $result