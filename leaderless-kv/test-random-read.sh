#!/bin/bash

# Test script to demonstrate random read node selection
# This script writes a value and then performs multiple reads to show
# that different nodes are being selected randomly

echo "=========================================="
echo "Random Read Node Selection Test"
echo "=========================================="
echo ""

# Configuration
NODE_1="http://localhost:8090"
NODE_2="http://localhost:8091"
NODE_3="http://localhost:8092"
NODE_4="http://localhost:8093"
NODE_5="http://localhost:8094"

# Check if all nodes are running
echo "Checking cluster health..."
for PORT in 8090 8091 8092 8093 8094; do
    if curl -s "http://localhost:$PORT/test/health" > /dev/null; then
        echo "✓ Node at port $PORT is running"
    else
        echo "✗ Node at port $PORT is NOT running"
        echo "Please start all 5 nodes first"
        exit 1
    fi
done
echo ""

# Write a test value to Node 1
echo "Writing test value to Node 1..."
WRITE_RESPONSE=$(curl -s -X POST "${NODE_1}/api/kv/set?key=random_test&value=hello_world")
echo "Write response: $WRITE_RESPONSE"
echo ""

# Wait for replication to complete
echo "Waiting 2 seconds for replication to complete..."
sleep 2
echo ""

# Perform 20 reads and show which nodes are accessed
echo "Performing 20 reads with random node selection..."
echo "Watch the server logs to see which nodes are selected!"
echo ""

for i in {1..20}; do
    # Send read request to any node (let's use Node 2)
    RESPONSE=$(curl -s "${NODE_2}/api/kv/get?key=random_test")
    VERSION=$(echo $RESPONSE | grep -o '"version":[0-9]*' | cut -d':' -f2)
    echo "Read #$i: version=$VERSION"
    sleep 0.5
done

echo ""
echo "=========================================="
echo "Test completed!"
echo "=========================================="
echo ""
echo "Check the server logs to see random node selection in action."
echo "Look for log messages like:"
echo "  'Reading key=random_test from LOCAL node'"
echo "  'Reading key=random_test from PEER node: http://localhost:XXXX'"
echo ""
echo "Each read should randomly select from all 5 nodes in the cluster."