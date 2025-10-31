# Product Service - Failure Simulation Mode

This document describes how to use the Product Service's built-in failure simulation feature to test load balancer behavior, automatic target weights, and system resilience.

## Overview

The Product Service can simulate failures by returning **503 Service Unavailable** errors for a configurable percentage of requests. This is useful for:

- Testing AWS ALB Automatic Target Weights
- Validating failover and circuit breaker patterns
- Load testing under degraded conditions
- Demonstrating resilience mechanisms

**Important:** The service is NOT actually failing - it's simulating failures for testing purposes only.

## Configuration Methods

### Method 1: Environment Variable (Recommended for Docker)

Set the `PRODUCT_SERVICE_ERROR_RATE` environment variable when starting the service:

```bash
# 50% failure rate
export PRODUCT_SERVICE_ERROR_RATE=0.5
java -jar product-service.jar
```

Or with Docker:

```bash
docker run -e PRODUCT_SERVICE_ERROR_RATE=0.5 product-service
```

**Error Rate Values:**
- `0.0` = 0% failures (normal operation)
- `0.1` = 10% failures (default)
- `0.5` = 50% failures (heavy degradation)
- `0.9` = 90% failures (critical degradation)
- `1.0` = 100% failures (complete failure simulation)

### Method 2: Runtime API Control (Dynamic)

Enable/disable failure mode and adjust error rate at runtime using REST endpoints:

#### Enable Failure Mode with Custom Error Rate

```bash
# Enable 50% failure rate
curl "http://localhost:8082/products/bad-mode?enabled=true&errorRate=0.5"

# Response:
# Bad mode ENABLED (50% of requests will return 503)
```

#### Enable with Default Error Rate (10%)

```bash
curl "http://localhost:8082/products/bad-mode?enabled=true"

# Response:
# Bad mode ENABLED (10% of requests will return 503)
```

#### Change Error Rate While Enabled

```bash
# Change to 75% failure rate
curl "http://localhost:8082/products/bad-mode?enabled=true&errorRate=0.75"

# Response:
# Bad mode ENABLED (75% of requests will return 503)
```

#### Disable Failure Mode

```bash
curl "http://localhost:8082/products/bad-mode?enabled=false"

# Response:
# Bad mode DISABLED (0% of requests will return 503)
```

#### Check Current Status

```bash
curl "http://localhost:8082/products/bad-mode/status"

# Response:
# Bad mode: ENABLED, Error rate: 50%
```

## Testing 50% Failure Rate

### Quick Start: Docker Compose

Use the provided Docker Compose configuration that runs two Product Service instances:
1. Normal instance (0% failure rate) on port 8082
2. Failing instance (50% failure rate) on port 8092

```bash
# Start services with failure simulation
docker-compose -f docker-compose.failure-test.yml up -d

# Check logs for failing instance
docker logs -f product-service-failing

# You'll see log entries like:
# BAD MODE: Simulating service unavailable (error rate: 50%)
```

### Manual Testing: Single Instance

```bash
# Start Product Service
cd product-service
./mvnw spring-boot:run

# In another terminal, enable 50% failure mode
curl "http://localhost:8082/products/bad-mode?enabled=true&errorRate=0.5"

# Test with multiple requests to see 50/50 distribution
for i in {1..20}; do
  echo "Request $i:"
  curl -w "\nHTTP Status: %{http_code}\n" http://localhost:8082/products/1
  echo "---"
done

# Expected output: ~10 successful (200) and ~10 failed (503) responses
```

### Load Testing with Failure Simulation

Use `ab` (Apache Bench) or similar tools:

```bash
# Install Apache Bench (if needed)
# macOS: brew install httpd
# Ubuntu: sudo apt-get install apache2-utils

# Enable 50% failure rate
curl "http://localhost:8082/products/bad-mode?enabled=true&errorRate=0.5"

# Send 1000 requests with 10 concurrent connections
ab -n 1000 -c 10 http://localhost:8082/products/1

# Expected results:
# - Complete requests: 1000
# - Failed requests: ~500 (503 errors)
# - Non-2xx responses: ~500
```

## AWS ALB Testing with Automatic Target Weights

This simulation is perfect for testing AWS ALB's automatic target weights feature:

### Scenario 1: Two Instances (One Failing)

Deploy two Product Service instances behind ALB:
- Instance A: Normal (0% failure)
- Instance B: 50% failure simulation

