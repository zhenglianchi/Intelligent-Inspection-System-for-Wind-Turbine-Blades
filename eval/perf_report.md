# Redis vs MySQL 性能对比 (10万行, 串行 1000 次请求)

2026-05-05 10:59:55 | windfarm=10001, turbine=1, 并发=1

## Redis
Avg:12.9ms P50:10.09ms P95:30.93ms P99:39.9ms Min:6.1ms Max:351.36ms 错误:0

## MySQL
Avg:24.94ms P50:29.81ms P95:45.63ms P99:48.41ms Min:5.83ms Max:53.58ms 错误:0

## P50 对比: Redis=10.09ms vs MySQL=29.81ms
Redis 快 3.0x