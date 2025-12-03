#!/bin/bash

# Consistency Test for Leader-Follower KV Store
# Tests eventual consistency and consistency windows

LEADER="http://localhost:8080"
FOLLOWER1="http://localhost:8081"
FOLLOWER2="http://localhost:8082"
FOLLOWER3="http://localhost:8083"
FOLLOWER4="http://localhost:8084"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

echo "=========================================="
echo "Consistency Tests"
echo "=========================================="
echo ""

# Check all nodes are running
echo "Checking nodes are running..."
for port in 8080 8081 8082 8083 8084; do
    if ! curl -s http://localhost:$port/test/health | grep -q "OK"; then
        echo -e "${RED}ERROR: Node $port is not running!${NC}"
        exit 1
    fi
done
echo -e "${GREEN}✓ All nodes are running${NC}"
echo ""

# ============================================================================
# TEST 1: Strong Consistency (W=5, R=1)
# ============================================================================
echo "=========================================="
echo "TEST 1: Strong Consistency (W=5, R=1)"
echo "=========================================="
echo ""
test_info "This test requires W=5, R=1 configuration"
test_info "Restart nodes with: ./start_nodes.sh 5 1"
echo ""
read -p "Press Enter when nodes are ready with W=5, R=1..."
echo ""

# 1.1: Write to Leader, Read from Leader (should be consistent)
echo "Test 1.1: Write to Leader → Read from Leader"
echo "----------------------------------------"
key="strong_$(date +%s)"
write_resp=$(curl -s -X POST "$LEADER/api/kv/set?key=$key&value=consistent")
write_version=$(echo "$write_resp" | grep -o '"version":[0-9]*' | cut -d':' -f2)

if [ -n "$write_version" ]; then
    test_info "Write succeeded: version=$write_version"

    # Read from leader
    read_resp=$(curl -s "$LEADER/api/kv/get?key=$key")
    read_value=$(echo "$read_resp" | grep -o '"value":"[^"]*"' | cut -d'"' -f4)
    read_version=$(echo "$read_resp" | grep -o '"version":[0-9]*' | cut -d':' -f2)

    if [ "$read_value" = "consistent" ] && [ "$read_version" = "$write_version" ]; then
        test_passed "Leader read is consistent (version=$read_version)"
    else
        test_failed "Leader read inconsistent (got: $read_value, version: $read_version)"
    fi
else
    test_failed "Write failed"
fi
echo ""

# 1.2: Write to Leader, Read from Follower (should be consistent after W=5)
echo "Test 1.2: Write to Leader → Read from Follower"
echo "----------------------------------------"
key="follower_$(date +%s)"
write_resp=$(curl -s -X POST "$LEADER/api/kv/set?key=$key&value=replicated")
write_version=$(echo "$write_resp" | grep -o '"version":[0-9]*' | cut -d':' -f2)

if [ -n "$write_version" ]; then
    test_info "Write succeeded: version=$write_version"
    test_info "With W=5, followers should have data immediately"

    # Read from follower using local_read
    read_resp=$(curl -s "$FOLLOWER1/test/local_read?key=$key")
    read_value=$(echo "$read_resp" | grep -o '"value":"[^"]*"' | cut -d'"' -f4)
    read_version=$(echo "$read_resp" | grep -o '"version":[0-9]*' | cut -d':' -f2)

    if [ "$read_value" = "replicated" ] && [ "$read_version" = "$write_version" ]; then
        test_passed "Follower has consistent data (version=$read_version)"
    else
        test_failed "Follower data inconsistent (got: $read_value, version: $read_version)"
    fi
else
    test_failed "Write failed"
fi
echo ""

# ============================================================================
# TEST 2: Eventual Consistency Window (W=1, R=5)
# ============================================================================
echo "=========================================="
echo "TEST 2: Eventual Consistency Window (W=1)"
echo "=========================================="
echo ""
test_info "This test requires W=1, R=5 configuration"
test_info "Restart nodes with: ./start_nodes.sh 1 5"
echo ""
read -p "Press Enter when nodes are ready with W=1, R=5..."
echo ""

