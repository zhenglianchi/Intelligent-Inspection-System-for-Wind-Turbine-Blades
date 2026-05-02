package com.itheima.realtime.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * MQTT消息实体
 * MQTT接收到的消息解析后的格式
 *
 * @Migration migrated from wtb-health-monitor
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MqttMssg {
    /**
     * 设备ID
     */
    private String id;
    /**
     * 循环计数
     */
    private int cyclecount;
    /**
     * 设备状态
     */
    private int state;
    /**
     * 故障计数
     */
    private int faultcount;
    /**
     * 循环周期
     */
    private double cycle;
    /**
     * 特征值1
     */
    private double feature1;
    /**
     * 特征值2
     */
    private double feature2;
    /**
     * 特征值3
     */
    private double feature3;
    /**
     * 发送时间戳
     */
    private Timestamp sendtime;
}
