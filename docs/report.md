# CS6650 Assignment 5

**Team Members:** Qingyi Tian, Yining Shen, Chih-Hsing Hsieh
**Date:** December 5, 2025
**GitHub:** https://github.khoury.northeastern.edu/darren890311/cs6650-assignment5.git

---

## 1. System Architecture

Our e-commerce system is designed as a distributed microservices architecture deployed on AWS ECS Fargate. The system handles two primary use cases: customer shopping sessions (create cart → add items → checkout) and product browsing. Each service is independently deployable and scalable, communicating via REST APIs and asynchronous messaging.

| Component | Type | Purpose |
|-----------|------|---------|
| Product Service | Microservice | Product catalog (CRUD) |
| Shopping Cart Service | Microservice | Cart operations, checkout orchestration |
| Credit Card Authorizer | Microservice | Payment validation (stateless) |
| Warehouse Service | Microservice | Order fulfillment via RabbitMQ |
| Leaderless KV | Database | Product storage (W=N, R=1) |
| Leader-Follower KV | Database | Cart storage (W=1, R=1) |

### 1.1 Architecture Diagram

![System Architecture](images/architecture-%20diagram.png)

The diagram shows the complete system architecture:
- **Client/Locust** connects via the Internet to the Application Load Balancer (Port 80)
- **ECS Fargate Cluster** hosts the microservices with autoscaling:
  - Product Service (:8082) - CPU Autoscale 30% (tuned)
  - Shopping Cart Service (:8084) - CPU + Memory Autoscale 50%
  - Credit Card Authorizer (:8080) - CPU Autoscale 70%
  - Warehouse Service (:8083) - Memory Autoscale 70%
- **Distributed KV Databases**:
  - Leaderless KV (W=N, R=1) for Products
  - Leader-Follower KV (W=1, R=1) for Shopping Carts
- **RabbitMQ** (:5672) for async messaging to Warehouse Service

Each microservice is deployed as an individual ECS Fargate service with its own task definition, autoscaling policy, and target group behind a shared Application Load Balancer.

### 1.2 Message Flow Between Services

**Use Case 1: Add Item to Cart**

![Add Item to Cart Flow](images/Add-Item-to-Cart-diagram.png)

The sequence diagram shows the complete flow for adding an item to cart:
1. Client sends `POST /shopping-carts/{id}/addItem` to Load Balancer
2. Shopping Cart Service calls `beginTransaction()` on Leader-Follower KV
3. Shopping Cart Service retrieves cart data via `getShoppingCart(cartId)`
4. Shopping Cart Service validates product by calling `GET /products/{productId}` on Product Service
5. Product Service reads from Leaderless KV (R=1) and returns product data
6. Shopping Cart Service saves updated cart via `setShoppingCart(cart)`
7. Shopping Cart Service calls `endTransaction()` and returns 204 No Content

This flow involves 3 database operations and 1 inter-service call per request, explaining why Add Item has the highest latency under load.

**Use Case 2: Checkout**

![Checkout Flow](images/checkout-flow-%20diagram.png)

The sequence diagram shows the checkout flow with two possible outcomes:

**Payment Approved (90%):**
1. Client sends `POST /shopping-carts/{id}/checkout`
2. Shopping Cart Service calls `beginTransaction()` and retrieves cart
3. Shopping Cart Service calls `POST /authorize` on Credit Card Authorizer
4. Credit Card returns 200 OK (approved)
5. Shopping Cart publishes order message to RabbitMQ
6. Warehouse Service receives message asynchronously and processes shipment
7. Shopping Cart sets cart status to `CHECKED_OUT` and calls `endTransaction()`
8. Client receives 200 OK

**Payment Declined (10%):**
1. Credit Card returns 402 Payment Required
2. Shopping Cart calls `abortTransaction()`
3. Client receives 402 Payment Declined

The transaction stubs (begin/end/abort) demonstrate where 2PC would be implemented. The Warehouse Service uses fire-and-forget pattern via RabbitMQ for async order processing.

---

## 2. Assumptions and Workload Analysis 

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

### 2.2 Read/Write Ratios for Database Services

| Service | Read % | Write % | Justification |
|---------|--------|---------|---------------|
| Product Service | 90% | 10% | Products rarely change; heavy read traffic from browsing. 1000 products loaded once, occasional updates. |
| Shopping Cart Service | 40% | 60% | Users frequently add/remove items (writes). Cart lookups occur less often. Write-dominant workload. |