# 2.1: Detect inconsistency window
echo "Test 2.1: Detect Inconsistency Window (W=1)"
echo "----------------------------------------"
test_info "Writing multiple keys rapidly and checking followers immediately"
test_info "Should catch some keys that haven't replicated yet"

inconsistent_count=0
consistent_count=0
total_tests=20

for i in $(seq 1 $total_tests); do
    key="eventual_$i"

    # Write to leader
    curl -s -X POST "$LEADER/api/kv/set?key=$key&value=data$i" > /dev/null

    # Immediately check follower (before async replication completes)
    read_resp=$(curl -s "$FOLLOWER1/test/local_read?key=$key")

    if echo "$read_resp" | grep -q "error"; then
        # Key not found = inconsistent (replication hasn't happened yet)
        ((inconsistent_count++))
    else
        ((consistent_count++))
    fi

    # Small delay between tests
    sleep 0.05
done

test_info "Results: $inconsistent_count inconsistent, $consistent_count consistent (out of $total_tests)"

if [ "$inconsistent_count" -gt 0 ]; then
    test_passed "Detected inconsistency window ($inconsistent_count/$total_tests keys not immediately replicated)"
else
    test_info "All keys were consistent (replication was faster than detection)"
fi
echo ""

# 2.2: Verify eventual consistency
echo "Test 2.2: Verify Eventual Consistency"
echo "----------------------------------------"
test_info "Waiting for async replication to complete..."
sleep 2

key="eventual_verify_$(date +%s)"
curl -s -X POST "$LEADER/api/kv/set?key=$key&value=eventual" > /dev/null

test_info "Waiting 2 seconds for replication..."
sleep 2

# Check all followers
all_consistent=true
for port in 8081 8082 8083 8084; do
    read_resp=$(curl -s "http://localhost:$port/test/local_read?key=$key")
    if echo "$read_resp" | grep -q "eventual"; then
        test_info "  ✓ Follower $port has data"
    else
        test_info "  ✗ Follower $port missing data"
        all_consistent=false
    fi
done

if [ "$all_consistent" = true ]; then
    test_passed "Eventual consistency achieved (all followers have data after 2s)"
else
    test_failed "Some followers still missing data after 2s"
fi
echo ""

# ============================================================================
# TEST 3: High-Load Consistency Test
# ============================================================================
echo "=========================================="
echo "TEST 3: High-Load Consistency Test"
echo "=========================================="
echo ""
test_info "Writing 50 keys rapidly to stress test replication"

# Rapid writes
for i in $(seq 1 50); do
    curl -s -X POST "$LEADER/api/kv/set?key=load_$i&value=data$i" > /dev/null &
done

test_info "Waiting for all writes to complete..."
wait
sleep 2  # Wait for replication

test_info "Checking consistency across all nodes..."

# Check random sample
sample_keys="load_1 load_10 load_25 load_40 load_50"
all_match=true

for key in $sample_keys; do
    leader_resp=$(curl -s "$LEADER/test/local_read?key=$key")
    leader_version=$(echo "$leader_resp" | grep -o '"version":[0-9]*' | cut -d':' -f2)

    for port in 8081 8082 8083 8084; do
        follower_resp=$(curl -s "http://localhost:$port/test/local_read?key=$key")
        follower_version=$(echo "$follower_resp" | grep -o '"version":[0-9]*' | cut -d':' -f2)

        if [ "$leader_version" != "$follower_version" ]; then
            test_info "  Version mismatch: $key leader=$leader_version, follower($port)=$follower_version"
            all_match=false
        fi
    done
done

if [ "$all_match" = true ]; then
    test_passed "All sampled keys have consistent versions across nodes"
else
    test_failed "Some version mismatches detected"
fi
echo ""

# Summary
echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo -e "${GREEN}Passed: $pass_count${NC}"
echo -e "${RED}Failed: $fail_count${NC}"
echo ""

if [ $fail_count -eq 0 ]; then
    echo -e "${GREEN}✓ All consistency tests passed!${NC}"
    exit 0
else
    echo -e "${RED}✗ Some tests failed${NC}"
    exit 1
fi