# AWS Application Load Balancer for E-Commerce Microservices

This Terraform configuration sets up an AWS Application Load Balancer (ALB) with Target Groups for the microservices architecture, including intelligent routing based on URL paths and HTTP headers.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    Application Load Balancer                     │
│                         (Port 80/443)                            │
└───────────────────────┬─────────────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┬─────────────────────────┐
        │               │               │                         │
        ▼               ▼               ▼                         ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐      ┌──────────────┐
│   Product    │ │ Shopping Cart│ │ Credit Card  │      │  Warehouse   │
│   Service    │ │   Service    │ │  Authorizer  │      │   Service    │
│              │ │              │ │              │      │              │
│  Port 8082   │ │  Port 8084   │ │  Port 8080   │      │  (RabbitMQ)  │
└──────────────┘ └──────────────┘ └──────────────┘      └──────────────┘
  Target Group     Target Group     Target Group        Not Load Balanced
```

## Routing Strategy

### 1. Path-Based Routing (String Matching)

The ALB routes requests to Target Groups based on URL path patterns:

| URL Pattern | Target Service | Priority |
|-------------|----------------|----------|
| `*/product*` or `*/products*` | Product Service | 100 |
| `*/shopping-cart*` or `*/cart*` | Shopping Cart Service | 200 |
| `*/credit-card*`, `*/payment*`, `*/authorize*` | Credit Card Authorizer | 300 |

**Examples:**
- `http://alb-dns-name/products` → Product Service
- `http://alb-dns-name/api/v1/product/123` → Product Service
- `http://alb-dns-name/shopping-cart` → Shopping Cart Service
- `http://alb-dns-name/credit-card-authorizer/authorize` → Credit Card Authorizer

### 2. HTTP Header-Based Routing

For more granular control, the ALB also supports routing based on HTTP headers:

| Header Name | Header Values | Target Service | Priority |
|-------------|---------------|----------------|----------|
| `X-Service-Type` | `product`, `catalog`, `inventory` | Product Service | 150 |
| `X-Service-Type` | `cart`, `checkout` | Shopping Cart Service | 250 |
| `X-Service-Type` | `payment`, `authorization` | Credit Card Authorizer | 350 |

**Example Request:**
```bash
curl -H "X-Service-Type: product" \
     http://alb-dns-name/api/product/123
```

## Automatic Target Weights

AWS ALB supports **Automatic Target Weights**, which uses an anomaly detection algorithm to dynamically adjust traffic distribution based on:

- Target health status
- Response times
- Error rates
- Connection counts
- Request processing capacity

### Configuration

In `terraform.tfvars`, you can configure targets with automatic or manual weights:

```hcl
product_service_targets = [
  {
    id     = "i-0123456789abcdef0"
    port   = 8082
    weight = null  # Automatic weight - AWS adjusts dynamically
  },
  {
    id     = "i-0123456789abcdef1"
    port   = 8082
    weight = 100   # Manual weight - fixed at 100
  },
]
```

**Weight Options:**
- `null` or omitted: Enable automatic weight adjustment (recommended)
- `1-999`: Manual weight for custom traffic distribution
- `0`: Stop routing traffic but keep target registered (for draining)

### Benefits of Automatic Target Weights

1. **Performance Optimization**: Automatically routes more traffic to better-performing targets
2. **Fault Tolerance**: Reduces traffic to degraded targets without removing them
3. **No Manual Intervention**: Self-adjusts based on real-time metrics
4. **Gradual Recovery**: Slowly increases traffic to recovering targets

## Target Group Configuration

Each microservice has its own Target Group with:

| Service | Port | Health Check Endpoint | Protocol |
|---------|------|-----------------------|----------|
| Product Service | 8082 | `/actuator/health` | HTTP |
| Shopping Cart Service | 8084 | `/actuator/health` | HTTP |
| Credit Card Authorizer | 8080 | `/actuator/health` | HTTP |

