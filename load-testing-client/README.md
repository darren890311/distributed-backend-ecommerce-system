# Load Testing Client

Multi-threaded load testing client for testing e-commerce microservices at scale.

## 🎯 Purpose

This client performs comprehensive load testing of the e-commerce system by:
- Creating shopping carts
- Adding items to carts
- Performing checkouts with credit card processing
- Measuring throughput, latency, and system performance
- Monitoring RabbitMQ queue behavior


## 📋 Prerequisites

Before running the load testing client, ensure the following services are running:

### Required Services:
1. **Product Service** (port 8082)
2. **Shopping Cart Service** (port 8084)
3. **Credit Card Authorizer** (port 8080)
4. **RabbitMQ** (ports 5672, 15672)
5. **Warehouse Consumer** (port 8083)

### Required Software:
- Java 17
- Maven 3.6+
- Docker & Docker Compose (for services)

---

## 🚀 Quick Start

### 1. Start All Services
```bash
# From project root
cd cs6650-assignment3

# Start all microservices
docker-compose up -d

# Wait for services to be ready (~30 seconds)
sleep 30

# Verify services are healthy
docker-compose ps
```

### 2. Run Load Test
```bash
# Navigate to load testing client
cd load-testing-client

# Compile the project
mvn clean compile

# Run with default configuration (1,000 checkouts, 4 threads)
mvn exec:java -Dexec.mainClass="com.cs6650.loadtest.LoadTestingClient"
```

### 3. View Results
```bash
# View summary
cat load_test_results.txt

# View detailed metrics
head -20 load_test_metrics.csv

# Check warehouse processing
curl http://localhost:8083/stats
```

---

## ⚙️ Configuration

### Configuration Files

The client supports multiple environment configurations:

- `config.properties` - Default configuration
- `config-local.properties` - Local Docker testing
- `config-aws.properties` - AWS Load Balancer testing

### Default Configuration (`config.properties`)
```properties
# Service URLs (update for your environment)
product.service.url=http://localhost:8082
shopping.cart.service.url=http://localhost:8084
credit.card.service.url=http://localhost:8080
warehouse.service.url=http://localhost:8083

# Load Test Parameters
total.checkouts=1000      # Total checkout operations to perform
items.per.cart=5          # Items to add to each cart
thread.count=4            # Number of concurrent threads
max.retries=5             # Max retry attempts for failed requests

# Timeout Settings (milliseconds)
connection.timeout=10000
request.timeout=30000

# Output Settings
metrics.output.file=load_test_metrics.csv
results.output.file=load_test_results.txt
```

### Switching Environments
```bash
# Use local configuration
mvn exec:java -Dexec.mainClass="com.cs6650.loadtest.LoadTestingClient" -Dexec.args="local"

# Use AWS configuration
mvn exec:java -Dexec.mainClass="com.cs6650.loadtest.LoadTestingClient" -Dexec.args="aws"

# Use default configuration
mvn exec:java -Dexec.mainClass="com.cs6650.loadtest.LoadTestingClient"
```

---

## 📊 Understanding the Output

### Console Output

The client displays three phases:

#### Phase 1: Product Pool Creation
```
Phase 1: Creating product pool...
Created 100/1000 products
...
Created 1000 products
```
- Creates 1,000 products in the Product Service
- Products are reused across all checkouts
- Progress shown every 100 products

#### Phase 2: Load Testing
```
Phase 2: Starting load test...
Target: 10000 checkouts with 8 threads

Launching worker threads...
Progress: 2377/10000 checkouts (23.8%)
...
All workers completed!
```
- Spawns configured number of worker threads
- Each worker performs checkout operations concurrently
- Progress updates every 5 seconds

#### Phase 3: Report Generation
```
=== Load Test Results ===
Total Requests: 70000
Successful Requests: 69047
Throughput: 3596.57 requests/second
...
```
- Calculates statistics from collected metrics
- Generates CSV and text reports

---

### Output Files

#### `load_test_metrics.csv`
Detailed per-request metrics for analysis:
```csv
start_time,request_type,latency_ms,response_code
1761363041285,POST_CREATE_CART,23,201
1761363041309,POST_ADD_ITEM,31,204
1761363041340,POST_CHECKOUT,45,200
```

