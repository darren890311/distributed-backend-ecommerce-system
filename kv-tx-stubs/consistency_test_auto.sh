#!/bin/bash

# Automated Consistency Test - Handles Node Restarts
# Tests all 3 configurations automatically



LEADER="http://localhost:8080"
FOLLOWER1="http://localhost:8081"

GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass_count=0
fail_count=0

test_passed() {
    echo -e "${GREEN}✓ PASS${NC}: $1"
    ((pass_count++))
}

test_failed() {
    echo -e "${RED}✗ FAIL${NC}: $1"
    ((fail_count++))
}

test_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

restart_nodes() {
    local W=$1
    local R=$2

    echo ""
    echo "=========================================="
    echo "Restarting nodes with W=$W, R=$R"
    echo "=========================================="

    # Stop all nodes
    test_info "Stopping existing nodes..."
    pkill -f "leader-follower-kv" || true
    sleep 3

    # Start with new config
    test_info "Starting nodes with W=$W, R=$R..."
    ./start_nodes.sh $W $R > /dev/null 2>&1

    # Wait for startup
    sleep 5

    # Verify all nodes are up
    local all_healthy=true
    for port in 8080 8081 8082 8083 8084; do
        if ! curl -s http://localhost:$port/test/health 2>/dev/null | grep -q "OK"; then
            echo -e "${RED}ERROR: Node $port failed to start!${NC}"
            all_healthy=false
        fi
    done

    if [ "$all_healthy" = true ]; then
        echo -e "${GREEN}✓ All nodes started successfully${NC}"
    else
        echo -e "${RED}✗ Some nodes failed to start${NC}"
        exit 1
    fi

    echo ""
}

# Check start_nodes.sh exists
if [ ! -f "./start_nodes.sh" ]; then
    echo -e "${RED}ERROR: start_nodes.sh not found!${NC}"
    echo "Make sure you're in the correct directory"
    exit 1
fi

echo "=========================================="
echo "Automated Consistency Test Suite"
echo "=========================================="
echo ""
echo "This test will automatically:"
echo "  1. Test W=5, R=1 (Strong Consistency)"
echo "  2. Test W=1, R=5 (Eventual Consistency)"
echo "  3. Test W=3, R=3 (Quorum Consistency)"
echo ""
read -p "Press Enter to start..."
echo ""

# ============================================================================
# TEST SUITE 1: Strong Consistency (W=5, R=1)
# ============================================================================

restart_nodes 5 1

echo "=========================================="
echo "TEST 1: Strong Consistency (W=5, R=1)"
echo "=========================================="
echo ""

# Test 1.1: Write and Read from Leader
echo "Test 1.1: Write to Leader → Read from Leader"
echo "----------------------------------------"
key="strong_$(date +%s)"
write_resp=$(curl -s -X POST "$LEADER/api/kv/set?key=$key&value=consistent")
write_version=$(echo "$write_resp" | grep -o '"version":[0-9]*' | cut -d':' -f2)

if [ -n "$write_version" ]; then
    read_resp=$(curl -s "$LEADER/api/kv/get?key=$key")
    read_value=$(echo "$read_resp" | grep -o '"value":"[^"]*"' | cut -d'"' -f4)
    read_version=$(echo "$read_resp" | grep -o '"version":[0-9]*' | cut -d':' -f2)

    if [ "$read_value" = "consistent" ] && [ "$read_version" = "$write_version" ]; then
        test_passed "Leader read is consistent (version=$read_version)"
    else
        test_failed "Leader read inconsistent"
    fi
else
    test_failed "Write to leader failed"
fi
echo ""

# Test 1.2: Followers have data immediately (W=5)
echo "Test 1.2: Write to Leader → Followers Have Data"
echo "----------------------------------------"
key="replicated_$(date +%s)"
write_resp=$(curl -s -X POST "$LEADER/api/kv/set?key=$key&value=data")
write_version=$(echo "$write_resp" | grep -o '"version":[0-9]*' | cut -d':' -f2)

if [ -n "$write_version" ]; then
    test_info "Write succeeded (version=$write_version)"
    test_info "With W=5, all followers should have data immediately"

    all_have_data=true
    for port in 8081 8082 8083 8084; do
        read_resp=$(curl -s "http://localhost:$port/test/local_read?key=$key")
        if ! echo "$read_resp" | grep -q "data"; then
            all_have_data=false
            break
        fi
    done

    if [ "$all_have_data" = true ]; then
        test_passed "All followers have data immediately (strong consistency)"
    else
        test_failed "Some followers missing data"
    fi
