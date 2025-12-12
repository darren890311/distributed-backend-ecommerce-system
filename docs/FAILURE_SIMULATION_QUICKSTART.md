# Product Service 50% Failure Simulation - Quick Start

This guide shows you how to quickly set up and test the Product Service with a 50% failure rate simulation.

## What This Does

The Product Service will:
- ✅ Return **201 Created** for ~50% of requests (success)
- ❌ Return **503 Service Unavailable** for ~50% of requests (simulated failure)
- 🎯 Service is NOT actually broken - it's simulating failures for testing

## Quick Start (3 Methods)

### Method 1: Automated Test Script (Easiest)

```bash
# Start Product Service
docker-compose up -d product-service

# Run the test script
./test-failure-simulation.sh

# Watch it automatically:
# 1. Test normal operation (0% failures)
# 2. Enable 50% failure mode
# 3. Send 20 test requests
# 4. Show success/failure statistics
# 5. Clean up (disable failure mode)
```

**Example Output:**
```
Test Results Summary
====================================================
Total requests:    20
Successful (200):  11 (55%)
Failed (503):      9 (45%)

Expected failures (50%): ~10
Actual failures: 9
Variance: 1 requests
✓ Results match expected 50% failure rate
```

### Method 2: Docker Compose with Two Instances

Run two Product Service instances - one normal, one with 50% failures:

```bash
# Start services
docker-compose -f docker-compose.failure-test.yml up -d

# Two instances will be running:
# - product-service-normal (port 8082) - 0% failure
# - product-service-failing (port 8092) - 50% failure

# Test normal instance
curl http://localhost:8082/products/1
# Should always succeed (200)

# Test failing instance
curl http://localhost:8092/products/1
# Should succeed ~50% of the time, fail ~50%

# Run multiple tests on failing instance
for i in {1..20}; do
  echo "Request $i:"
  curl -w "HTTP %{http_code}\n" http://localhost:8092/products/1 2>/dev/null | grep -E "(HTTP|error)"
done
```

### Method 3: Manual API Control

```bash
# Start Product Service
docker-compose up -d product-service

# Create a test product
curl -X POST http://localhost:8082/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "TEST-001",
    "manufacturer": "Test Co",
    "category_id": 1,
    "weight": 1.5,
    "some_other_id": 100
  }'

# Response: {"product_id": 1}

# Enable 50% failure mode
curl "http://localhost:8082/products/bad-mode?enabled=true&errorRate=0.5"

# Response: Bad mode ENABLED (50% of requests will return 503)

# Test multiple times - you'll see ~50% failures
for i in {1..10}; do
  curl -w "\nHTTP %{http_code}\n" http://localhost:8082/products/1
  echo "---"
done

# Disable when done
curl "http://localhost:8082/products/bad-mode?enabled=false"
```

## Observing the Behavior

### Watch Logs in Real-Time

```bash
# Watch Product Service logs
docker logs -f product-service | grep "BAD MODE"

# When failures occur, you'll see:
# BAD MODE: Simulating service unavailable (error rate: 50%)
```

### Check Current Status

```bash
curl http://localhost:8082/products/bad-mode/status

# Response:
# Bad mode: ENABLED, Error rate: 50%
```

### Test with Load

```bash
# Enable 50% failure mode
curl "http://localhost:8082/products/bad-mode?enabled=true&errorRate=0.5"

# Generate 100 requests
for i in {1..100}; do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8082/products/1
done | sort | uniq -c

# Expected output:
#   50 200  (successful responses)
#   50 503  (failed responses)
```

## Understanding the Responses

### Successful Response (201/200)

```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "product_id": 1
}
```

### Failed Response (503)

```http
HTTP/1.1 503 Service Unavailable
Content-Type: application/json

{
  "error": "SERVICE_UNAVAILABLE",
  "message": "Service temporarily unavailable",
  "details": "The service is temporarily unavailable. Please try again later."
}
```

## Testing Different Failure Rates

```bash
# 10% failure rate (mild degradation)
curl "http://localhost:8082/products/bad-mode?enabled=true&errorRate=0.1"

# 25% failure rate (moderate degradation)
curl "http://localhost:8082/products/bad-mode?enabled=true&errorRate=0.25"

# 50% failure rate (heavy degradation)
curl "http://localhost:8082/products/bad-mode?enabled=true&errorRate=0.5"

# 75% failure rate (severe degradation)
curl "http://localhost:8082/products/bad-mode?enabled=true&errorRate=0.75"

# 100% failure rate (complete failure simulation)
curl "http://localhost:8082/products/bad-mode?enabled=true&errorRate=1.0"
```

