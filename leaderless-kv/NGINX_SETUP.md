# Leaderless KV Store with Nginx Load Balancer

This setup demonstrates a **leaderless distributed key-value store** with **W=N, R=1** configuration and an Nginx load balancer using **round-robin** strategy.

## Architecture Overview

### Configuration
- **W = N = 5**: All writes must be replicated to all 5 nodes before returning success
- **R = 1**: Reads return local value from the node that receives the request
- **Read Delay**: 500ms delay on each read to increase inconsistency window
- **Round-Robin Load Balancing**: Nginx distributes requests evenly across all nodes

### Write Coordinator Pattern
1. Client sends write to load balancer (port 8080)
2. Nginx uses round-robin to select a node
3. **Selected node becomes Write Coordinator** for that request
4. Coordinator writes locally, then propagates to all 4 peer nodes in parallel
5. Coordinator waits for **all peers** to acknowledge (W=N)
6. Only then returns **201 Created** to client

### Read Pattern
1. Client sends read to load balancer (port 8080)
2. Nginx uses round-robin to select a node
3. **Selected node sleeps for 500ms** (configurable via `cluster.read-delay-ms`)
4. Node returns its **local value only** (R=1)
5. Demonstrates **inconsistency window**: reads can hit un-updated nodes

## Cluster Components

### Nodes
- **Node 1**: Port 8081
- **Node 2**: Port 8082
- **Node 3**: Port 8083
- **Node 4**: Port 8084
- **Node 5**: Port 8085

### Nginx Load Balancer
- **Port**: 8080
- **Strategy**: Round-robin (default)
- **Config**: `nginx/nginx.conf`

## Quick Start

### Option 1: Local Deployment (without Docker)

#### Prerequisites
- Java 17+
- Maven
- Nginx installed locally

#### Steps

1. **Build the project**
   ```bash
   cd leaderless-kv
   mvn clean package -DskipTests
   ```

2. **Start the cluster**
   ```bash
   ./start-cluster.sh
   ```
   This will:
   - Build the JAR
   - Start all 5 nodes on ports 8081-8085
   - Save logs to `logs/` directory
   - Display health status

3. **Start Nginx**
   ```bash
   # From leaderless-kv directory
   nginx -c $(pwd)/nginx/nginx.conf
   ```

4. **Test the setup**
   ```bash
   # Write through load balancer
   curl -X POST "http://localhost:8080/api/kv/set?key=foo&value=bar"

   # Read through load balancer (round-robin will distribute)
   curl "http://localhost:8080/api/kv/get?key=foo"

   # Check health
   curl "http://localhost:8080/health"
   ```

5. **Stop the cluster**
   ```bash
   ./stop-cluster.sh
   nginx -s stop
   ```

### Option 2: Docker Deployment

#### Prerequisites
- Docker
- Docker Compose

#### Steps

1. **Start the cluster**
   ```bash
   cd leaderless-kv
   docker-compose up --build
   ```

2. **Test the setup**
   ```bash
   # Write through load balancer
   curl -X POST "http://localhost:8080/api/kv/set?key=foo&value=bar"

   # Read through load balancer
   curl "http://localhost:8080/api/kv/get?key=foo"
   ```

3. **Stop the cluster**
   ```bash
   docker-compose down
   ```

## Demonstrating Inconsistency Window

The read delay of 500ms creates a larger window where reads can observe stale data:

```bash
# Terminal 1: Watch Node 1 directly
watch -n 0.1 "curl -s http://localhost:8081/test/local_read?key=test"

# Terminal 2: Watch Node 2 directly
watch -n 0.1 "curl -s http://localhost:8082/test/local_read?key=test"

# Terminal 3: Write through load balancer
curl -X POST "http://localhost:8080/api/kv/set?key=test&value=v1"
curl -X POST "http://localhost:8080/api/kv/set?key=test&value=v2"

# Terminal 4: Read through load balancer multiple times
for i in {1..10}; do
  echo "Read $i:"
  curl -s "http://localhost:8080/api/kv/get?key=test" | jq
  sleep 0.1
done
```

**Expected behavior**:
- During write propagation, different nodes may have different versions
- The 500ms read delay increases chances of observing inconsistency
- Reads might return older versions if they hit un-updated nodes
- Eventually all nodes converge to the same value

## API Endpoints

### Client Endpoints (via Load Balancer)

**Base URL**: `http://localhost:8080`

