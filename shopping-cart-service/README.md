# Shopping Cart Service

## Overview
Orchestrates the e-commerce checkout process, coordinating between product validation, payment authorization, and warehouse order fulfillment.

## Responsibilities
1. **Cart Management**: Create and manage shopping carts
2. **Item Management**: Add/update items in carts with product validation
3. **Checkout Orchestration**:
   - Validate cart state
   - Authorize payment via Credit Card service
   - Publish order to RabbitMQ for warehouse
4. **Inter-Service Communication**: Calls Product and CCA services via private IPs

## Configuration

### Environment Variables

**Required:**
- `RABBITMQ_HOST`: RabbitMQ broker address (private IP)
- `RABBITMQ_PORT`: RabbitMQ port (default: 5672)
- `RABBITMQ_USERNAME`: RabbitMQ credentials
- `RABBITMQ_PASSWORD`: RabbitMQ credentials
- `SERVICES_PRODUCT_URL`: Product service endpoint (format: http://IP:PORT)
- `SERVICES_CREDIT_CARD_AUTHORIZER_URL`: CCA service endpoint (format: http://IP:PORT)

**Example (AWS):**
```bash
SERVICES_PRODUCT_URL=http://172.31.16.160:8082
SERVICES_CREDIT_CARD_AUTHORIZER_URL=http://172.31.12.110:8080
RABBITMQ_HOST=172.31.28.49
```

**Note:** IPs are session-specific in AWS Learner Lab. Use `terraform/get-service-ips.sh` to get current IPs.

## API Endpoints

### POST /shopping-cart
Create a new shopping cart

### POST /shopping-carts/{id}/addItem  
Add items to cart with product validation

### POST /shopping-carts/{id}/checkout
Process checkout:
1. Validates product exists (calls Product Service)
2. Authorizes payment (calls CCA Service)  
3. Publishes order to RabbitMQ
4. Returns order ID

## Dependencies
- Product Service (for validation)
- Credit Card Authorizer (for payment)
- RabbitMQ (for warehouse orders)

## Design Decisions

### Why Private IPs?
AWS Learner Lab doesn't support Service Discovery. Using private IPs for direct container communication avoids ALB hairpinning issues.

### Fire-and-Forget Pattern
Checkout doesn't wait for warehouse to fulfill order (could take days in real system). RabbitMQ ensures reliable delivery.

## Testing
See `../load-testing-client` for comprehensive integration tests.
