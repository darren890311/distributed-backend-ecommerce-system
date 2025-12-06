# Leaderless Key-Value Store

## Overview

This module implements a **leaderless distributed key-value store** with **W=N (all nodes)** and **R=1 (single node)** configuration. Unlike the leader-follower architecture, there is no distinguished leader node - all nodes are peers with equal capabilities.

## Architecture

### Leaderless Pattern

```
                    Client Requests
                    (can go to any node)
                           |
         +-----------------+-----------------+
         |                 |                 |
         v                 v                 v
    +--------+        +--------+        +--------+
    | Node 1 |<------>| Node 2 |<------>| Node 3 |
    | (8090) |        | (8091) |        | (8092) |
    +--------+        +--------+        +--------+
         ^                 ^                 ^
         |                 |                 |
    +--------+        +--------+             |
    | Node 4 |<------>| Node 5 |<------------+
    | (8093) |        | (8094) |
    +--------+        +--------+
```

### Key Characteristics

- **No Leader**: All 5 nodes are identical peers
- **Any Node Can Handle Requests**: Clients can send reads/writes to any node
- **Write Coordinator**: The node receiving a write request becomes the coordinator for that specific write
- **W=N (5)**: All nodes must acknowledge writes before success is returned
- **R=1**: Reads return the local value from a single node (fast but may be stale)

## Write Operation Flow

When a node receives a write request from a client:

1. **Becomes Write Coordinator**: The node that receives the request coordinates this write
2. **Writes Locally**: Stores the key-value pair with a new version number
3. **Propagates to All Peers**: Sends write requests to all other N-1 nodes sequentially
   - Simulates 200ms network delay between each peer write
   - Waits for acknowledgment from each peer
4. **Waits for All**: Blocks until all N nodes (including itself) have acknowledged
5. **Returns Success**: Only after all writes complete, returns 201 Created to client

### Example Write Sequence

```
Client -> POST /api/kv/set?key=foo&value=bar (to Node 2)

Node 2 (Write Coordinator):
  1. Writes locally: foo=bar, version=1
  2. Sleep 200ms, then write to Node 1: foo=bar, v=1
  3. Sleep 200ms, then write to Node 3: foo=bar, v=1
  4. Sleep 200ms, then write to Node 4: foo=bar, v=1
  5. Sleep 200ms, then write to Node 5: foo=bar, v=1
  6. All 5 nodes acknowledged
  7. Return 201 Created to client

Total latency: ~800ms (4 × 200ms delays)
```

## Read Operation Flow

When a node receives a read request from a client:

1. **Returns Local Value**: Simply returns what it has in its local storage
2. **No Coordination**: Does not contact other nodes (R=1)
3. **Fast Response**: Very low latency (no network round-trips)
4. **May Be Stale**: If a write is still being propagated, this node may have old data

### Example Read Sequence

```
Client -> GET /api/kv/get?key=foo (to Node 4)

Node 4:
  1. Reads local storage: foo=bar, version=1
  2. Returns immediately to client

Total latency: <5ms (in-memory lookup)
```

## Inconsistency Window

The leaderless architecture with W=N, R=1 has an **inconsistency window** where stale reads can occur:

### Scenario

```
Time    Event
T0      Client writes foo=bar to Node 1 (becomes coordinator)
T1      Node 1 writes locally (foo=bar, v=1)
T2      Node 1 writing to Node 2... (200ms delay)
T3      ** Client reads foo from Node 3 **
        -> Returns old value or "not found" (STALE READ)
T4      Node 1 writing to Node 3...
T5      Node 1 writing to Node 4...
T6      Node 1 writing to Node 5...
T7      All nodes have foo=bar, v=1 (consistency achieved)
```

During the window T1-T7, reads to un-updated nodes will return stale data. This demonstrates **eventual consistency** in practice.

## API Endpoints

### Client Endpoints

#### Write Key-Value Pair
```http
POST /api/kv/set?key={key}&value={value}
```

**Response (201 Created):**
```json
{
  "value": "bar",
  "version": 1,
  "timestamp": 1699123456789
}
```

