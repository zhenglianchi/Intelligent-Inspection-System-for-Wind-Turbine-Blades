package com.itheima.realtime.config;

import com.itheima.realtime.mqtt.MqttCallbackHandler;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.springframework.context.annotation.Lazy;

import lombok.extern.slf4j.Slf4j;

/**
 * MQTT配置类
 * 连接RabbitMQ MQTT插件，接收风机设备上传的实时监测数据
 *
 * RabbitMQ需要先启用MQTT插件：rabbitmq-plugins enable rabbitmq_mqtt
 * 默认端口：1883
 *
 * @Migration migrated from wtb-health-monitor
 * @Modified 适配RabbitMQ MQTT插件
 */
@Configuration
@Slf4j
public class MqttConfig {

    @Value("${mqtt.enabled:true}")
    private boolean enabled;

    @Value("${mqtt.host:tcp://localhost:1883}")
    private String host;

    @Value("${mqtt.username:guest}")
    private String username;

    @Value("${mqtt.password:guest}")
    private String password;

    @Value("${mqtt.client-id:wind-power-agent-${random.uuid}}")
    private String clientId;

    @Value("${mqtt.timeout:30}")
    private int timeout;

    @Value("${mqtt.keepalive:20}")
    private int keepAlive;

    @Value("${mqtt.topic:publish}")
    private String topic;

    private final MqttCallbackHandler mqttCallbackHandler;

    public MqttConfig(@Lazy MqttCallbackHandler mqttCallbackHandler) {
        this.mqttCallbackHandler = mqttCallbackHandler;
    }

    /**
     * 创建并初始化MQTT客户端，连接RabbitMQ MQTT插件
     * 连接失败会自动重试10次，每次间隔2秒
     *
     * @return MqttClient实例
     */
    @Bean
    public MqttClient mqttClient() {
        if (!enabled) {
            log.info("MQTT disabled by configuration");
            return null;
        }

        try {
            MqttClient client = new MqttClient(host, clientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(username);
            options.setPassword(password.toCharArray());
            options.setConnectionTimeout(timeout);
            options.setKeepAliveInterval(keepAlive);
            options.setCleanSession(true);

            // 设置回调
            client.setCallback(mqttCallbackHandler);

            // 尝试连接，最多重试10次
            for (int i = 0; i < 10; i++) {
                try {
                    client.connect(options);
                    client.subscribe(topic, 1);
                    log.info("✅ [MQTT] 连接RabbitMQ MQTT成功，订阅主题: {}", topic);
                    return client;
                } catch (Exception e) {
                    log.error("❌ [MQTT] 连接失败，重试次数: {}, 错误: {}", i, e.getMessage());
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            log.error("❌ [MQTT] 多次连接失败，放弃连接");
            return client;
        } catch (Exception e) {
            log.error("❌ [MQTT] 创建客户端失败: {}", e.getMessage(), e);
            return null;
        }
    }

    public String getTopic() {
        return topic;
    }
}