### Health Check Configuration

- **Interval**: 30 seconds
- **Timeout**: 5 seconds
- **Healthy Threshold**: 3 consecutive successes
- **Unhealthy Threshold**: 3 consecutive failures
- **Matcher**: HTTP 200 status code

### Stickiness Configuration

Session stickiness is enabled with:
- **Type**: Load balancer cookie
- **Duration**: 86400 seconds (24 hours)
- **Purpose**: Maintain user sessions on the same target

## Prerequisites

1. **AWS Account** with appropriate IAM permissions
2. **VPC** with at least 2 public subnets in different Availability Zones
3. **EC2 Instances** running the microservices
4. **Terraform** >= 1.0
5. **AWS Provider** >= 5.0

## Setup Instructions

### 1. Configure Variables

Copy the example variables file and update with your values:

```bash
cd terraform
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars` and update:
- `vpc_id`: Your VPC ID
- `public_subnet_ids`: At least 2 public subnet IDs
- `product_service_targets`: EC2 instance IDs for Product Service
- `shopping_cart_service_targets`: EC2 instance IDs for Shopping Cart Service
- `credit_card_service_targets`: EC2 instance IDs for Credit Card Authorizer

### 2. Initialize Terraform

```bash
terraform init
```

### 3. Review the Plan

```bash
terraform plan
```

This will show you all the resources that will be created:
- 1 Application Load Balancer
- 1 Security Group
- 3 Target Groups
- 1 HTTP Listener (Port 80)
- 9 Listener Rules (3 path-based + 3 header-based + 3 combined)
- N Target Group Attachments (based on your configuration)

### 4. Apply the Configuration

```bash
terraform apply
```

Type `yes` when prompted to confirm.

### 5. Get the ALB DNS Name

After successful deployment:

```bash
terraform output alb_dns_name
```

Example output:
```
ecommerce-alb-1234567890.us-east-1.elb.amazonaws.com
```

## Testing the Load Balancer

### Test Product Service

```bash
# Get ALB DNS name
ALB_DNS=$(terraform output -raw alb_dns_name)

# Test product endpoint
curl http://$ALB_DNS/products/1

# Test with header
curl -H "X-Service-Type: product" http://$ALB_DNS/api/product/1
```

### Test Shopping Cart Service

```bash
# Create shopping cart
curl -X POST http://$ALB_DNS/shopping-cart \
  -H "Content-Type: application/json" \
  -d '{"customer_id": 123}'

# Test with header
curl -H "X-Service-Type: cart" http://$ALB_DNS/shopping-cart/1
```

### Test Credit Card Authorizer

```bash
# Authorize payment
curl -X POST http://$ALB_DNS/credit-card-authorizer/authorize \
  -H "Content-Type: application/json" \
  -d '{"credit_card_number": "1234-5678-9012-3456"}'

# Test with header
curl -H "X-Service-Type: payment" \
     http://$ALB_DNS/credit-card-authorizer/authorize
```

## Monitoring

### CloudWatch Metrics

The ALB automatically publishes metrics to CloudWatch:

- **TargetResponseTime**: Time elapsed after request leaves the load balancer until response received
- **RequestCount**: Number of requests completed
- **HealthyHostCount**: Number of healthy targets
- **UnHealthyHostCount**: Number of unhealthy targets
- **HTTPCode_Target_2XX_Count**: Number of 2XX responses from targets
- **HTTPCode_Target_4XX_Count**: Number of 4XX responses from targets
- **HTTPCode_Target_5XX_Count**: Number of 5XX responses from targets

### View Metrics

```bash
# Via AWS CLI
aws cloudwatch get-metric-statistics \
  --namespace AWS/ApplicationELB \
  --metric-name RequestCount \
  --dimensions Name=LoadBalancer,Value=app/ecommerce-alb/1234567890abcdef \
  --start-time 2024-01-01T00:00:00Z \
  --end-time 2024-01-01T23:59:59Z \
  --period 3600 \
  --statistics Sum
```