The node becomes the Write Coordinator and ensures all N nodes are updated before returning.

#### Read Key-Value Pair
```http
GET /api/kv/get?key={key}
```

**Response (200 OK):**
```json
{
  "value": "bar",
  "version": 1,
  "timestamp": 1699123456789
}
```

Returns the local value immediately (R=1). May be stale if write propagation is in progress.

### Internal Endpoints

#### Peer Write (Internal Use Only)
```http
POST /api/kv/peer-write
Content-Type: application/json

{
  "key": "foo",
  "value": "bar",
  "version": 1,
  "timestamp": 1699123456789
}
```

Used by the Write Coordinator to replicate data to peer nodes.

### Testing Endpoints

#### Local Read (Testing)
```http
GET /test/local_read?key={key}
```

Reads the local value directly (same as GET but explicitly for testing).

#### Health Check
```http
GET /test/health
```

**Response:**
```
OK - Leaderless KV Store Node 1 is running, 42 keys stored (W=5, R=1)
```

## Configuration

### Cluster Parameters

Each node is configured with:

- `cluster.node-id`: Unique identifier for this node (1-5)
- `cluster.total-nodes`: Total number of nodes in cluster (5)
- `cluster.write-quorum`: Write quorum W=N (5)
- `cluster.read-quorum`: Read quorum R=1 (1)
- `cluster.network-delay-ms`: Simulated network delay per write (200ms)
- `cluster.peers`: List of URLs for all other peer nodes

### Node Configuration Files

Five profiles for local testing:

- `application-node1.properties` - Port 8090, peers: 8091-8094
- `application-node2.properties` - Port 8091, peers: 8090, 8092-8094
- `application-node3.properties` - Port 8092, peers: 8090-8091, 8093-8094
- `application-node4.properties` - Port 8093, peers: 8090-8092, 8094
- `application-node5.properties` - Port 8094, peers: 8090-8093

## Running Locally

### Prerequisites

- Java 17 or higher
- Maven 3.9 or higher

### Build

```bash
cd leaderless-kv
mvn clean package -DskipTests
```

### Start All Nodes

Open 5 terminal windows:

**Terminal 1 (Node 1):**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=node1
```

**Terminal 2 (Node 2):**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=node2
```

**Terminal 3 (Node 3):**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=node3
```

**Terminal 4 (Node 4):**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=node4
```

**Terminal 5 (Node 5):**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=node5
```

Wait for all nodes to display: "Started LeaderlessKvApplication"

### Verify Cluster

```bash
# Check health of all nodes
curl http://localhost:8090/test/health
curl http://localhost:8091/test/health
curl http://localhost:8092/test/health
curl http://localhost:8093/test/health
curl http://localhost:8094/test/health
```

### Test Write and Read

```bash
# Write to Node 1 (becomes coordinator)
curl -X POST "http://localhost:8090/api/kv/set?key=test&value=hello"

# Read from Node 3 (after propagation completes)
curl "http://localhost:8092/api/kv/get?key=test"

# Read from Node 5
curl "http://localhost:8094/api/kv/get?key=test"
```

### Demonstrate Inconsistency Window

This requires precise timing but you can observe stale reads:

```bash
# Terminal 1: Start a slow write (will take ~800ms)
curl -X POST "http://localhost:8090/api/kv/set?key=race&value=v1" &

