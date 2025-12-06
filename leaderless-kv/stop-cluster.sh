#!/bin/bash

# Stop script for leaderless KV cluster

echo "Stopping Leaderless KV Cluster..."
echo "================================="

# Read PIDs from file if it exists
if [ -f .cluster_pids ]; then
    echo "Stopping nodes using saved PIDs..."
    read -r NODE1_PID NODE2_PID NODE3_PID NODE4_PID NODE5_PID < .cluster_pids

    for pid in $NODE1_PID $NODE2_PID $NODE3_PID $NODE4_PID $NODE5_PID; do
        if kill -0 $pid 2>/dev/null; then
            echo "Stopping process $pid..."
            kill $pid
        fi
    done

    rm .cluster_pids
else
    echo "No .cluster_pids file found. Attempting to kill by port..."

    # Kill processes by port
    for port in 8081 8082 8083 8084 8085; do
        pid=$(lsof -ti:$port)
        if [ ! -z "$pid" ]; then
            echo "Stopping process on port $port (PID: $pid)..."
            kill $pid
        fi
    done
fi

# Wait a moment for graceful shutdown
sleep 2

# Force kill if still running
echo "Ensuring all processes are stopped..."
for port in 8081 8082 8083 8084 8085; do
    pid=$(lsof -ti:$port)
    if [ ! -z "$pid" ]; then
        echo "Force stopping process on port $port (PID: $pid)..."
        kill -9 $pid
    fi
done

echo ""
echo "================================="
echo "Cluster stopped successfully!"
echo "================================="