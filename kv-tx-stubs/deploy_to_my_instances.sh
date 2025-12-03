#!/bin/bash

# Deploy KV Store to Your Specific EC2 Instances
# Configured for: cs6650-kv-cluster instances

set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Your instance IPs
declare -a IPS=(
    "44.197.207.152"
    "3.239.21.156"
    "3.239.182.50"
    "35.153.156.243"
    "44.192.5.81"
)

declare -a PORTS=(8080 8081 8082 8083 8084)
declare -a PROFILES=("leader" "follower1" "follower2" "follower3" "follower4")

# Configuration
JAR_FILE="target/leader-follower-kv-0.0.1-SNAPSHOT.jar"
KEY_FILE="cs6650-key.pem"
W=5
R=1

echo "=========================================="
echo "EC2 Deployment for cs6650-kv-cluster"
echo "=========================================="
echo ""

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    -k|--key)
      KEY_FILE="$2"
      shift 2
      ;;
    -w|--write-quorum)
      W="$2"
      shift 2
      ;;
    -r|--read-quorum)
      R="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1"
      echo "Usage: $0 [-k <key-file>] [-w <write-quorum>] [-r <read-quorum>]"
      exit 1
      ;;
  esac
done

# Verify requirements
if [ ! -f "$KEY_FILE" ]; then
    echo -e "${RED}Error: Key file not found: $KEY_FILE${NC}"
    echo "Make sure cs6650-key.pem is in the current directory"
    exit 1
fi

if [ ! -f "$JAR_FILE" ]; then
    echo -e "${RED}Error: JAR file not found: $JAR_FILE${NC}"
    echo "Run 'mvn clean package -DskipTests' first"
    exit 1
fi

chmod 400 "$KEY_FILE"

echo -e "${BLUE}Configuration:${NC}"
echo "  Key file: $KEY_FILE"
echo "  JAR file: $JAR_FILE"
echo "  Write quorum (W): $W"
echo "  Read quorum (R): $R"
echo ""

# Function to deploy to a single node
deploy_node() {
    local node_num=$1
    local ip=$2
    local profile=$3
    local port=$4

    echo -e "${BLUE}=== Deploying Node $node_num ($profile) at $ip:$port ===${NC}"

    # Test SSH connection first
    echo "  Testing SSH connection..."
    if ! ssh -i "$KEY_FILE" -o StrictHostKeyChecking=no -o ConnectTimeout=5 \
         ec2-user@$ip "echo 'Connected'" &>/dev/null; then
        echo -e "  ${RED}✗ Cannot connect to $ip${NC}"
        return 1
    fi
    echo -e "  ${GREEN}✓ SSH connection successful${NC}"

    # Copy JAR file
    echo "  Copying JAR file..."
    if scp -i "$KEY_FILE" -o StrictHostKeyChecking=no "$JAR_FILE" ec2-user@$ip:~/ &>/dev/null; then
        echo -e "  ${GREEN}✓ JAR copied successfully${NC}"
    else
        echo -e "  ${RED}✗ Failed to copy JAR${NC}"
        return 1
    fi

    # Stop existing process
    echo "  Stopping existing process..."
    ssh -i "$KEY_FILE" -o StrictHostKeyChecking=no ec2-user@$ip \
        "pkill -f leader-follower-kv || true" 2>/dev/null
    sleep 2

    # Check if Java is installed
    echo "  Checking Java installation..."
    if ! ssh -i "$KEY_FILE" -o StrictHostKeyChecking=no ec2-user@$ip \
         "java -version" &>/dev/null; then
        echo "  Installing Java 17..."
        ssh -i "$KEY_FILE" -o StrictHostKeyChecking=no ec2-user@$ip \
            "sudo yum install -y java-17-amazon-corretto-devel" &>/dev/null
    fi

    # Start new process with proper URLs for leader
    echo "  Starting $profile with W=$W, R=$R..."

    if [ "$profile" = "leader" ]; then
        # Leader needs follower URLs for replication
        ssh -i "$KEY_FILE" -o StrictHostKeyChecking=no ec2-user@$ip \
            "nohup java -jar leader-follower-kv-0.0.1-SNAPSHOT.jar \
            --spring.profiles.active=$profile \
            --kvstore.write-quorum=$W \
            --kvstore.read-quorum=$R \
            --kvstore.followers=http://3.239.21.156:8081,http://3.239.182.50:8082,http://35.153.156.243:8083,http://44.192.5.81:8084 \
            --kvstore.all-nodes=http://44.197.207.152:8080,http://3.239.21.156:8081,http://3.239.182.50:8082,http://35.153.156.243:8083,http://44.192.5.81:8084 \
            > kv-$profile.log 2>&1 &" 2>/dev/null
    else
        # Followers don't need follower URLs (they don't initiate replication)
        # But they need all-nodes for reads
        ssh -i "$KEY_FILE" -o StrictHostKeyChecking=no ec2-user@$ip \
            "nohup java -jar leader-follower-kv-0.0.1-SNAPSHOT.jar \
            --spring.profiles.active=$profile \
            --kvstore.write-quorum=$W \
            --kvstore.read-quorum=$R \
            --kvstore.all-nodes=http://44.197.207.152:8080,http://3.239.21.156:8081,http://3.239.182.50:8082,http://35.153.156.243:8083,http://44.192.5.81:8084 \
            > kv-$profile.log 2>&1 &" 2>/dev/null
    fi

    # Wait for startup
    echo "  Waiting for node to start..."
    sleep 5

    # Verify node is running
    echo "  Verifying node health..."
    local max_attempts=10
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        if curl -s -m 2 "http://$ip:$port/test/health" 2>/dev/null | grep -q "OK"; then
            echo -e "  ${GREEN}✓ Node $node_num started successfully${NC}"
            echo ""
            return 0
        fi
        echo "  Attempt $attempt/$max_attempts..."
        sleep 2
        ((attempt++))
    done

    echo -e "  ${RED}✗ Node $node_num failed to start (health check timeout)${NC}"
    echo "  Check logs with: ssh -i $KEY_FILE ec2-user@$ip 'tail -50 kv-$profile.log'"
    echo ""
    return 1
}

