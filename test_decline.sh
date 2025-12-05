#!/bin/bash
while true; do
  CART=$(curl -s -X POST http://localhost:8084/shopping-cart \
    -H "Content-Type: application/json" \
    -d '{"customer_id": 100}' | grep -o '[0-9]*')
  
  curl -s -X POST http://localhost:8084/shopping-carts/$CART/addItem \
    -H "Content-Type: application/json" \
    -d '{"product_id": 1072957318, "quantity": 1}' > /dev/null
  
  echo "Testing cart $CART..."
  RESULT=$(curl -s -X POST http://localhost:8084/shopping-carts/$CART/checkout \
    -H "Content-Type: application/json" \
    -d '{"credit_card_number": "1234-5678-9012-3456"}')
  
  if echo "$RESULT" | grep -q "PAYMENT_DECLINED"; then
    echo "🎯 GOT A DECLINE!"
    echo "$RESULT"
    echo "Check Terminal 5 and Terminal 6 for ABORT TRANSACTION logs!"
    break
  fi
  
  echo "Success - trying again..."
  sleep 0.5
done
