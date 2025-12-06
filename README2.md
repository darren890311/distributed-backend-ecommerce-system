# CS6650 Assignment 5 - E-Commerce Microservices with Distributed Databases

**Team Members:** Christy (Qingyi), [Add other members]

---

## Table of Contents

1. [System Architecture](#system-architecture)
2. [Database Design and CAP Trade-offs](#database-design-and-cap-trade-offs)
3. [Microservices Implementation](#microservices-implementation)
4. [Use Cases](#use-cases)
5. [Load Testing Results](#load-testing-results)
6. [Autoscaling Evidence](#autoscaling-evidence)
7. [Assumptions and Reasoning](#assumptions-and-reasoning)
8. [Deployment Instructions](#deployment-instructions)

---

## 1. System Architecture

### 1.1 Architecture Diagram

```
                              Internet
                                  |
                                  v
                    +---------------------------+
                    |  Application Load         |
                    |  Balancer (ALB)           |
                    |  Port 80                  |
                    +-------------+-------------+
                                  |
         +------------------------+------------------------+
         |                        |                        |
         v                        v                        v
+-----------------+    +-----------------+    +-----------------+
| Product Service |    | Shopping Cart   |    | Credit Card     |
|   (port 8082)   |    |   Service       |    |  Authorizer     |
|                 |    |   (port 8084)   |    |   (port 8080)   |
| Auto-scale:     |    |                 |    |                 |
| CPU 70%, max 3  |    | Auto-scale:     |    | Auto-scale:     |
+---------+-------+    | Memory 70%,max 3|    | CPU 70%, max 3  |
          |            +--------+--------+    +-----------------+
          |                     |
          |                     +------------------+
          |                     |                  |
          v                     v                  v
+-----------------+    +-----------------+    +-----------------+
|  LEADERLESS KV  |    | LEADER-FOLLOWER |    |    RabbitMQ     |
|   (port 8090)   |    |   KV (port 8080)|    |   (port 5672)   |
|                 |    |                 |    +--------+--------+
|   W=N, R=1      |    |   W=1, R=1      |             |
|   Products      |    |   Shopping Carts|             v
+-----------------+    +-----------------+    +-----------------+
                                              | Warehouse       |
                                              |   Service       |
                                              |   (port 8083)   |
                                              |                 |
                                              | Auto-scale:     |
                                              | Memory 70%,max 3|
                                              +-----------------+
```

### 1.2 Message Flow Between Services

**Use Case 1: Add Item to Cart**
```
Client -> ALB -> Shopping Cart Service
                    |
                    +-> Leader-Follower KV (get cart)
                    +-> Product Service -> Leaderless KV (validate product)
                    +-> Leader-Follower KV (save cart)
```

**Use Case 2: Checkout**
```
Client -> ALB -> Shopping Cart Service
                    |
                    +-> Leader-Follower KV (get cart)
                    +-> Credit Card Authorizer (90% approve)
                    +-> RabbitMQ (publish order) -> Warehouse Service
                    +-> Leader-Follower KV (mark checked out)
```

---

## 2. Database Design and CAP Trade-offs

### 2.1 Database Selection Rationale

| Service | Database Type | Configuration | Rationale |
|---------|---------------|---------------|-----------|
| Product Service | Leaderless KV | W=N, R=1 | Read-heavy workload (90% reads), products rarely change |
| Shopping Cart | Leader-Follower KV | W=1, R=1 | Write-heavy workload (60% writes), fast cart updates required |

### 2.2 Leaderless KV Database (Product Service)

**Architecture:**
- 5 peer nodes, any node can accept writes
- Write coordinator forwards to all peers
- Load balancer distributes reads across nodes

**Configuration:**
- W = N (all nodes): Write must reach ALL nodes before returning success
- R = 1 (single node): Read from any single node via load balancer

**Trade-off Analysis:**
- Writes are slow (must propagate to all nodes)
- Reads are fast (any node can respond immediately)
- Suitable for products: rarely updated, frequently read

### 2.3 Leader-Follower KV Database (Shopping Cart)

**Architecture:**
- Single leader accepts all writes
- Multiple followers receive async replication
- Dynamic follower registration via /internal/register endpoint

**Configuration:**
- W = 1: Leader stores locally and returns immediately (async replication)
- R = 1: Read from leader only (always returns latest data)

**Trade-off Analysis:**
- Writes are fast (no waiting for replication)
- Brief inconsistency window on followers (acceptable for personal cart data)
- Suitable for carts: frequent updates, single-user data with no conflicts

### 2.4 CAP Theorem Decision

**Choice: AP (Availability + Partition Tolerance)**

**Sacrificed: Strong Consistency (accepting Eventual Consistency)**

**Justification:**
1. Shopping carts are user-specific with no conflict risk between users
2. Brief stale reads are acceptable; users will not notice sub-second delays
3. "Add to Cart" operations must be fast (under 500ms) for good user experience
4. Checkout validates final state from leader, ensuring accuracy when it matters
5. System must continue operating during network partitions

### 2.5 Transaction Stubs

Both KV databases implement transaction stubs as required:

```
Endpoints:
POST /api/kv/transaction/begin    - Prints "Transaction begun for key: X"
POST /api/kv/transaction/end      - Prints "Transaction ended for key: X"
POST /api/kv/transaction/abort    - Prints "Transaction aborted for key: X"
```

Transaction calls are placed in ShoppingCartService.checkoutCart():
- beginTransaction() called before payment authorization
- endTransaction() called after successful checkout
- abortTransaction() called on payment decline or any error

---

## 3. Microservices Implementation

### 3.1 Service Overview

| Service | Port | Database | Key Functions |
|---------|------|----------|---------------|
| Product Service | 8082 | Leaderless KV | Create and retrieve products |
| Shopping Cart Service | 8084 | Leader-Follower KV | Cart CRUD operations, checkout |
| Credit Card Authorizer | 8080 | None | Authorize payments (90% approve, 10% decline) |
| Warehouse Service | 8083 | None | Receive and process ship orders via RabbitMQ |

### 3.2 Business Logic Delays

All endpoints include simulated delays (100-1000ms linear random) to stimulate autoscaling:

```java
private void addBusinessLogicDelay() {
    long delay = 100 + ThreadLocalRandom.current().nextInt(900);
    Thread.sleep(delay);
}
```

### 3.3 RabbitMQ Integration

The Warehouse Service receives ship orders via RabbitMQ (fire-and-forget pattern):

**Publisher (Shopping Cart Service):**
```java
rabbitTemplate.convertAndSend("checkoutQueue", orderMessage);
```

**Consumer (Warehouse Service):**
```java
@RabbitListener(queues = "checkoutQueue")
public void receiveMessage(Message message) {
    warehouseService.recordOrder(orderId, products);
    channel.basicAck(deliveryTag, false);
}
```

Ship operations always succeed as specified in the requirements.

---

## 4. Use Cases

### 4.1 Use Case 1: Customer Adds Item to Cart

**Prerequisites:**
- Customer is logged in (customerId available in requests)

**Steps:**
1. Customer selects a product
2. Customer chooses quantity
3. Customer clicks "Add to Cart"
4. System creates cart if none exists
5. System validates product exists via Product Service
6. System adds item to cart in Leader-Follower KV

**Error Handling:**
- Product not found: 404 response
- Invalid quantity: 400 response
- Cart not found: Creates new cart automatically

**API Calls:**
```
POST /shopping-cart
Body: {"customer_id": 123}

POST /shopping-carts/{id}/addItem
Body: {"product_id": 1, "quantity": 2}
```

### 4.2 Use Case 2: Customer Checks Out

**Prerequisites:**
- Customer has items in cart

**Steps:**
1. Customer enters credit card information
2. Customer clicks "Checkout"
3. System calls beginTransaction()
4. Credit Card Service authorizes (90% approve, 10% decline)
5. If approved: publish order to RabbitMQ for warehouse
6. Mark cart as CHECKED_OUT in database
7. Call endTransaction()

**Error Handling:**
- Payment declined (10%): 402 response, abortTransaction() called
- Empty cart: 400 response
- Cart not found: 404 response
- Any error: abortTransaction() called

**API Calls:**
```
POST /shopping-carts/{id}/checkout
Body: {"credit_card_number": "1234-5678-9012-3456"}
```

---

## 5. Load Testing Results

### 5.1 Test Configuration

| Parameter | Value |
|-----------|-------|
| Tool | Locust |
| Concurrent Users | 500-1500 |
| Spawn Rate | 50-100 users/second |
| Test Duration | 40+ minutes |
| Target | AWS Application Load Balancer |

### 5.2 Results at 500 Concurrent Users

```
Type     Name                # Requests  # Fails  Median   95%ile   99%ile   Avg
----------------------------------------------------------------------------------
POST     UC1.1 Create Cart   24,066      0        1500ms   2300ms   2600ms   1524ms
POST     UC1.2 Add Item      58,215      0        3600ms   4800ms   5300ms   3637ms
POST     UC1.3 Checkout      23,702      0        2600ms   3600ms   4000ms   2564ms
GET      UC2 View Product    45,578      0        1200ms   1900ms   2200ms   1225ms
----------------------------------------------------------------------------------
         Aggregated          151,561     0        2200ms   4500ms   5000ms   2408ms

Throughput: 112.1 requests/second
Failure Rate: 0%
```

### 5.3 Results at 1500 Concurrent Users

At 1500 users, the system became overloaded:
- 502 Bad Gateway errors occurred
- This indicates backend services could not handle the request volume
- This condition triggers autoscaling

### 5.4 Latency Analysis

Latencies are higher due to intentional stacking of business logic delays:

| Operation | Delay Sources | Expected Range |
|-----------|---------------|----------------|
| Create Cart | Controller delay + KV write | 200-2000ms |
| Add Item | Controller + KV read + Product validation + KV write | 400-4000ms |
| Checkout | Controller + KV + Credit Card + RabbitMQ + KV | 500-5000ms |
| View Product | Controller + KV read | 200-2000ms |

---

## 6. Autoscaling Evidence

### 6.1 Autoscaling Configuration

Two different metrics are used as required:

| Service | Metric | Threshold | Max Instances |
|---------|--------|-----------|---------------|
| Product Service | CPU Utilization | 70% | 3 |
| Shopping Cart Service | Memory Utilization | 70% | 3 |
| Credit Card Authorizer | CPU Utilization | 70% | 3 |
| Warehouse Service | Memory Utilization | 70% | 3 |

### 6.2 Overload Condition

At 1500 concurrent users:
- 502 Bad Gateway errors indicate backend saturation
- Services unable to process incoming requests fast enough
- This is the trigger condition for autoscaling

### 6.3 Scaling Behavior Analysis

**Are all systems equally scaled?**

No, and this is expected due to different load distributions:

| Service | Requests per User Session | Relative Load |
|---------|---------------------------|---------------|
| Shopping Cart Service | 5+ requests | Highest |
| Product Service | 3+ requests | Medium |
| Credit Card Authorizer | 1 request | Low |
| Warehouse Service | 1 async message | Lowest |

**Primary Bottleneck: Shopping Cart Service**
- Handles all cart operations (create, add, checkout)
- Each Add Item also calls Product Service for validation
- First service to return 502 errors under heavy load

### 6.4 Recommendations With Additional Budget

| Priority | Improvement | Estimated Cost | Expected Impact |
|----------|-------------|----------------|-----------------|
| 1 | Increase max instances to 10-20 | Low | Handle 5-10x more traffic |
| 2 | Add autoscaling to database layer | Medium | Remove database bottleneck |
| 3 | Increase container size (1024 CPU, 2048 MB) | Medium | Each instance handles more requests |
| 4 | Add Redis caching for product data | Medium | Reduce database load significantly |
| 5 | Multi-AZ deployment | High | Improved availability and fault tolerance |

---

## 7. Assumptions and Reasoning

### 7.1 Workload Assumptions

| Assumption | Value | Reasoning |
|------------|-------|-----------|
| Average items per cart | 2-5 | Log-normal distribution; most customers buy few items |
| Checkout completion rate | 70% of sessions | Remaining 30% browse without purchasing |
| Product catalog size | 1,000 products | Pre-loaded by Java client before load testing |
| Product update frequency | 1-2 per day | Catalog changes infrequently in typical e-commerce |
| Cart modification frequency | Multiple per session | Users frequently add and remove items |

### 7.2 Read/Write Ratios

| Service | Read Percentage | Write Percentage | Justification |
|---------|-----------------|------------------|---------------|
| Product Service | 90% | 10% | Catalog browsing dominates; products rarely updated |
| Shopping Cart Service | 40% | 60% | Cart modifications (add/remove/update) dominate |

### 7.3 Use Case Distribution in Load Testing

```
Locust Task Weights:
- Shopping Session (Use Case 1 + 2): 70%
- Product Browsing Only: 30%
```

This distribution reflects typical e-commerce behavior where many users browse but fewer complete purchases.

---

## 8. Deployment Instructions

### 8.1 Prerequisites

- AWS Learner Lab account with available credits
- Terraform installed locally
- AWS CLI configured with credentials
- Python 3 with Locust installed
- Java 17+ and Maven for load testing client

### 8.2 Deploy Infrastructure

```bash
cd terraform
terraform init
terraform apply -auto-approve

# Wait 5-10 minutes for all services to become healthy
```

### 8.3 Pre-load Products

```bash
cd load-testing-client
mvn exec:java -Dexec.mainClass="com.cs6650.loadtest.LoadTestingClient" -Dexec.args="aws"

# This creates 1,000 products and exports products.json for Locust
```

### 8.4 Run Load Test

```bash
# From project root directory
locust -f locustfile_aws.py --host=http://YOUR-ALB-DNS-NAME

# Or headless mode with specific parameters
locust -f locustfile_aws.py --headless -u 500 -r 50 --run-time 15m
```

### 8.5 Monitor Autoscaling

```bash
# Watch ECS service task counts
watch -n 10 'aws ecs describe-services --cluster ecommerce-cluster \
  --services product-service shopping-cart-service \
  --query "services[*].[serviceName,runningCount]"'
```

### 8.6 Cleanup

```bash
cd terraform
terraform destroy -auto-approve
```

---

## 9. Project Structure

```
cs6650-assignment5/
+-- product-service/              # Product microservice
+-- shopping-cart-service/        # Shopping cart microservice
+-- credit-card-authorizer/       # Credit card microservice
+-- warehouse-service/            # Warehouse microservice (RabbitMQ consumer)
+-- kv-tx-stubs/                  # Leader-Follower KV database
+-- leaderless-kv/                # Leaderless KV database
+-- load-testing-client/          # Java client for product pre-loading
+-- locustfile_aws.py             # Locust load test script
+-- products.json                 # Product IDs exported for Locust
+-- terraform/                    # AWS infrastructure as code
    +-- autoscaling.tf            # Autoscaling policies
    +-- ecs_task_definitions.tf   # ECS task definitions
    +-- ecs_kv_database.tf        # Leader-Follower KV deployment
    +-- ecs_leaderless_kv.tf      # Leaderless KV deployment
    +-- rabbitmq.tf               # RabbitMQ EC2 instance
```

---

## 10. Requirements Compliance

| Requirement | Status | Evidence |
|-------------|--------|----------|
| Four microservices with business logic | Complete | Product, Cart, CreditCard, Warehouse services |
| Distributed KV database | Complete | Leader-Follower and Leaderless implementations |
| Transaction stubs (begin/end/abort) | Complete | Implemented in both KV stores, called in ShoppingCartService |
| Business logic delays (100-1000ms) | Complete | addBusinessLogicDelay() in all controllers |
| RabbitMQ for warehouse ship | Complete | WarehouseConsumer listens to checkoutQueue |
| Credit Card 90% approve / 10% decline | Complete | AUTHORIZATION_RATE = 0.9 |
| Pre-load 1000 products | Complete | Java LoadTestingClient creates products |
| Locust load testing with 2 use cases | Complete | locustfile_aws.py implements both use cases |
| Autoscaling with 2 different metrics | Complete | CPU and Memory, max 3 instances |
| CAP trade-off documented | Complete | AP chosen, consistency sacrificed |
| Terraform deployment | Complete | Full infrastructure as code |

---