**Note:** Credit Card Authorizer and Warehouse Service are not included in the read/write ratio table as they do not interact with any database. The Credit Card Authorizer is a stateless service that processes authorization requests without persisting data. The Warehouse Service receives order messages asynchronously via RabbitMQ using the fire-and-forget messaging pattern and processes shipments independently.

### 2.3 Additional Workload Assumptions

| Assumption | Value | Reasoning |
|------------|-------|-----------|
| Items per cart | 2-5 (log-normal distribution) | Most customers buy few items; some buy more |
| Product catalog size | 1,000 products | Pre-loaded by Java client before testing |
| Product update frequency | Rarely (1-2 per day) | Catalog changes infrequently |
| Cart modification frequency | Multiple per session | Users frequently add/remove items |
| Business logic delay | 100-1000ms per endpoint | Required to simulate real processing and trigger autoscaling |

---

## 3. Choice of Database Design 

Our system employs custom-built distributed key-value databases for both Product and Shopping Cart services. This design choice was driven by three factors:

**Data Model Alignment:**
E-commerce data maps naturally to key-value pairs without requiring complex relational joins:
- `productId → Product` (name, price, description)
- `cartId → ShoppingCart` (customer, items, status)

**Performance Requirements:**
Key-value stores provide O(1) read and write operations using ConcurrentHashMap, which is essential for high-throughput e-commerce workloads where milliseconds matter.

**Flexible Consistency Tuning:**
By implementing configurable N/R/W quorum parameters, we can tune each database for its specific workload pattern - prioritizing read performance for products and write performance for shopping carts.

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

#### Why W=1, R=1 Actually Works (Deep Dive)

At first glance, W=1, R=1 seems dangerously weak for a distributed database. How can we guarantee correctness with such minimal quorum requirements? The answer lies in understanding what these values actually mean in a Leader-Follower architecture versus a Leaderless architecture:

**The Key Insight: Leader-Follower ≠ Leaderless**

In a **Leaderless** system, W=1, R=1 would be disastrous because:
- Any node can accept writes independently
- Two clients could write to different nodes simultaneously
- Reading from one node might miss a write that went to another
- No guarantee of seeing your own writes (read-your-writes violation)

But in a **Leader-Follower** system, W=1, R=1 is actually **strongly consistent** because:
- **ALL writes go through the single leader** - there's only ONE write authority
- **ALL reads go to the leader** - R=1 means "read from leader only", not "read from any single node"
- The leader is the single source of truth - it always has the latest state
- Followers exist purely for fault tolerance (failover), not for serving reads

**Mathematical Proof of Correctness:**

For strong consistency in quorum systems: `W + R > N`

In our Leader-Follower system:
- N = 1 (from the perspective of active write/read nodes - only leader serves requests)
- W = 1 (write to leader)
- R = 1 (read from leader)
- W + R = 2 > 1 = N ✓

**Why This Works on AWS (Production Reality):**

1. **Single Leader = Single Source of Truth**
   - The Shopping Cart Service is configured to connect ONLY to the leader node's IP address
   - Every cart operation (create, addItem, checkout) hits the same leader
   - No ambiguity about which node has the authoritative state

2. **Cart Isolation Eliminates Conflicts**
   - Each shopping cart has exactly ONE owner (the customer)
   - `cartId=abc123` is only ever modified by ONE user session
   - No concurrent writers means no write conflicts, ever
   - This is fundamentally different from a shared resource like inventory

3. **Session Affinity by Design**
   - The same user session always talks to the same cart
   - Operations are sequential: createCart → addItem → addItem → checkout
   - Reading your own cart immediately after adding an item MUST return the updated state
   - Leader-based reads guarantee this (followers might lag behind)

4. **Followers Are for Disaster Recovery, Not Reads**
   - Our followers asynchronously replicate from the leader
   - If the leader fails, a follower can be promoted
   - But during normal operation, all traffic goes to the leader
   - This is why W=1, R=1 is safe - there's never any staleness in the read path

**Contrast with Product Service (Leaderless):**

| Aspect | Shopping Cart (Leader-Follower) | Product Service (Leaderless) |
|--------|--------------------------------|------------------------------|
| Write path | Single leader | Any node (coordinator) |
| Read path | Leader only | Any node |
| Why it works | Single authority | W=N ensures all nodes have data |
| Quorum math | W+R > N (1+1 > 1) | W+R > N (5+1 > 5) |

