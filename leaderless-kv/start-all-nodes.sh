#!/bin/bash

# Script to start all 5 leaderless nodes in separate terminal windows (macOS only)

echo "Starting all 5 leaderless KV nodes..."

# Start Node 1
osascript -e 'tell application "Terminal"
    do script "cd \"'$(pwd)'\" && mvn spring-boot:run -Dspring-boot.run.profiles=node1"
end tell'

# Wait a bit before starting next node
sleep 2

# Start Node 2
osascript -e 'tell application "Terminal"
    do script "cd \"'$(pwd)'\" && mvn spring-boot:run -Dspring-boot.run.profiles=node2"
end tell'

sleep 2

# Start Node 3
osascript -e 'tell application "Terminal"
    do script "cd \"'$(pwd)'\" && mvn spring-boot:run -Dspring-boot.run.profiles=node3"
end tell'

sleep 2

# Start Node 4
osascript -e 'tell application "Terminal"
    do script "cd \"'$(pwd)'\" && mvn spring-boot:run -Dspring-boot.run.profiles=node4"
end tell'

sleep 2

# Start Node 5
osascript -e 'tell application "Terminal"
    do script "cd \"'$(pwd)'\" && mvn spring-boot:run -Dspring-boot.run.profiles=node5"
end tell'

echo "All 5 nodes are starting in separate Terminal windows."
echo "Wait for all nodes to show 'Started LeaderlessKvApplication'"
echo ""
echo "Node URLs:"
echo "  Node 1: http://localhost:8090"
echo "  Node 2: http://localhost:8091"
echo "  Node 3: http://localhost:8092"
echo "  Node 4: http://localhost:8093"
echo "  Node 5: http://localhost:8094"
