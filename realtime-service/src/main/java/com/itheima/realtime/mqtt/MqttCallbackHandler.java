package com.itheima.realtime.mqtt;

import cn.hutool.core.util.CharsetUtil;
import com.itheima.realtime.config.MqttConfig;
import com.itheima.realtime.entity.MqttMssg;
import com.itheima.consultant.entity.RealtimeDO;
import com.itheima.realtime.service.RealTimeService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.Date;

/**
 * MQTT消息回调处理器
 * 处理连接断开重连、消息接收、消息确认
 * 接收到的消息解析后存入数据库并更新缓存
 *
 * @Migration migrated from wtb-health-monitor
 * @Modified 适配Spring Boot 3 + Redis缓存
 */
@Slf4j
@Component
public class MqttCallbackHandler implements MqttCallbackExtended {

    private final MqttClient mqttClient;
    private final MqttConfig mqttConfig;
    private final RealTimeService realTimeService;

    @Autowired
    public MqttCallbackHandler(@Lazy MqttClient mqttClient,
                               @Lazy MqttConfig mqttConfig,
                               RealTimeService realTimeService) {
        this.mqttClient = mqttClient;
        this.mqttConfig = mqttConfig;
        this.realTimeService = realTimeService;
    }

    /**
     * 连接丢失回调，自动重连
     *
     * @param throwable 连接断开异常
     */
    @Override
    public void connectionLost(Throwable throwable) {
        log.error("🔌 [MQTT] 连接断开: {}, 5秒后尝试重连", throwable.getMessage());
        long reconnectAttempts = 1;
        while (true) {
            try {
                if (mqttClient != null && mqttClient.isConnected()) {
                    log.info("✅ [MQTT] 重新连接成功，重新订阅主题");
                    mqttClient.subscribe(mqttConfig.getTopic(), 1);
                    return;
                }
                reconnectAttempts++;
                log.warn("⏳ [MQTT] 重连尝试次数: {}，正在重连...", reconnectAttempts);
                mqttClient.reconnect();
            } catch (Exception e) {
                log.error("❌ [MQTT] 重连失败: {}", e.getMessage(), e);
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * 消息到达回调，解析消息并入库
     *
     * @param topic   主题
     * @param message MQTT消息
     * @throws Exception 处理异常
     */
    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String payload = new String(message.getPayload(), CharsetUtil.UTF_8);
        log.debug("📥 [MQTT] 接收消息 - 主题: {}, 内容: {}", topic, payload);

        // 只处理配置的订阅主题
        if (topic.equals(mqttConfig.getTopic())) {
            processMessage(payload);
        }
    }

    /**
     * 解析MQTT消息并保存到数据库
     *
     * 消息格式: id; cyclecount; state; faultcount; cycle; feature1; feature2; feature3;
     * 各字段说明:
     * 1. id - 设备ID/风机编号
     * 2. cyclecount - 循环计数
     * 3. state - 设备状态 (0正常 1故障 2已连接 9未连接)
     * 4. faultcount - 故障计数
     * 5. cycle - 循环周期
     * 6. feature1 - 特征值1
     * 7. feature2 - 特征值2
     * 8. feature3 - 特征值3
     *
     * @param payload 消息内容
     */
    private void processMessage(String payload) {
        // 按分号分割字段
        String[] fields = payload.split(";");
        if (fields.length < 8) {
            log.warn("⚠️ [MQTT] 消息格式错误，字段不足: {}", payload);
            return;
        }

        try {
            // 解析各个字段
            String deviceId = fields[0];
            int deviceCycleCount = Integer.parseInt(fields[1]);
            int deviceState = Integer.parseInt(fields[2]);
            int deviceFaultCount = Integer.parseInt(fields[3]);
            double deviceCycle = Double.parseDouble(fields[4]);
            double deviceFeature1 = Double.parseDouble(fields[5]);
            double deviceFeature2 = Double.parseDouble(fields[6]);
            double deviceFeature3 = Double.parseDouble(fields[7]);

            // 当前时间
            Date now = new Date();
            Timestamp timestamp = new Timestamp(now.getTime());

            // 构建MQTT消息对象
            MqttMssg mqttMssg = MqttMssg.builder()
                    .id(deviceId)
                    .cyclecount(deviceCycleCount)
                    .state(deviceState)
                    .faultcount(deviceFaultCount)
                    .cycle(deviceCycle)
                    .feature1(deviceFeature1)
                    .feature2(deviceFeature2)
                    .feature3(deviceFeature3)
                    .sendtime(timestamp)
                    .build();

            // 转换为RealtimeDO并入库
            // TODO: 风场编号这里固定为10001，实际应用可以从消息或配置中获取
            RealtimeDO realtimeDO = RealtimeDO.builder()
                    .windfarm("10001")
                    .windturbine(Integer.parseInt(deviceId))
                    .status(deviceState)
                    .feature1(deviceFeature1 * 13 / 7) // 保持原转换算法
                    .feature2(deviceFeature2)
                    .feature3(deviceFeature3)
                    .gmtReceived(timestamp)
                    .build();

            // 调用服务保存数据
            realTimeService.insertRealtimeData(realtimeDO);
            log.info("✅ [MQTT] 数据入库成功 - 风机{} 状态{}", deviceId, deviceState);

        } catch (Exception e) {
            log.error("❌ [MQTT] 消息解析失败: {}", payload, e);
        }
    }

    /**
     * 消息投递完成回调
     *
     * @param token 投递token
     */
    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        log.debug("📤 [MQTT] 消息投递完成: {}", token.isComplete());
    }

    /**
     * 连接完成回调
     *
     * @param reconnect 是否重连
     * @param serverURI 服务端URI
     */
    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        if (reconnect) {
            log.info("✅ [MQTT] 重连完成: {}", serverURI);
        } else {
            log.info("✅ [MQTT] 初始连接完成: {}", serverURI);
        }
    }
}
