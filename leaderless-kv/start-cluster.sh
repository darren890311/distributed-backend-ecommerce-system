#!/bin/bash

# Start script for leaderless KV cluster with Nginx load balancer
# This script starts 5 nodes locally and an Nginx load balancer

echo "Starting Leaderless KV Cluster..."
echo "================================="

# Build the project first
echo "Building the project..."
mvn clean package -DskipTests

# Create logs directory if it doesn't exist
mkdir -p logs

# Start each node in the background
echo "Starting Node 1 on port 8081..."
java -jar target/leaderless-kv-*.jar --spring.profiles.active=node1 > logs/node1.log 2>&1 &
NODE1_PID=$!
echo "Node 1 PID: $NODE1_PID"

echo "Starting Node 2 on port 8082..."
java -jar target/leaderless-kv-*.jar --spring.profiles.active=node2 > logs/node2.log 2>&1 &
NODE2_PID=$!
echo "Node 2 PID: $NODE2_PID"

echo "Starting Node 3 on port 8083..."
java -jar target/leaderless-kv-*.jar --spring.profiles.active=node3 > logs/node3.log 2>&1 &
NODE3_PID=$!
echo "Node 3 PID: $NODE3_PID"

echo "Starting Node 4 on port 8084..."
java -jar target/leaderless-kv-*.jar --spring.profiles.active=node4 > logs/node4.log 2>&1 &
NODE4_PID=$!
echo "Node 4 PID: $NODE4_PID"

echo "Starting Node 5 on port 8085..."
java -jar target/leaderless-kv-*.jar --spring.profiles.active=node5 > logs/node5.log 2>&1 &
NODE5_PID=$!
echo "Node 5 PID: $NODE5_PID"

# Wait for nodes to start
echo ""
echo "Waiting for nodes to start up..."
sleep 10

# Check if nodes are running
echo ""
echo "Checking node health..."
for port in 8081 8082 8083 8084 8085; do
    response=$(curl -s http://localhost:$port/test/health)
    if [ $? -eq 0 ]; then
        echo "✓ Node on port $port: $response"
    else
        echo "✗ Node on port $port: FAILED TO START"
    fi
done

# Save PIDs to file for easy cleanup
echo "$NODE1_PID $NODE2_PID $NODE3_PID $NODE4_PID $NODE5_PID" > .cluster_pids

echo ""
echo "================================="
echo "Cluster started successfully!"
echo ""
echo "Node ports: 8081, 8082, 8083, 8084, 8085"
echo ""
echo "To start Nginx load balancer, run:"
echo "  nginx -c $(pwd)/nginx/nginx.conf"
echo ""
echo "Load balancer will be available at: http://localhost:8080"
echo ""
echo "To stop the cluster, run:"
echo "  ./stop-cluster.sh"
echo ""
echo "Logs are available in the 'logs' directory"
echo "================================="