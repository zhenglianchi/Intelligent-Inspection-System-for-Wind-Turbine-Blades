package com.itheima.consultant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 风场信息实体
 * 对应数据库表 hm_windfarm_info - 存储风场基础信息
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 */
@TableName("hm_windfarm_info")
@Data
public class WindfarmInfoDO {

    @TableId(type = IdType.AUTO)
    private Integer id;
    /**
     * 风场编号
     */
    private String windfarm;
    /**
     * 风场名称
     */
    private String name;
    /**
     * 风机总数量
     */
    private Integer windturbineCount;
    /**
     * 所在省份
     */
    private String province;
    /**
     * 区域编号
     */
    private Integer region;
    /**
     * 未连接风机数量
     */
    private Integer unconnectedCount;
    /**
     * 故障风机数量
     */
    private Integer faultCount;
    /**
     * 健康风机数量
     */
    private Integer healthCount;
}
