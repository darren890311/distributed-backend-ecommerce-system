#!/bin/bash

# Get LAN IP Address for Load Testing
# This script displays the IP address to use from another laptop on the same LAN

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}==================================================${NC}"
echo -e "${YELLOW}  KV Store LAN Connection Information${NC}"
echo -e "${YELLOW}==================================================${NC}"

# Get all network interfaces and their IPs
echo -e "\n${YELLOW}Available network interfaces:${NC}"

# macOS method
if [[ "$OSTYPE" == "darwin"* ]]; then
    # Wi-Fi
    WIFI_IP=$(ipconfig getifaddr en0 2>/dev/null)
    if [ -n "$WIFI_IP" ]; then
        echo -e "${GREEN}Wi-Fi (en0):${NC} $WIFI_IP"
        PRIMARY_IP="$WIFI_IP"
    fi

    # Ethernet
    ETH_IP=$(ipconfig getifaddr en1 2>/dev/null)
    if [ -n "$ETH_IP" ]; then
        echo -e "${GREEN}Ethernet (en1):${NC} $ETH_IP"
        [ -z "$PRIMARY_IP" ] && PRIMARY_IP="$ETH_IP"
    fi

    # Additional interfaces
    for i in {2..9}; do
        IP=$(ipconfig getifaddr en$i 2>/dev/null)
        if [ -n "$IP" ]; then
            echo -e "${GREEN}Network en$i:${NC} $IP"
            [ -z "$PRIMARY_IP" ] && PRIMARY_IP="$IP"
        fi
    done

# Linux method
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    PRIMARY_IP=$(hostname -I | awk '{print $1}')
    echo -e "${GREEN}Primary IP:${NC} $PRIMARY_IP"
fi

# Display configuration info
if [ -n "$PRIMARY_IP" ]; then
    echo -e "\n${YELLOW}==================================================${NC}"
    echo -e "${YELLOW}  Use this IP from another laptop:${NC}"
    echo -e "${GREEN}  $PRIMARY_IP${NC}"
    echo -e "${YELLOW}==================================================${NC}"

    echo -e "\n${YELLOW}KV Store URLs for load testing:${NC}"
    echo -e "Leader:     http://${PRIMARY_IP}:8080"
    echo -e "Follower 1: http://${PRIMARY_IP}:8081"
    echo -e "Follower 2: http://${PRIMARY_IP}:8082"
    echo -e "Follower 3: http://${PRIMARY_IP}:8083"
    echo -e "Follower 4: http://${PRIMARY_IP}:8084"

    echo -e "\n${YELLOW}Health check from another laptop:${NC}"
    echo "curl http://${PRIMARY_IP}:8080/test/health"

    echo -e "\n${YELLOW}Test write/read from another laptop:${NC}"
    echo "curl -X POST 'http://${PRIMARY_IP}:8080/api/kv/set?key=test&value=hello'"
    echo "curl 'http://${PRIMARY_IP}:8081/api/kv/get?key=test'"

    echo -e "\n${YELLOW}Update load-test-client configuration with:${NC}"
    echo "LEADER_URL = \"http://${PRIMARY_IP}:8080\""
    echo "FOLLOWER_URLS = {"
    echo "    \"http://${PRIMARY_IP}:8081\","
    echo "    \"http://${PRIMARY_IP}:8082\","
    echo "    \"http://${PRIMARY_IP}:8083\","
    echo "    \"http://${PRIMARY_IP}:8084\""
    echo "}"
else
    echo -e "\n${RED}Could not determine LAN IP address${NC}"
    echo "Please check your network connection"
fi

echo -e "\n${YELLOW}Note:${NC} This IP may change when you reconnect to the network"
echo "Run this script again if your IP changes"