**Columns:**
- `start_time`: Request timestamp (epoch milliseconds)
- `request_type`: Operation performed
- `latency_ms`: Response time in milliseconds
- `response_code`: HTTP status code

#### `load_test_results.txt`
Summary statistics:
```
=== Load Test Results ===
Total Requests: 70000
Successful Requests: 69047
Success Rate: 98.64%

=== Latency Statistics ===
Mean Latency: 3.82 ms
Median Latency: 2.00 ms
P99 Latency: 24.00 ms

=== Throughput ===
Throughput: 2086.87 requests/second
```

---

## 🔧 How It Works

### Load Testing Flow
```
1. Product Pool Creation (Phase 1)
   ├─ Generate 1,000 random products
   ├─ POST to Product Service
   └─ Store returned product IDs in queue

2. Concurrent Checkout Testing (Phase 2)
   ├─ Spawn N worker threads
   └─ Each worker performs M checkouts:
      ├─ Create shopping cart
      ├─ Add 5 items (from product pool)
      └─ Checkout with fake credit card

3. Metrics Collection & Reporting (Phase 3)
   ├─ Calculate latency statistics
   ├─ Calculate throughput
   └─ Generate CSV and text reports
```

### Complete Checkout Flow
```
Client creates cart
    ↓
POST /shopping-cart → Returns cart_id
    ↓
POST /addItem (5×) → 204 No Content
    ↓  
POST /checkout → Calls Credit Card Service
    ↓
    ├─ 90%: Authorized (200) → Message to RabbitMQ
    │                           ↓
    │                      Warehouse Consumer
    │                      (updates counters)
    │
    └─ 10%: Declined (402) → No warehouse message
```

---

## 📈 Monitoring During Tests

### RabbitMQ Management Console

**Access:** http://localhost:15672 (guest/guest)

**Navigate to:** Queues and Streams → checkoutQueue

**Key Metrics to Monitor:**
- **Queue Depth (Ready)**: Messages waiting to be processed
    - **Goal**: Keep < 1,000 messages
    - **Current**: ~380 messages peak ✅

- **Publish Rate**: How fast Shopping Cart sends messages
- **Consumer Ack Rate**: How fast Warehouse processes messages
- **Goal**: Rates should be approximately equal

**Screenshot Location for Report:**
- Queues → checkoutQueue → Scroll to "Queued messages" graph

### Warehouse Statistics
```bash
# Check processing stats
curl http://localhost:8083/stats

# Example output:
# Total Orders: 8999 | Tracked Products: 1000
```

**Metrics:**
- **Total Orders**: Number of authorized checkouts processed
- **Tracked Products**: Unique product IDs encountered

---

## 🎛️ Tuning Guidelines

### Finding Optimal Thread Count

**Test different configurations:**
```bash
# Edit config.properties and test each:
thread.count=4   # Low concurrency
thread.count=8   # Medium concurrency
thread.count=16  # High concurrency
thread.count=32  # Very high concurrency
```

**Document results:**

| Threads | Throughput (req/s) | Mean Latency | P99 Latency | Max Queue Depth |
|---------|-------------------|--------------|-------------|-----------------|
| 4       | ~2,000            | ~2 ms        | ~8 ms       | ~0              |
| 8       | ~3,600            | ~2 ms        | ~10 ms      | ~380            |
| 16      | ~3,600            | ~4 ms        | ~18 ms      | ~4,000          |
| 32      | Test and record   | -            | -           | -               |

**Observations:**
- Throughput plateaus around 8-16 threads (~3,600 req/sec)
- More threads don't improve throughput significantly
- Lower thread counts have better latency
- Queue depth increases with more threads

---

## 🧪 Testing Scenarios

### Scenario 1: Small Smoke Test (Verify Everything Works)
```properties
total.checkouts=100
thread.count=2
```

**Purpose:** Quick verification that all services are responding  
**Duration:** ~5 seconds  
**Expected:** All checkouts succeed, minimal queue buildup

### Scenario 2: Performance Baseline (Current Configuration)
```properties
total.checkouts=1000
thread.count=4
```