# Deploy to all nodes
echo "=========================================="
echo "Starting Deployment to All Nodes"
echo "=========================================="
echo ""

success_count=0
fail_count=0

for i in {0..4}; do
    if deploy_node $i "${IPS[$i]}" "${PROFILES[$i]}" "${PORTS[$i]}"; then
        ((success_count++))
    else
        ((fail_count++))
    fi
done

# Summary
echo "=========================================="
echo "Deployment Summary"
echo "=========================================="
echo -e "${GREEN}Successfully deployed: $success_count/5 nodes${NC}"

if [ $fail_count -gt 0 ]; then
    echo -e "${RED}Failed: $fail_count/5 nodes${NC}"
fi
echo ""

if [ $success_count -eq 5 ]; then
    echo -e "${GREEN}✓ All nodes deployed successfully!${NC}"
    echo ""
    echo "Node URLs:"
    for i in {0..4}; do
        echo "  ${PROFILES[$i]}: http://${IPS[$i]}:${PORTS[$i]}"
    done
    echo ""
    echo "Test commands:"
    echo "  # Test health"
    echo "  curl http://${IPS[0]}:8080/test/health"
    echo ""
    echo "  # Write to leader"
    echo "  curl -X POST 'http://${IPS[0]}:8080/api/kv/set?key=test&value=hello'"
    echo ""
    echo "  # Read from leader"
    echo "  curl 'http://${IPS[0]}:8080/api/kv/get?key=test'"
    echo ""
    echo "View logs on any node:"
    echo "  ssh -i $KEY_FILE ec2-user@${IPS[0]} 'tail -100 kv-leader.log'"
    echo ""

    # Run quick health check
    echo "Running quick health check on all nodes..."
    echo ""
    all_healthy=true
    for i in {0..4}; do
        if curl -s -m 2 "http://${IPS[$i]}:${PORTS[$i]}/test/health" 2>/dev/null | grep -q "OK"; then
            echo -e "  ${GREEN}✓${NC} Node $i (${PROFILES[$i]}): http://${IPS[$i]}:${PORTS[$i]}"
        else
            echo -e "  ${RED}✗${NC} Node $i (${PROFILES[$i]}): http://${IPS[$i]}:${PORTS[$i]}"
            all_healthy=false
        fi
    done
    echo ""

    if [ "$all_healthy" = true ]; then
        echo -e "${GREEN}✓ All nodes are healthy and ready for load testing!${NC}"
    else
        echo -e "${YELLOW}⚠ Some nodes are not responding to health checks${NC}"
        echo "Wait a few more seconds and try the health check again"
    fi
else
    echo -e "${RED}✗ Some nodes failed to deploy${NC}"
    echo "Check logs on failed nodes for errors"
fi

echo ""
echo "To change configuration (W/R values), run:"
echo "  $0 -k $KEY_FILE -w <W> -r <R>"
echo ""