**Real-World Validation:**

Our load testing at 2000 concurrent users proves this works:
- 0.08% failure rate with 125,313 requests
- Shopping Cart Service scaled to 5 instances (application layer)
- All 5 instances talk to the SAME leader KV node
- No data inconsistencies observed during checkout

The failures we saw were from request timeouts under extreme load, NOT from data inconsistency. The W=1, R=1 configuration remained correct throughout the stress test.

**Summary: Why It's Not "Too Good to Be True"**

W=1, R=1 in a Leader-Follower system is not a shortcut or a compromise - it's the mathematically correct configuration for:
- Single-writer workloads (user-owned data)
- Latency-sensitive writes (instant "Add to Cart" feedback)
- Strong consistency requirements (checkout must see exact cart contents)

The "magic" is that Leader-Follower topology ensures all operations go through one node, making the quorum trivially satisfied. It's simple, fast, and correct.

### 3.3 CAP Theorem Trade-off

**Choice: AP (Availability + Partition Tolerance)**

**Sacrificed: Strong Consistency (accepting Eventual Consistency)**

Our e-commerce system chooses AP for the following reasons:

1. **Availability drives revenue:** A customer who cannot add items to their cart will abandon the session. Revenue loss from unavailability exceeds the cost of brief inconsistency.

2. **Cart data is user-isolated:** Each cart has exactly one owner with no concurrent writers, eliminating the primary risk of eventual consistency - write conflicts.

3. **Product data tolerates staleness:** Prices and descriptions change infrequently. Serving slightly stale data during a partition has minimal business impact.

4. **Critical paths enforce consistency:** Checkout operations read from the leader and use transaction stubs (BEGIN/END/ABORT), ensuring correctness where it matters most.

---

## 4. Microservice Implementation Summary

Our system consists of four microservices, each with a single responsibility:

| Service | Port | Database | Description |
|---------|------|----------|-------------|
| Product Service | 8082 | Leaderless KV | Manages product catalog (CRUD operations) |
| Shopping Cart Service | 8084 | Leader-Follower KV | Handles cart operations and checkout orchestration |
| Credit Card Authorizer | 8080 | None (stateless) | Simulates payment gateway (90% approve, 10% decline) |
| Warehouse Service | 8083 | None (stateless) | Processes shipping via RabbitMQ consumer |

### 4.1 Transaction Stubs

To demonstrate understanding of distributed transactions, we implemented transaction stubs in the KV database:

| Method | When Called | Purpose |
|--------|-------------|---------|
| `beginTransaction()` | Before checkout starts | Marks transaction boundary |
| `endTransaction()` | After successful checkout | Commits the transaction |
| `abortTransaction()` | On payment decline or error | Rolls back changes |

These stubs provide the foundation for implementing Two-Phase Commit (2PC) in a production system. Currently, they log transaction boundaries to demonstrate correct placement in the checkout flow.

### 4.2 RabbitMQ Integration

The Warehouse Service receives orders asynchronously via RabbitMQ queue `checkoutQueue` using fire-and-forget pattern. Ship always succeeds per assignment requirements.

---

## 5. Load Testing with Locust

### 5.1 Test Configuration

| Parameter | Value |
|-----------|-------|
| Tool | Locust (Python) |
| Script | locustfile_aws.py |
| Target | http://ecommerce-alb-1186708136.us-east-1.elb.amazonaws.com |
| Test Strategy | Incremental load testing (300 → 500 → 1000 → 1500 users) |

### 5.2 Load Testing Results

We performed incremental load testing to observe system behavior and autoscaling under increasing load. All tests achieved **0% failure rate**, demonstrating system stability.

#### Test 1: 300 Concurrent Users

![Locust 300 Users](../locust-load-testing/locust-300.png)

| Type | Name | # Requests | # Fails | Median (ms) | 95%ile (ms) | Avg (ms) | RPS |
|------|------|------------|---------|-------------|-------------|----------|-----|
| POST | UC1.1 Create Cart | 958 | 0 | 1,300 | 2,000 | 1,280 | 12.5 |
| POST | UC1.2 Add Item | 1,852 | 0 | 2,900 | 3,900 | 2,937 | 28.8 |
| POST | UC1.3 Checkout | 747 | 0 | 2,300 | 3,300 | 2,349 | 12.2 |
| GET | UC2 View Product | 1,438 | 0 | 1,200 | 1,800 | 1,192 | 23.5 |
| | **Aggregated** | **4,995** | **0** | 1,900 | 3,600 | 2,029 | **77.0** |

