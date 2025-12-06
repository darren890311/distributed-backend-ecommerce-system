
# AWS ALB Quick Start Guide

This guide will help you quickly deploy the Application Load Balancer for your e-commerce microservices.

## Prerequisites Checklist

- [ ] AWS Account with appropriate IAM permissions
- [ ] AWS CLI installed and configured
- [ ] Terraform >= 1.0 installed
- [ ] VPC with at least 2 public subnets in different AZs
- [ ] EC2 instances running the microservi ces (or AMIs ready to launch)
- [ ] EC2 instances have the microservices running on correct ports:
  - Product Service: Port 8082
  - Shopping Cart Service: Port 8084
  - Credit Card Authorizer: Port 8080

## 5-Minute Deployment

### Step 1: Navigate to Terraform Directory

```bash
cd terraform
```

### Step 2: Create Your Variables File

```bash
cp terraform.tfvars.example terraform.tfvars
```

### Step 3: Edit terraform.tfvars

Update the following values:

```hcl
# Required: Network Configuration
vpc_id            = "vpc-YOUR_VPC_ID"
public_subnet_ids = ["subnet-AZ1", "subnet-AZ2"]

# Required: Target Instances
product_service_targets = [
  { id = "i-INSTANCE_ID1", port = 8082, weight = null },
  { id = "i-INSTANCE_ID2", port = 8082, weight = null },
]

shopping_cart_service_targets = [
  { id = "i-INSTANCE_ID3", port = 8084, weight = null },
]

credit_card_service_targets = [
  { id = "i-INSTANCE_ID4", port = 8080, weight = null },
]
```

**Quick Tip:** Get your EC2 instance IDs:
```bash
aws ec2 describe-instances \
  --filters "Name=tag:Service,Values=product-service" \
  --query "Reservations[*].Instances[*].[InstanceId,State.Name,Tags[?Key=='Name'].Value|[0]]" \
  --output table
```

### Step 4: Initialize Terraform

```bash
terraform init
```

### Step 5: Plan and Review

```bash
terraform plan
```

Review the resources that will be created:
- ✓ 1 Application Load Balancer
- ✓ 4 Security Groups (ALB + 3 services)
- ✓ 3 Target Groups
- ✓ 1 HTTP Listener
- ✓ 9 Listener Rules (routing logic)
- ✓ N Target Attachments (your instances)

### Step 6: Deploy

```bash
terraform apply
```

Type `yes` when prompted.

### Step 7: Get Your ALB DNS Name

```bash
terraform output alb_dns_name
```

Save this DNS name - this is your application's entry point!

### Step 8: Test Your Deployment

```bash
# Set the ALB DNS
export ALB_DNS=$(terraform output -raw alb_dns_name)

# Test Product Service
curl http://$ALB_DNS/products

# Test Shopping Cart Service
curl -X POST http://$ALB_DNS/shopping-cart \
  -H "Content-Type: application/json" \
  -d '{"customer_id": 123}'

# Test Credit Card Authorizer
curl -X POST http://$ALB_DNS/credit-card-authorizer/authorize \
  -H "Content-Type: application/json" \
  -d '{"credit_card_number": "1234-5678-9012-3456"}'
```

## Verify Target Health

```bash
# Product Service
aws elbv2 describe-target-health \
  --target-group-arn $(terraform output -raw product_service_tg_arn)

# Shopping Cart Service
aws elbv2 describe-target-health \
  --target-group-arn $(terraform output -raw shopping_cart_service_tg_arn)

# Credit Card Authorizer
aws elbv2 describe-target-health \
  --target-group-arn $(terraform output -raw credit_card_service_tg_arn)
```

All targets should show `State: healthy`.

## Understanding the Routing

### Path-Based Routing

The ALB automatically routes requests based on URL patterns:

```
http://$ALB_DNS/products         → Product Service
http://$ALB_DNS/shopping-cart    → Shopping Cart Service
http://$ALB_DNS/credit-card-*    → Credit Card Authorizer
```

### Header-Based Routing

For advanced routing, include the `X-Service-Type` header:

```bash
# Route to Product Service
curl -H "X-Service-Type: product" http://$ALB_DNS/api/products/1

# Route to Shopping Cart Service
curl -H "X-Service-Type: cart" http://$ALB_DNS/api/shopping-cart/1

# Route to Credit Card Authorizer
curl -H "X-Service-Type: payment" http://$ALB_DNS/api/authorize
```

## Automatic Target Weights

Your configuration uses **Automatic Target Weights** by setting `weight = null`.

