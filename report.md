# CS6650 Assignment 5

**Team Members:** Qingyi Tian, Yining Shen, Chih-Hsing Hsieh
**Date:** December 5, 2025
**GitHub:** https://github.khoury.northeastern.edu/darren890311/cs6650-assignment5.git

---

## 1. System Architecture (10 points)

Our Assignment 5 e-commerce system consists of four microservices and two distributed key-value databases deployed on AWS ECS with autoscaling enabled.

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

Each microservice is deployed as an individual ECS Fargate service with its own task definition, autoscaling policy, and target group behind a shared Application Load Balancer.

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
                    +-> beginTransaction()
                    +-> Leader-Follower KV (get cart)
                    +-> Credit Card Authorizer (90% approve, 10% decline)
                    |
                    +-- If approved:
                    |       +-> RabbitMQ (publish order) -> Warehouse Service (ship)
                    |       +-> Leader-Follower KV (mark cart CHECKED_OUT)
                    |       +-> endTransaction()
                    |
                    +-- If declined:
                            +-> abortTransaction()
                            +-> Return 402 Payment Declined
```

---

## 2. Assumptions and Workload Analysis (10 points)

Because real production traces are unavailable, we document reasonable assumptions aligned with industry patterns.

### 2.1 Relative Frequency of Each Use Case

| Use Case | Description | Frequency | Justification |
|----------|-------------|-----------|---------------|
| UC1 - Shopping Flow | Create cart, add items, checkout | 70% | Core business flow being stress-tested |
| UC2 - Product Browsing | View products only | 30% | Simulates customers who browse but do not buy |

**Locust Implementation:**
```python
tasks = {
    UseCase1_CustomerShoppingSession: 7,  # 70% weight
    UseCase2_ProductBrowsing: 3           # 30% weight
}
```

### 2.2 Read/Write Ratios for Each Use Case

| Service | Read % | Write % | Justification |
|---------|--------|---------|---------------|
| Product Service | 90% | 10% | Products rarely change; heavy read traffic from browsing. 1000 products loaded once, occasional updates. |
| Shopping Cart Service | 40% | 60% | Users frequently add/remove items (writes). Cart lookups occur less often. Write-dominant workload. |
| Credit Card Authorizer | 100% Read | 0% Write | Stateless service, no database. Only processes authorization requests. |
| Warehouse Service | 100% Write | 0% Read | Receives ship orders via RabbitMQ. Fire-and-forget, no queries. |

### 2.3 Additional Workload Assumptions

| Assumption | Value | Reasoning |
|------------|-------|-----------|
| Items per cart | 2-5 (log-normal distribution) | Most customers buy few items; some buy more |
| Product catalog size | 1,000 products | Pre-loaded by Java client before testing |
| Product update frequency | Rarely (1-2 per day) | Catalog changes infrequently |
| Cart modification frequency | Multiple per session | Users frequently add/remove items |
| Business logic delay | 100-1000ms per endpoint | Required to simulate real processing and trigger autoscaling |

---

## 3. Choice of Database Design (10 points)

### 3.1 Product Service - Leaderless KV (W=N, R=1)

**Configuration:**
- W = N (all nodes): Write must propagate to ALL nodes before success
- R = 1 (single node): Read from any single node via load balancer
- 5 peer nodes, any node can be write coordinator

**Why Leaderless for Products:**
- Read-heavy workload (90% reads): Any node can respond to reads
- Products rarely change: Slow writes (W=N) are acceptable
- No single point of failure: All nodes are equal
- Horizontal read scaling: Load balancer distributes reads

**Why NOT Leader-Follower:**
- Leader would become bottleneck for reads
- Products don't need strong write consistency
- Leaderless gives better read throughput for large catalogs

**Real-Life Analogy - Product Catalog:**
Think of a retail chain like Walmart or Amazon's product catalog:
- Product information (name, price, description) is updated by the merchandising team maybe once a day
- Millions of customers browse the catalog every hour
- Every store (node) needs the same product info, so W=N ensures consistency
- Any store can answer "What's the price of item X?" so R=1 is fast
- If one store's system goes down, others still serve customers (no single point of failure)

Real companies like Amazon use similar patterns - their product catalog is replicated across data centers worldwide. Updates are slow but reads are distributed globally.

**Learning from Assignment 4:**
- Leaderless with W=N ensures all nodes have consistent data
- R=1 provides fast reads since all nodes are synchronized

### 3.2 Shopping Cart Service - Leader-Follower KV (W=1, R=1)

**Configuration:**
- W = 1: Leader stores locally, returns immediately (async replication)
- R = 1: Read from leader only (always returns latest data)
- Dynamic follower registration via /internal/register endpoint

**Why Leader-Follower for Shopping Carts:**
- Write-heavy workload (60% writes): W=1 provides fast writes
- Cart updates must be fast: Users expect instant "Add to Cart" response
- Single authority: Leader always has latest cart state
- Checkout accuracy: Reading from leader ensures correct final state

**Why NOT Leaderless:**
- Conflicting writes from multiple nodes would cause merge conflicts
- Cart operations are sequential (add, add, checkout) - need ordering
- Leader provides clear write authority

**Real-Life Analogy - Shopping Cart:**
Think of your personal shopping cart at Target or Best Buy:
- You're the ONLY person modifying YOUR cart (no conflicts with other users)
- You expect "Add to Cart" to feel instant (<500ms) - W=1 makes this possible
- When you checkout, the system MUST know exactly what's in your cart - reading from leader guarantees accuracy
- If you add an item and immediately view your cart, you expect to see it - leader ensures no stale reads
- Followers exist for backup/disaster recovery, not for serving reads

Real e-commerce sites like Shopify use similar patterns - cart data is written to a primary database and replicated asynchronously. The primary always has the authoritative state.

**Why This Matches Real E-Commerce:**

| Scenario | Product Catalog | Shopping Cart |
|----------|-----------------|---------------|
| Who modifies? | Admin team (few) | Each customer (many) |
| How often? | Rarely (daily) | Frequently (per session) |
| Who reads? | Everyone (millions) | Only the cart owner |
| Conflict risk? | Low (coordinated updates) | None (personal data) |
| Latency priority? | Reads must be fast | Writes must be fast |
| Best fit | Leaderless (W=N, R=1) | Leader-Follower (W=1, R=1) |

**Learning from Assignment 4:**
- Leader-Follower with W=1 provides lowest write latency
- Async replication acceptable for personal cart data (no conflicts)

### 3.3 CAP Theorem Trade-off

**Choice: AP (Availability + Partition Tolerance)**

**Sacrificed: Strong Consistency (accepting Eventual Consistency)**

**Justification:**
1. Shopping carts are user-specific - no conflict risk between users
2. Brief stale reads acceptable - users will not notice sub-second delays
3. "Add to Cart" must be fast (<500ms) - availability is critical
4. Checkout validates final state from leader - consistency when it matters
5. System must continue operating during network partitions

---

## 4. Microservice Implementation Summary

### 4.1 Four Microservices

| Service | Port | Database | Key Functions |
|---------|------|----------|---------------|
| Product Service | 8082 | Leaderless KV | Create/Get products, 100-1000ms delay |
| Shopping Cart Service | 8084 | Leader-Follower KV | Cart CRUD, Checkout, Transaction stubs |
| Credit Card Authorizer | 8080 | None | 90% approve, 10% decline, 100-1000ms delay |
| Warehouse Service | 8083 | None | RabbitMQ consumer, ship always succeeds |

### 4.2 Transaction Stubs

In ShoppingCartService, transaction stubs are implemented:

- `beginTransaction()` - Called before payment authorization
- `endTransaction()` - Called after successful checkout
- `abortTransaction()` - Called on payment decline or any error

**Location in code:**
- KV Database endpoints: `kv-tx-stubs/.../controller/KVController.java` (lines 116-138)
- Shopping Cart client: `shopping-cart-service/.../kvclient/KvStoreClient.java` (lines 102-112)
- Usage in checkout: `shopping-cart-service/.../service/ShoppingCartService.java` (lines 135-158)

These stubs print messages to show understanding of where 2PC would be implemented:
```
"--- [KV DB] RECEIVED BEGIN TRANSACTION (Simulated) ---"
"--- [KV DB] RECEIVED END TRANSACTION (Simulated) ---"
"--- [KV DB] RECEIVED ABORT TRANSACTION (Simulated) ---"
```

### 4.3 RabbitMQ Integration

Warehouse receives ship orders via RabbitMQ (fire-and-forget):
- Shopping Cart publishes to "checkoutQueue" after payment approved
- Warehouse consumes messages and records orders
- Ship always succeeds (per assignment requirements)

---

## 5. Load Testing with Locust

### 5.1 Test Configuration

| Parameter | Value |
|-----------|-------|
| Tool | Locust (Python) |
| Concurrent Users | 500-1500 |
| Spawn Rate | 50-100 users/second |
| Test Duration | 15-40 minutes |
| Target | AWS Application Load Balancer |

### 5.2 Load Testing Results

**At 500 Concurrent Users:**

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

**At 1500 Concurrent Users:**

- 502 Bad Gateway errors occurred (system overload)
- Shopping Cart Service became saturated
- This triggered autoscaling

### 5.3 Latency Analysis

Latencies are higher due to intentional stacking of business logic delays:

| Operation | Delay Sources | Expected Range |
|-----------|---------------|----------------|
| Create Cart | Controller delay + KV write | 200-2000ms |
| Add Item | Controller + KV read + Product validation + KV write | 400-4000ms |
| Checkout | Controller + KV + Credit Card + RabbitMQ + KV | 500-5000ms |
| View Product | Controller + KV read | 200-2000ms |

---

## 6. Evidence of Autoscaling (10 points)

### 6.1 Autoscaling Configuration (Terraform)

Two different metrics used (as required):

| Service | Metric | Threshold | Min | Max (Terraform) |
|---------|--------|-----------|-----|-----------------|
| Product Service | CPU Utilization | 70% | 1 | 3 |
| Shopping Cart Service | Memory Utilization | 70% | 1 | 3 |
| Credit Card Authorizer | CPU Utilization | 70% | 1 | 3 |
| Warehouse Service | Memory Utilization | 70% | 1 | 3 |

**Note:** During load testing, we increased Shopping Cart max capacity to 5 via AWS CLI to demonstrate additional scaling headroom. We also added a CPU-based scaling policy in addition to Memory.

### 6.2 Load Test Configuration for Autoscaling

| Parameter | Value |
|-----------|-------|
| Tool | Locust |
| Concurrent Users | 2,000 |
| Spawn Rate | 100 users/second |
| Duration | 15 minutes |

### 6.3 Overload Condition Observed

At 1500-2000 concurrent users:
- 502 Bad Gateway errors indicate backend saturation
- Services unable to process incoming requests
- ~10.8% failure rate at peak load
- This triggered autoscaling

### 6.4 Are All Systems Equally Scaled?

**No, and this is expected.**

| Service | Initial | Final | Scaled? |
|---------|---------|-------|---------|
| Shopping Cart Service | 1 | 5 (maxed) | YES |
| Product Service | 1 | 1 | NO |
| Credit Card Authorizer | 1 | 1 | NO |
| Warehouse Service | 1 | 1 | NO |

### 6.5 Scaling Timeline

- **18:32:23** - shopping-cart-service: 1 -> 2 replicas (CPU exceeded 70%)
- **18:38:23** - shopping-cart-service: 2 -> 5 replicas (reached max capacity)

### 6.6 Bottleneck Analysis

**Primary Bottleneck: Shopping Cart Service**

Reasons:
- Handles ALL user operations (create cart, add item, checkout)
- Each Add Item also validates with Product Service
- Each Checkout calls Credit Card and publishes to RabbitMQ
- 5+ requests per user session vs 1 request for other services
- Complex transaction orchestration with BEGIN/END/ABORT semantics

**Why other services did NOT scale:**
- Product Service: Simple read operations with fast response times (~1.2s avg)
- Credit Card Authorizer: Lightweight validation with simulated delays
- Warehouse Service: Asynchronous processing via RabbitMQ (fire-and-forget)

**Evidence:**
- Shopping Cart scaled from 1 to 5 instances (hit max capacity)
- Other services remained at 1 instance (sufficient capacity)
- First service to return 502 errors under load

### 6.7 What Would You Do With More Money?

| Priority | Improvement | Cost | Expected Impact |
|----------|-------------|------|-----------------|
| 1 | Increase Shopping Cart max instances to 10-20 | Low | Handle 5-10x more traffic |
| 2 | Add autoscaling to KV database layer | Medium | Remove database bottleneck |
| 3 | Increase container size (1024 CPU, 2048 MB) | Medium | Each instance handles more requests |
| 4 | Add Redis caching for product data | Medium | Reduce database load significantly |
| 5 | Multi-AZ deployment | High | Improved availability and fault tolerance |

---

## 7. Deployment Architecture (Terraform)

Our entire infrastructure is deployed using Terraform:

- ECS Cluster (Fargate)
- 4 microservices with autoscaling
- Leaderless KV database cluster
- Leader-Follower KV database cluster
- RabbitMQ on EC2
- Application Load Balancer with path-based routing
- CloudWatch logging and monitoring
- Security groups and VPC networking

**Commands:**
```bash
cd terraform
terraform init
terraform apply -auto-approve
terraform destroy -auto-approve
```

---

## 8. Conclusion

This assignment demonstrates an end-to-end distributed e-commerce system integrating:

- Four microservices with business logic delays
- Two distributed databases (Leaderless + Leader-Follower)
- Simulated ACID transaction stubs
- Autoscaling with two different metrics (CPU + Memory)
- Load testing with Locust (two use cases)
- RabbitMQ for async warehouse communication

**Key Findings:**

1. Shopping Cart Service is the natural bottleneck (handles most operations)
2. Product Service benefits from Leaderless design (read-heavy)
3. Shopping Cart benefits from Leader-Follower design (write-heavy)
4. CAP trade-off: AP chosen, eventual consistency acceptable
5. Autoscaling triggers appropriately under high load
6. 0% failure rate at 500 users, overload at 1500 users triggers scaling

---

## 9. Team Contributions

| Team Member | Responsibilities |
|-------------|------------------|
| Qingyi Tian | Infrastructure, Terraform, Autoscaling, Load Testing |
| Yining Shen | KV Database Implementation, Replication Logic |
| Chih-Hsing Hsieh | Microservices, RabbitMQ Integration, Documentation |