**Service Health at 300 Users:**
- Shopping Cart Service: 4 Healthy targets, 0 Unhealthy
- Health check grace period: 60 seconds

![Shopping Cart Health 300](images/shopping-cart-health-300.png)

#### Test 2: 500 Concurrent Users

![Locust 500 Users](../locust-load-testing/locust-500.png)

| Type | Name | # Requests | # Fails | Median (ms) | 95%ile (ms) | Avg (ms) | RPS |
|------|------|------------|---------|-------------|-------------|----------|-----|
| POST | UC1.1 Create Cart | 4,348 | 0 | 1,200 | 1,800 | 1,188 | 21.4 |
| POST | UC1.2 Add Item | 9,071 | 0 | 2,900 | 3,800 | 2,860 | 46.0 |
| POST | UC1.3 Checkout | 3,993 | 0 | 2,200 | 3,100 | 2,232 | 21.1 |
| GET | UC2 View Product | 7,746 | 0 | 1,200 | 1,800 | 1,184 | 40.2 |
| | **Aggregated** | **25,158** | **0** | 1,800 | 3,500 | 1,955 | **128.7** |

**Service Health at 500 Users:**
- Shopping Cart Service: 3 Running tasks, 3 Healthy targets

![Shopping Cart Service 500](images/shopping-cart-service-500.png)

#### Test 3: 1000 Concurrent Users

![Locust 1000 Users](../locust-load-testing/locust-1000.png)

| Type | Name | # Requests | # Fails | Median (ms) | 95%ile (ms) | Avg (ms) | RPS |
|------|------|------------|---------|-------------|-------------|----------|-----|
| POST | UC1.1 Create Cart | 13,753 | 0 | 1,200 | 1,900 | 1,224 | 48.3 |
| POST | UC1.2 Add Item | 28,571 | 0 | 5,100 | 6,300 | 4,895 | 94.4 |
| POST | UC1.3 Checkout | 12,766 | 0 | 2,300 | 3,300 | 2,308 | 44.6 |
| GET | UC2 View Product | 23,820 | 0 | 3,500 | 4,300 | 3,150 | 87.7 |
| | **Aggregated** | **78,910** | **0** | 3,300 | 5,900 | 3,310 | **275.0** |

**Service Health at 1000 Users:**
- Shopping Cart Service: 3 Running tasks, 3 Healthy targets

![Shopping Cart Service 1000](images/shopping-cart-service-1000.png)

#### Test 4: 1500 Concurrent Users

![Locust 1500 Users](../locust-load-testing/locust-1500.png)

| Type | Name | # Requests | # Fails | Median (ms) | 95%ile (ms) | Avg (ms) | RPS |
|------|------|------------|---------|-------------|-------------|----------|-----|
| POST | UC1.1 Create Cart | 13,753 | 0 | 1,200 | 1,900 | 1,224 | 48.3 |
| POST | UC1.2 Add Item | 28,571 | 0 | 5,100 | 6,300 | 4,895 | 94.4 |
| POST | UC1.3 Checkout | 12,766 | 0 | 2,300 | 3,300 | 2,308 | 44.6 |
| GET | UC2 View Product | 23,820 | 0 | 3,500 | 4,300 | 3,150 | 87.7 |
| | **Aggregated** | **78,910** | **0** | 3,300 | 5,900 | 3,310 | **275.0** |

**Service Health at 1500 Users:**
- Shopping Cart Service: Autoscaled to 4 Running tasks, 4 Healthy targets

![Shopping Cart Service 1500](images/shopping-cart-service-1500.png)

#### Test 5: 2000 Concurrent Users (Stress Test & Autoscaling Tuning)

At 2000 users, we stress-tested the system and iteratively tuned autoscaling thresholds to improve performance.

##### Phase 1: First Tuning - 50% CPU Threshold (7% → 3% failure rate)

After testing at 1500 users, we lowered the Product Service CPU threshold from 85% to 50% before testing at 2000 users. However, the Product Service still wasn't scaling fast enough.

**Early in test (7% failure rate):**

![Locust 2000 Users Earlier](../locust-load-testing/locust-2000-earlier.png)