**Purpose:** Establish baseline performance metrics  
**Duration:** ~15 seconds  
**Expected:** ~2,000 req/sec throughput

### Scenario 3: Load Testing (Medium Scale)
```properties
total.checkouts=10000
thread.count=8
```

**Purpose:** Test system under moderate load  
**Duration:** ~20-35 seconds  
**Expected:** ~3,600 req/sec, queue depth ~380-7,000

### Scenario 4: Stress Testing (High Scale)
```properties
total.checkouts=50000
thread.count=16
```

**Purpose:** Identify system limits  
**Duration:** ~1-2 minutes  
**Expected:** Observe queue behavior under sustained load

### Scenario 5: Full Assignment Test (AWS Deployment)
```properties
total.checkouts=200000
thread.count=32
```

**Purpose:** Final assignment requirement  
**Duration:** ~3-5 minutes  
**Expected:** Max throughput, queue depth monitoring critical

---

## 🐛 Troubleshooting

### Issue: Connection Refused

**Symptoms:**
```
Load test failed: Connection refused to http://localhost:8084
```

**Solution:**
```bash
# Check if services are running
docker-compose ps

# If not running, start them
docker-compose up -d

# Wait for services to be ready
sleep 30

# Verify
curl http://localhost:8084/actuator/health
```

---

### Issue: All Checkouts Failing

**Symptoms:**
```
Failed Checkouts: 1000
Successful Checkouts: 0
```

**Solution:**
```bash
# Check Shopping Cart Service logs
docker logs shopping-cart-service --tail 50

# Check Credit Card Authorizer logs
docker logs credit-card-authorizer --tail 50

# Test endpoints manually
curl -X POST http://localhost:8084/shopping-cart \
  -H "Content-Type: application/json" \
  -d '{"customer_id": 1}'
```

---

### Issue: High Failed Requests Rate

**Symptoms:**
```
Success Rate: 85.00%
Failed Requests: 10500
```

**Solution:**
- Increase timeouts in `config.properties`:
```properties
  connection.timeout=20000
  request.timeout=60000
```
- Reduce thread count (less contention)
- Check service logs for errors

---

### Issue: RabbitMQ Queue Keeps Growing

**Symptoms:**
- Queue depth > 10,000 messages
- Never drains back to zero
- Pointy sawtooth pattern (/\/\/\)

**Solution:**
- **Reduce client threads** (slower publishing)
- **Increase warehouse consumer threads** (faster processing)
- **Goal**: Publish rate ≈ Consumer rate

**Coordinate with warehouse team:**
```properties
# Warehouse Service needs more consumer threads
spring.rabbitmq.listener.simple.concurrency=2
spring.rabbitmq.listener.simple.max-concurrency=4
```

---

## 📊 Metrics Explained

### Request-Level Metrics

**Total Requests:**
```
For N checkouts with 5 items per cart:
N × POST /shopping-cart  = N requests
N × 5 × POST /addItem    = 5N requests
N × POST /checkout       = N requests
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Total = 7N requests
```

**Successful Requests:**
- HTTP requests that received valid responses (2xx status codes)
- Includes 200, 201, 204, AND 402 responses
- **402 (Payment Declined) counts as successful HTTP request!**

**Failed Requests:**
- HTTP requests that failed after all retries
- Network timeouts, connection errors, or server failures
- Target: < 2% failure rate

### Business-Level Metrics

**Successful Checkouts:**
- Checkout operations that completed (both authorized AND declined)
- **200 (Authorized)**: Payment approved, order sent to warehouse
- **402 (Declined)**: Payment declined, no order sent
- Target: 100% (all checkouts should complete)

**Failed Checkouts:**
- Checkout operations that encountered server errors
- 5xx responses or timeouts
- Target: 0%

### RabbitMQ Metrics

**Messages to Warehouse:**
```
Expected: Total Checkouts × 90% approval rate

Example:
10,000 checkouts × 90% = ~9,000 orders to warehouse
```

Only **authorized checkouts** send messages to RabbitMQ.

---

## 🔄 Testing Workflow

### Step-by-Step Testing Process

