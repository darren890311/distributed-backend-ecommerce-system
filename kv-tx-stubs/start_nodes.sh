#!/bin/bash

# Start All Nodes - Leader-Follower KV Store
# Usage: ./start_nodes.sh [W] [R]
# Example: ./start_nodes.sh 5 1  (W=5, R=1)
#          ./start_nodes.sh 1 5  (W=1, R=5)
#          ./start_nodes.sh 3 3  (W=3, R=3)

set -e  # Exit on error

# Default values
W=${1:-5}
R=${2:-1}

echo "=========================================="
echo "Starting Leader-Follower KV Store"
echo "Configuration: W=$W, R=$R"
echo "=========================================="
echo ""

# Check JAR exists
if [ ! -f "target/leader-follower-kv-0.0.1-SNAPSHOT.jar" ]; then
    echo " ERROR: JAR file not found!"
    echo "Run 'mvn clean package -DskipTests' first"
    exit 1
fi

# Create logs directory
mkdir -p logs

# Kill any existing nodes
echo "Stopping any existing nodes..."
pkill -f "leader-follower-kv" || true
sleep 2

# Function to start a node
start_node() {
    local PORT=$1
    local PROFILE=$2

    echo "Starting $PROFILE on port $PORT..."

    java -jar target/leader-follower-kv-0.0.1-SNAPSHOT.jar \
      --spring.profiles.active=$PROFILE \
      --kvstore.write-quorum=$W \
      --kvstore.read-quorum=$R \
      > logs/node-$PORT.log 2>&1 &

    local PID=$!
    echo "  ✓ Started (PID: $PID)"
    sleep 2
}

# Start all nodes
echo ""
start_node 8080 leader
start_node 8081 follower1
start_node 8082 follower2
start_node 8083 follower3
start_node 8084 follower4

echo ""
echo "=========================================="
echo "Waiting for nodes to initialize..."
echo "=========================================="
sleep 5

# Health check
echo ""
echo "Health Check:"
echo "----------------------------------------"
all_healthy=true
for port in 8080 8081 8082 8083 8084; do
    response=$(curl -s http://localhost:$port/test/health 2>/dev/null || echo "FAIL")
    if echo "$response" | grep -q "OK"; then
        echo "  ✓ Node $port is healthy"
    else
        echo "  ✗ Node $port failed to start"
        all_healthy=false
    fi
done

echo ""
if [ "$all_healthy" = true ]; then
    echo "=========================================="
    echo "✓ All nodes started successfully!"
    echo "=========================================="
    echo ""
    echo "Configuration: W=$W, R=$R"
    echo ""
    echo "View logs:"
    echo "  tail -f logs/node-8080.log  # Leader"
    echo "  tail -f logs/node-8081.log  # Follower 1"
    echo ""
    echo "Stop all nodes:"
    echo "  pkill -f leader-follower-kv"
    echo ""
    echo "Test commands:"
    echo "  curl -X POST 'http://localhost:8080/api/kv/set?key=test&value=hello'"
    echo "  curl 'http://localhost:8080/api/kv/get?key=test'"
    echo ""
else
    echo " Some nodes failed to start!"
    echo "Check logs for errors:"
    echo "  tail -50 logs/node-8080.log"
    exit 1
fi