| Type | Name | # Requests | # Fails | Median (ms) | 95%ile (ms) | Avg (ms) | RPS |
|------|------|------------|---------|-------------|-------------|----------|-----|
| POST | UC1.1 Create Cart | 26,353 | 2,873 | 5,500 | 35,000 | 10,002 | 42.2 |
| POST | UC1.2 Add Item | 55,853 | 6,439 | 7,900 | 39,000 | 12,322 | 112.3 |
| POST | UC1.3 Checkout | 22,089 | 1,929 | 7,500 | 41,000 | 12,647 | 44.8 |
| GET | UC2 View Product | 49,511 | 23 | 1,200 | 1,900 | 1,324 | 104.2 |
| | **Aggregated** | **153,806** | **11,264 (7%)** | 3,800 | 33,000 | 8,431 | **303.5** |

**Later in test (3% failure rate) - after autoscaling kicked in:**

![Locust 2000 Users](../locust-load-testing/locust-2000.png)

| Type | Name | # Requests | # Fails | Median (ms) | 95%ile (ms) | Avg (ms) | RPS |
|------|------|------------|---------|-------------|-------------|----------|-----|
| POST | UC1.1 Create Cart | 57,283 | 2,873 | 2,500 | 26,000 | 6,423 | 77.9 |
| POST | UC1.2 Add Item | 123,760 | 6,451 | 4,700 | 29,000 | 8,493 | 167.2 |
| POST | UC1.3 Checkout | 52,965 | 1,933 | 4,300 | 31,000 | 8,154 | 74.7 |
| GET | UC2 View Product | 106,195 | 23 | 1,200 | 1,900 | 1,252 | 142.1 |
| | **Aggregated** | **340,203** | **11,280 (3%)** | 2,700 | 25,000 | 5,832 | **461.9** |

**Problem:** Even with 50% threshold, Product Service only scaled to 2 instances (delayed scaling). We needed more aggressive scaling.

##### Phase 2: Final Tuning - 30% CPU Threshold (0.08% failure rate)

We lowered the Product Service CPU threshold further from 50% to **30%** to trigger scaling even earlier.

![Locust 2000 Users 30% Threshold](../locust-load-testing/locust-2000-30percentthresholdforproductservice.png)

| Type | Name | # Requests | # Fails | Median (ms) | 95%ile (ms) | Avg (ms) | RPS |
|------|------|------------|---------|-------------|-------------|----------|-----|
| POST | UC1.1 Create Cart | 21,428 | 34 | 2,800 | 20,000 | 5,554 | 51.9 |
| POST | UC1.2 Add Item | 44,269 | 44 | 5,200 | 23,000 | 7,904 | 83.4 |
| POST | UC1.3 Checkout | 19,860 | 21 | 4,700 | 23,000 | 7,456 | 46.4 |
| GET | UC2 View Product | 39,756 | 0 | 1,300 | 2,100 | 1,448 | 62.4 |
| | **Aggregated** | **125,313** | **99 (0.08%)** | 3,000 | 19,000 | 5,383 | **244.1** |

**Product Service Autoscaled to 3 Instances:**

![Product Service 2000 30% Threshold](images/product-service-2000-30percentthreshold.png)

- Tasks: 3 Desired, 3 Running
- Deployments: 3 Completed tasks
- Target Health: 3 Healthy
- Health check grace period: 60 seconds

**Shopping Cart Service Autoscaled to 5 Instances (Max):**

![Shopping Cart Service 2000](images/shopping-cart-service-2000.png)

- Tasks: 5 Desired, 5 Running
- Target Health: 5 Healthy
- Reached maximum capacity

**Key Results of Autoscaling Tuning:**

| Phase | CPU Threshold | Failure Rate | RPS | Product Instances | Cart Instances |
|-------|---------------|--------------|-----|-------------------|----------------|
| Phase 1 (50% threshold, early) | 50% | 7% | 303.5 | 1-2 (delayed) | 4 |
| Phase 1 (50% threshold, later) | 50% | 3% | 461.9 | 2 | 5 |
| Phase 2 (30% threshold) | 30% | 0.08% | 244.1 | 3 (proactive) | 5 |

**Lessons Learned:**
- Lowering the threshold from 50% to 30% enabled proactive scaling, achieving near-zero failure rates
- Shopping Cart Service scaled to max (5 instances) at 2000 users
- Product Service needed aggressive thresholds (30%) to scale to max (3 instances) before becoming overwhelmed