#### 1. **Setup Environment**
```bash
# From project root
cd cs6650-assignment3

# Start all services
docker-compose up -d

# Wait for services to initialize
sleep 30

# Verify all services healthy
curl http://localhost:8082/actuator/health
curl http://localhost:8084/actuator/health
curl http://localhost:8080/actuator/health
curl http://localhost:8083/stats
```

#### 2. **Configure Test**

Edit `load-testing-client/src/main/resources/config.properties`:
```properties
# For small test (verify everything works)
total.checkouts=1000
thread.count=4

# For performance testing
total.checkouts=10000
thread.count=8

# For full assignment test
total.checkouts=200000
thread.count=32
```

#### 3. **Open Monitoring**

**RabbitMQ Console:**
```bash
# Open in browser
open http://localhost:15672

# Login: guest/guest
# Navigate to: Queues and Streams → checkoutQueue
# Scroll down to see graphs
```

**Terminal monitoring (optional):**
```bash
# Watch warehouse stats
watch -n 2 'curl -s http://localhost:8083/stats'
```

#### 4. **Run Test**
```bash
cd load-testing-client

# Compile (if not already compiled)
mvn clean compile

# Run test
mvn exec:java -Dexec.mainClass="com.cs6650.loadtest.LoadTestingClient"
```

#### 5. **Collect Results**

**Generated files:**
- `load_test_metrics.csv` - Detailed per-request data
- `load_test_results.txt` - Summary statistics

**RabbitMQ screenshots:**
- Save queue depth graph
- Save message rates graph

**Warehouse verification:**
```bash
curl http://localhost:8083/stats
```

---

## 🧹 Cleanup Between Tests

### Quick Reset (Keep Services Running)
```bash
# Restart services to clear databases
docker-compose restart product-service shopping-cart-service warehouse-service

# Wait for restart
sleep 15

# Verify clean state
curl http://localhost:8082/products/1  # Should return 404
curl http://localhost:8083/stats       # Should show 0 orders

# Clean previous test outputs
cd load-testing-client
rm -f *.csv *.txt
```

### Full Reset (Nuclear Option)
```bash
# Stop everything and remove volumes
docker-compose down -v

# Restart fresh
docker-compose up -d
sleep 30

# Clean outputs
cd load-testing-client
rm -f *.csv *.txt
```

---

## 📈 Performance Testing Guide

### Recommended Testing Sequence

#### Test 1: Baseline (Verify Functionality)
```properties
total.checkouts=1000
thread.count=4
```
**Expected Results:**
- Throughput: ~2,000 req/sec
- Queue depth: Near zero
- Success rate: > 98%

#### Test 2: Medium Load
```properties
total.checkouts=10000
thread.count=8
```
**Expected Results:**
- Throughput: ~3,600 req/sec
- Queue depth: 300-1,000 messages
- Success rate: > 98%

#### Test 3: Find Maximum Throughput
```properties
total.checkouts=10000
thread.count=16
```
**Expected Results:**
- Throughput: ~3,600 req/sec (similar to 8 threads)
- Queue depth: 2,000-4,000 messages
- Diminishing returns beyond 16 threads

#### Test 4: Stress Test
```properties
total.checkouts=50000
thread.count=32
```
**Expected Results:**
- Throughput: Plateaus around 3,500-4,000 req/sec
- Queue depth: Monitor for stability
- Identify system bottlenecks

---

## 🎯 Assignment Requirements Checklist

### What This Client Provides:

- [x] Sends 200k checkout messages (configurable)
- [x] Multi-threaded for maximum throughput
- [x] Records detailed metrics:
    - [x] Start time (timestamp)
    - [x] Request type (POST_CREATE_CART, POST_ADD_ITEM, POST_CHECKOUT)
    - [x] Latency (milliseconds)
    - [x] Response code (200, 201, 204, 402, etc.)
- [x] Calculates statistics:
    - [x] Mean latency
    - [x] Median latency
    - [x] P99 latency
    - [x] Min/Max latency
    - [x] Throughput (requests/second)
- [x] Configurable thread counts
- [x] Retry logic with exponential backoff
- [x] CSV output for detailed analysis

---

## 🔍 Monitoring & Validation

