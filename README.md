# CS6650 Assignment 5 - E-Commerce System: KV DB Integration & Auto-Scaling

## Project Overview
This assignment integrates all distributed components built throughout the course. The primary goal is to simulate a production-ready e-commerce environment by implementing all core business logic, replacing JPA with a Distributed Key-Value Database (KV DB), and introducing deliberate processing delays to stimulate horizontal auto-scaling in AWS.
The system now features a complete transactional flow from adding an item to finalizing the checkout process, coordinated by the Shopping Cart Service (SCS).


## Key Features

### Microservices
1. **Product Service**: Migrated to Distributed KV DB storage. Includes server-side ID generation and delays.
2. **Shopping Cart Service**: Full checkout orchestration with simulated ACID transactions (BEGIN/END/ABORT). Fully migrated from JPA to KV DB.
3. **Credit Card Authorizer**: Payment validation (90% approval rate). Implements explicit card syntax check and delays.
4. **Warehouse Consumer**: reserve (90% stock check) and ship endpoints. Functions as the RabbitMQ Consumer for asynchronous order fulfillment.


## Local Code Review & Quick Start Guide
Local Development & Integration Test Guide

This guide details the precise steps and commands required to launch the integrated microservice ecosystem locally for code review and functional testing.

---

## 1. Start Infrastructure (KV DB Leader & RabbitMQ)

### KV DB Leader (Assignment 4)

**Terminal Location:**
```
cs6650-assignment4/leader-follower-kv
```

**Run Command:**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=leader
```

**Notes:**
- Leader runs on **port 8080**.

---

### RabbitMQ Broker (Docker)

**Terminal Location:** Any directory

**Run Command:**
```bash
docker run -d --hostname rabbit-server --name rabbitmq \
  -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

**Notes:**
- Management UI: http://localhost:15672  
- Username/password: `guest / guest`

---

## 2. Start Microservices (Assignment 5)

Start each service in a **separate terminal**.

| Service | Directory | Command | Port |
|--------|-----------|---------|------|
| **Product Service (PS)** | `product-service` | `mvn spring-boot:run` | 8082 |
| **Warehouse Service (WS)** | `warehouse-service` | `mvn spring-boot:run` | 9083 |
| **Credit Card Auth (CCA)** | `credit-card-authorizer` | `mvn spring-boot:run` | 8085 |
| **Shopping Cart Service (SCS)** | `shopping-cart-service` | `mvn spring-boot:run` | 8084 |

---

## 3. Integration Test Sequence (Postman)

Once all services are running, test the end-to-end flow through **Shopping Cart Service (SCS)**.

---

### 3.1 Product Pre-Check

**Endpoint:**
```
POST http://localhost:8082/products
```

**Example Body:**
```json
{
    "product_id": 1,
    "sku": "ABC123XYZ9",
    "manufacturer": "Global Manufacturing Co",
    "category_id": 1001,
    "weight": 500,
    "some_other_id": 1
}
```

**Purpose:**
- Verify KV DB write  
- Save generated `product_id`

---

### 3.2 Create Cart

**Endpoint:**
```
POST http://localhost:8084/shopping-cart
```

**Body:**
```json
{
  "customer_id": 100
}
```

**Purpose:**
- Create a new shopping cart  
- Save returned `shoppingCartId`

---

### 3.3 Add Item (Use Case 1)

**Endpoint:**
```
POST http://localhost:8084/shopping-carts/{cartId}/addItem
```

**Body:**
```json
{
  "product_id": "<SAVED_PRODUCT_ID>",
  "quantity": 1
}
```

**Verify in Logs:**
- `BEGIN TRANSACTION` / `END TRANSACTION`  
- Product existence check (PS)  
- Stock check (WS)

---

### 3.4 Checkout (Use Case 2)

**Endpoint:**
```
POST http://localhost:8084/shopping-carts/{cartId}/checkout
```

**Body:**
```json
{
  "credit_card_number": "1234-5678-9012-3456"
}
```

**Expected Behavior:**
- Randomized payment simulation:
  - `402 Payment Required` → failure  
  - `200 OK` → success
- On success:
  - RabbitMQ publish event  
  - Final `END TRANSACTION` persisted

---

## Summary

This setup launches the full microservice ecosystem:

- KV DB Leader  
- RabbitMQ  
- Product Service  
- Warehouse Service  
- Credit Card Auth  
- Shopping Cart Service  

This environment supports complete end-to-end integration testing.
