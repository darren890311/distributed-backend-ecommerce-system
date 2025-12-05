#!/bin/bash
ALB="ecommerce-alb-529175832.us-east-1.elb.amazonaws.com"

echo "Starting aggressive load test..."
echo "Generating massive concurrent load to trigger auto-scaling..."
echo "Press Ctrl+C when you see services scale to 3 instances"
echo ""

# Launch 100 parallel workers hammering endpoints
for worker in {1..100}; do
  (
    while true; do
      curl -s http://$ALB/actuator/health > /dev/null 2>&1 &
      curl -s http://$ALB/actuator/health > /dev/null 2>&1 &
      curl -s http://$ALB/actuator/health > /dev/null 2>&1 &
      sleep 0.01
    done
  ) &
done

echo "Load test running with 100 parallel workers"
echo "Each worker sends continuous requests"
echo "Open AWS Console: ECS -> Clusters -> ecommerce-cluster -> Services"
echo "Watch Desired count increase from 1 to 2 to 3"
echo ""

wait
