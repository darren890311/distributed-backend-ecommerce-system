"""
CS6650 Assignment 5 - E-Commerce Load Testing
Simulates realistic customer shopping sessions with configurable load patterns
"""

from locust import HttpUser, task, between, SequentialTaskSet
import random
import logging
import os

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Load actual product IDs from file (created by product pre-load script)
PRODUCT_IDS = []
product_ids_file = "/tmp/product_ids.txt"
if os.path.exists(product_ids_file):
    with open(product_ids_file, 'r') as f:
        PRODUCT_IDS = [int(line.strip()) for line in f if line.strip()]
    logger.info(f"Loaded {len(PRODUCT_IDS)} product IDs from {product_ids_file}")
else:
    logger.warning(f"Product IDs file not found: {product_ids_file}")
    logger.warning("Using fallback range 1-1000 (may result in 404s)")


class CustomerShoppingSession(SequentialTaskSet):
    """
    Simulates a complete customer shopping session:
    1. Create shopping cart
    2. Add 3-5 items (random products from 1-1000)
    3. Checkout with credit card
    """

    def on_start(self):
        """Initialize session - runs once per customer"""
        self.cart_id = None
        self.items_added = 0
        self.target_items = random.randint(3, 5)  # Each customer adds 3-5 items
        logger.info(f"New customer session started - will add {self.target_items} items")

    @task
    def create_cart(self):
        """Step 1: Create a shopping cart"""
        customer_id = random.randint(1, 10000)

        with self.client.post(
                "/shopping-cart",
                json={"customer_id": customer_id},
                catch_response=True,
                name="1. Create Cart"
        ) as response:
            if response.status_code in [200, 201]:
                data = response.json()
                self.cart_id = data.get("shopping_cart_id")
                logger.info(f"Cart created: {self.cart_id}")
                response.success()
            else:
                logger.error(f"Failed to create cart: {response.status_code}")
                response.failure(f"Failed to create cart: {response.status_code}")
                self.interrupt()  # Stop this session if cart creation fails

    @task
    def add_items(self):
        """Step 2: Add multiple items to cart (3-5 items)"""
        if not self.cart_id:
            logger.error("No cart ID - skipping add items")
            return

        # Add items until we reach target
        while self.items_added < self.target_items:
            # Random product from pre-loaded product IDs
            if PRODUCT_IDS:
                product_id = random.choice(PRODUCT_IDS)
            else:
                # Fallback if product IDs not loaded
                product_id = random.randint(1, 1000)
            quantity = random.randint(1, 3)

            with self.client.post(
                    f"/shopping-carts/{self.cart_id}/addItem",
                    json={
                        "product_id": product_id,
                        "quantity": quantity
                    },
                    catch_response=True,
                    name="2. Add Item"
            ) as response:
                if response.status_code == 204:
                    self.items_added += 1
                    logger.info(f"Added item {self.items_added}/{self.target_items} to cart {self.cart_id}")
                    response.success()
                elif response.status_code == 404:
                    # Product not found or insufficient stock (expected 10% of time)
                    logger.info(f"Product {product_id} unavailable (expected)")
                    response.success()  # Count as success - this is expected behavior
                else:
                    logger.warning(f"Unexpected response adding item: {response.status_code}")
                    response.failure(f"Add item failed: {response.status_code}")

            # Small delay between adding items (simulate browsing)
            self.wait()

    @task
    def checkout(self):
        """Step 3: Checkout the cart"""
        if not self.cart_id:
            logger.error("No cart ID - skipping checkout")
            self.interrupt()
            return

        if self.items_added == 0:
            logger.warning(f"Cart {self.cart_id} is empty - skipping checkout")
            self.interrupt()
            return

        # Generate random credit card number
        card_number = f"{random.randint(1000, 9999)}-{random.randint(1000, 9999)}-{random.randint(1000, 9999)}-{random.randint(1000, 9999)}"

        with self.client.post(
                f"/shopping-carts/{self.cart_id}/checkout",
                json={"credit_card_number": card_number},
                catch_response=True,
                name="3. Checkout"
        ) as response:
            if response.status_code == 200:
                logger.info(f"✅ Checkout successful for cart {self.cart_id}")
                response.success()
            elif response.status_code == 402:
                # Payment declined (expected 10% of time)
                logger.info(f"💳 Payment declined for cart {self.cart_id} (expected)")
                response.success()  # Count as success - this is expected behavior
            else:
                logger.error(f"Checkout failed with {response.status_code}: {response.text}")
                response.failure(f"Checkout failed: {response.status_code}")

        # End this session - customer is done
        self.interrupt()


class EcommerceCustomer(HttpUser):
    """
    Simulates an e-commerce customer.
    Each user goes through a complete shopping session.
    """

    # Wait time between tasks (simulates user think time)
    wait_time = between(2, 5)  # 2-5 seconds between actions

    # Tasks to execute
    tasks = [CustomerShoppingSession]

    # Host will be set via command line or here
    host = "http://localhost:8084"  # Shopping Cart Service endpoint


class HighVolumeCustomer(HttpUser):
    """
    Alternative user type for stress testing - faster actions, less think time
    """
    wait_time = between(0.5, 2)  # Faster customers
    tasks = [CustomerShoppingSession]
    host = "http://localhost:8084"