## Use Cases

### 1. Testing AWS ALB Automatic Target Weights

Deploy two instances behind ALB:
- Instance A: 0% failure (normal)
- Instance B: 50% failure (simulated degradation)

ALB will automatically detect Instance B's poor performance and route more traffic to Instance A.

### 2. Circuit Breaker Testing

Enable high failure rate (75-90%) to trigger circuit breaker patterns in client applications.

### 3. Resilience Testing

Verify your system can handle partial failures gracefully:
- Requests succeed eventually through retries
- Users see appropriate error messages
- System remains stable despite degraded service

### 4. Load Balancer Failover

Test that load balancers route traffic away from degraded instances.

## Cleanup

### Disable Failure Mode

```bash
curl "http://localhost:8082/products/bad-mode?enabled=false"
```

### Stop Services

```bash
# Standard docker-compose
docker-compose down

# Failure test docker-compose
docker-compose -f docker-compose.failure-test.yml down
```

## Advanced Testing

### Test with Apache Bench

```bash
# Enable 50% failure
curl "http://localhost:8082/products/bad-mode?enabled=true&errorRate=0.5"

# Run 1000 requests with 10 concurrent connections
ab -n 1000 -c 10 http://localhost:8082/products/1

# Check results:
# Non-2xx responses: ~500 (the failed requests)
```

### Test with curl and jq

```bash
# Enable 50% failure
curl "http://localhost:8082/products/bad-mode?enabled=true&errorRate=0.5"

# Test 50 times and analyze
for i in {1..50}; do
  curl -s http://localhost:8082/products/1
done | jq -s '[.[] | select(.error == "SERVICE_UNAVAILABLE")] | length'

# Output: ~25 (number of failed requests)
```

### Environment Variable Configuration

```bash
# Set error rate at startup
docker run -e PRODUCT_SERVICE_ERROR_RATE=0.5 \
           -p 8082:8082 \
           product-service

# Or in docker-compose.yml:
# environment:
#   - PRODUCT_SERVICE_ERROR_RATE=0.5
```

## Troubleshooting

### Issue: All requests succeed (no failures)

**Check:**
1. Is bad mode enabled?
   ```bash
   curl http://localhost:8082/products/bad-mode/status
   ```

2. Enable it:
   ```bash
   curl "http://localhost:8082/products/bad-mode?enabled=true&errorRate=0.5"
   ```

### Issue: All requests fail (100% failure)

**Check:**
1. Is error rate set to 1.0?
   ```bash
   curl http://localhost:8082/products/bad-mode/status
   ```

2. Reduce it:
   ```bash
   curl "http://localhost:8082/products/bad-mode?enabled=true&errorRate=0.5"
   ```

### Issue: Failure rate doesn't match exactly 50%

**Explanation:** With small sample sizes (< 50 requests), variance is expected due to random distribution.

**Solution:** Test with 100+ requests for more accurate statistics:
```bash
NUM_REQUESTS=100 ./test-failure-simulation.sh
```

## Key Points to Remember

1. ✅ **Service is NOT broken** - it's simulating failures for testing
2. 🎲 **Failures are random** - each request has 50% chance to fail
3. 💚 **Health checks always pass** - `/actuator/health` always returns 200
4. 🔄 **Dynamic control** - enable/disable anytime via API
5. 🧹 **Clean up** - always disable failure mode when done testing

## Next Steps

- 📖 Read the detailed documentation: [FAILURE_SIMULATION.md](product-service/FAILURE_SIMULATION.md)
- 🚀 Deploy to AWS and test with ALB automatic target weights
- 🧪 Integrate with your CI/CD pipeline for resilience testing
- 📊 Monitor CloudWatch metrics during failure simulation

## Quick Reference

```bash
# Enable 50% failure
curl "http://localhost:8082/products/bad-mode?enabled=true&errorRate=0.5"

# Check status
curl "http://localhost:8082/products/bad-mode/status"

# Disable
curl "http://localhost:8082/products/bad-mode?enabled=false"

# Test endpoint
curl http://localhost:8082/products/1

# Watch logs
docker logs -f product-service | grep "BAD MODE"
```

Happy testing! 🎉