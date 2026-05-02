"""
MQTT 风机数据模拟器
模拟风机设备向 RabbitMQ MQTT 插件发送监测数据

消息格式: id;cyclecount;state;faultcount;cycle;feature1;feature2;feature3;
- id:          风机编号
- cyclecount:  循环计数(自增)
- state:       0=正常 1=故障 2=已连接 9=未连接
- faultcount:  故障累计次数
- cycle:       循环周期
- feature1:    特征值1 (后端会 *13/7 转换)
- feature2:    特征值2
- feature3:    特征值3

使用前安装依赖: pip install paho-mqtt
"""

import paho.mqtt.client as mqtt
import random
import time
import json
from datetime import datetime

# ==================== 配置 ====================
MQTT_HOST = "localhost"
MQTT_PORT = 1883
MQTT_USERNAME = "guest"
MQTT_PASSWORD = "guest"
MQTT_TOPIC = "$share/wind-power-group/windpower/monitoring"
INTERVAL = 5  # 发送间隔(秒)

# 模拟风机列表: {风机编号: (初始cyclecount, 初始faultcount)}
TURBINES = {
    1:  {"cyclecount": 1000, "faultcount": 0, "state": "normal"},
    2:  {"cyclecount": 2000, "faultcount": 2, "state": "normal"},
    3:  {"cyclecount": 1500, "faultcount": 0, "state": "normal"},
}

# ==================== 连接回调 ====================
def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print(f"[{datetime.now().strftime('%H:%M:%S')}] ✅ MQTT 连接成功")
    else:
        print(f"[{datetime.now().strftime('%H:%M:%S')}] ❌ 连接失败, rc={rc}")

def on_publish(client, userdata, mid):
    pass  # 忽略发布确认

# ==================== 消息生成 ====================
def gen_message(turbine_id, info):
    """生成一条模拟消息"""
    info["cyclecount"] += 1

    # 随机状态：95%正常，4%故障，1%未连接
    rand = random.random()
    if rand < 0.95:
        state = 0  # 正常
    elif rand < 0.99:
        state = 1  # 故障
        info["faultcount"] += 1
    else:
        state = 9  # 未连接

    # 特征值：正常时小范围波动，故障时大幅偏离
    if state == 0:
        f1 = round(random.uniform(0.6, 1.2), 2)
        f2 = round(random.uniform(0.5, 1.1), 2)
        f3 = round(random.uniform(0.7, 1.3), 2)
    elif state == 1:
        f1 = round(random.uniform(2.0, 4.0), 2)
        f2 = round(random.uniform(2.0, 4.0), 2)
        f3 = round(random.uniform(2.0, 4.0), 2)
    else:
        f1 = f2 = f3 = 0.0

    msg = (
        f"{turbine_id};"
        f"{info['cyclecount']};"
        f"{state};"
        f"{info['faultcount']};"
        f"{round(random.uniform(0.8, 1.2), 2)};"
        f"{f1};{f2};{f3};"
    )
    return msg, state

def state_name(state):
    return {0: "正常", 1: "故障", 9: "未连接"}.get(state, f"未知({state})")

# ==================== 主循环 ====================
def main():
    client = mqtt.Client()
    client.username_pw_set(MQTT_USERNAME, MQTT_PASSWORD)
    client.on_connect = on_connect
    client.on_publish = on_publish

    try:
        client.connect(MQTT_HOST, MQTT_PORT, 60)
        client.loop_start()
        time.sleep(1)

        print(f"🚀 开始模拟 {len(TURBINES)} 台风机，每 {INTERVAL}s 发送一次")
        print(f"   Topic: {MQTT_TOPIC}")
        print(f"   风机: {list(TURBINES.keys())}")
        print("-" * 50)

        while True:
            for tid, info in TURBINES.items():
                msg, state = gen_message(tid, info)
                client.publish(MQTT_TOPIC, msg, qos=1)
                ts = datetime.now().strftime("%H:%M:%S")
                print(f"[{ts}] 📤 风机{tid:2d} | 状态:{state_name(state)} | "
                      f"特征:[{msg.split(';')[5]},{msg.split(';')[6]},{msg.split(';')[7]}]")

            time.sleep(INTERVAL)

    except KeyboardInterrupt:
        print("\n⏹️ 停止模拟")
    finally:
        client.loop_stop()
        client.disconnect()

if __name__ == "__main__":
    main()
