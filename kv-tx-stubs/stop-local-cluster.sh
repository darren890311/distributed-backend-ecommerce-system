#!/bin/bash

# Stop Local KV Cluster

# Colors for output
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Stopping all KV store nodes...${NC}"

# Kill all java processes running the leader-follower-kv JAR
pkill -f "leader-follower-kv"

# Wait a moment
sleep 2

# Check if any processes are still running
if pgrep -f "leader-follower-kv" > /dev/null; then
    echo -e "${YELLOW}Force killing remaining processes...${NC}"
    pkill -9 -f "leader-follower-kv"
    sleep 1
fi

# Verify all stopped
if ! pgrep -f "leader-follower-kv" > /dev/null; then
    echo -e "${GREEN}✓ All KV store nodes stopped successfully${NC}"
else
    echo -e "${RED}✗ Some processes may still be running${NC}"
    echo "Running processes:"
    pgrep -f "leader-follower-kv" -l
fi