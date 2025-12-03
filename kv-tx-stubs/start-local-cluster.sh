#!/bin/bash

# Start Local KV Cluster
# This script starts all 5 nodes (1 leader + 4 followers) on localhost

# Configuration
JAR_FILE="target/leader-follower-kv-0.0.1-SNAPSHOT.jar"
LOG_DIR="logs"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${YELLOW}==================================================${NC}"
echo -e "${YELLOW}  Starting Local KV Store Cluster${NC}"
echo -e "${YELLOW}==================================================${NC}"

# Check if JAR exists
if [ ! -f "$JAR_FILE" ]; then
    echo -e "${RED}Error: JAR file not found at $JAR_FILE${NC}"
    echo "Please run: mvn clean package -DskipTests"
    exit 1
fi

# Create logs directory
mkdir -p "$LOG_DIR"

# Stop any existing processes
echo -e "\n${YELLOW}Stopping any existing KV store processes...${NC}"
pkill -f "leader-follower-kv" 2>/dev/null
sleep 2

# Start Leader
echo -e "\n${GREEN}Starting Leader (port 8080)...${NC}"
nohup java -jar "$JAR_FILE" \
    --spring.profiles.active=local-leader \
    > "$LOG_DIR/leader.log" 2>&1 &
LEADER_PID=$!
echo "Leader PID: $LEADER_PID"

# Wait a bit for leader to start
sleep 3

# Start Follower 1
echo -e "\n${GREEN}Starting Follower 1 (port 8081)...${NC}"
nohup java -jar "$JAR_FILE" \
    --spring.profiles.active=local-follower1 \
    > "$LOG_DIR/follower1.log" 2>&1 &
echo "Follower 1 PID: $!"

# Start Follower 2
echo -e "\n${GREEN}Starting Follower 2 (port 8082)...${NC}"
nohup java -jar "$JAR_FILE" \
    --spring.profiles.active=local-follower2 \
    > "$LOG_DIR/follower2.log" 2>&1 &
echo "Follower 2 PID: $!"

# Start Follower 3
echo -e "\n${GREEN}Starting Follower 3 (port 8083)...${NC}"
nohup java -jar "$JAR_FILE" \
    --spring.profiles.active=local-follower3 \
    > "$LOG_DIR/follower3.log" 2>&1 &
echo "Follower 3 PID: $!"

# Start Follower 4
echo -e "\n${GREEN}Starting Follower 4 (port 8084)...${NC}"
nohup java -jar "$JAR_FILE" \
    --spring.profiles.active=local-follower4 \
    > "$LOG_DIR/follower4.log" 2>&1 &
echo "Follower 4 PID: $!"

echo -e "\n${YELLOW}Waiting for nodes to start up...${NC}"
sleep 5

# Health check
echo -e "\n${YELLOW}==================================================${NC}"
echo -e "${YELLOW}  Checking Node Health${NC}"
echo -e "${YELLOW}==================================================${NC}"

check_health() {
    local port=$1
    local name=$2
    local url="http://localhost:$port/test/health"

    response=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null)

    if [ "$response" = "200" ]; then
        echo -e "${GREEN}✓ $name (port $port): HEALTHY${NC}"
        return 0
    else
        echo -e "${RED}✗ $name (port $port): UNHEALTHY (HTTP $response)${NC}"
        return 1
    fi
}

# Check all nodes
all_healthy=true
check_health 8080 "Leader    " || all_healthy=false
check_health 8081 "Follower 1" || all_healthy=false
check_health 8082 "Follower 2" || all_healthy=false
check_health 8083 "Follower 3" || all_healthy=false
check_health 8084 "Follower 4" || all_healthy=false

echo -e "\n${YELLOW}==================================================${NC}"
if [ "$all_healthy" = true ]; then
    echo -e "${GREEN}✓ All nodes are healthy and ready!${NC}"
else
    echo -e "${RED}✗ Some nodes failed to start. Check logs in $LOG_DIR/${NC}"
fi
echo -e "${YELLOW}==================================================${NC}"

# Get laptop IP address
echo -e "\n${YELLOW}Network Information:${NC}"
echo "Local access: http://localhost:8080"

# Try to get LAN IP address (macOS)
LAN_IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null)
if [ -n "$LAN_IP" ]; then
    echo -e "LAN access:   ${GREEN}http://$LAN_IP:8080${NC}"
    echo -e "\n${YELLOW}For load testing from another laptop, use: ${GREEN}$LAN_IP${NC}"
else
    echo "Could not determine LAN IP address"
fi

echo -e "\n${YELLOW}Quick Test Commands:${NC}"
echo "curl http://localhost:8080/test/health"
echo "curl -X POST 'http://localhost:8080/api/kv/set?key=test&value=hello'"
echo "curl 'http://localhost:8081/api/kv/get?key=test'"

echo -e "\n${YELLOW}To stop all nodes:${NC}"
echo "./stop-local-cluster.sh"

echo -e "\n${YELLOW}Logs available in: $LOG_DIR/${NC}"
echo "tail -f $LOG_DIR/leader.log"