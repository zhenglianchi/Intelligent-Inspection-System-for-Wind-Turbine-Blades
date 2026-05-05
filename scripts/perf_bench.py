import requests, time, statistics, os
from datetime import datetime

GATEWAY = "http://localhost:8080"
REDIS_URL = f"{GATEWAY}/realtime/quaryLatestFeaCurve"
MYSQL_URL = f"{GATEWAY}/realtime/queryLastNFromDB"
PARAMS = {"windfarm": "10001", "windturbine": 1}
MYSQL_PARAMS = {**PARAMS, "N": 20}
ROUNDS = 1000


def bench(name, url, params):
    times, errors = [], 0
    for _ in range(ROUNDS):
        t0 = time.perf_counter()
        try:
            r = requests.get(url, params=params, timeout=5)
            if r.status_code != 200:
                errors += 1
        except Exception:
            errors += 1
        times.append((time.perf_counter() - t0) * 1000)
    s = sorted(times)
    n = len(times)
    return {
        "name": name, "rounds": ROUNDS, "errors": errors,
        "avg_ms": round(statistics.mean(times), 2),
        "p50_ms": round(s[int(n * 0.50)], 2),
        "p95_ms": round(s[int(n * 0.95)], 2),
        "p99_ms": round(s[min(int(n * 0.99), n - 1)], 2),
        "min_ms": round(s[0], 2), "max_ms": round(s[-1], 2),
    }


def main():
    # 预热: 触发 Redis 回填
    requests.get(REDIS_URL, params=PARAMS, timeout=5)
    time.sleep(0.5)

    redis_res = bench("Redis", REDIS_URL, PARAMS)
    mysql_res = bench("MySQL", MYSQL_URL, MYSQL_PARAMS)

    lines = []
    def p(s=""): print(s); lines.append(s)

    p(f"# Redis vs MySQL 性能对比 (10万行, 串行 {ROUNDS} 次请求)")
    p(f"\n{datetime.now().strftime('%Y-%m-%d %H:%M:%S')} | windfarm=10001, turbine=1, 并发=1")
    p()

    for r in [redis_res, mysql_res]:
        p(f"## {r['name']}")
        p(f"Avg:{r['avg_ms']}ms P50:{r['p50_ms']}ms P95:{r['p95_ms']}ms P99:{r['p99_ms']}ms Min:{r['min_ms']}ms Max:{r['max_ms']}ms 错误:{r['errors']}")
        p()

    ratio = mysql_res["p50_ms"] / max(redis_res["p50_ms"], 0.01)
    p(f"## P50 对比: Redis={redis_res['p50_ms']}ms vs MySQL={mysql_res['p50_ms']}ms")
    p(f"{'Redis' if ratio > 1 else 'MySQL'} 快 {max(ratio, 1/ratio):.1f}x")

    out = os.path.join(os.path.dirname(__file__), "..", "eval")
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(out, "perf_report.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(f"\neval/perf_report.md")


if __name__ == "__main__":
    main()