### Access Logs

To enable ALB access logs, add to `alb.tf`:

```hcl
resource "aws_lb" "ecommerce_alb" {
  # ... existing configuration ...

  access_logs {
    bucket  = "my-alb-logs-bucket"
    prefix  = "ecommerce-alb"
    enabled = true
  }
}
```

## Scaling Targets

### Add New Target to Existing Service

Update `terraform.tfvars`:

```hcl
product_service_targets = [
  # ... existing targets ...
  {
    id     = "i-newtarget123"
    port   = 8082
    weight = null  # Automatic weight
  },
]
```

Apply the changes:

```bash
terraform apply
```

### Auto Scaling Integration

To integrate with Auto Scaling Groups, modify the target group attachments to use ASG instead of individual instances.

## Security Considerations

1. **Security Group**: The ALB security group allows HTTP (80) and HTTPS (443) from anywhere
2. **Target Security Groups**: Ensure target instances allow inbound traffic from the ALB security group
3. **HTTPS**: To enable HTTPS, uncomment the HTTPS listener in `alb.tf` and provide an SSL certificate ARN
4. **Private Subnets**: Consider placing target instances in private subnets with NAT gateway for internet access

### Enable HTTPS

1. Request or import an SSL certificate in AWS Certificate Manager
2. Update `terraform.tfvars`:
   ```hcl
   ssl_certificate_arn = "arn:aws:acm:us-east-1:123456789012:certificate/..."
   ```
3. Uncomment the HTTPS listener in `alb.tf`
4. Apply changes: `terraform apply`

## Cost Estimation

**Application Load Balancer Pricing (us-east-1):**
- ALB hour: ~$0.0225/hour (~$16.20/month)
- LCU (Load Balancer Capacity Unit): ~$0.008/hour per LCU
- Data processed: Varies based on traffic

**Example Monthly Cost:**
- 1 ALB running 24/7: ~$16.20
- Average 10 LCU: ~$57.60
- **Total**: ~$73.80/month (excluding data transfer)

## Troubleshooting

### Targets Not Registering

Check:
1. Target instances are running
2. Security groups allow traffic from ALB to targets
3. Health check endpoint is accessible
4. Target instances are in the same VPC

```bash
# Check target health
aws elbv2 describe-target-health \
  --target-group-arn $(terraform output -raw product_service_tg_arn)
```

### 503 Service Unavailable

Possible causes:
1. No healthy targets in target group
2. All targets failing health checks
3. Target capacity exhausted

### Routing Not Working

Verify:
1. Listener rules are configured correctly
2. Path patterns match your request URLs
3. Rule priorities are correct (lower number = higher priority)

```bash
# List listener rules
aws elbv2 describe-rules \
  --listener-arn $(terraform output -raw alb_listener_arn)
```

## Cleanup

To destroy all resources:

```bash
terraform destroy
```

Type `yes` when prompted to confirm.

## Architecture Decisions

### Why Not Include Warehouse Service?

The Warehouse Service is not included in the ALB because:
1. It communicates asynchronously via RabbitMQ
2. It doesn't expose REST APIs for external client requests
3. It's an internal consumer service, not a public-facing service

### Listener Rule Priorities

Rules are prioritized to ensure specific patterns match before generic ones:
- Lower numbers = higher priority
- Path-based rules: 100, 200, 300
- Header-based rules: 150, 250, 350 (after path rules)
- Default action: 404 response for unmatched routes

## References

- [AWS ALB Documentation](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/)
- [Automatic Target Weights](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-target-groups.html#automatic-target-weights)
- [Terraform AWS ALB Resources](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/lb)

## Support

For issues or questions:
1. Check the troubleshooting section above
2. Review AWS CloudWatch logs and metrics
3. Consult the project documentation in the parent repository