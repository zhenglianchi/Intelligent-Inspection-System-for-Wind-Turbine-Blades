package com.itheima.consultant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.sql.Timestamp;

/**
 * 风机实时监测数据实体
 * 对应数据库表 hm_realtime - 存储风机通过MQTT上传的实时监测数据
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 */
@TableName("hm_realtime")
@Data
@ToString
@Builder
public class RealtimeDO {
    @TableId(type = IdType.AUTO)
    private Integer id;
    /**
     * 风机编号
     */
    private Integer windturbine;
    /**
     * 风场编号
     */
    private String windfarm;
    /**
     * 风机状态
     * 0-异常 1-正常 其他状态码可自定义
     */
    private Integer status;
    /**
     * 监测特征值1
     */
    private Double feature1;
    /**
     * 监测特征值2
     */
    private Double feature2;
    /**
     * 监测特征值3
     */
    private Double feature3;
    /**
     * 数据接收时间
     */
    private Timestamp gmtReceived;
}