# Terminal 2: Immediately read from different node
# (may get stale data if timing is right)
sleep 0.3 && curl "http://localhost:8094/api/kv/get?key=race"
```

## Load Testing

The leaderless store uses the **same API** as the leader-follower store, so you can use the existing load test client with minor modifications.

### Modify Load Test Client

Edit `load-test-client/src/main/java/com/cs6650/loadtestclient/KVLoadTestClient.java`:

```java
// Change URLs to leaderless nodes
private static final String LEADER_URL = "http://localhost:8090";  // Any node
private static final String[] FOLLOWER_URLS = {
    "http://localhost:8090",  // All nodes are equal
    "http://localhost:8091",
    "http://localhost:8092",
    "http://localhost:8093",
    "http://localhost:8094"
};
```

### Run Load Tests

```bash
cd load-test-client
mvn clean compile
mvn exec:java
```

Results will show:
- **Higher stale read percentage** compared to W=5, R=1 leader-follower (due to inconsistency window)
- **Similar write latency** (~800ms for W=5 with sequential replication)
- **Very fast read latency** (<5ms for R=1 local reads)

## Expected Performance Characteristics

### Write Operations (W=N=5)

- **Latency**: ~800-1000ms
  - 200ms × 4 peers = 800ms sequential delay
  - Plus network overhead
- **Throughput**: Low for write-heavy workloads
  - Similar to leader-follower W=5
- **Consistency**: Strong (all nodes updated before acknowledgment)

### Read Operations (R=1)

- **Latency**: <5ms
  - In-memory lookup only
  - No network communication
- **Throughput**: Very high
  - Can serve from any node
  - Load distributed across all 5 nodes
- **Consistency**: Weak (may return stale data during propagation)

### Stale Reads

- **Expected**: 0.5-2% with moderate load
- **Higher than leader-follower W=5, R=1**: Yes, because:
  - Write coordination takes time
  - Reads can hit any node during propagation
  - Larger inconsistency window

## Comparison: Leaderless vs Leader-Follower

| Aspect | Leaderless (W=5, R=1) | Leader-Follower (W=5, R=1) |
|--------|----------------------|----------------------------|
| **Architecture** | All nodes equal | One leader, four followers |
| **Write Coordinator** | Any node (dynamic) | Always leader (static) |
| **Write Distribution** | Load-balanced across nodes | All writes go to leader |
| **Read Distribution** | Load-balanced across nodes | Can read from any node |
| **Single Point of Failure** | No | Yes (leader) |
| **Write Latency** | ~800-1000ms | ~800-1000ms |
| **Read Latency** | <5ms | <5ms |
| **Stale Reads** | Higher (~1-2%) | Lower (~0.1-0.3%) |
| **Complexity** | Higher (peer coordination) | Lower (centralized) |
| **Scalability** | Better (no bottleneck) | Limited (leader bottleneck) |

## Docker Deployment

### Build Image

```bash
cd leaderless-kv
docker buildx build --platform linux/amd64 -t leaderless-kv:latest --load .
```

### Run Containers Locally

```bash
# Node 1
docker run -d \
  --name leaderless-node1 \
  -p 8090:8090 \
  -e SPRING_PROFILES_ACTIVE=node1 \
  leaderless-kv:latest

# Node 2
docker run -d \
  --name leaderless-node2 \
  -p 8091:8091 \
  -e SPRING_PROFILES_ACTIVE=node2 \
  leaderless-kv:latest

# Repeat for nodes 3-5...
```

Note: For Docker networking between containers, use container names or Docker network.

---

## AWS Deployment (Step-by-Step)

This section provides detailed instructions for deploying the leaderless KV store to AWS EC2 instances.

### Prerequisites

- AWS account with EC2 access
- AWS CLI installed and configured
- Docker installed locally
- SSH key pair for EC2 access

### Step 1: Create AWS Infrastructure

#### 1.1 Create ECR Repository

```bash
# Login to AWS
aws configure

# Create ECR repository for leaderless KV store
aws ecr create-repository \
    --repository-name cs6650-leaderless-kv \
    --region us-east-1

# Note the repository URI (e.g., 058264419251.dkr.ecr.us-east-1.amazonaws.com/cs6650-leaderless-kv)
```

#### 1.2 Launch EC2 Instances

Launch 5 EC2 instances with the following specifications:

**Instance Configuration:**
- **AMI**: Amazon Linux 2023
- **Instance Type**: t2.micro (Free Tier eligible)
- **Region**: us-east-1 (N. Virginia)
- **Number of Instances**: 5

**Via AWS Console:**
1. Go to EC2 Dashboard → Launch Instance
2. Name: `leaderless-kv-node-1` (repeat for nodes 2-5)
3. Select "Amazon Linux 2023 AMI"
4. Instance type: t2.micro
5. Key pair: Select or create new key pair (save the .pem file)
6. Network settings:
   - Create security group: `leaderless-kv-sg`
   - Add rules:
     - SSH (22) from My IP
     - Custom TCP (8090-8094) from Anywhere (0.0.0.0/0)
     - Custom TCP (8080) from Anywhere (for consistency)
7. Launch instances

**Via AWS CLI:**
```bash
# Create security group
aws ec2 create-security-group \
    --group-name leaderless-kv-sg \
    --description "Security group for leaderless KV store" \
    --region us-east-1

