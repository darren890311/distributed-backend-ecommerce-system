#!/bin/bash

# Improved Test Script with Better JSON Parsing

LEADER="http://localhost:8080"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

pass_count=0
fail_count=0

function test_passed() {
    echo -e "${GREEN}✓ PASS${NC}: $1"
    ((pass_count++))
}

function test_failed() {
    echo -e "${RED}✗ FAIL${NC}: $1"
    ((fail_count++))
}

function test_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

echo "=========================================="
echo "Leader-Follower KV Store - Critical Tests"
echo "=========================================="
echo ""

# Test 1: All Nodes Running
echo "Test 1: Health Checks"
echo "----------------------------------------"
all_healthy=true
for port in 8080 8081 8082 8083 8084; do
    if curl -s http://localhost:$port/test/health 2>/dev/null | grep -q "OK"; then
        test_passed "Node $port is healthy"
    else
        test_failed "Node $port is down"
        all_healthy=false
    fi
done
echo ""

if [ "$all_healthy" = false ]; then
    echo -e "${RED}ERROR: Not all nodes are running!${NC}"
    echo "Start all nodes first, then run tests."
    exit 1
fi

# Test 2: Write to Leader
echo "Test 2: Leader Accepts Writes"
echo "----------------------------------------"
response=$(curl -s -X POST "$LEADER/api/kv/set?key=test1&value=hello")
if echo "$response" | grep -q "success"; then
    version=$(echo "$response" | grep -o '"version":[0-9]*' | cut -d':' -f2)
    test_passed "Leader accepted write (version=$version)"
else
    test_failed "Leader rejected write: $response"
fi
echo ""

# Test 3: Read from Leader
echo "Test 3: Read from Leader"
echo "----------------------------------------"
response=$(curl -s "$LEADER/api/kv/get?key=test1")
if echo "$response" | grep -q "hello"; then
    test_passed "Read returned correct value"
else
    test_failed "Read failed: $response"
fi
echo ""

# Test 4: Follower Rejects Writes
echo "Test 4: Follower Rejects Writes"
echo "----------------------------------------"
status=$(curl -s -o /dev/null -w "%{http_code}" -X POST "http://localhost:8081/api/kv/set?key=test&value=fail")
if [ "$status" = "403" ]; then
    test_passed "Follower correctly rejected write (403)"
else
    test_failed "Follower did not reject write (got $status)"
fi
echo ""

# Test 5: Version Numbers Increase
echo "Test 5: Version Numbers Increase"
echo "----------------------------------------"
r1=$(curl -s -X POST "$LEADER/api/kv/set?key=ver1&value=a")
v1=$(echo "$r1" | grep -o '"version":[0-9]*' | cut -d':' -f2)

r2=$(curl -s -X POST "$LEADER/api/kv/set?key=ver2&value=b")
v2=$(echo "$r2" | grep -o '"version":[0-9]*' | cut -d':' -f2)

if [ -n "$v1" ] && [ -n "$v2" ] && [ "$v2" -gt "$v1" ]; then
    test_passed "Versions increase (v1=$v1, v2=$v2)"
else
    test_failed "Versions not increasing (v1=$v1, v2=$v2)"
fi
echo ""

# Test 6: Replication to Followers
echo "Test 6: Data Replicates to Followers (W=5)"
echo "----------------------------------------"
test_info "Writing to leader..."
curl -s -X POST "$LEADER/api/kv/set?key=replicate&value=testdata" > /dev/null

test_info "Waiting 2 seconds for replication..."
sleep 2

for port in 8081 8082 8083 8084; do
    response=$(curl -s "http://localhost:$port/test/local_read?key=replicate")
    if echo "$response" | grep -q "testdata"; then
        test_passed "Follower $port has data"
    else
        test_failed "Follower $port missing data"
    fi
done
echo ""

# Test 7: Write Timing (Check W setting)
echo "Test 7: Write Latency Check"
echo "----------------------------------------"
test_info "Testing write latency (check logs to confirm W setting)..."

start=$(date +%s%3N)
curl -s -X POST "$LEADER/api/kv/set?key=timing&value=test" > /dev/null
end=$(date +%s%3N)
duration=$((end - start))

test_info "Write took ${duration}ms"

if [ "$duration" -gt 700 ] && [ "$duration" -lt 1100 ]; then
    test_passed "Latency suggests W=5 (sequential: ~800ms)"
elif [ "$duration" -lt 100 ]; then
    test_info "Fast write (~${duration}ms) suggests W=1 (async)"
elif [ "$duration" -gt 300 ] && [ "$duration" -lt 700 ]; then
    test_info "Medium write (~${duration}ms) suggests W=3 (quorum)"
else
    test_info "Write took ${duration}ms"
fi
echo ""

# Critical Test 8: Check R=5 Implementation
echo "Test 8: R=5 Reads from ALL Nodes (CRITICAL)"
echo "----------------------------------------"
test_info "This requires checking server logs!"
test_info "After running a read with R=5, check logs for:"
test_info '  "R=5: Reading from ALL 5 nodes"'
test_info '  "R=5: Read from 5/5 nodes"'
echo ""
echo -e "${YELLOW}⚠ Manual check required:${NC}"
echo "1. Stop all nodes"
echo "2. Restart with W=1, R=5 config"
echo "3. Write: curl -X POST 'http://localhost:8080/api/kv/set?key=r5test&value=data'"
echo "4. Read: curl 'http://localhost:8080/api/kv/get?key=r5test'"
echo "5. Check leader logs for 'R=5: Reading from ALL 5 nodes'"
echo ""

# Critical Test 9: Quorum Failure Handling
echo "Test 9: Quorum Timeout (No Infinite Hang)"
echo "----------------------------------------"
echo -e "${YELLOW}⚠ Manual test required:${NC}"
echo "1. Restart all nodes with W=3, R=3"
echo "2. Kill 3 of 4 followers"
echo "3. Try write: curl -X POST 'http://localhost:8080/api/kv/set?key=fail&value=test'"
echo "4. Should return error after ~10 seconds (NOT hang forever)"
echo ""

# Summary
echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo -e "${GREEN}Passed: $pass_count${NC}"
echo -e "${RED}Failed: $fail_count${NC}"
echo ""

if [ $fail_count -eq 0 ]; then
    echo -e "${GREEN}✓ All automated tests passed!${NC}"
    echo ""
    echo "Next steps:"
    echo "1. Test W=1, R=5 strategy (manual)"
    echo "2. Test W=3, R=3 strategy (manual)"
    echo "3. Test quorum failure handling (manual)"
    echo ""
    echo "If those pass, your implementation is correct! ✨"
    exit 0
else
    echo -e "${RED}✗ Some tests failed${NC}"
    exit 1
fi