### Verify Correct Behavior

#### 1. Check All Services Respond
```bash
# Test each service
curl http://localhost:8082/actuator/health  # Product
curl http://localhost:8084/actuator/health  # Shopping Cart
curl http://localhost:8080/actuator/health  # Credit Card
curl http://localhost:8083/stats            # Warehouse
```

#### 2. Test Complete Flow Manually
```bash
# Create cart
CART=$(curl -s -X POST http://localhost:8084/shopping-cart \
  -H "Content-Type: application/json" \
  -d '{"customer_id": 1}' | grep -o '"shopping_cart_id":[0-9]*' | grep -o '[0-9]*')

echo "Created cart: $CART"

# Add item
curl -X POST http://localhost:8084/shopping-carts/$CART/addItem \
  -H "Content-Type: application/json" \
  -d '{"product_id": 1, "quantity": 2}'

# Checkout
curl -X POST http://localhost:8084/shopping-carts/$CART/checkout \
  -H "Content-Type: application/json" \
  -d '{"credit_card_number": "1234-5678-9012-3456"}'
```

#### 3. Verify RabbitMQ Queue
```bash
# Check queue exists and is empty before test
curl -s -u guest:guest http://localhost:15672/api/queues/%2F/checkoutQueue \
  | grep -o '"messages":[0-9]*'

# Expected before test: "messages":0
```

#### 4. Verify Warehouse Processing
```bash
# Before test
curl http://localhost:8083/stats
# Expected: Total Orders: 0 | Tracked Products: 0

# After test (with 10,000 checkouts)
curl http://localhost:8083/stats
# Expected: Total Orders: ~9000 | Tracked Products: 1000
```

---

## 📝 Expected Results

### For 10,000 Checkouts with 8 Threads:

**Performance:**
- Throughput: 2,000-3,600 req/sec
- Wall time: 20-35 seconds
- Mean latency: 2-4 ms
- P99 latency: 10-25 ms

**Operations:**
- Total HTTP requests: 70,000
- Successful requests: ~69,000 (98%+)
- Failed requests: ~1,000 (2%)
- Successful checkouts: 10,000 (100%)

**Warehouse:**
- Orders processed: ~9,000 (90% approval rate)
- Tracked products: 1,000 (all products used)

**RabbitMQ:**
- Messages published: ~9,000
- Peak queue depth: 300-7,000 (varies by warehouse capacity)
- Pattern: Plateau or smooth curve

---

## ⚠️ Known Issues & Limitations

### Issue 1: Product Service Validation

**Problem:** Product Service requires `product_id` in POST request (validation)

**Workaround:** `ProductGenerator` sends dummy `product_id` that server ignores

**Impact:** None - server regenerates correct IDs

### Issue 2: 204 No Content Handling

**Problem:** `POST /addItem` returns 204 with no response body

**Solution:** Special handling in `executeWithRetry()` to avoid NullPointerException
```java
if (statusCode == 204) {
    if (response.getEntity() != null) {
        EntityUtils.consume(response.getEntity());
    }
    return "";
}
```

### Issue 3: Warehouse Consumer Bottleneck

**Problem:** Single-threaded warehouse consumer limits throughput

**Current:** ~245 messages/second processing rate

**Solution:** Increase warehouse consumer concurrency (coordinate with teammate)
```properties
# In Warehouse Service
spring.rabbitmq.listener.simple.concurrency=2
spring.rabbitmq.listener.simple.max-concurrency=4
```

---

## 🤝 Integration with Other Components

### Dependencies:

**Product Service:**
- Creates products during Phase 1
- Returns auto-generated product IDs
- All products stored in H2 in-memory database

**Shopping Cart Service:**
- Creates carts
- Adds items to carts
- Processes checkouts
- Publishes messages to RabbitMQ queue

**Credit Card Authorizer:**
- Called by Shopping Cart during checkout
- Returns 200 (90%) or 402 (10%) randomly

**RabbitMQ:**
- Queues messages from Shopping Cart
- Delivers to Warehouse Consumer
- Management console for monitoring

**Warehouse Consumer:**
- Processes order messages
- Tracks total orders and product quantities
- Sends manual acknowledgements

