"""
MQTT 风机数据模拟器 (19台风机, 异步发送)
模拟风机设备向 RabbitMQ MQTT 插件发送监测数据

每台风机独立 5s 间隔发送，启动时随机错开避免同步

消息格式: id;cyclecount;state;faultcount;cycle;feature1;feature2;feature3;
"""

import paho.mqtt.client as mqtt
import random
import time
import threading
from datetime import datetime

MQTT_HOST = "localhost"
MQTT_PORT = 1883
MQTT_USERNAME = "guest"
MQTT_PASSWORD = "guest"
MQTT_TOPIC = "$share/wind-power-group/windpower/monitoring"
INTERVAL = 5  # 每台风机发送间隔(秒)
TURBINE_COUNT = 19

turbines = {}
print_lock = threading.Lock()


def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print(f"[{datetime.now().strftime('%H:%M:%S')}] MQTT connected")
    else:
        print(f"[{datetime.now().strftime('%H:%M:%S')}] MQTT connect failed, rc={rc}")


def gen_message(tid, info):
    info["cyclecount"] += 1
    rand = random.random()
    if rand < 0.95:
        state = 0
    elif rand < 0.99:
        state = 1
        info["faultcount"] += 1
    else:
        state = 9

    if state == 0:
        f1, f2, f3 = round(random.uniform(0.6, 1.2), 2), round(random.uniform(0.5, 1.1), 2), round(random.uniform(0.7, 1.3), 2)
    elif state == 1:
        f1, f2, f3 = round(random.uniform(2.0, 4.0), 2), round(random.uniform(2.0, 4.0), 2), round(random.uniform(2.0, 4.0), 2)
    else:
        f1 = f2 = f3 = 0.0

    return (f"{tid};{info['cyclecount']};{state};{info['faultcount']};"
            f"{round(random.uniform(0.8, 1.2), 2)};{f1};{f2};{f3};"), state


def state_name(s):
    return {0: "OK", 1: "FAULT", 9: "OFF"}.get(s, str(s))


def turbine_loop(tid, client):
    """每台风机独立线程，异步发送"""
    info = {"cyclecount": random.randint(1000, 5000), "faultcount": random.randint(0, 3)}
    # 随机初始延迟 0~5s，避免所有风机同一时刻发送
    time.sleep(random.uniform(0, INTERVAL))
    while True:
        try:
            msg, state = gen_message(tid, info)
            client.publish(MQTT_TOPIC, msg, qos=1)
            with print_lock:
                ts = datetime.now().strftime("%H:%M:%S")
                print(f"[{ts}] turbine {tid:2d} {state_name(state)} [{msg.split(';')[5]},{msg.split(';')[6]},{msg.split(';')[7]}]")
        except Exception as e:
            with print_lock:
                print(f"turbine {tid} error: {e}")
        time.sleep(INTERVAL)


def main():
    client = mqtt.Client()
    client.username_pw_set(MQTT_USERNAME, MQTT_PASSWORD)
    client.on_connect = on_connect

    try:
        client.connect(MQTT_HOST, MQTT_PORT, 60)
        client.loop_start()
        time.sleep(1)

        print(f"Starting {TURBINE_COUNT} turbines (async, {INTERVAL}s each)")
        print(f"Topic: {MQTT_TOPIC}")
        print("-" * 50)

        threads = []
        for tid in range(1, TURBINE_COUNT + 1):
            t = threading.Thread(target=turbine_loop, args=(tid, client), daemon=True)
            t.start()
            threads.append(t)

        # 主线程等待
        for t in threads:
            t.join()

    except KeyboardInterrupt:
        print("\nStopped")
    finally:
        client.loop_stop()
        client.disconnect()


if __name__ == "__main__":
    main()