AWS automatically adjusts traffic distribution based on:
- Target health
- Response times
- Error rates
- Connection capacity

**No manual intervention needed!** AWS optimizes traffic flow for you.

## Common Issues & Solutions

### Issue: Targets showing unhealthy

**Solution:**
1. Check if microservices are running:
   ```bash
   ssh ec2-user@<instance-ip>
   sudo systemctl status your-service
   ```

2. Verify health check endpoint:
   ```bash
   curl http://localhost:8082/actuator/health
   ```

3. Check security groups allow traffic from ALB

### Issue: 503 Service Unavailable

**Solution:**
- Ensure at least one target is healthy in each target group
- Check target instance capacity (CPU, memory)

### Issue: Cannot connect to ALB

**Solution:**
1. Verify ALB security group allows inbound traffic on port 80
2. Check if ALB is in public subnets
3. Verify your public subnets have internet gateway routes

### Issue: Routing not working correctly

**Solution:**
1. Check listener rules priority (lower = higher priority)
2. Verify path patterns match your request URLs
3. Review listener rules:
   ```bash
   aws elbv2 describe-rules --listener-arn <listener-arn>
   ```

## Monitoring Your ALB

### View Metrics in CloudWatch

```bash
aws cloudwatch get-metric-statistics \
  --namespace AWS/ApplicationELB \
  --metric-name RequestCount \
  --dimensions Name=LoadBalancer,Value=app/ecommerce-alb/$(terraform output -raw alb_arn | cut -d'/' -f4) \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Sum
```

### Key Metrics to Monitor

- **RequestCount**: Total requests
- **TargetResponseTime**: Backend response time
- **HealthyHostCount**: Number of healthy targets
- **HTTPCode_Target_5XX_Count**: Backend errors

## Scaling Up

### Add More Targets

1. Launch new EC2 instances with your microservice
2. Update `terraform.tfvars`:
   ```hcl
   product_service_targets = [
     # ... existing targets ...
     { id = "i-NEW_INSTANCE", port = 8082, weight = null },
   ]
   ```
3. Apply changes:
   ```bash
   terraform apply
   ```

### Remove Targets

1. Remove the target from `terraform.tfvars`
2. Apply changes:
   ```bash
   terraform apply
   ```

The target will be gracefully drained (30 seconds deregistration delay).

## Production Readiness Checklist

Before going to production:

- [ ] Enable HTTPS (uncomment HTTPS listener in `alb.tf`)
- [ ] Set `enable_deletion_protection = true`
- [ ] Restrict `management_cidr_blocks` to your office/VPN IP range
- [ ] Enable ALB access logs
- [ ] Set up CloudWatch alarms for key metrics
- [ ] Configure Auto Scaling Groups for targets
- [ ] Set up proper DNS with Route 53
- [ ] Enable AWS WAF for DDoS protection
- [ ] Review and harden security groups
- [ ] Set up backup and disaster recovery

## Clean Up

To destroy all resources:

```bash
terraform destroy
```

**Warning:** This will delete the ALB and all associated resources!

## Next Steps

1. **Set up DNS**: Create a Route 53 alias record pointing to your ALB
2. **Enable HTTPS**: Request SSL certificate in ACM and update configuration
3. **Configure Auto Scaling**: Use Auto Scaling Groups instead of manual instance management
4. **Set up monitoring**: Create CloudWatch dashboards and alarms
5. **Enable logging**: Configure ALB access logs to S3

## Getting Help

- Review the detailed [README.md](./README.md)
- Check AWS documentation: https://docs.aws.amazon.com/elasticloadbalancing/
- Review Terraform AWS provider docs: https://registry.terraform.io/providers/hashicorp/aws/

## Summary of What You've Deployed

```
Internet
    ↓
Application Load Balancer (Port 80)
    ↓
┌───────────────┬─────────────────────┬──────────────────────┐
│               │                     │                      │
Product Service │ Shopping Cart       │ Credit Card          │
Target Group    │ Target Group        │ Target Group         │
(Port 8082)     │ (Port 8084)         │ (Port 8080)          │
    ↓           │     ↓               │     ↓                │
Your EC2        │ Your EC2            │ Your EC2             │
Instances       │ Instances           │ Instances            │
```

**Routing:**
- Path-based: URL patterns route to correct service
- Header-based: `X-Service-Type` header provides granular control
- Automatic weights: AWS optimizes traffic distribution
- Health checks: Only healthy targets receive traffic
- Session stickiness: Users stay on same target for consistency

Congratulations! Your microservices are now load-balanced! 🎉