**Expected ALB Behavior:**
1. ALB initially distributes traffic 50/50
2. Anomaly detection algorithm notices Instance B's high error rate
3. ALB gradually reduces Instance B's weight
4. Instance A receives ~80-90% of traffic
5. Instance B receives ~10-20% of traffic

### Scenario 2: Gradual Recovery

Simulate a recovering instance:

```bash
# Start with 90% failure
curl "http://instance-b:8082/products/bad-mode?enabled=true&errorRate=0.9"

# Wait 2 minutes for ALB to detect and adjust weights

# Reduce to 50% failure (simulating recovery)
curl "http://instance-b:8082/products/bad-mode?enabled=true&errorRate=0.5"

# Wait 2 minutes - ALB increases weight to Instance B

# Reduce to 10% failure (nearly recovered)
curl "http://instance-b:8082/products/bad-mode?enabled=true&errorRate=0.1"

# Wait 2 minutes - ALB further increases weight

# Fully recovered
curl "http://instance-b:8082/products/bad-mode?enabled=false"

# ALB returns to equal distribution
```

### Monitoring ALB Target Health During Failure Simulation

```bash
# Check target health
aws elbv2 describe-target-health \
  --target-group-arn arn:aws:elasticloadbalancing:region:account:targetgroup/...

# Sample output with failing target:
# {
#   "TargetHealthDescriptions": [
#     {
#       "Target": {"Id": "i-instance-a", "Port": 8082},
#       "HealthCheckPort": "8082",
#       "TargetHealth": {
#         "State": "healthy",
#         "Reason": "Target.ResponseCodeMismatch",
#         "Description": "Health checks succeeded"
#       }
#     },
#     {
#       "Target": {"Id": "i-instance-b", "Port": 8082},
#       "HealthCheckPort": "8082",
#       "TargetHealth": {
#         "State": "healthy",
#         "Reason": "Target.ResponseCodeMismatch",
#         "Description": "Health checks succeeded but high error rate"
#       }
#     }
#   ]
# }
```

**Note:** The target will still be marked as "healthy" because:
- Health check endpoint (`/actuator/health`) always returns 200
- Only actual API requests randomly fail
- This is by design to test ALB's anomaly detection, not health checks

## Error Response Format

When failure mode is active and a request fails, the service returns:

```http
HTTP/1.1 503 Service Unavailable
Content-Type: application/json

{
  "error": "SERVICE_UNAVAILABLE",
  "message": "Service temporarily unavailable",
  "details": "The service is temporarily unavailable. Please try again later."
}
```

## Implementation Details

### Random Failure Generation

```java
// Each request generates a random number between 0.0 and 1.0
double randomValue = random.nextDouble();

// If random value < error rate, simulate failure
if (badMode && randomValue < errorRate) {
  throw new ServiceUnavailableException("Service temporarily unavailable");
}

// Examples:
// errorRate = 0.5 (50%)
//   - If random = 0.23 → FAIL (0.23 < 0.5)
//   - If random = 0.67 → SUCCESS (0.67 >= 0.5)
//   - If random = 0.49 → FAIL (0.49 < 0.5)
```

### Affected Endpoints

Failure simulation applies to:
- `POST /products` - Create product
- `GET /products/{productId}` - Get product by ID

Not affected:
- `GET /actuator/health` - Always returns 200 (for health checks)
- `GET /products/bad-mode` - Control endpoint
- `GET /products/bad-mode/status` - Status endpoint

## Best Practices

### 1. Always Disable After Testing

```bash
# Disable failure mode when done
curl "http://localhost:8082/products/bad-mode?enabled=false"
```

### 2. Use Environment Variables for Containers

Set `PRODUCT_SERVICE_ERROR_RATE` at container startup rather than runtime API for consistent behavior:

```yaml
# docker-compose.yml
environment:
  - PRODUCT_SERVICE_ERROR_RATE=0.5
```

### 3. Monitor Application Logs

```bash
# Watch for failure simulation log entries
docker logs -f product-service | grep "BAD MODE"

# Example output:
# BAD MODE: Simulating service unavailable (error rate: 50%)
```

### 4. Document Test Configuration

When running load tests, document:
- Error rate used
- Duration of test
- Number of instances
- Expected vs actual failure distribution

### 5. Use Different Ports for Multiple Instances

When running multiple instances locally:

```bash
# Instance 1 (normal) - port 8082
docker run -p 8082:8082 -e PRODUCT_SERVICE_ERROR_RATE=0.0 product-service

# Instance 2 (failing) - port 8092
docker run -p 8092:8082 -e PRODUCT_SERVICE_ERROR_RATE=0.5 product-service
```

