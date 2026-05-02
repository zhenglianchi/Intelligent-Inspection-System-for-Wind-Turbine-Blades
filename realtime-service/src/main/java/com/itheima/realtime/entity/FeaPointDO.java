package com.itheima.realtime.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeaPointDO {

    /**
     * 特征值1
     */
    private Double feature1;
    /**
     * 特征值2
     */
    private Double feature2;
    /**
     * 特征值3
     */
    private Double feature3;

    /**
     * 数据采集时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp gmtReceived;
}