# Get security group ID
SG_ID=$(aws ec2 describe-security-groups \
    --group-names leaderless-kv-sg \
    --query 'SecurityGroups[0].GroupId' \
    --output text \
    --region us-east-1)

# Add inbound rules
aws ec2 authorize-security-group-ingress \
    --group-id $SG_ID \
    --protocol tcp \
    --port 22 \
    --cidr 0.0.0.0/0 \
    --region us-east-1

aws ec2 authorize-security-group-ingress \
    --group-id $SG_ID \
    --protocol tcp \
    --port 8090-8094 \
    --cidr 0.0.0.0/0 \
    --region us-east-1

# Launch 5 instances (replace AMI ID with latest Amazon Linux 2023)
aws ec2 run-instances \
    --image-id ami-0c101f26f147fa7fd \
    --count 5 \
    --instance-type t2.micro \
    --key-name your-key-pair-name \
    --security-group-ids $SG_ID \
    --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=leaderless-kv-node}]' \
    --region us-east-1
```

#### 1.3 Note Instance Public IPs

After instances are running, get their public IP addresses:

```bash
aws ec2 describe-instances \
    --filters "Name=tag:Name,Values=leaderless-kv-node" \
    --query 'Reservations[*].Instances[*].[PublicIpAddress,InstanceId,State.Name]' \
    --output table \
    --region us-east-1
```

**Example IPs (yours will differ):**
```
Node 1: 3.235.123.45
Node 2: 3.235.123.46
Node 3: 3.235.123.47
Node 4: 3.235.123.48
Node 5: 3.235.123.49
```

### Step 2: Build and Push Docker Image

#### 2.1 Build for AMD64 Architecture

```bash
cd leaderless-kv

# Build image for AMD64 (EC2 architecture)
docker buildx build --platform linux/amd64 -t leaderless-kv:latest --load .
```

#### 2.2 Authenticate with ECR

```bash
# Get ECR login password and authenticate Docker
aws ecr get-login-password --region us-east-1 | \
    docker login --username AWS --password-stdin \
    058264269542.dkr.ecr.us-east-1.amazonaws.com
```

Replace `058264419251` with your AWS account ID.

#### 2.3 Tag and Push Image

```bash
# Tag image with ECR repository URI
docker tag leaderless-kv:latest \
    058264269542.dkr.ecr.us-east-1.amazonaws.com/cs6650-leaderless-kv:latest

# Push to ECR
docker push 058264269542.dkr.ecr.us-east-1.amazonaws.com/cs6650-leaderless-kv:latest
```

### Step 3: Configure EC2 Instances

#### 3.1 Install Docker on All Instances

SSH into each instance and install Docker:

```bash
# SSH into instance (repeat for all 5 instances)
ssh -i your-key.pem ec2-user@3.235.123.45

# Update system
sudo yum update -y

# Install Docker
sudo yum install docker -y

# Start Docker service
sudo systemctl start docker
sudo systemctl enable docker

# Add ec2-user to docker group
sudo usermod -a -G docker ec2-user

# Log out and log back in for group changes to take effect
exit
```

#### 3.2 Authenticate ECR on Each Instance

SSH into each instance and authenticate with ECR:

```bash
ssh -i leaderless-key.pem ec2-user@54.90.95.15

# Install AWS CLI (if not present)
sudo yum install aws-cli -y

# Configure AWS credentials (use your credentials)
aws configure
# Enter: Access Key ID, Secret Access Key, Region (us-east-1), Output format (json)

# Authenticate Docker with ECR
aws ecr get-login-password --region us-east-1 | \
    docker login --username AWS --password-stdin \
    058264269542.dkr.ecr.us-east-1.amazonaws.com

