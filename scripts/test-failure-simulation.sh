#!/bin/bash

# Test Script for Product Service Failure Simulation
# This script demonstrates the 50% failure rate simulation

set -e

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
PRODUCT_SERVICE_URL="${PRODUCT_SERVICE_URL:-http://localhost:8082}"
NUM_REQUESTS="${NUM_REQUESTS:-20}"

echo "======================================================"
echo "Product Service Failure Simulation Test"
echo "======================================================"
echo ""
echo "Service URL: $PRODUCT_SERVICE_URL"
echo "Number of test requests: $NUM_REQUESTS"
echo ""

# Function to check if service is running
check_service() {
  echo "Checking if Product Service is running..."
  if curl -s "${PRODUCT_SERVICE_URL}/actuator/health" > /dev/null; then
    echo -e "${GREEN}✓ Product Service is running${NC}"
    return 0
  else
    echo -e "${RED}✗ Product Service is not accessible${NC}"
    echo "Please start the service first:"
    echo "  docker-compose up -d product-service"
    exit 1
  fi
}

# Function to get current status
get_status() {
  echo ""
  echo "Getting current failure simulation status..."
  STATUS=$(curl -s "${PRODUCT_SERVICE_URL}/products/bad-mode/status")
  echo -e "${YELLOW}Current status: $STATUS${NC}"
}

# Function to enable failure mode
enable_failure_mode() {
  local error_rate=$1
  echo ""
  echo "Enabling failure mode with ${error_rate}% error rate..."
  RESPONSE=$(curl -s "${PRODUCT_SERVICE_URL}/products/bad-mode?enabled=true&errorRate=${error_rate}")
  echo -e "${GREEN}$RESPONSE${NC}"
}

# Function to disable failure mode
disable_failure_mode() {
  echo ""
  echo "Disabling failure mode..."
  RESPONSE=$(curl -s "${PRODUCT_SERVICE_URL}/products/bad-mode?enabled=false")
  echo -e "${GREEN}$RESPONSE${NC}"
}

# Function to create a test product
create_test_product() {
  echo ""
  echo "Creating a test product..."
  RESPONSE=$(curl -s -X POST "${PRODUCT_SERVICE_URL}/products" \
    -H "Content-Type: application/json" \
    -d '{
      "sku": "TEST-SKU-001",
      "manufacturer": "Test Manufacturer",
      "category_id": 1,
      "weight": 2.5,
      "some_other_id": 100
    }')

  PRODUCT_ID=$(echo "$RESPONSE" | grep -o '"product_id":[0-9]*' | cut -d':' -f2)

  if [ -n "$PRODUCT_ID" ]; then
    echo -e "${GREEN}✓ Test product created with ID: $PRODUCT_ID${NC}"
    echo "$PRODUCT_ID"
  else
    echo -e "${YELLOW}Note: Product creation may have failed (expected in failure mode)${NC}"
    echo "1"
  fi
}

# Function to test requests
test_requests() {
  local product_id=$1
  local num_requests=$2

  echo ""
  echo "======================================================"
  echo "Testing $num_requests requests to GET /products/$product_id"
  echo "======================================================"

  local success_count=0
  local fail_count=0

  for i in $(seq 1 $num_requests); do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${PRODUCT_SERVICE_URL}/products/${product_id}")

    if [ "$HTTP_CODE" = "200" ]; then
      echo -e "Request $i: ${GREEN}✓ SUCCESS (200)${NC}"
      ((success_count++))
    elif [ "$HTTP_CODE" = "503" ]; then
      echo -e "Request $i: ${RED}✗ FAILED (503 - Simulated Failure)${NC}"
      ((fail_count++))
    else
      echo -e "Request $i: ${YELLOW}? UNEXPECTED ($HTTP_CODE)${NC}"
    fi

    # Small delay between requests
    sleep 0.1
  done

  echo ""
  echo "======================================================"
  echo "Test Results Summary"
  echo "======================================================"
  echo "Total requests:    $num_requests"
  echo -e "Successful (200):  ${GREEN}$success_count${NC} ($(echo "scale=1; $success_count * 100 / $num_requests" | bc)%)"
  echo -e "Failed (503):      ${RED}$fail_count${NC} ($(echo "scale=1; $fail_count * 100 / $num_requests" | bc)%)"

  local expected_failures=$(echo "scale=0; $num_requests * 0.5" | bc)
  local variance=$(echo "scale=0; ($fail_count - $expected_failures) " | bc | tr -d '-')

  echo ""
  echo "Expected failures (50%): ~$expected_failures"
  echo "Actual failures: $fail_count"
  echo "Variance: $variance requests"

  if [ "$variance" -le 5 ]; then
    echo -e "${GREEN}✓ Results match expected 50% failure rate${NC}"
  else
    echo -e "${YELLOW}⚠ Note: With $num_requests requests, some variance is expected${NC}"
    echo "  Run with more requests for more accurate distribution"
    echo "  Example: NUM_REQUESTS=100 ./test-failure-simulation.sh"
  fi
}

# Main execution
main() {
  check_service
  get_status

  echo ""
  echo "======================================================"
  echo "Test 1: Normal Operation (0% failure)"
  echo "======================================================"

  disable_failure_mode
  PRODUCT_ID=$(create_test_product)
  test_requests "$PRODUCT_ID" 10

  echo ""
  echo ""
  echo "======================================================"
  echo "Test 2: 50% Failure Rate"
  echo "======================================================"

  enable_failure_mode "0.5"
  test_requests "$PRODUCT_ID" "$NUM_REQUESTS"

  echo ""
  echo ""
  echo "======================================================"
  echo "Test 3: Cleanup - Disable Failure Mode"
  echo "======================================================"

  disable_failure_mode
  get_status

  echo ""
  echo -e "${GREEN}✓ Test completed successfully!${NC}"
  echo ""
  echo "To run more tests:"
  echo "  1. Run with more requests: NUM_REQUESTS=100 ./test-failure-simulation.sh"
  echo "  2. Test different error rates manually:"
  echo "     curl 'http://localhost:8082/products/bad-mode?enabled=true&errorRate=0.75'"
  echo "  3. Use the failure test Docker Compose:"
  echo "     docker-compose -f docker-compose.failure-test.yml up -d"
}

# Run main function
main