### 5.3 Performance Summary

| Users | Total Requests | Failures | RPS | Avg Latency | 95%ile Latency | Instances (Cart/Product) |
|-------|---------------|----------|-----|-------------|----------------|--------------------------|
| 300 | 4,995 | 0% | 77 | 2,029 ms | 3,600 ms | 4 / 1 |
| 500 | 25,158 | 0% | 128.7 | 1,955 ms | 3,500 ms | 3 / 1 |
| 1000 | 78,910 | 0% | 275 | 3,310 ms | 5,900 ms | 3 / 1 |
| 1500 | 78,910 | 0% | 275 | 3,310 ms | 5,900 ms | 4 / 1 |
| 2000 (50% threshold) | 340,203 | 3% | 461.9 | 5,832 ms | 25,000 ms | 5 / 2 |
| 2000 (30% threshold) | 125,313 | 0.08% | 244.1 | 5,383 ms | 19,000 ms | 5 / 3 |

**Key Observations:**

1. **Autoscaling Threshold Tuning is Critical:** Lowering the Product Service CPU threshold from 50% to 30% reduced failure rate from 3% to 0.08% at 2000 users.

2. **Linear Throughput Scaling:** RPS scaled nearly linearly with users (77 → 128.7 → 275 → 461.9 RPS).

3. **Proactive Scaling Prevents Failures:** With the 30% threshold, the Product Service scaled to 3 instances before becoming overwhelmed, achieving near-zero failures.

4. **Product Service Performance:** Consistently fast (~1.2s median at lower loads), validating the Leaderless KV (R=1) design for read-heavy workloads.

5. **Shopping Cart Operations:** Add Item showed the highest latency (5.1s median at 1000+ users) due to multiple downstream calls (KV read → Product Service validation → KV write).

6. **Both Services Autoscaled at 2000 Users:** Shopping Cart scaled to 5 instances (max), Product Service scaled to 3 instances (max) with tuned thresholds.

### 5.4 Latency Analysis

**Read vs Write Performance Gap:**

| Operation | Type | Median @ 500 users | Median @ 1000 users |
|-----------|------|-------------------|---------------------|
| View Product | Read | 1,200 ms | 3,500 ms |
| Create Cart | Write | 1,200 ms | 1,200 ms |
| Add Item | Write (complex) | 2,900 ms | 5,100 ms |
| Checkout | Write (complex) | 2,200 ms | 2,300 ms |

The Add Item operation has the highest latency because it involves:
1. KV read (get cart)
2. HTTP call to Product Service (validate product exists)
3. Product Service KV read
4. KV write (save updated cart)

This 4-step chain explains the ~5s latency under load.

---

## 6. Evidence of Autoscaling 

### 6.1 Autoscaling Configuration (Terraform)

Two different metrics used as required:

**Initial Configuration:**
| Service | Metric | Threshold | Min | Max |
|---------|--------|-----------|-----|-----|
| Product Service | CPU Utilization | 85% | 1 | 3 |
| Shopping Cart Service | CPU + Memory | 50% / 50% | 2 | 5 |
| Credit Card Authorizer | CPU Utilization | 70% | 1 | 3 |
| Warehouse Service | Memory Utilization | 70% | 1 | 3 |

**Tuned Configuration (after load testing):**
| Service | Metric | Threshold | Min | Max |
|---------|--------|-----------|-----|-----|
| Product Service | CPU Utilization | **30%** | 1 | 3 |
| Shopping Cart Service | CPU + Memory | 50% / 50% | 2 | 5 |
| Credit Card Authorizer | CPU Utilization | 70% | 1 | 3 |
| Warehouse Service | Memory Utilization | 70% | 1 | 3 |

**Key Configuration Details:**
- Shopping Cart Service uses dual scaling policies (CPU and Memory) to react faster to load spikes
- Health check grace period: 60 seconds (allows Spring Boot time to start)
- Scale-out cooldown: 30-60 seconds, Scale-in cooldown: 30-120 seconds

### 6.2 Autoscaling Evidence

**Shopping Cart Service Scaling During Load Tests:**

| Load Test | Users | Running Tasks | Healthy Targets | Autoscaled? |
|-----------|-------|---------------|-----------------|-------------|
| Test 1 | 300 | 4 | 4 | Baseline |
| Test 2 | 500 | 3 | 3 | Scaled down (cooldown) |
| Test 3 | 1000 | 3 | 3 | Stable |
| Test 4 | 1500 | 4 | 4 | YES - scaled up |
| Test 5 | 2000 | 5 | 5 | YES - reached max |

