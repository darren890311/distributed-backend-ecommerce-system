"""
CS6650 Assignment 5 - AWS Load Testing with Locust
Two Use Cases for E-Commerce System

Use Case 1: Customer Shopping Session
- Create cart → Add items → Checkout
- Uses log-normal distribution for item quantities (more realistic shopping behavior)

Use Case 2: Product Browsing
- Browse/view products
- Simulates customers who browse but don't buy

Distribution Rationale:
- Log-normal distribution is used for items per cart because:
  1. Most customers buy few items (1-3)
  2. Fewer customers buy moderate amounts (4-8)
  3. Very few customers buy many items (9+)
  This mirrors real e-commerce behavior where purchases follow a right-skewed distribution.

Call Rate Justification:
- Shopping sessions are weighted 70% (realistic conversion rate from browsing)
- Product browsing is weighted 30% (many users browse without buying)
- Wait time between actions: 1-3 seconds (simulates human reading/clicking time)

Product Loading:
- Products are loaded from products.json file (written by Java load testing client)
- If file doesn't exist, falls back to random product IDs 1-1000
"""

from locust import HttpUser, task, between, SequentialTaskSet, events
import random
import logging
import numpy as np
import json
import os
import requests

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Configuration
PRODUCTS_FILE = "products.json"  # Shared file with Java client
DEFAULT_NUM_PRODUCTS = 1000  # Fallback if no products file

# Global product list (loaded at startup)
PRODUCT_IDS = []


def load_products_from_file():
    """Load product IDs from shared JSON file"""
    global PRODUCT_IDS
    if os.path.exists(PRODUCTS_FILE):
        try:
            with open(PRODUCTS_FILE, 'r') as f:
                data = json.load(f)
                PRODUCT_IDS = data.get('product_ids', [])
                logger.info(f"Loaded {len(PRODUCT_IDS)} products from {PRODUCTS_FILE}")
                return True
        except Exception as e:
            logger.warning(f"Failed to load products from file: {e}")
    return False


def load_products_from_api(host):
    """Fetch product IDs from the API"""
    global PRODUCT_IDS
    try:
        # Try to get a sample of products by checking which IDs exist
        logger.info(f"Fetching products from API: {host}")
        valid_ids = []
        # Quick sample: check products 1-100 to verify they exist
        for i in range(1, min(101, DEFAULT_NUM_PRODUCTS + 1)):
            try:
                resp = requests.get(f"{host}/products/{i}", timeout=2)
                if resp.status_code == 200:
                    valid_ids.append(i)
            except:
                pass

        if valid_ids:
            # Assume products are loaded sequentially 1-N
            max_id = max(valid_ids)
            PRODUCT_IDS = list(range(1, DEFAULT_NUM_PRODUCTS + 1))
            logger.info(f"Verified {len(valid_ids)} products exist, using IDs 1-{DEFAULT_NUM_PRODUCTS}")
            return True
    except Exception as e:
        logger.warning(f"Failed to fetch products from API: {e}")
    return False


def get_random_product_id():
    """Get a random product ID from loaded products or fallback range"""
    if PRODUCT_IDS:
        return random.choice(PRODUCT_IDS)
    return random.randint(1, DEFAULT_NUM_PRODUCTS)


@events.init.add_listener
def on_locust_init(environment, **kwargs):
    """Initialize product list when Locust starts"""
    global PRODUCT_IDS

    # Try loading from file first
    if load_products_from_file():
        return

    # Try loading from API
    host = getattr(environment, 'host', None) or "http://ecommerce-alb-511228928.us-east-1.elb.amazonaws.com"
    if load_products_from_api(host):
        return

    # Fallback to default range
    PRODUCT_IDS = list(range(1, DEFAULT_NUM_PRODUCTS + 1))
    logger.info(f"Using default product IDs 1-{DEFAULT_NUM_PRODUCTS}")


NUM_PRODUCTS = DEFAULT_NUM_PRODUCTS  # For backward compatibility
MIN_ITEMS = 1
MAX_ITEMS = 10
LOG_NORMAL_MU = 0.7  # Mean of underlying normal distribution (results in ~2 items median)
LOG_NORMAL_SIGMA = 0.7  # Standard deviation (controls spread)


def get_num_items_lognormal():
    """
    Generate number of items using log-normal distribution.
    This models realistic shopping behavior where:
    - Most customers buy 1-3 items
    - Some buy 4-6 items
    - Few buy 7+ items
    """
    num = int(np.random.lognormal(LOG_NORMAL_MU, LOG_NORMAL_SIGMA))
    return max(MIN_ITEMS, min(MAX_ITEMS, num))  # Clamp to reasonable range


def get_quantity_lognormal():
    """
    Generate quantity per item using log-normal distribution.
    Most items are bought in quantity 1, sometimes 2, rarely more.
    """
    qty = int(np.random.lognormal(0.3, 0.5))
    return max(1, min(5, qty))  # Clamp between 1-5


