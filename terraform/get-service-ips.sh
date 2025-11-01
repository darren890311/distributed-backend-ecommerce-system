#!/bin/bash
# Helper script to get ECS service private IPs for inter-service communication
# Run this after 'terraform apply' and services have started

echo "=== Getting Service Private IPs ==="
echo ""

# Product Service
echo "Fetching Product Service IP..."
PRODUCT_TASK=$(aws ecs list-tasks --cluster ecommerce-cluster --service-name product-service --query 'taskArns[0]' --output text)
if [ ! -z "$PRODUCT_TASK" ] && [ "$PRODUCT_TASK" != "None" ]; then
  PRODUCT_ENI=$(aws ecs describe-tasks --cluster ecommerce-cluster --tasks $PRODUCT_TASK --query 'tasks[0].attachments[0].details[?name==`networkInterfaceId`].value' --output text)
  PRODUCT_IP=$(aws ec2 describe-network-interfaces --network-interface-ids $PRODUCT_ENI --query 'NetworkInterfaces[0].PrivateIpAddress' --output text)
  echo "✅ Product Service IP: $PRODUCT_IP"
else
  echo "⚠️  Product service not running yet"
fi

# Credit Card Authorizer
echo "Fetching Credit Card Authorizer IP..."
CCA_TASK=$(aws ecs list-tasks --cluster ecommerce-cluster --service-name credit-card-authorizer --query 'taskArns[0]' --output text)
if [ ! -z "$CCA_TASK" ] && [ "$CCA_TASK" != "None" ]; then
  CCA_ENI=$(aws ecs describe-tasks --cluster ecommerce-cluster --tasks $CCA_TASK --query 'tasks[0].attachments[0].details[?name==`networkInterfaceId`].value' --output text)
  CCA_IP=$(aws ec2 describe-network-interfaces --network-interface-ids $CCA_ENI --query 'NetworkInterfaces[0].PrivateIpAddress' --output text)
  echo "✅ Credit Card Authorizer IP: $CCA_IP"
else
  echo "⚠️  Credit Card Authorizer not running yet"
fi

echo ""
echo "=== Update ecs_task_definitions.tf ==="
echo ""
echo "In the shopping-cart-service environment block, update:"
echo ""
echo "  SERVICES_PRODUCT_URL: http://$PRODUCT_IP:8082"
echo "  SERVICES_CREDIT_CARD_AUTHORIZER_URL: http://$CCA_IP:8080"
echo ""
echo "Then run: terraform apply"
echo "Then restart: aws ecs update-service --cluster ecommerce-cluster --service shopping-cart-service --force-new-deployment"