# Pull the image
docker pull 058264269542.dkr.ecr.us-east-1.amazonaws.com/cs6650-leaderless-kv:latest
```

### Step 4: Deploy Nodes with Peer Configuration

**IMPORTANT**: Each node needs to know the URLs of all other peer nodes.

#### 4.1 Deploy Node 1 (Port 8090)

```bash
ssh -i leaderless-key.pem ec2-user@54.90.95.15

sudo docker run -d \
    --name leaderless-kv \
    --restart unless-stopped \
    -p 8090:8090 \
    -e SERVER_PORT=8090 \
    -e CLUSTER_NODE_ID=1 \
    -e CLUSTER_TOTAL_NODES=5 \
    -e CLUSTER_WRITE_QUORUM=5 \
    -e CLUSTER_READ_QUORUM=1 \
    -e CLUSTER_NETWORK_DELAY_MS=200 \
    -e CLUSTER_PEERS_0=http://54.227.17.233:8091 \
    -e CLUSTER_PEERS_1=http://3.81.230.245:8092 \
    -e CLUSTER_PEERS_2=http://34.224.173.172:8093 \
    -e CLUSTER_PEERS_3=http://13.218.89.69:8094 \
    -e SPRING_PROFILES_ACTIVE=node1 \
    058264269542.dkr.ecr.us-east-1.amazonaws.com/cs6650-leaderless-kv:latest
```

#### 4.2 Deploy Node 2 (Port 8091)

```bash
ssh -i leaderless-key.pem ec2-user@54.227.17.233

sudo docker run -d \
    --name leaderless-kv \
    --restart unless-stopped \
    -p 8091:8091 \
    -e SERVER_PORT=8091 \
    -e CLUSTER_NODE_ID=2 \
    -e CLUSTER_TOTAL_NODES=5 \
    -e CLUSTER_WRITE_QUORUM=5 \
    -e CLUSTER_READ_QUORUM=1 \
    -e CLUSTER_NETWORK_DELAY_MS=200 \
    -e CLUSTER_PEERS_0=http://54.90.95.15:8090 \
    -e CLUSTER_PEERS_1=http://3.81.230.245:8092 \
    -e CLUSTER_PEERS_2=http://34.224.173.172:8093 \
    -e CLUSTER_PEERS_3=http://13.218.89.69:8094 \
    -e SPRING_PROFILES_ACTIVE=node2 \
    058264269542.dkr.ecr.us-east-1.amazonaws.com/cs6650-leaderless-kv:latest
```

#### 4.3 Deploy Node 3 (Port 8092)

```bash
ssh -i leaderless-key.pem ec2-user@3.81.230.245

sudo docker run -d \
    --name leaderless-kv \
    --restart unless-stopped \
    -p 8092:8092 \
    -e SERVER_PORT=8092 \
    -e CLUSTER_NODE_ID=3 \
    -e CLUSTER_TOTAL_NODES=5 \
    -e CLUSTER_WRITE_QUORUM=5 \
    -e CLUSTER_READ_QUORUM=1 \
    -e CLUSTER_NETWORK_DELAY_MS=200 \
    -e CLUSTER_PEERS_0=http://54.90.95.15:8090 \
    -e CLUSTER_PEERS_1=http://54.227.17.233:8091 \
    -e CLUSTER_PEERS_2=http://34.224.173.172:8093 \
    -e CLUSTER_PEERS_3=http://13.218.89.69:8094 \
    -e SPRING_PROFILES_ACTIVE=node3 \
    058264269542.dkr.ecr.us-east-1.amazonaws.com/cs6650-leaderless-kv:latest
```

#### 4.4 Deploy Node 4 (Port 8093)

```bash
ssh -i leaderless-key.pem ec2-user@34.224.173.172

