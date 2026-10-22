"""Experiment 5 (simulation): sharding FoodFleet's orders 16 ways. 1 M orders from 200,000 customers and 20,000
restaurants (popularity follows Zipf distributions: a few chains take a large share) in 8 cities of unequal size.
For each shard key: how evenly the data spreads, and how many shards a typical query must ask. Then: what moves
when 16 shards become 20, with modulo hashing, a consistent-hash ring, and a fixed set of slots."""
import bisect
import hashlib
import itertools
import random
import statistics

random.seed(33)
SHARDS = 16


def h(x):
    return int.from_bytes(hashlib.blake2b(str(x).encode(), digest_size=8).digest(), "big")


def zipf_choices(n, s, k):
    weights = list(itertools.accumulate(1 / (i ** s) for i in range(1, n + 1)))
    return random.choices(range(1, n + 1), cum_weights=weights, k=k)


ORDERS = 1_000_000
customers = zipf_choices(200_000, 0.7, ORDERS)
restaurants = zipf_choices(20_000, 1.1, ORDERS)
city_of_restaurant = {r: random.choices(range(8), [30, 20, 15, 10, 10, 7, 5, 3])[0] for r in range(1, 20_001)}
orders = [(i, customers[i], restaurants[i], city_of_restaurant[restaurants[i]]) for i in range(ORDERS)]

KEYS = {
    "order id": lambda o: h(o[0]) % SHARDS,
    "customer id": lambda o: h(o[1]) % SHARDS,
    "restaurant id": lambda o: h(o[2]) % SHARDS,
    "city": lambda o: o[3] * 2 % SHARDS,        # 8 cities on 16 shards: a city per shard pair, the other 8 idle
}
# query mix: (share, the order field it filters on)
QUERIES = [(0.40, "order id"), (0.30, "customer id"), (0.25, "restaurant id"), (0.05, "city")]


def shards_asked(key, filter_field):
    if key == filter_field:
        return 1
    if key == "customer id, shard in order id" and filter_field in ("order id", "customer id"):
        return 1
    return SHARDS


def main():
    print(f"{ORDERS:,} orders on {SHARDS} shards\n")
    print(f"{'shard key':34}{'largest shard':>15}{'vs average':>12}{'emptiest':>10}{'single-shard queries':>22}{'shards per query':>18}")
    for key, fn in list(KEYS.items()) + [("customer id, shard in order id", KEYS["customer id"])]:
        counts = [0] * SHARDS
        for o in orders:
            counts[fn(o)] += 1
        mean = ORDERS / SHARDS
        single = sum(share for share, f in QUERIES if shards_asked(key, f) == 1)
        avg = sum(share * shards_asked(key, f) for share, f in QUERIES)
        print(f"{key:34}{max(counts):>15,}{max(counts) / mean:>11.2f}x{min(counts):>10,}{single:>22.0%}{avg:>18.1f}")

    print(f"\ngoing from 16 to 20 shards: share of the {ORDERS:,} orders (keyed by customer id) that must move\n")
    keys = [o[1] for o in orders]
    hashed = [h(k) for k in keys]
    moved = sum(1 for x in hashed if x % 16 != x % 20)
    print(f"{'hash(key) mod N':44}{moved / ORDERS:>8.1%}")

    def ring(n, vnodes=100):
        points = sorted((h(f"shard-{s}-vnode-{v}"), s) for s in range(n) for v in range(vnodes))
        return [p for p, _ in points], [s for _, s in points]

    def owner(r, x):
        pts, owners = r
        i = bisect.bisect(pts, x) % len(pts)
        return owners[i]
    r16, r20 = ring(16), ring(20)
    moved = sum(1 for x in hashed if owner(r16, x) != owner(r20, x))
    print(f"{'consistent-hash ring, 100 points per shard':44}{moved / ORDERS:>8.1%}")
    sizes = [0] * 20
    for x in hashed:
        sizes[owner(r20, x)] += 1
    ring_spread = max(sizes) / (ORDERS / 20)

    SLOTS = 4096
    slot_map = [s % 16 for s in range(SLOTS)]
    new_map = list(slot_map)
    per_new = SLOTS // 20
    for new_shard in range(16, 20):          # each new shard takes per_new slots, taken evenly from the old shards
        taken = 0
        for slot in range(SLOTS):
            if taken == per_new:
                break
            old = new_map[slot]
            if old < 16 and sum(1 for s in new_map if s == old) > per_new:
                new_map[slot] = new_shard
                taken += 1
    moved = sum(1 for x in hashed if slot_map[x % SLOTS] != new_map[x % SLOTS])
    print(f"{'4,096 fixed slots, slots reassigned':44}{moved / ORDERS:>8.1%}")
    sizes = [0] * 20
    for x in hashed:
        sizes[new_map[x % SLOTS]] += 1
    print(f"\nlargest of 20 shards vs average afterwards: ring {ring_spread:.2f}x, slots {max(sizes) / (ORDERS / 20):.2f}x"
          f" (the ideal move is 4/20 = 20%)")


if __name__ == "__main__":
    main()
