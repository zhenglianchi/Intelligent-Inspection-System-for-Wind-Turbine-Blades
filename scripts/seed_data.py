"""
向 hm_realtime 批量追加数据，测试数据量增长对 MySQL 性能的影响

用法:
  python scripts/seed_data.py 10000    # 追加 1 万行
  python scripts/seed_data.py 100000   # 追加 10 万行
  python scripts/seed_data.py reset    # 从 SQL 文件恢复原始数据

不删除已有数据，只追加。
"""

import sys, random, pymysql, os
from datetime import datetime, timedelta

DB = {"host": "localhost", "port": 3306, "user": "root", "password": "root",
      "database": "wind_farm_db", "charset": "utf8mb4"}
WINDFARMS = ["10001", "10002", "10003"]
TURBINES_PER_FARM = 19


def get_count(conn):
    with conn.cursor() as cur:
        cur.execute("SELECT COUNT(*) FROM hm_realtime")
        return cur.fetchone()[0]


def reset_from_sql(conn):
    sql_file = os.path.join(os.path.dirname(__file__), "..",
                            "realtime-service/src/main/resources/static/healthmonitor.sql")
    if not os.path.exists(sql_file):
        print(f"SQL file not found: {sql_file}")
        return
    with conn.cursor() as cur:
        cur.execute("DROP TABLE IF EXISTS hm_realtime")
        cur.execute("DROP TABLE IF EXISTS hm_region")
        cur.execute("DROP TABLE IF EXISTS hm_user")
        cur.execute("DROP TABLE IF EXISTS hm_windfarm_info")
        cur.execute("DROP TABLE IF EXISTS hm_windturbine_info")
    conn.commit()
    # 用 mysql 命令行导入
    import subprocess
    cmd = f'mysql -u{DB["user"]} -p{DB["password"]} -h{DB["host"]} {DB["database"]} < "{sql_file}"'
    subprocess.run(cmd, shell=True)
    print(f"Restored from {sql_file}, now {get_count(conn)} rows")


def insert_rows(conn, count):
    batch = 1000
    base_time = datetime(2026, 5, 2, 0, 0, 0)
    sql = ("INSERT INTO hm_realtime (windturbine,windfarm,status,feature1,feature2,feature3,gmt_received) "
           "VALUES (%s,%s,%s,%s,%s,%s,%s)")
    total = 0
    while total < count:
        rows = [(random.randint(1, TURBINES_PER_FARM), random.choice(WINDFARMS),
                 random.choices([0, 1, 9], weights=[90, 7, 3])[0],
                 round(random.uniform(0.3, 1.8), 2), round(random.uniform(0.3, 1.8), 2),
                 round(random.uniform(0.3, 1.8), 2),
                 base_time + timedelta(seconds=random.randint(0, 86400)))
                for _ in range(min(batch, count - total))]
        with conn.cursor() as cur:
            cur.executemany(sql, rows)
        conn.commit()
        total += len(rows)
        print(f"  inserted {total}/{count} ...")


def main():
    arg = sys.argv[1] if len(sys.argv) > 1 else "10000"
    conn = pymysql.connect(**DB)

    if arg == "reset":
        reset_from_sql(conn)
    else:
        count = int(arg)
        before = get_count(conn)
        print(f"Before: {before} rows. Appending {count} ...")
        insert_rows(conn, count)
        print(f"Now: {get_count(conn)} rows")

    conn.close()


if __name__ == "__main__":
    main()
