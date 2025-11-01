# CS6650 Assignment 3 - E-Commerce Microservices with RabbitMQ

## Project Overview
Distributed e-commerce system with microservices architecture, demonstrating:
- Scalable message queue processing with RabbitMQ
- AWS deployment with Application Load Balancer
- High-throughput load testing and optimization
- Container orchestration with ECS Fargate

## Architecture
```
Client (Load Tester)
    ↓ HTTP
Application Load Balancer
    ↓
┌─────────────────┬──────────────────┬───────────────────┐
│ Product Service │ Shopping Cart    │ Credit Card       │
│ (Port 8082)     │ Service          │ Authorizer        │
│                 │ (Port 8084)      │ (Port 8080)       │
└─────────────────┴──────────────────┴───────────────────┘
         ↑                 ↓                    ↑
         │        RabbitMQ Queue                │
         │         (Port 5672)                  │
         │                 ↓                    │
         │        Warehouse Consumer            │
         │        (8-16 threads)                │
         └─────────────────────────────────────┘
           (Private IP Communication)
```

## Key Features

### Microservices
1. **Product Service**: CRUD operations for products
2. **Shopping Cart Service**: Cart management and checkout orchestration
3. **Credit Card Authorizer**: Payment validation (90% approval rate)
4. **Warehouse Consumer**: Asynchronous order processing via RabbitMQ

### Infrastructure
- **AWS ECS Fargate**: Serverless container orchestration
- **Application Load Balancer**: Traffic distribution with health-based routing
- **RabbitMQ**: Message queue for asynchronous communication
- **Terraform**: Infrastructure as Code for repeatable deployments

### Performance Optimization
- **Thread Configuration**: 128 client threads, 8-16 warehouse consumer threads
- **Connection Pooling**: HTTP client reuse for efficiency
- **Optimized Load Testing**: 1 item per cart for maximum throughput
- **Achieved Throughput**: ~115 requests/second with 96.7% success rate

## Quick Start

### Prerequisites
- AWS Learner Lab access
- Docker Desktop
- Java 17+
- Maven 3.8+
- Terraform 1.0+

### Local Development (Docker Compose)
```bash
# Start all services locally
docker-compose up --build

# Access RabbitMQ Management: http://localhost:15672 (guest/guest)
```

### AWS Deployment

#### 1. Configure AWS Credentials
```bash
# Update credentials from AWS Learner Lab
nano ~/.aws/credentials
# Test: aws sts get-caller-identity
```

#### 2. Deploy Infrastructure
```bash
cd terraform
terraform init
terraform apply  # Takes ~10 minutes
```

#### 3. Get Service Private IPs
```bash
# Wait 3 minutes for services to start
./get-service-ips.sh

# Update ecs_task_definitions.tf with displayed IPs
# Then: terraform apply
```

#### 4. Build and Push Docker Images
```bash
cd ..
aws ecr get-login-password --region us-west-2 | \
  docker login --username AWS --password-stdin 590183802817.dkr.ecr.us-west-2.amazonaws.com

./build-and-push-all.sh  # Takes ~15 minutes
```

#### 5. Force Services to Pull New Images
```bash
for service in product-service shopping-cart-service credit-card-authorizer warehouse-service; do
  aws ecs update-service --cluster ecommerce-cluster --service $service --force-new-deployment --no-cli-pager
done

# Wait 3 minutes for services to restart
sleep 180
```

### Run Load Test
```bash
cd load-testing-client

# Update config with new ALB DNS
nano src/main/resources/config-aws.properties

# Compile and run
mvn clean compile
mvn exec:java -Dexec.mainClass="com.cs6650.loadtest.LoadTestingClient" -Dexec.args="aws"
```

Monitor RabbitMQ: Check terraform output for `rabbitmq_management_url`

## Configuration

### Key Configuration Files

#### Load Testing (`load-testing-client/src/main/resources/config-aws.properties`)
```properties
total.checkouts=200000    # Number of checkout operations
items.per.cart=1          # Items per cart (optimized for throughput)
thread.count=128          # Client threads for maximum throughput
```

#### Warehouse Consumer (`terraform/ecs_task_definitions.tf`)
```hcl
SPRING_RABBITMQ_LISTENER_SIMPLE_CONCURRENCY=8      # Initial consumer threads
SPRING_RABBITMQ_LISTENER_SIMPLE_MAX_CONCURRENCY=16 # Max consumer threads
SPRING_RABBITMQ_LISTENER_SIMPLE_PREFETCH=250       # Messages per consumer
```

### Inter-Service Communication

**AWS Learner Lab Limitation:** Service Discovery (AWS Cloud Map) not available

**Solution:** Direct container-to-container communication using private IPs
- Shopping Cart → Product Service: `http://[PRODUCT_IP]:8082`
- Shopping Cart → Credit Card Authorizer: `http://[CCA_IP]:8080`
- Services → RabbitMQ: `[RABBITMQ_PRIVATE_IP]:5672`

**Note:** Private IPs change with each lab session and must be updated in `terraform/ecs_task_definitions.tf`


## Repository Structure
```
├── product-service/              # Product CRUD microservice
│   ├── src/
│   ├── Dockerfile
│   └── pom.xml
├── shopping-cart-service/        # Cart & checkout orchestrator
│   ├── src/
│   ├── Dockerfile
│   └── pom.xml
├── credit-card-authorizer/       # Payment validation service
│   ├── src/
│   ├── Dockerfile
│   └── pom.xml
├── warehouse-service/            # RabbitMQ consumer
│   ├── src/
│   ├── Dockerfile
│   └── pom.xml
├── load-testing-client/          # Performance testing client
│   ├── src/
│   └── pom.xml
├── terraform/                    # Infrastructure as Code
│   ├── *.tf files
│   ├── get-service-ips.sh       # Helper for IP discovery
│   └── RESTART_GUIDE.md
├── docker-compose.yml            # Local development
├── build-and-push-all.sh         # Build automation
└── README.md
```


## Troubleshooting

### Common Issues

**Service IPs need updating:**
```bash
cd terraform
./get-service-ips.sh
# Update ecs_task_definitions.tf with displayed IPs
terraform apply
aws ecs update-service --cluster ecommerce-cluster --service shopping-cart-service --force-new-deployment
```

**RabbitMQ not accessible:**
```bash
# Check if RabbitMQ is running
aws ec2 describe-instances --instance-ids $(terraform output -raw rabbitmq_instance_id) --query 'Reservations[0].Instances[0].State.Name'
# If stopped: aws ec2 start-instances --instance-ids $(terraform output -raw rabbitmq_instance_id)
```

**Services failing health checks:**
```bash
# Check logs
aws logs tail /ecs/ecommerce --since 10m --follow
```
