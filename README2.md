# CS6650 Assignment 5 - E-Commerce Microservices Infrastructure

**Completed by:** Christy (Qingyi)  
**Status:** Infrastructure deployed and tested - Ready for database integration and load testing

---

## Table of Contents

1. [What's Already Done](#whats-already-done)
2. [How to Deploy](#how-to-deploy)
3. [What Needs to be Completed](#what-needs-to-be-completed)
4. [Architecture Overview](#architecture-overview)
5. [Troubleshooting](#troubleshooting)

---

## What's Already Done

### Infrastructure Deployed

**1. Docker Images in ECR:**
- cs6650-product-service
- cs6650-shopping-cart-service
- cs6650-credit-card-authorizer
- cs6650-warehouse-service
- cs6650-kv-database (image ready, service not deployed)

**2. AWS Infrastructure (Terraform):**
- Application Load Balancer with URL path-based routing
- 5 ECS Fargate services (all tested and healthy)
- RabbitMQ EC2 instance for asynchronous messaging
- Auto-scaling configuration (1-3 instances per service)
- Security groups and VPC networking
- CloudWatch logging and monitoring

**3. Code Modifications:**
- Added 100-1000ms random delays to all endpoints
- Fixed RabbitMQ environment variables (SPRING_RABBITMQ_*)
- Fixed Credit Card service port configuration (SERVER_PORT=8080)
- All services tested locally and on AWS

**4. Load Testing:**
- Locust test scripts created and tested locally
- Results show proper endpoint functionality
- Ready for AWS deployment testing

**Current Status:**
- AWS resources: DESTROYED (to save credits)
- Terraform code: READY to redeploy in ~10 minutes
- Estimated AWS cost: $0.25/hour

---

## How to Deploy

### Prerequisites

1. AWS Learner Lab account with available credits
2. Terraform installed locally
3. AWS CLI configured
4. AWS key pair (e.g., cs6650-key2)

### Step 1: Get the Code
```bash
cd ~/Desktop/cs6650
git clone <repo-url> cs6650-assignment5
# Or if you already have it
cd cs6650-assignment5
git pull origin Qingyi
```

### Step 2: Configure AWS Credentials

From AWS Learner Lab, get your session credentials:
```bash
aws configure set aws_access_key_id YOUR_ACCESS_KEY
aws configure set aws_secret_access_key YOUR_SECRET_KEY
aws configure set aws_session_token YOUR_SESSION_TOKEN
aws configure set region us-east-1

# Verify
aws sts get-caller-identity
```

### Step 3: Set Terraform Variables
```bash
cd terraform
echo 'key_name = "cs6650-key2"' > terraform.tfvars
```

### Step 4: Deploy Infrastructure
```bash
terraform init
terraform apply -auto-approve
```

Wait 5-10 minutes for all services to become healthy.

### Step 5: Get Load Balancer URL
```bash
terraform output alb_dns_name
```

### Step 6: Verify Services Health
```bash
# Check all services are running
aws ecs list-services --cluster ecommerce-cluster --region us-east-1

# Check target health
aws elbv2 describe-target-health \
  --target-group-arn $(terraform output -raw shopping_cart_service_tg_arn) \
  --region us-east-1
```

Wait for all targets to show `"State": "healthy"`

### Step 7: Test Services
```bash
export ALB_DNS=$(terraform output -raw alb_dns_name)

# Health check
curl http://$ALB_DNS/actuator/health

# Shopping cart
curl -X POST http://$ALB_DNS/shopping-cart \
  -H "Content-Type: application/json" \
  -d '{"customerId": "123"}'
```

### Step 8: Destroy When Done
```bash
terraform destroy -auto-approve
```

---

## What Needs to be Completed

### Part 1: KV Database Deployment

#### Step 1.1: Verify KV Database Image in ECR
```bash
aws ecr describe-images --repository-name cs6650-kv-database --region us-east-1
```

#### Step 1.2: Create KV Database Terraform Configuration

Create file `terraform/ecs_kv_database.tf`:
```hcl
# ECR Repository reference
data "aws_ecr_repository" "kv_database" {
  name = "cs6650-kv-database"
}

# Task Definition
resource "aws_ecs_task_definition" "kv_database" {
  family                   = "kv-database"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = data.aws_iam_role.lab_role.arn
  task_role_arn            = data.aws_iam_role.lab_role.arn

  container_definitions = jsonencode([
    {
      name      = "kv-database"
      image     = "${data.aws_ecr_repository.kv_database.repository_url}:latest"
      essential = true

      portMappings = [
        {
          containerPort = 8080
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "SERVER_PORT"
          value = "8080"
        },
        {
          name  = "N_VALUE"
          value = "3"
        },
        {
          name  = "R_VALUE"
          value = "2"
        },
        {
          name  = "W_VALUE"
          value = "2"
        },
        {
          name  = "IS_LEADER"
          value = "true"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.ecs_logs.name
          "awslogs-region"        = "us-east-1"
          "awslogs-stream-prefix" = "kv-database"
        }
      }
    }
  ])
}

# ECS Service
resource "aws_ecs_service" "kv_database" {
  name            = "kv-database"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.kv_database.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = data.aws_subnet.public[*].id
    security_groups  = [aws_security_group.kv_database_sg.id]
    assign_public_ip = true
  }
}

# Security Group
resource "aws_security_group" "kv_database_sg" {
  name        = "kv-database-sg"
  description = "Security group for KV Database"
  vpc_id      = data.aws_vpc.main.id

  ingress {
    description     = "HTTP from services"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [
      aws_security_group.product_service_sg.id,
      aws_security_group.shopping_cart_service_sg.id
    ]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}
```

#### Step 1.3: Update Product and Shopping Cart Services

Edit `ecs_task_definitions.tf` and add to both services' environment arrays:
```hcl
{
  name  = "KVSTORE_LEADER_URL"
  value = "http://REPLACE_WITH_KV_IP:8080"
}
```

#### Step 1.4: Deploy and Get KV Database IP
```bash
# Deploy
terraform apply -auto-approve

# Get IP address
KV_TASK_ARN=$(aws ecs list-tasks --cluster ecommerce-cluster \
  --service-name kv-database --region us-east-1 \
  --query 'taskArns[0]' --output text)

KV_IP=$(aws ecs describe-tasks --cluster ecommerce-cluster \
  --tasks $KV_TASK_ARN \
  --region us-east-1 \
  --query 'tasks[0].containers[0].networkInterfaces[0].privateIpv4Address' \
  --output text)

echo "KV Database IP: $KV_IP"

# Update ecs_task_definitions.tf with this IP, then redeploy
terraform apply -auto-approve
```

#### Step 1.5: Verify Database Connection
```bash
# Test endpoints that require database
curl http://$ALB_DNS/products/1
```

### Part 2: Adjust Auto-Scaling Thresholds

Current threshold is 70%, but testing showed CPU only reached ~50%.

Edit `autoscaling.tf`:
```bash
# Lower threshold from 70 to 30
sed -i 's/target_value       = 70/target_value       = 30/g' autoscaling.tf

# Apply changes
terraform apply -auto-approve
```

Alternatively, deploying the KV database will create real workload that naturally increases CPU usage.

### Part 3: Load Testing with Locust

#### Step 3.1: Pre-load 1000 Products

Create `preload_products.py`:
```python
import requests
import random

ALB_DNS = "your-alb-dns.amazonaws.com"

print("Pre-loading 1000 products...")
for i in range(1, 1001):
    product = {
        "id": i,
        "name": f"Product {i}",
        "price": round(random.uniform(10, 500), 2),
        "description": f"Description for product {i}"
    }
    response = requests.post(f"http://{ALB_DNS}/products", json=product)
    if i % 100 == 0:
        print(f"Created {i} products")

print("Done!")
```
```bash
python preload_products.py
```

#### Step 3.2: Update Locust Configuration

Update `locustfile.py` with actual ALB DNS:
```python
class EcommerceCustomer(HttpUser):
    tasks = [CustomerShoppingSession]
    wait_time = between(2, 5)
    host = "http://your-actual-alb-dns.amazonaws.com"  # UPDATE THIS
```

#### Step 3.3: Run Load Test
```bash
locust -f locustfile.py
```

Open browser: http://localhost:8089

**Test Configuration:**
- Start with 20 users, spawn rate 2
- Gradually increase to 50, then 100 users
- Monitor for 5-10 minutes at each level

#### Step 3.4: Monitor Auto-Scaling

Watch ECS services scale:
```bash
# Monitor service scaling
watch -n 5 'aws ecs describe-services --cluster ecommerce-cluster \
  --services shopping-cart-service --region us-east-1 \
  --query "services[0].{Desired:desiredCount,Running:runningCount}"'

# Monitor CPU metrics
aws cloudwatch get-metric-statistics \
  --namespace AWS/ECS \
  --metric-name CPUUtilization \
  --dimensions Name=ServiceName,Value=shopping-cart-service Name=ClusterName,Value=ecommerce-cluster \
  --start-time $(date -u -d '10 minutes ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 60 \
  --statistics Average,Maximum \
  --region us-east-1
```

#### Step 3.5: Capture Evidence

Take screenshots of:
- ECS console showing 2-3 tasks running per service
- CloudWatch metrics showing CPU/Memory increasing
- Locust dashboard showing request statistics
- Target Group health showing multiple healthy targets

### Part 4: Documentation

Document the following in your assignment report:

**1. Database Design Choices:**
- Replication strategy (Leader-based vs Leaderless)
- N/R/W values and reasoning
- CAP trade-off decision and justification
- Based on Assignment 4 results

**2. Workload Assumptions:**
- Use case distribution (70% add to cart, 30% checkout)
- Product selection distribution
- Items per cart (average 3-5)
- Customer think time between actions
- Read/write ratio per service

**3. Auto-Scaling Results:**
- Which service scaled first
- CPU/Memory thresholds reached
- Time to scale from 1 to 3 instances
- Bottleneck analysis
- What would you do with more resources

---

## Architecture Overview

### Current Architecture
```
                    Internet
                        |
                        v
        +-------------------------------+
        |  Application Load Balancer    |
        |        (Port 80)               |
        +---------------+---------------+
                        |
        +---------------+-----------------+
        |               |                 |
        v               v                 v
+--------------+  +--------------+  +--------------+
|   Product    |  | Shopping Cart|  | Credit Card  |
|   Service    |  |   Service    |  |  Authorizer  |
| (ECS Fargate)|  | (ECS Fargate)|  | (ECS Fargate)|
|   Port 8082  |  |   Port 8084  |  |   Port 8080  |
|              |  |      |        |  |              |
| Auto-scale   |  |      |        |  | Auto-scale   |
|  1-3 (CPU)   |  |      |        |  |  1-3 (CPU)   |
+--------------+  +------+--------+  +--------------+
                        |
                        v
                +--------------+
                |   RabbitMQ   |
                |  (EC2 t2.micro)|
                |   Port 5672  |
                +------+-------+
                       |
                       v
                +--------------+
                |  Warehouse   |
                |   Service    |
                | (ECS Fargate)|
                |   Port 9083  |
                |              |
                | Auto-scale   |
                | 1-3 (Memory) |
                +--------------+
```

### Target Architecture (After Database Integration)
```
                    Internet
                        |
                        v
        +-------------------------------+
        |  Application Load Balancer    |
        +---------------+---------------+
                        |
        +---------------+-------------+
        |               |             |
        v               v             v
+--------------+  +--------------+  +--------------+
|   Product    |  | Shopping Cart|  | Credit Card  |
|   Service    |  |   Service    |  |  Authorizer  |
+-------+------+  +------+-------+  +--------------+
        |                |
        |                +-------------+
        |                              |
        v                              v
+------------------------------+  +--------------+
|     KV Database Cluster      |  |   RabbitMQ   |
| (Leader or Leaderless)       |  +------+-------+
|    N=3, R=2, W=2             |         |
+------------------------------+         v
                                  +--------------+
                                  |  Warehouse   |
                                  |   Service    |
                                  +--------------+
```

### ALB Routing Rules

| URL Pattern | Target Service | Port | Priority |
|-------------|----------------|------|----------|
| /products/* | Product Service | 8082 | 100 |
| /shopping-cart/* | Shopping Cart | 8084 | 200 |
| /credit-card/* | Credit Card | 8080 | 300 |

---

## Troubleshooting

### Services Not Healthy

Check logs:
```bash
aws logs tail /ecs/ecommerce --follow --region us-east-1 --since 5m
```

Check task status:
```bash
aws ecs describe-tasks --cluster ecommerce-cluster \
  --tasks $(aws ecs list-tasks --cluster ecommerce-cluster \
  --service-name shopping-cart-service --region us-east-1 \
  --query 'taskArns[0]' --output text) \
  --region us-east-1
```

Common issues:
- RabbitMQ connection refused: Verify SPRING_RABBITMQ_* environment variables
- Port mismatch: Ensure containerPort matches health check port
- Image not found: Verify Docker images exist in ECR

### Auto-Scaling Not Working

Check CloudWatch metrics:
```bash
aws cloudwatch get-metric-statistics \
  --namespace AWS/ECS \
  --metric-name CPUUtilization \
  --dimensions Name=ServiceName,Value=product-service Name=ClusterName,Value=ecommerce-cluster \
  --start-time $(date -u -d '10 minutes ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 60 \
  --statistics Average,Maximum \
  --region us-east-1
```

Solutions:
- Lower threshold from 70% to 30%
- Increase load test intensity
- Deploy KV database for real workload

### Terraform Errors

Security group already exists:
```bash
terraform import aws_security_group.alb_sg <sg-id>
```

Target group already exists:
```bash
aws elbv2 delete-target-group --target-group-arn <arn>
```

State file issues:
```bash
rm -rf .terraform terraform.tfstate*
terraform init
```

### KV Database Connection Issues

Get database IP:
```bash
KV_IP=$(aws ecs describe-tasks --cluster ecommerce-cluster \
  --tasks $(aws ecs list-tasks --cluster ecommerce-cluster \
  --service-name kv-database --region us-east-1 \
  --query 'taskArns[0]' --output text) \
  --region us-east-1 \
  --query 'tasks[0].containers[0].networkInterfaces[0].privateIpv4Address' \
  --output text)

echo "KV Database IP: $KV_IP"
```

Common issues:
- Connection refused: Security group not allowing traffic
- Timeout: Database not healthy yet (wait 2-3 minutes)
- Wrong URL: Verify IP in service environment variables

---

## Key Files
```
terraform/
├── README.md                    # This guide
├── alb.tf                       # Load balancer
├── autoscaling.tf               # Auto-scaling policies
├── ecs_cluster.tf               # ECS cluster
├── ecs_services.tf              # Service definitions
├── ecs_task_definitions.tf      # Task configurations
├── ecs_kv_database.tf           # [TO BE CREATED]
├── ecr.tf                       # Container registry
├── outputs.tf                   # Terraform outputs
├── rabbitmq.tf                  # RabbitMQ instance
├── rabbitmq_security_groups.tf  # RabbitMQ networking
├── variables.tf                 # Input variables
└── terraform.tfvars             # Configuration values
```

---

## Assignment Requirements

| Requirement | Status | Location |
|-------------|--------|----------|
| 5 microservices | Completed | ecs_services.tf |
| Load balancer | Completed | alb.tf |
| Message queue | Completed | rabbitmq.tf |
| KV database | To be added | ecs_kv_database.tf |
| Auto-scaling | Completed | autoscaling.tf |
| 2 use cases | To be tested | locustfile.py |
| Load testing | To be done | Locust + screenshots |
| Documentation | In progress | Assignment document |

---

## Team Division of Work

**Already Done (Infrastructure):**
- Complete Terraform infrastructure code
- All Docker images in ECR
- ALB with routing rules
- 5 ECS services deployed and tested
- Auto-scaling configuration
- RabbitMQ message queue
- Locust test scripts created

**What needs to be done (Database & Testing):**
- Deploy KV database service
- Connect services to database
- Run load tests with Locust
- Capture auto-scaling evidence
- Document database design decisions
- Document workload assumptions