sudo docker run -d \
    --name leaderless-kv \
    --restart unless-stopped \
    -p 8093:8093 \
    -e SERVER_PORT=8093 \
    -e CLUSTER_NODE_ID=4 \
    -e CLUSTER_TOTAL_NODES=5 \
    -e CLUSTER_WRITE_QUORUM=5 \
    -e CLUSTER_READ_QUORUM=1 \
    -e CLUSTER_NETWORK_DELAY_MS=200 \
    -e CLUSTER_PEERS_0=http://54.90.95.15:8090 \
    -e CLUSTER_PEERS_1=http://54.227.17.233:8091 \
    -e CLUSTER_PEERS_2=http://3.81.230.245:8092 \
    -e CLUSTER_PEERS_3=http://13.218.89.69:8094 \
    -e SPRING_PROFILES_ACTIVE=node4 \
    058264269542.dkr.ecr.us-east-1.amazonaws.com/cs6650-leaderless-kv:latest
```

#### 4.5 Deploy Node 5 (Port 8094)

```bash
ssh -i leaderless-key.pem ec2-user@13.218.89.69

sudo docker run -d \
    --name leaderless-kv \
    --restart unless-stopped \
    -p 8094:8094 \
    -e SERVER_PORT=8094 \
    -e CLUSTER_NODE_ID=5 \
    -e CLUSTER_TOTAL_NODES=5 \
    -e CLUSTER_WRITE_QUORUM=5 \
    -e CLUSTER_READ_QUORUM=1 \
    -e CLUSTER_NETWORK_DELAY_MS=200 \
    -e CLUSTER_PEERS_0=http://54.90.95.15:8090 \
    -e CLUSTER_PEERS_1=http://54.227.17.233:8091 \
    -e CLUSTER_PEERS_2=http://3.81.230.245:8092 \
    -e CLUSTER_PEERS_3=http://34.224.173.172:8093 \
    -e SPRING_PROFILES_ACTIVE=node5 \
    058264269542.dkr.ecr.us-east-1.amazonaws.com/cs6650-leaderless-kv:latest
```

### Step 5: Verify Deployment

#### 5.1 Check Container Status

On each instance, verify the container is running:

```bash
# Check running containers
sudo docker ps

# Check logs
sudo docker logs leaderless-kv

# Should see: "Started LeaderlessKvApplication"
```

#### 5.2 Health Check All Nodes

From your local machine:

```bash
# Node 1
curl http://54.90.95.15:8090/test/health

# Node 2
curl http://54.227.17.233:8091/test/health

# Node 3
curl http://3.81.230.245:8092/test/health

# Node 4
curl http://34.224.173.172:8093/test/health

# Node 5
curl http://13.218.89.69:8094/test/health
```

Expected response:
```
OK - Leaderless KV Store Node X is running, 0 keys stored (W=5, R=1)
```

#### 5.3 Test Write Coordination

```bash
# Write to Node 1 (becomes coordinator)
curl -X POST "http://54.90.95.15:8090/api/kv/set?key=test&value=hello"

# Expected response (after ~800ms):
# {"value":"hello","version":1,"timestamp":1699123456789}

# Wait for propagation to complete (1-2 seconds)
sleep 2

# Read from Node 3 (should have replicated value)
curl "http://3.81.230.245:8092/api/kv/get?key=test"

# Expected response:
# {"value":"hello","version":1,"timestamp":1699123456789}

# Read from Node 5
curl "http://3.235.123.49:8094/api/kv/get?key=test"
```

#### 5.4 Test Write to Different Coordinator

```bash
# Write to Node 4 (different coordinator)
curl -X POST "http://3.235.123.48:8093/api/kv/set?key=foo&value=bar"

sleep 2