class UseCase1_CustomerShoppingSession(SequentialTaskSet):
    """
    USE CASE 1: Complete Customer Shopping Session

    Flow:
    1. Create shopping cart
    2. Add N items (N from log-normal distribution)
    3. Checkout with credit card

    This simulates a customer who completes their purchase.
    """

    def on_start(self):
        self.cart_id = None
        self.items_added = 0
        self.target_items = get_num_items_lognormal()
        logger.info(f"Starting shopping session with target {self.target_items} items")

    @task
    def step1_create_cart(self):
        """Step 1: Create a shopping cart"""
        customer_id = random.randint(1, 100000)

        with self.client.post(
            "/shopping-cart",
            json={"customer_id": customer_id},
            catch_response=True,
            name="UC1.1 Create Cart"
        ) as response:
            if response.status_code in [200, 201]:
                try:
                    data = response.json()
                    self.cart_id = data.get("shopping_cart_id")
                    logger.info(f"Cart created: {self.cart_id}")
                    response.success()
                except Exception as e:
                    response.failure(f"Failed to parse cart response: {e}")
                    self.interrupt()
            else:
                logger.error(f"Failed to create cart: {response.status_code}")
                response.failure(f"Failed to create cart: {response.status_code}")
                self.interrupt()

    @task
    def step2_add_items(self):
        """Step 2: Add items to cart (log-normal distributed)"""
        if not self.cart_id:
            logger.error("No cart ID - skipping add items")
            self.interrupt()
            return

        while self.items_added < self.target_items:
            product_id = get_random_product_id()
            quantity = get_quantity_lognormal()

            with self.client.post(
                f"/shopping-carts/{self.cart_id}/addItem",
                json={"product_id": product_id, "quantity": quantity},
                catch_response=True,
                name="UC1.2 Add Item"
            ) as response:
                if response.status_code == 204:
                    self.items_added += 1
                    logger.debug(f"Added product {product_id} x{quantity}")
                    response.success()
                elif response.status_code == 404:
                    # Product not found - try another
                    response.success()
                else:
                    logger.warning(f"Add item failed: {response.status_code}")
                    response.failure(f"Add item failed: {response.status_code}")

            # Small wait between adds (simulates browsing)
            self.wait()

    @task
    def step3_checkout(self):
        """Step 3: Checkout with credit card"""
        if not self.cart_id:
            self.interrupt()
            return

        if self.items_added == 0:
            logger.warning(f"Cart {self.cart_id} is empty, skipping checkout")
            self.interrupt()
            return

        # Generate a credit card number
        card_number = f"{random.randint(1000,9999)}-{random.randint(1000,9999)}-{random.randint(1000,9999)}-{random.randint(1000,9999)}"

        with self.client.post(
            f"/shopping-carts/{self.cart_id}/checkout",
            json={"credit_card_number": card_number},
            catch_response=True,
            name="UC1.3 Checkout"
        ) as response:
            if response.status_code == 200:
                logger.info(f"Checkout successful: cart={self.cart_id}, items={self.items_added}")
                response.success()
            elif response.status_code == 402:
                # Payment declined is still a valid response
                logger.info(f"Payment declined: cart={self.cart_id}")
                response.success()
            else:
                logger.error(f"Checkout failed: {response.status_code} - {response.text}")
                response.failure(f"Checkout failed: {response.status_code}")

        # End this user session
        self.interrupt()


class UseCase2_ProductBrowsing(SequentialTaskSet):
    """
    USE CASE 2: Product Browsing Session

    Flow:
    1. Browse/view multiple products
    2. Customer decides not to buy (abandonment)

    This simulates customers who browse but don't convert.
    Many e-commerce visitors browse without purchasing.
    """

    def on_start(self):
        self.products_viewed = 0
        self.target_products = get_num_items_lognormal() * 2  # Browse more than buy

    @task
    def browse_products(self):
        """Browse multiple products without buying"""
        while self.products_viewed < self.target_products:
            product_id = get_random_product_id()

            with self.client.get(
                f"/products/{product_id}",
                catch_response=True,
                name="UC2 View Product"
            ) as response:
                if response.status_code == 200:
                    self.products_viewed += 1
                    response.success()
                elif response.status_code == 404:
                    # Product not found - expected for some IDs
                    response.success()
                else:
                    response.failure(f"Browse failed: {response.status_code}")

            self.wait()

        # End browsing session
        self.interrupt()


class EcommerceCustomer(HttpUser):
    """
    Main Locust User class representing an e-commerce customer.

    Task weights:
    - 70% shopping sessions (customers who complete purchases)
    - 30% browsing sessions (customers who only browse)

    This reflects typical e-commerce conversion rates of 2-5%,
    though we weight shopping higher to stress-test the checkout flow.
    """

    # Wait time between tasks (simulates human think time)
    wait_time = between(1, 3)

    # Task distribution
    tasks = {
        UseCase1_CustomerShoppingSession: 7,  # 70% weight
        UseCase2_ProductBrowsing: 3           # 30% weight
    }

    # Target host (ALB endpoint)
    host = "http://ecommerce-alb-1186708136.us-east-1.elb.amazonaws.com"
