package com.itheima.realtime.entity;

import lombok.Builder;
import lombok.Data;

/**
 * TXT音频文件数据实体
 * 用于存储从TXT文件读取的音频采样数据
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 */
@Data
@Builder
public class TxtFileDo {
    /**
     * 采样数据数组
     */
    private Double[] data;
    /**
     * 最大值
     */
    private Double max;
    /**
     * 最小值
     */
    private Double min;
}