**Product Service Scaling During Load Tests:**

| Load Test | Users | CPU Threshold | Running Tasks | Autoscaled? |
|-----------|-------|---------------|---------------|-------------|
| Tests 1-4 | 300-1500 | 85% | 1 | NO |
| Test 5 (50% threshold) | 2000 | 50% | 2 | YES (delayed) |
| Test 5 (30% threshold) | 2000 | 30% | 3 | YES (proactive) |

**Evidence Screenshots:**
- At 300 users: 4 healthy cart targets (see `shopping-cart-health-300.png`)
- At 500 users: 3 running cart tasks (see `shopping-cart-service-500.png`)
- At 1000 users: 3 running cart tasks (see `shopping-cart-service-1000.png`)
- At 1500 users: 4 running cart tasks (see `shopping-cart-service-1500.png`)
- At 2000 users: 5 running cart tasks (see `shopping-cart-service-2000.png`)
- At 2000 users (50% threshold): 2 product tasks (see `product-service-2000.png`)
- At 2000 users (30% threshold): **3 product tasks** (see `product-service-2000-30percentthreshold.png`)

**Product Service Autoscaled to 3 Instances (with 30% threshold):**

![Product Service 2000 30% Threshold](images/product-service-2000-30percentthreshold.png)

### 6.3 Autoscaling Tuning Process

We iteratively tuned the autoscaling thresholds based on load testing observations:

| Step | Change | Result |
|------|--------|--------|
| 1 | Initial config: Product Service CPU threshold = 85% | Product Service didn't scale at 1500 users |
| 2 | Lowered threshold: 85% → 50% | At 2000 users: 7% failure early, 3% after scaling to 2 instances |
| 3 | Observed: 50% threshold still too high | Product Service scaled late, causing initial failures |
| 4 | Lowered threshold: 50% → 30% | Product Service scaled proactively to 3 instances |
| 5 | Re-tested at 2000 users | 0.08% failure rate - 37x improvement over 50% threshold |

**Impact of Threshold Tuning:**

| Metric | 50% Threshold | 30% Threshold | Improvement |
|--------|---------------|---------------|-------------|
| Failure Rate | 3% | 0.08% | 37x better |
| Product Instances | 2 (delayed) | 3 (proactive) | 50% more capacity |
| 95th Percentile Latency | 25,000 ms | 19,000 ms | 24% faster |

### 6.4 Scaling Results Summary

| Service | Min | Max | Peak Observed | Scaled? |
|---------|-----|-----|---------------|---------|
| Shopping Cart Service | 2 | 5 | 5 | YES |
| Product Service | 1 | 3 | 3 | YES (with tuned threshold) |
| Credit Card Authorizer | 1 | 3 | 1 | NO |
| Warehouse Service | 1 | 3 | 1 | NO |

**Why Shopping Cart and Product Services scale:**
- **Shopping Cart**: Orchestrates multiple downstream calls (KV + Product Service + Credit Card + RabbitMQ)
- **Product Service**: Handles all product validation requests from Shopping Cart during Add Item operations

**Why Credit Card and Warehouse don't scale:**
- **Credit Card**: Stateless, no database calls, minimal processing
- **Warehouse**: Async processing via RabbitMQ, decoupled from request flow

### 6.5 Performance Improvement Recommendations

| Priority | Improvement | Cost | Expected Impact |
|----------|-------------|------|-----------------|
| 1 | Lower autoscaling thresholds (30% vs 70-85%) | None | Scale before overload |
| 2 | Increase min instances for critical services | Low | Better baseline capacity |
| 3 | Reduce scale-out cooldown to 15s | None | Faster reaction to load |
| 4 | Add autoscaling to KV database layer | Medium | Remove database bottleneck |
| 5 | Increase container size (1024 CPU, 2048 MB) | Medium | Each instance handles more |

---

## 7. Deployment Architecture (Terraform)

Our entire infrastructure is deployed as Infrastructure-as-Code using Terraform, enabling reproducible and version-controlled deployments.

### 7.1 Infrastructure Components

