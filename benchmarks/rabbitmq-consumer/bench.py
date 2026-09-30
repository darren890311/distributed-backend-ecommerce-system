"""
Step-load benchmark for warehouse-service's RabbitMQ consumer.

Publisher: sends OrderMessage-shaped JSON (same as Spring's Jackson2JsonMessageConverter
output from ShoppingCartService.publishOrderToWarehouse) to the default exchange,
routing key checkoutQueue, persistent delivery (Spring default), at stepped target rates.

Monitor: polls RabbitMQ management API every 1s and records queue depth plus
publish/ack counters; rates are computed from counter deltas.

Usage: python bench.py <label> <rate1,rate2,...> <seconds_per_step> <drain_seconds>
"""
import csv, json, random, sys, threading, time, base64, urllib.request
import pika

LABEL = sys.argv[1]
RATES = [int(r) for r in sys.argv[2].split(",")]
STEP_S = int(sys.argv[3])
DRAIN_S = int(sys.argv[4])
OUT = f"{LABEL}.csv"

API = "http://localhost:15672/api/queues/%2F/checkoutQueue"
AUTH = "Basic " + base64.b64encode(b"guest:guest").decode()

current_target = 0
published = 0
stop = threading.Event()


def get_queue():
    req = urllib.request.Request(API, headers={"Authorization": AUTH})
    with urllib.request.urlopen(req, timeout=5) as r:
        return json.load(r)


def warehouse_total():
    with urllib.request.urlopen("http://localhost:8083/warehouse/stats", timeout=5) as r:
        return int(r.read().decode().split("Total Orders:")[1].split("|")[0])


def monitor():
    base = warehouse_total()  # consumer counter may carry over from earlier runs
    t0 = time.time()
    prev = None
    with open(OUT, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["t", "target_rate", "client_published_total", "queue_depth", "ready",
                    "unacked", "consumed_total", "publish_rate", "consume_rate", "backlog_calc", "consumers"])
        while not stop.is_set():
            time.sleep(1)
            try:
                q = get_queue()
            except Exception as e:
                print("monitor error", e)
                continue
            now = time.time()
            pub = published
            try:
                ack = warehouse_total() - base
            except Exception as e:
                print("warehouse stats error", e)
                continue
            if prev:
                dt = now - prev[0]
                pr, ar = (pub - prev[1]) / dt, (ack - prev[2]) / dt
            else:
                pr = ar = 0.0
            prev = (now, pub, ack)
            w.writerow([round(now - t0, 1), current_target, published, q.get("messages", 0),
                        q.get("messages_ready", 0), q.get("messages_unacknowledged", 0),
                        ack, round(pr, 1), round(ar, 1), pub - ack, q.get("consumers", 0)])
            f.flush()


def make_body(order_id):
    products = [{"product_id": random.randint(1, 1000), "quantity": random.randint(1, 5)}
                for _ in range(random.randint(1, 5))]
    return json.dumps({"order_id": order_id, "products": products}).encode()


def publish_steps():
    global current_target, published
    conn = pika.BlockingConnection(pika.ConnectionParameters("localhost", 5672))
    ch = conn.channel()
    props = pika.BasicProperties(content_type="application/json", delivery_mode=2)
    order_id = 1
    for rate in RATES:
        current_target = rate
        print(f"[{LABEL}] step {rate} msg/s for {STEP_S}s", flush=True)
        start = time.time()
        sent_in_step = 0
        while True:
            elapsed = time.time() - start
            if elapsed >= STEP_S:
                break
            due = int(rate * elapsed)
            while sent_in_step < due:
                ch.basic_publish("", "checkoutQueue", make_body(order_id), props)
                order_id += 1
                sent_in_step += 1
                published += 1
            time.sleep(0.001)
        actual = sent_in_step / (time.time() - start)
        print(f"[{LABEL}]   actual publish {actual:.0f} msg/s", flush=True)
    current_target = 0
    conn.close()


m = threading.Thread(target=monitor, daemon=True)
m.start()
time.sleep(3)
publish_steps()
print(f"[{LABEL}] draining up to {DRAIN_S}s", flush=True)
end = time.time() + DRAIN_S
while time.time() < end:
    time.sleep(2)
    if get_queue().get("messages", 1) == 0:
        time.sleep(3)
        break
stop.set()
m.join()
print(f"[{LABEL}] done -> {OUT}; total published {published}")