## Troubleshooting

### All Requests Failing

**Symptom:** 100% of requests return 503

**Solutions:**
1. Check if error rate is set to 1.0:
   ```bash
   curl http://localhost:8082/products/bad-mode/status
   ```

2. Disable bad mode:
   ```bash
   curl "http://localhost:8082/products/bad-mode?enabled=false"
   ```

### No Failures Despite Bad Mode Enabled

**Symptom:** All requests succeed even with bad mode enabled

**Solutions:**
1. Verify bad mode is actually enabled:
   ```bash
   curl http://localhost:8082/products/bad-mode/status
   ```

2. Check error rate is > 0:
   ```bash
   echo $PRODUCT_SERVICE_ERROR_RATE
   ```

3. Test multiple times (with 10% error rate, you need ~20+ requests to see failures)

### Inconsistent Failure Rate

**Symptom:** Actual failure rate doesn't match configured rate

**Explanation:** This is expected with small sample sizes. The failure rate is probabilistic:

```
10 requests:   Could be 0-10 failures (high variance)
100 requests:  Should be ~45-55 failures (moderate variance)
1000 requests: Should be ~490-510 failures (low variance)
```

**Solution:** Test with larger sample sizes (100+ requests)

## Example Test Scenarios

### Scenario 1: Validate ALB Automatic Target Weights

```bash
# Setup: 2 instances behind ALB
# Instance A: Normal (0% failure)
# Instance B: 50% failure

# Enable failure mode on Instance B
curl "http://instance-b:8082/products/bad-mode?enabled=true&errorRate=0.5"

# Generate load through ALB for 5 minutes
for i in {1..300}; do
  curl http://alb-dns-name/products/1
  sleep 1
done

# Monitor ALB metrics in CloudWatch
# Expected: Instance A receives 80-90% of traffic after ~2-3 minutes
```

### Scenario 2: Test Circuit Breaker Pattern

```bash
# Enable 90% failure rate to trigger circuit breaker
curl "http://localhost:8082/products/bad-mode?enabled=true&errorRate=0.9"

# Make requests and observe circuit breaker opening
for i in {1..50}; do
  curl http://localhost:8082/products/1
done

# Expected: Circuit breaker opens after threshold reached
# Expected: Fast-fail responses instead of waiting for timeouts
```

### Scenario 3: Gradual Degradation Testing

```bash
# Start normal
curl "http://localhost:8082/products/bad-mode?enabled=false"

# Gradually increase failure rate
for rate in 0.1 0.2 0.3 0.4 0.5; do
  echo "Setting error rate to $(echo "$rate * 100" | bc)%"
  curl "http://localhost:8082/products/bad-mode?enabled=true&errorRate=$rate"

  # Generate load for 1 minute
  for i in {1..60}; do
    curl -s http://localhost:8082/products/1 > /dev/null
    sleep 1
  done
done

# Monitor system behavior at each failure level
```

## Integration with Load Testing Tools

### Apache Bench (ab)

```bash
# Enable 50% failure rate
curl "http://localhost:8082/products/bad-mode?enabled=true&errorRate=0.5"

# Run load test
ab -n 10000 -c 100 -g results.tsv http://localhost:8082/products/1

# Analyze results
# grep "503" results.tsv | wc -l
```

### JMeter

1. Create HTTP Request sampler pointing to Product Service
2. Add Response Assertion to capture 503 errors
3. Enable failure mode via HTTP Request to `/products/bad-mode`
4. Run test plan
5. Analyze error percentage in Summary Report

### Locust (Python)

```python
from locust import HttpUser, task, between

class ProductServiceUser(HttpUser):
    wait_time = between(1, 3)

    def on_start(self):
        # Enable 50% failure rate
        self.client.get("/products/bad-mode?enabled=true&errorRate=0.5")

    @task
    def get_product(self):
        with self.client.get("/products/1", catch_response=True) as response:
            if response.status_code == 503:
                # Expected failure - mark as success for metrics
                response.success()
```

## Summary

The Product Service failure simulation feature provides a powerful way to test system resilience without actually causing real failures. Key takeaways:

- ✅ Configurable error rate from 0% to 100%
- ✅ Environment variable or runtime API control
- ✅ Perfect for testing ALB automatic target weights
- ✅ Simulates realistic degradation patterns
- ✅ Easy to enable/disable dynamically
- ✅ Logs all simulated failures for monitoring

For questions or issues, check the application logs or disable bad mode to return to normal operation.