---

## 🏗️ Project Structure
```
load-testing-client/
├── src/
│   ├── main/
│   │   ├── java/com/cs6650/loadtest/
│   │   │   ├── LoadTestingClient.java       # Main entry point
│   │   │   ├── LoadTestWorker.java          # Worker thread
│   │   │   ├── config/
│   │   │   │   └── LoadTestConfig.java      # Configuration loader
│   │   │   ├── model/
│   │   │   │   ├── Product.java             # Product data model
│   │   │   │   ├── CartItem.java            # Cart item model
│   │   │   │   ├── CheckoutRequest.java     # Checkout request
│   │   │   │   ├── ShoppingCart.java        # Cart response
│   │   │   │   └── RequestMetrics.java      # Metrics data
│   │   │   └── util/
│   │   │       ├── HttpClientService.java   # HTTP client with retry
│   │   │       ├── ProductGenerator.java    # Random product generator
│   │   │       ├── CreditCardGenerator.java # Fake CC generator
│   │   │       ├── CSVMetricsWriter.java    # CSV output
│   │   │       └── MetricsCalculator.java   # Statistics calculator
│   │   └── resources/
│   │       ├── config.properties            # Default config
│   │       ├── config-local.properties      # Local testing
│   │       └── config-aws.properties        # AWS testing
│   └── test/                                # Unit tests (future)
├── pom.xml                                  # Maven dependencies
├── README.md                                # This file
└── load_test_*.csv/txt                     # Generated outputs (gitignored)
```

---

## 📦 Building Executable JAR

For running without Maven:
```bash
# Package into executable JAR
mvn clean package

# Run the JAR
java -jar target/load-testing-client.jar

# Run with custom config
java -jar target/load-testing-client.jar local
java -jar target/load-testing-client.jar aws
```

---

## 🧪 Testing Different Configurations

### Create Test Script (`run-tests.sh`)
```bash
#!/bin/bash

# Array of thread counts to test
THREAD_COUNTS=(4 8 12 16 24 32)

for threads in "${THREAD_COUNTS[@]}"; do
  echo "======================================"
  echo "Testing with $threads threads"
  echo "======================================"
  
  # Update config
  sed -i '' "s/thread.count=.*/thread.count=$threads/" \
    src/main/resources/config.properties
  
  # Run test
  mvn exec:java -Dexec.mainClass="com.cs6650.loadtest.LoadTestingClient"
  
  # Save results
  cp load_test_results.txt "results_${threads}_threads.txt"
  cp load_test_metrics.csv "metrics_${threads}_threads.csv"
  
  # Brief pause between tests
  sleep 10
  
  echo ""
  echo "Completed test with $threads threads"
  echo ""
done

echo "All tests complete!"
echo "Results saved as: results_*_threads.txt"
```

**Usage:**
```bash
chmod +x run-tests.sh
./run-tests.sh
```

---

## 📋 Pre-Test Checklist

Before running load tests:

- [ ] All Docker services running (`docker-compose ps`)
- [ ] Services show healthy status
- [ ] RabbitMQ queue is empty (0 messages)
- [ ] Warehouse stats show 0 orders
- [ ] Product Service database clean (no existing products)
- [ ] Previous test output files deleted
- [ ] RabbitMQ Management Console open in browser
- [ ] Configuration file updated with desired parameters
- [ ] Sufficient system resources (CPU/memory available)

---

## 📞 Support & Questions

### Common Questions:

**Q: Why does the test create 1,000 products first?**  
A: Products are reused across all checkouts via a BlockingQueue, avoiding the need to create products for each checkout.

**Q: Why do some checkouts return 402?**  
A: Credit Card Authorizer randomly declines 10% of payments (simulation of real-world behavior).

**Q: Why are there failed requests if all checkouts succeed?**  
A: Failed requests are HTTP-level failures (timeouts, network issues) that occurred during intermediate steps but were retried successfully.

**Q: How do I get queue depth below 1,000?**  
A: Either reduce client threads or coordinate with teammate to increase warehouse consumer threads.

---

**Last Updated**: October 27, 2025  
**Version**: 1.0.0  
**Status**: ✅ Fully Functional
```