#### Write (POST)
```bash
POST /api/kv/set?key=<key>&value=<value>

# Returns 201 Created only after W=N writes succeed
```

#### Read (GET)
```bash
GET /api/kv/get?key=<key>

# Returns local value after 500ms delay (R=1)
```

#### Health Check
```bash
GET /health

# Returns health status of a randomly selected node
```

### Direct Node Endpoints (Bypass Load Balancer)

**Base URLs**:
- `http://localhost:8081` (Node 1)
- `http://localhost:8082` (Node 2)
- etc.

#### Local Read (for testing)
```bash
GET /test/local_read?key=<key>

# Returns local value without delay
```

#### Health
```bash
GET /test/health

# Returns node ID, key count, W, R values
```

## Configuration

### Read Delay
Edit `application-nodeX.properties`:
```properties
cluster.read-delay-ms=500  # Change to desired delay in milliseconds
```

### Write Quorum
Edit `application-nodeX.properties`:
```properties
cluster.write-quorum=5  # W=N for strong consistency
```

### Read Quorum
Edit `application-nodeX.properties`:
```properties
cluster.read-quorum=1  # R=1 for fast reads
```

### Nginx Load Balancing

The default is **round-robin**. To change the strategy, edit `nginx/nginx.conf`:

```nginx
upstream leaderless_cluster {
    # Round-robin (default)
    server localhost:8081;
    server localhost:8082;
    # ...

    # For IP hash (session persistence):
    # ip_hash;

    # For least connections:
    # least_conn;

    # For weighted round-robin:
    # server localhost:8081 weight=3;
    # server localhost:8082 weight=2;
}
```

## Monitoring

### View Logs
```bash
# All nodes
tail -f logs/*.log

# Specific node
tail -f logs/node1.log

# Nginx access log
tail -f nginx/logs/access.log
```

### Check Node Status
```bash
# Via load balancer
curl http://localhost:8080/health

# Direct node access
curl http://localhost:8081/test/health
curl http://localhost:8082/test/health
```

### Monitor Round-Robin Distribution
```bash
# Run multiple reads and observe which nodes handle them
for i in {1..10}; do
  curl -s "http://localhost:8080/api/kv/get?key=foo" -v 2>&1 | grep "Connected to"
done
```

## Troubleshooting

### Port Already in Use
```bash
# Check what's using the port
lsof -i :8080
lsof -i :8081

# Kill the process
kill -9 <PID>
```

### Nginx Not Starting
```bash
# Check config syntax
nginx -t -c $(pwd)/nginx/nginx.conf

# View error log
tail -f nginx/logs/error.log
```

### Nodes Not Communicating
```bash
# Check if all nodes are running
curl http://localhost:8081/test/health
curl http://localhost:8082/test/health
# ... etc

# Check node logs for errors
grep ERROR logs/*.log
```

## Testing W=N Consistency

### Test 1: Verify All Nodes Updated
```bash
# Write a value
curl -X POST "http://localhost:8080/api/kv/set?key=consistency&value=test123"

# Read from each node directly (should all return same version)
curl http://localhost:8081/test/local_read?key=consistency
curl http://localhost:8082/test/local_read?key=consistency
curl http://localhost:8083/test/local_read?key=consistency
curl http://localhost:8084/test/local_read?key=consistency
curl http://localhost:8085/test/local_read?key=consistency
```

### Test 2: Write Failure if Node Down
```bash
# Stop one node
kill <NODE_PID>

# Try to write (should fail because W=N requires all nodes)
curl -X POST "http://localhost:8080/api/kv/set?key=test&value=fail"
# Should return 500 Internal Server Error
```

### Test 3: Inconsistency Window
```bash
# Write rapidly
for i in {1..5}; do
  curl -X POST "http://localhost:8080/api/kv/set?key=race&value=v$i" &
done

# Immediately read multiple times (may see different versions due to 500ms delay)
for i in {1..10}; do
  curl "http://localhost:8080/api/kv/get?key=race"
  sleep 0.05
done
```

## References

- [Nginx Load Balancing](https://docs.nginx.com/nginx/admin-guide/load-balancer/http-load-balancer/)
- [Distributed Systems: W+R>N Quorums](https://en.wikipedia.org/wiki/Quorum_(distributed_computing))
- [Eventual Consistency](https://en.wikipedia.org/wiki/Eventual_consistency)