# Read from Node 2
curl "http://3.235.123.46:8091/api/kv/get?key=foo"
```

### Step 6: Run Load Tests Against AWS

#### 6.1 Update Load Test Client

Edit `load-test-client/src/main/java/com/cs6650/loadtestclient/KVLoadTestClient.java`:

```java
// Update with your AWS instance IPs
private static final String LEADER_URL = "http://3.235.123.45:8090";  // Any node
private static final String[] FOLLOWER_URLS = {
    "http://3.235.123.45:8090",  // Node 1
    "http://3.235.123.46:8091",  // Node 2
    "http://3.235.123.47:8092",  // Node 3
    "http://3.235.123.48:8093",  // Node 4
    "http://3.235.123.49:8094"   // Node 5
};
```

#### 6.2 Run Load Tests

```bash
cd load-test-client
mvn clean compile
mvn exec:java
```

Tests will run for approximately 10-15 minutes and generate CSV results showing:
- Write latency: ~1000-1200ms (higher than local due to network)
- Read latency: ~50-100ms (network round-trip)
- Stale reads: 1-3% (inconsistency window observable)
- Throughput: ~15-20 req/s for write-heavy workloads

### Step 7: Monitor and Debug

#### 7.1 View Logs

```bash
# Real-time logs
ssh -i your-key.pem ec2-user@<INSTANCE-IP>
sudo docker logs -f leaderless-kv

# Last 100 lines
sudo docker logs --tail 100 leaderless-kv
```

#### 7.2 Check Container Stats

```bash
sudo docker stats leaderless-kv
```

#### 7.3 Restart Node

```bash
sudo docker restart leaderless-kv
```

#### 7.4 Stop and Remove Container

```bash
sudo docker stop leaderless-kv
sudo docker rm leaderless-kv
```

### Step 8: AWS Deployment Script (Optional)

Create a deployment script `deploy-aws.sh`:

```bash
#!/bin/bash

# Configuration
ECR_REPO="058264419251.dkr.ecr.us-east-1.amazonaws.com/cs6650-leaderless-kv"
KEY_FILE="your-key.pem"
NODES=(
    "3.235.123.45:8090:1"
    "3.235.123.46:8091:2"
    "3.235.123.47:8092:3"
    "3.235.123.48:8093:4"
    "3.235.123.49:8094:5"
)

# Build and push image
echo "Building Docker image..."
docker buildx build --platform linux/amd64 -t leaderless-kv:latest --load .

echo "Pushing to ECR..."
docker tag leaderless-kv:latest $ECR_REPO:latest
docker push $ECR_REPO:latest

# Deploy to each node
for node_config in "${NODES[@]}"; do
    IFS=':' read -r ip port node_id <<< "$node_config"

    echo "Deploying Node $node_id at $ip:$port..."

    # Build peer list (exclude current node)
    peers=""
    peer_idx=0
    for peer_config in "${NODES[@]}"; do
        IFS=':' read -r peer_ip peer_port peer_node_id <<< "$peer_config"
        if [ "$peer_node_id" != "$node_id" ]; then
            peers="$peers -e CLUSTER_PEERS_$peer_idx=http://$peer_ip:$peer_port"
            ((peer_idx++))
        fi
    done

    # Deploy container
    ssh -i $KEY_FILE ec2-user@$ip << EOF
        sudo docker pull $ECR_REPO:latest
        sudo docker stop leaderless-kv 2>/dev/null || true
        sudo docker rm leaderless-kv 2>/dev/null || true
        sudo docker run -d \
            --name leaderless-kv \
            --restart unless-stopped \
            -p $port:$port \
            -e SERVER_PORT=$port \
            -e CLUSTER_NODE_ID=$node_id \
            $peers \
            $ECR_REPO:latest
EOF

    echo "Node $node_id deployed successfully!"
done

echo "All nodes deployed! Verifying..."
sleep 5

# Verify all nodes
for node_config in "${NODES[@]}"; do
    IFS=':' read -r ip port node_id <<< "$node_config"
    echo "Checking Node $node_id..."
    curl -s http://$ip:$port/test/health || echo "FAILED"
done
```

Make it executable and run:

```bash
chmod +x deploy-aws.sh
./deploy-aws.sh
```

### Cost Considerations

**Estimated Monthly Costs (as of 2024):**
- 5x t2.micro instances: ~$4.50/month (Free Tier: first 750 hours/month free for 12 months)
- ECR storage: ~$0.10/month (< 1GB)
- Data transfer: Varies based on load testing
- **Total**: ~$5-10/month (Free under Free Tier)

### Cleanup

To avoid charges, stop and terminate instances when done:

```bash
# Stop instances (can restart later)
aws ec2 stop-instances \
    --instance-ids i-xxx i-yyy i-zzz ... \
    --region us-east-1

