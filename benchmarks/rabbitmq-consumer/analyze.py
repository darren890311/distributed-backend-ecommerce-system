"""Per-step summary + chart for bench.py CSVs. Usage: python analyze.py label1 [label2 ...]"""
import csv, sys
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

for label in sys.argv[1:]:
    rows = list(csv.DictReader(open(f"{label}.csv")))
    for r in rows:
        for k in r:
            r[k] = float(r[k])
    print(f"\n=== {label} ===")
    print(f"{'target':>7} {'pub/s':>7} {'cons/s avg':>10} {'cons/s p50':>10} "
          f"{'backlog max':>11} {'backlog end':>11} {'broker max':>10}")
    targets = []
    for r in rows:
        if r["target_rate"] and (not targets or targets[-1] != r["target_rate"]):
            targets.append(r["target_rate"])
    for t in targets:
        seg = [r for r in rows if r["target_rate"] == t]
        seg = seg[1:]  # skip the partial first second of a step
        pub = sum(r["publish_rate"] for r in seg) / len(seg)
        cons = sorted(r["consume_rate"] for r in seg)
        print(f"{int(t):>7} {pub:>7.0f} {sum(cons)/len(cons):>10.0f} {cons[len(cons)//2]:>10.0f} "
              f"{int(max(r['backlog_calc'] for r in seg)):>11} {int(seg[-1]['backlog_calc']):>11} "
              f"{int(max(r['queue_depth'] for r in seg)):>10}")
    last = rows[-1]
    print(f"total published {int(last['client_published_total'])}, consumed {int(last['consumed_total'])}, "
          f"final backlog {int(last['backlog_calc'])}, duration {last['t']:.0f}s")

    t = [r["t"] for r in rows]
    fig, (a1, a2) = plt.subplots(2, 1, figsize=(11, 7), sharex=True)
    a1.plot(t, [r["target_rate"] for r in rows], "k--", lw=1, label="target publish rate")
    a1.plot(t, [r["publish_rate"] for r in rows], lw=1, label="publish msg/s")
    a1.plot(t, [r["consume_rate"] for r in rows], lw=1, label="consume (ack) msg/s")
    a1.set_ylabel("messages / sec"); a1.legend(loc="upper left"); a1.grid(alpha=.3)
    a2.plot(t, [r["backlog_calc"] for r in rows], lw=1.2, label="backlog (published - consumed)")
    a2.plot(t, [r["queue_depth"] for r in rows], lw=1, alpha=.7, label="broker queue depth (5s samples)")
    a2.axhline(1000, color="r", lw=.8, ls=":", label="1,000")
    a2.set_ylabel("messages"); a2.set_xlabel("seconds"); a2.legend(loc="upper left"); a2.grid(alpha=.3)
    fig.suptitle(f"checkoutQueue step load - {label}")
    fig.tight_layout(); fig.savefig(f"{label}.png", dpi=110)
    print(f"chart -> {label}.png")