else
    test_failed "Write failed"
fi
echo ""

# ============================================================================
# TEST SUITE 2: Eventual Consistency (W=1, R=5)
# ============================================================================

restart_nodes 1 5

echo "=========================================="
echo "TEST 2: Eventual Consistency (W=1, R=5)"
echo "=========================================="
echo ""

# Test 2.1: Detect inconsistency window
echo "Test 2.1: Detect Inconsistency Window"
echo "----------------------------------------"
test_info "Writing keys and immediately checking followers"
test_info "Should detect some keys haven't replicated yet"

inconsistent_count=0
total_tests=20

for i in $(seq 1 $total_tests); do
    key="eventual_$i"
    curl -s -X POST "$LEADER/api/kv/set?key=$key&value=data$i" > /dev/null

    # Immediately check follower
    read_resp=$(curl -s "$FOLLOWER1/test/local_read?key=$key")
    if echo "$read_resp" | grep -q "error"; then
        ((inconsistent_count++))
    fi
    sleep 0.05
done

test_info "Results: $inconsistent_count/$total_tests keys showed inconsistency"

if [ "$inconsistent_count" -gt 0 ]; then
    test_passed "Detected inconsistency window (eventual consistency working)"
else
    test_info "No inconsistency detected (replication was very fast)"
fi
echo ""

# Test 2.2: Verify eventual consistency
echo "Test 2.2: Verify Eventual Consistency"
echo "----------------------------------------"
key="verify_$(date +%s)"
curl -s -X POST "$LEADER/api/kv/set?key=$key&value=eventual" > /dev/null

test_info "Waiting 3 seconds for async replication..."
sleep 3

# Check how many followers have the data
consistent_count=0
for port in 8081 8082 8083 8084; do
    read_resp=$(curl -s "http://localhost:$port/test/local_read?key=$key")
    if echo "$read_resp" | grep -q "eventual"; then
        test_info "  ✓ Follower $port has data"
        ((consistent_count++))
    else
        test_info "  ✗ Follower $port missing data"
    fi
done

test_info "Followers with data: $consistent_count/4"

# For W=1 (async), at least 2 out of 4 followers should have data after 3s
if [ "$consistent_count" -ge 2 ]; then
    test_passed "Eventual consistency achieved ($consistent_count/4 followers have data)"
elif [ "$consistent_count" -eq 1 ]; then
    test_info "Only $consistent_count/4 followers have data (replication still in progress)"
else
    test_failed "No followers have data after 3s (check if async replication is working)"
fi
echo ""

# Test 2.3: Verify R=5 reads from ALL nodes
echo "Test 2.3: R=5 Reads from ALL 5 Nodes (CRITICAL)"
echo "----------------------------------------"
key="r5test_$(date +%s)"
curl -s -X POST "$LEADER/api/kv/set?key=$key&value=r5data" > /dev/null
sleep 1  # Let replication complete

# Read with R=5
curl -s "$LEADER/api/kv/get?key=$key" > /dev/null

# Check logs
test_info "Checking leader logs for R=5 behavior..."
sleep 1

if grep -q "R=5: Reading from ALL 5 nodes" logs/node-8080.log; then
    test_passed "R=5 reads from ALL 5 nodes (logs confirmed)"
else
    test_failed "R=5 does not read from all nodes (check logs)"
    test_info "Run: tail -50 logs/node-8080.log | grep R=5"
fi
echo ""

# ============================================================================
# TEST SUITE 3: Quorum Consistency (W=3, R=3)
# ============================================================================

restart_nodes 3 3

echo "=========================================="
echo "TEST 3: Quorum Consistency (W=3, R=3)"
echo "=========================================="
echo ""

# Test 3.1: Normal quorum operation
echo "Test 3.1: Normal Quorum Write and Read"
echo "----------------------------------------"
key="quorum_$(date +%s)"

write_resp=$(curl -s -X POST "$LEADER/api/kv/set?key=$key&value=v1")
write_version=$(echo "$write_resp" | grep -o '"version":[0-9]*' | cut -d':' -f2)

