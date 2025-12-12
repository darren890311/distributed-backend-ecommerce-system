# CS6650 Assignment 5 - System Architecture

## High-Level Architecture

```mermaid
flowchart TD
    subgraph Internet
        Client[Client/Locust]
    end

    subgraph AWS["AWS Cloud"]
        ALB[Application Load Balancer<br/>Port 80]

        subgraph ECS["ECS Fargate Cluster"]
            PS[Product Service<br/>:8082<br/>CPU Autoscale 70%]
            SC[Shopping Cart Service<br/>:8084<br/>Memory Autoscale 70%]
            CC[Credit Card Authorizer<br/>:8080<br/>CPU Autoscale 70%]
            WH[Warehouse Service<br/>:8083<br/>Memory Autoscale 70%]
        end

        subgraph Databases["Distributed KV Databases"]
            LLK[(Leaderless KV<br/>W=N, R=1<br/>Products)]
            LFK[(Leader-Follower KV<br/>W=1, R=1<br/>Shopping Carts)]
        end

        RMQ[RabbitMQ<br/>:5672]
    end

    Client --> ALB
    ALB --> PS
    ALB --> SC
    ALB --> CC

    PS --> LLK
    SC --> LFK
    SC --> CC
    SC --> RMQ
    RMQ --> WH
```

## Use Case 1: Add Item to Cart

```mermaid
sequenceDiagram
    participant C as Client
    participant ALB as Load Balancer
    participant SC as Shopping Cart
    participant LFK as Leader-Follower KV
    participant PS as Product Service
    participant LLK as Leaderless KV

    C->>ALB: POST /shopping-carts/{id}/addItem
    ALB->>SC: Route request
    SC->>LFK: beginTransaction()
    SC->>LFK: getShoppingCart(cartId)
    LFK-->>SC: Cart data
    SC->>PS: GET /products/{productId}
    PS->>LLK: Read product (R=1)
    LLK-->>PS: Product data
    PS-->>SC: Product exists
    SC->>LFK: setShoppingCart(cart)
    SC->>LFK: endTransaction()
    SC-->>ALB: 204 No Content
    ALB-->>C: Success
```

## Use Case 2: Checkout Flow

```mermaid
sequenceDiagram
    participant C as Client
    participant SC as Shopping Cart
    participant LFK as Leader-Follower KV
    participant CC as Credit Card
    participant RMQ as RabbitMQ
    participant WH as Warehouse

    C->>SC: POST /shopping-carts/{id}/checkout
    SC->>LFK: beginTransaction()
    SC->>LFK: getShoppingCart(cartId)
    LFK-->>SC: Cart data
    SC->>CC: POST /authorize

    alt Payment Approved (90%)
        CC-->>SC: 200 OK
        SC->>RMQ: Publish order message
        RMQ->>WH: Deliver message (async)
        WH->>WH: Process shipment
        SC->>LFK: setShoppingCart(CHECKED_OUT)
        SC->>LFK: endTransaction()
        SC-->>C: 200 OK
    else Payment Declined (10%)
        CC-->>SC: 402 Payment Required
        SC->>LFK: abortTransaction()
        SC-->>C: 402 Payment Declined
    end
```

## Database Replication Models

### Leaderless KV (Product Service)

```mermaid
flowchart LR
    subgraph Leaderless["Leaderless Cluster (W=N, R=1)"]
        N1[(Node 1)]
        N2[(Node 2)]
        N3[(Node 3)]
        N4[(Node 4)]
        N5[(Node 5)]
    end

    LB[Load Balancer] --> N1
    LB --> N2
    LB --> N3
    LB --> N4
    LB --> N5

    Write[Write Request] --> |"W=N (all nodes)"| N1
    Write --> N2
    Write --> N3
    Write --> N4
    Write --> N5

    Read[Read Request] --> |"R=1 (any node)"| LB
```

### Leader-Follower KV (Shopping Cart Service)

```mermaid
flowchart LR
    subgraph LeaderFollower["Leader-Follower Cluster (W=1, R=1)"]
        L[(Leader)]
        F1[(Follower 1)]
        F2[(Follower 2)]
    end

    Write[Write Request] --> |"W=1"| L
    Read[Read Request] --> |"R=1"| L

    L --> |"Async Replication"| F1
    L --> |"Async Replication"| F2
```

## Autoscaling Behavior Under Load

```mermaid
flowchart TD
    subgraph Before["Before Load Test"]
        PS1[Product Service: 1]
        SC1[Shopping Cart: 1]
        CC1[Credit Card: 1]
        WH1[Warehouse: 1]
    end

    subgraph During["At 1500-2000 Users"]
        PS2[Product Service: 1]
        SC2[Shopping Cart: 1→2→5]
        CC2[Credit Card: 1]
        WH2[Warehouse: 1]
    end

    Before --> |"502 Errors<br/>CPU > 70%"| During
```

## Key Design Decisions

| Component | Choice | Rationale |
|-----------|--------|-----------|
| Product DB | Leaderless (W=N, R=1) | Read-heavy, rare updates |
| Cart DB | Leader-Follower (W=1, R=1) | Write-heavy, fast responses |
| Warehouse | RabbitMQ (async) | Fire-and-forget shipping |
| CAP Trade-off | AP | Availability over consistency |