# Terminate instances (permanent deletion)
aws ec2 terminate-instances \
    --instance-ids i-xxx i-yyy i-zzz ... \
    --region us-east-1

# Delete ECR repository
aws ecr delete-repository \
    --repository-name cs6650-leaderless-kv \
    --force \
    --region us-east-1

# Delete security group
aws ec2 delete-security-group \
    --group-id sg-xxxxx \
    --region us-east-1
```

---

## Project Structure

```
leaderless-kv/
├── src/
│   └── main/
│       ├── java/com/cs6650/leaderlesskv/
│       │   ├── LeaderlessKvApplication.java      # Main application
│       │   ├── config/
│       │   │   └── ClusterConfig.java            # Cluster configuration
│       │   ├── controller/
│       │   │   └── KVController.java             # REST API endpoints
│       │   ├── model/
│       │   │   ├── VersionedValue.java           # Value with version
│       │   │   └── WriteRequest.java             # Peer write request
│       │   └── service/
│       │       ├── KVStore.java                  # In-memory storage
│       │       └── WriteCoordinatorService.java  # Write coordination
│       └── resources/
│           ├── application.properties            # Default config
│           ├── application-node1.properties      # Node 1 config
│           ├── application-node2.properties      # Node 2 config
│           ├── application-node3.properties      # Node 3 config
│           ├── application-node4.properties      # Node 4 config
│           └── application-node5.properties      # Node 5 config
├── Dockerfile                                    # Multi-stage build
├── pom.xml                                       # Maven configuration
└── README.md                                     # This file
```

## Key Implementation Details

### Version Management

Each node maintains its own version counter:
- When acting as Write Coordinator: generates new version
- When receiving peer write: updates counter to max(local, received)
- Ensures monotonically increasing versions across cluster

### Write Coordinator Logic

`WriteCoordinatorService.coordinateWrite()`:
1. Iterates through peer list sequentially
2. Sleeps 200ms before each peer write (network delay simulation)
3. Sends async write request to peer
4. Collects all futures and waits for completion (blocking)
5. Throws exception if any peer fails (W=N requirement)

### Local Reads

`KVController.get()`:
- Simply calls `kvStore.get(key)`
- Returns immediately with local value
- No coordination or version checking
- Fast but may be stale

## Troubleshooting

### Issue: Write Times Out

**Cause**: One or more peer nodes are down or unreachable.

**Solution**: Ensure all 5 nodes are running and healthy. Check with `/test/health`.

### Issue: Stale Reads Not Observed

**Cause**:
- Key set too large (no temporal locality)
- Not enough concurrent load
- Network delay simulation disabled

**Solution**:
- Use smaller key set (100 keys) in load test
- Increase number of concurrent threads
- Verify `cluster.network-delay-ms=200` in config

### Issue: Port Already in Use

```bash
# Kill processes on ports 8090-8094
lsof -ti:8090 | xargs kill -9
lsof -ti:8091 | xargs kill -9
lsof -ti:8092 | xargs kill -9
lsof -ti:8093 | xargs kill -9
lsof -ti:8094 | xargs kill -9
```

## Future Enhancements

1. **Read Repair**: Detect and fix stale data during reads
2. **Hinted Handoff**: Handle temporary node failures
3. **Quorum Reads**: Implement R>1 with version comparison
4. **Anti-Entropy**: Background process to sync nodes
5. **Consistent Hashing**: Distribute keys across nodes (partitioning)
6. **Async Writes**: W<N for better performance with eventual consistency

## Summary

This leaderless implementation demonstrates:
- ✅ **Peer-to-peer architecture** (no single leader)
- ✅ **Dynamic write coordination** (any node can coordinate)
- ✅ **W=N consistency** (all nodes updated before success)
- ✅ **R=1 fast reads** (local access only)
- ✅ **Inconsistency window** (stale reads observable in practice)
- ✅ **Same API as leader-follower** (compatible with existing load tests)

The implementation successfully shows the trade-offs between consistency and performance in distributed systems, particularly the inconsistency window that exists with W=N, R=1 configuration.