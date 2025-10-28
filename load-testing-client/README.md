# Load Testing Client
## 📋 Overview

This PR adds a comprehensive multi-threaded load testing client for CS6650 Assignment 3. The client is designed to test the e-commerce microservices system under heavy load, specifically focusing on optimizing RabbitMQ queue management and maximizing system throughput.

## 🎯 Purpose

- Load test the complete checkout flow: Create cart → Add items → Checkout → Warehouse processing
- Measure system performance metrics (throughput, latency, success rate)
- Optimize RabbitMQ queue configuration to prevent message buildup
- Find optimal client and warehouse consumer thread counts
- Validate system can handle 200,000 checkout operations

## ✨ Features

### Core Functionality
- Multi-threaded concurrent checkout operations
- Configurable client thread count (tested: 8, 12, 16 threads)
- Product pool creation for realistic test data
- Complete checkout flow testing
- Comprehensive metrics collection (CSV output)
- Statistical analysis (mean, median, P99, min, max latency)
- Environment-based configuration (local, AWS)

### Performance Features
- HTTP connection pooling for efficiency
- Thread-safe metrics collection
- Non-blocking product queue for zero wait time
- Optimized for high throughput (4,000+ req/sec)

### Monitoring & Reporting
- CSV metrics file with per-request details
- Text summary with key statistics
- Throughput calculation
- Latency percentiles (P99)
- Success/failure breakdown

## 📂 Files Added
```
load-testing-client/
├── src/
│   ├── main/
│   │   ├── java/com/cs6650/loadtest/
│   │   │   ├── LoadTestingClient.java          # Main entry point
│   │   │   ├── LoadTestWorker.java             # Worker thread implementation
│   │   │   ├── config/
│   │   │   │   └── LoadTestConfig.java         # Configuration loader
│   │   │   ├── model/
│   │   │   │   ├── Product.java                # Product data model
│   │   │   │   ├── CartItem.java               # Cart item model
│   │   │   │   ├── CheckoutRequest.java        # Checkout request model
│   │   │   │   ├── ShoppingCart.java           # Shopping cart response
│   │   │   │   └── RequestMetrics.java         # Metrics data model
│   │   │   └── util/
│   │   │       ├── HttpClientService.java      # HTTP client with retry logic
│   │   │       ├── ProductGenerator.java       # Random product generator
│   │   │       ├── CreditCardGenerator.java    # Fake credit card generator
│   │   │       ├── CSVMetricsWriter.java       # CSV output writer
│   │   │       └── MetricsCalculator.java      # Statistics calculator
│   │   └── resources/
│   │       ├── config.properties               # Default configuration
│   │       ├── config-local.properties         # Local Docker configuration
│   │       └── config-aws.properties           # AWS deployment configuration
│   └── test/                                   # Unit tests (future)
├── pom.xml                                     # Maven dependencies
└── README.md                                   # Comprehensive documentation
```

## 🚀 How to Run

### Prerequisites

Ensure all microservices are running:
```bash
# From project root
cd cs6650-assignment3
docker-compose up -d

# Wait for services to be ready
sleep 30

# Verify services are healthy
curl http://localhost:8082/actuator/health  # Product Service
curl http://localhost:8084/actuator/health  # Shopping Cart Service
curl http://localhost:8080/actuator/health  # Credit Card Authorizer
curl http://localhost:8083/stats            # Warehouse Service
```

### Running the Load Test (Local)
```bash
# Navigate to load testing client
cd load-testing-client

# Compile the project (first time or after changes)
mvn clean compile

# Run with default configuration (local Docker)
mvn exec:java -Dexec.mainClass="com.cs6650.loadtest.LoadTestingClient"

# Or explicitly specify local environment
mvn exec:java -Dexec.mainClass="com.cs6650.loadtest.LoadTestingClient" -Dexec.args="local"
```

### Running Against AWS Deployment

**⚠️ IMPORTANT: Update AWS URLs First!**

Before running against AWS, you MUST update the service URLs in `config-aws.properties`:


**Update these URLs with your actual AWS Load Balancer URL:**
```properties
# Replace with YOUR AWS Application Load Balancer DNS name
product.service.url=http://YOUR-ALB-DNS-NAME.us-east-1.elb.amazonaws.com/product
shopping.cart.service.url=http://YOUR-ALB-DNS-NAME.us-east-1.elb.amazonaws.com/shopping-cart
credit.card.service.url=http://YOUR-ALB-DNS-NAME.us-east-1.elb.amazonaws.com/credit-card-authorizer
warehouse.service.url=http://YOUR-WAREHOUSE-URL:8083
```

**How to get your AWS Load Balancer DNS:**
```bash
# Using AWS CLI
aws elbv2 describe-load-balancers \
  --query 'LoadBalancers[*].[LoadBalancerName,DNSName]' \
  --output table

# Or from AWS Console:
# EC2 → Load Balancers → Select your ALB → Copy "DNS name"
```

**Then run the test:**
```bash
# Recompile to pick up new URLs
mvn clean compile

# Run against AWS
mvn exec:java -Dexec.mainClass="com.cs6650.loadtest.LoadTestingClient" -Dexec.args="aws"
```

### Configuration Options

Edit `src/main/resources/config.properties` to customize:
```properties
# Number of checkout operations
total.checkouts=200000        # Reduce for quick tests (e.g., 1000, 10000)

# Number of concurrent client threads
thread.count=12               # Tested optimal: 12 threads

# Items added to each cart
items.per.cart=5

# Retry configuration
max.retries=5

# Timeouts (milliseconds)
connection.timeout=10000
request.timeout=30000
```