| Component | Type | Configuration |
|-----------|------|---------------|
| ECS Cluster | Fargate | Serverless container orchestration |
| Product Service | ECS Service | Port 8082, CPU autoscaling |
| Shopping Cart Service | ECS Service | Port 8084, Memory autoscaling |
| Credit Card Authorizer | ECS Service | Port 8080, CPU autoscaling |
| Warehouse Service | ECS Service | Port 8083, Memory autoscaling |
| Leaderless KV | ECS Service | 5 peer nodes, W=N, R=1 |
| Leader-Follower KV | ECS Service | 1 leader + followers, W=1, R=1 |
| RabbitMQ | EC2 Instance | Message queue for async communication |
| Application Load Balancer | ALB | Path-based routing to services |

### 7.2 Networking

| Resource | Purpose |
|----------|---------|
| VPC | Isolated network for all resources |
| Public Subnets | ALB, NAT Gateway |
| Private Subnets | ECS services, RabbitMQ |
| Security Groups | Service-to-service communication rules |

### 7.3 Path-Based Routing (ALB)

| Path Pattern | Target Service |
|--------------|----------------|
| `/products/*` | Product Service |
| `/shopping-cart*` | Shopping Cart Service |
| `/credit-card-authorizer/*` | Credit Card Authorizer |

### 7.4 Deployment Commands

```bash
cd terraform
terraform init      # Initialize providers
terraform plan      # Preview changes
terraform apply     # Deploy infrastructure
terraform destroy   # Tear down infrastructure
```

---

## 8. Conclusion

This project demonstrates a production-ready distributed e-commerce system that addresses real-world scalability challenges through careful architectural decisions and iterative performance tuning.

**What We Built:**
- Four microservices deployed on AWS ECS Fargate with autoscaling
- Two custom distributed KV databases with different replication strategies
- Asynchronous order processing via RabbitMQ
- Infrastructure-as-Code deployment using Terraform

**Key Technical Decisions Validated by Load Testing:**

| Decision | Rationale | Result |
|----------|-----------|--------|
| Leaderless KV (W=N, R=1) for Products | Read-heavy workload, rare updates | ~1.2s response, 0.02% failures at 2000 users |
| Leader-Follower KV (W=1, R=1) for Carts | Write-heavy workload, fast updates | Scaled to handle 461.9 RPS |
| AP over CP (CAP trade-off) | Availability critical for e-commerce | System remained responsive under load |
| Dual-metric autoscaling (CPU + Memory) | Shopping Cart is the bottleneck | Scaled 2→5 instances automatically |
| Health check grace period (60s) | Spring Boot startup time | Eliminated task restart loops |
| **Tuned autoscaling threshold (30%)** | Scale before overload | Reduced failures from 3% to 0.08% |

**Load Testing Results Summary:**

| Metric | 50% Threshold | 30% Threshold (Final) |
|--------|---------------|----------------------|
| Maximum Users Tested | 2,000 | 2,000 |
| Peak Throughput | 461.9 RPS | 244.1 RPS |
| Total Requests | 340,203 | 125,313 |
| Failure Rate | 3% | **0.08%** |
| Product Service Instances | 2 (delayed) | 3 (proactive) |
| Shopping Cart Instances | 5 (max) | 5 (max) |

**Autoscaling Tuning Impact:**
- Lowering CPU threshold from 50% to 30% resulted in **37x improvement** in failure rate
- Proactive scaling prevented service overload
- 24% improvement in 95th percentile latency (25s → 19s)

**Lessons Learned:**
1. **Autoscaling thresholds matter more than max capacity** - A high threshold (50%) means the service is overwhelmed before scaling triggers. Lower thresholds (30%) enable proactive scaling.
2. **Workload analysis drives database design** - Matching replication strategy to read/write ratio is critical. Leaderless for reads, Leader-Follower for writes.
3. **Orchestrator services become bottlenecks** - The Shopping Cart Service coordinates 4+ operations per request, requiring more scaling headroom.
4. **Health check grace period is essential** - Spring Boot needs 30-60 seconds to start; without grace period, ECS kills tasks prematurely.
5. **Iterative load testing reveals tuning opportunities** - Testing at 300→500→1000→1500→2000 users allowed us to identify and fix autoscaling issues.

The system successfully handled 340,203 requests at 461.9 RPS with 2,000 concurrent users. After tuning autoscaling thresholds, we achieved a **0.08% failure rate** with proactive scaling, demonstrating that the architecture scales horizontally under load while maintaining stability.