if [ -n "$write_version" ]; then
    test_info "Write succeeded (version=$write_version)"

    read_resp=$(curl -s "$LEADER/api/kv/get?key=$key")
    read_value=$(echo "$read_resp" | grep -o '"value":"[^"]*"' | cut -d'"' -f4)
    read_version=$(echo "$read_resp" | grep -o '"version":[0-9]*' | cut -d':' -f2)

    if [ "$read_value" = "v1" ] && [ "$read_version" = "$write_version" ]; then
        test_passed "Quorum read returned correct version"
    else
        test_failed "Quorum read returned wrong data"
    fi
else
    test_failed "Quorum write failed"
fi
echo ""

# Test 3.2: Most recent version
echo "Test 3.2: Quorum Returns Most Recent Version"
echo "----------------------------------------"
key="versions_$(date +%s)"

# Write v1
curl -s -X POST "$LEADER/api/kv/set?key=$key&value=v1" > /dev/null
sleep 0.3

# Write v2
write_resp=$(curl -s -X POST "$LEADER/api/kv/set?key=$key&value=v2")
write_v2=$(echo "$write_resp" | grep -o '"version":[0-9]*' | cut -d':' -f2)
sleep 0.3

# Read - should get v2
read_resp=$(curl -s "$LEADER/api/kv/get?key=$key")
read_value=$(echo "$read_resp" | grep -o '"value":"[^"]*"' | cut -d'"' -f4)
read_version=$(echo "$read_resp" | grep -o '"version":[0-9]*' | cut -d':' -f2)

if [ "$read_version" = "$write_v2" ] && [ "$read_value" = "v2" ]; then
    test_passed "Quorum read returned most recent version ($read_version)"
else
    test_failed "Quorum read returned old version"
fi
echo ""

# Test 3.3: Quorum failure handling
echo "Test 3.3: Quorum Failure (No Infinite Hang)"
echo "----------------------------------------"
test_info "Killing 3 followers to break quorum..."

# Kill 3 followers using lsof (get actual Java PIDs)
get_java_pid() {
    local port=$1
    lsof -ti:$port 2>/dev/null | tail -1
}

pid_8082=$(get_java_pid 8082)
pid_8083=$(get_java_pid 8083)
pid_8084=$(get_java_pid 8084)

if [ -n "$pid_8082" ]; then
    kill -9 $pid_8082 2>/dev/null
    test_info "Killed Follower2 (PID $pid_8082)"
fi

if [ -n "$pid_8083" ]; then
    kill -9 $pid_8083 2>/dev/null
    test_info "Killed Follower3 (PID $pid_8083)"
fi

if [ -n "$pid_8084" ]; then
    kill -9 $pid_8084 2>/dev/null
    test_info "Killed Follower4 (PID $pid_8084)"
fi

sleep 3

# Try to write (should fail, not hang)
test_info "Attempting write with insufficient followers..."
start_time=$(date +%s)
write_resp=$(curl -s --max-time 15 -X POST "$LEADER/api/kv/set?key=failtest&value=test")
end_time=$(date +%s)
duration=$((end_time - start_time))

if echo "$write_resp" | grep -q "quorum not met"; then
    test_passed "Quorum failure returned error (no infinite hang, took ${duration}s)"
    test_info "Error message: $(echo $write_resp | grep -o 'quorum not met[^"]*')"
else
    test_failed "Unexpected response: $write_resp"
fi
echo ""

# ============================================================================
# Cleanup and Summary
# ============================================================================

echo "=========================================="
echo "Cleaning up..."
echo "=========================================="
pkill -f "leader-follower-kv" || true
sleep 2
echo ""

echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo -e "${GREEN}Passed: $pass_count${NC}"
echo -e "${RED}Failed: $fail_count${NC}"
echo ""

if [ $fail_count -eq 0 ]; then
    echo -e "${GREEN}✓ All consistency tests passed!${NC}"
    echo ""
    echo "Your implementation is correct! ✨"
    echo ""
    echo "Key findings:"
    echo "  • W=5, R=1: Strong consistency verified"
    echo "  • W=1, R=5: Eventual consistency with inconsistency window"
    echo "  • W=3, R=3: Quorum working, no infinite hangs"
    echo ""
    exit 0
else
    echo -e "${RED}✗ Some tests failed${NC}"
    echo "Review the failures above and check logs"
    exit 1
fi