**Quick test example (1,000 checkouts):**
```bash
# Change in config.properties:
total.checkouts=1000
thread.count=4

# Run test (completes in ~15 seconds)
mvn exec:java -Dexec.mainClass="com.cs6650.loadtest.LoadTestingClient"
```

## 📊 Output Files

After each test run, two files are generated:

### 1. `load_test_metrics.csv`
Detailed per-request metrics for analysis:
```csv
start_time,request_type,latency_ms,response_code
1730073041285,POST_CREATE_CART,23,201
1730073041309,POST_ADD_ITEM,31,204
1730073041340,POST_CHECKOUT,45,200
```

**Columns:**
- `start_time`: Request timestamp (epoch milliseconds)
- `request_type`: Operation performed (CREATE_CART, ADD_ITEM, CHECKOUT)
- `latency_ms`: Response time in milliseconds
- `response_code`: HTTP status code (200, 201, 204, 402, etc.)

### 2. `load_test_results.txt`
Summary statistics:
```
=== Load Test Results ===
Total Requests: 1,400,000
Successful Requests: 1,380,236
Success Rate: 98.59%

=== Latency Statistics ===
Mean Latency: 2.93 ms
Median Latency: 2.00 ms
P99 Latency: 12.00 ms

=== Throughput ===
Throughput: 4,082.45 requests/second
Wall Time: 342.92 seconds

=== Checkout Results ===
Successful Checkouts: 199,992
Failed Checkouts: 0
```

## 🧪 Test Results

### Optimal Configuration Found

**Client Configuration:**
- Client threads: **12**
- Warehouse consumer threads: **4-8** (min-max concurrency)

**Performance Achieved:**
- Throughput: **4,082 requests/second**
- Mean latency: **2.93 ms**
- P99 latency: **12 ms**
- Success rate: **98.59%**
- Total checkouts: **199,992 / 200,000**

**RabbitMQ Queue Behavior:**
- Peak queue depth: **~200 messages** (target: < 1,000) 
- Queue pattern: **Flat plateau** (no pointy growth) 
- Production rate: **580 msg/sec**
- Consumption rate: **580 msg/sec** (perfectly balanced) 

### Configuration Comparison

| Client Threads | Throughput (req/s) | Mean Latency (ms) | Peak Queue Depth | Result |
|----------------|-------------------|-------------------|------------------|---------|
| 8              | 2,497             | 3.20              | ~7.5 msgs        | Good |
| **12**         | **4,082**         | **2.93**          | **~200 msgs**    | **Optimal**  |
| 16             | 2,520             | 6.33              | ~1,000 msgs      | Degraded |

**Conclusion:** 12 client threads with 4-8 warehouse consumer threads provides optimal performance.

## 🔧 Technical Implementation

### Architecture

**Three-Phase Execution:**
1. **Phase 1**: Product pool creation (1,000 products)
   - Creates products concurrently
   - Stores product IDs in thread-safe BlockingQueue
   - Workers pull from queue (zero wait time)

2. **Phase 2**: Concurrent load testing
   - Spawns N worker threads
   - Each worker performs complete checkout flow:
     - Create shopping cart → Add 5 items → Checkout
   - Collects metrics for every request

3. **Phase 3**: Report generation
   - Calculates statistics from collected metrics
   - Generates CSV and text reports
   - Displays summary in console

### Key Design Decisions

**1. Product Pool Pattern**
- Pre-creates 1,000 products before load testing
- Workers reuse products via BlockingQueue
- Eliminates product creation bottleneck during testing
- More realistic test scenario

**2. HTTP Client with Retry Logic**
- Exponential backoff (100ms, 200ms, 400ms, 800ms, 1600ms)
- Max 5 retries for transient failures
- Connection pooling for efficiency
- Thread-safe implementation

**3. Thread-Safe Metrics Collection**
- ConcurrentLinkedQueue for lock-free operations
- Minimal contention during high-load testing
- CSV writer handles concurrent writes safely

**4. Special Handling for 204 No Content**
- Shopping Cart's `addItem` returns 204 (no response body)
- Custom handling prevents NullPointerException
- Properly consumes entity to free connection




## 🔄 Integration with Other Components

### Dependencies
- **Product Service**: Creates products during Phase 1
- **Shopping Cart Service**: Creates carts, adds items, processes checkouts
- **Credit Card Authorizer**: Approves/declines payments (90%/10% split)
- **RabbitMQ**: Queues checkout messages
- **Warehouse Service**: Processes orders from queue

### Data Flow
```
Load Test Client
    ↓ (Phase 1: Create 1,000 products)
Product Service
    ↓ (Phase 2: Create cart, add items, checkout)
Shopping Cart Service → Credit Card Authorizer
    ↓ (90% approved)
RabbitMQ Queue
    ↓
Warehouse Service
    ↓
Order Processing Complete
```


## 📚 Additional Resources

### Monitoring During Tests

**RabbitMQ Management Console:**
```bash
# Access at: http://localhost:15672
# Login: guest/guest
# Navigate to: Queues and Streams → checkoutQueue
```

### Troubleshooting

**Services not responding:**
```bash
# Check if services are running
docker-compose ps

# Restart services
docker-compose restart product-service shopping-cart-service warehouse-service
sleep 30
```
