package com.itheima.consultant.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.sql.Timestamp;

/**
 * 风机信息实体
 * 对应数据库表 hm_windturbine_info - 存储单台风机基础信息
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 */
@Data
@TableName("hm_windturbine_info")
public class WindturbineInfoDO {

    @TableId(type = IdType.AUTO)
    private Integer id;
    /**
     * 风机编号
     */
    private Integer windturbine;
    /**
     * 所属风场编号
     */
    private String windfarm;
    /**
     * 当前风机状态
     */
    private Integer status;

    /**
     * 创建时间
     */
    @JsonFormat(locale = "zh", timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private Timestamp gmtCreate;

    /**
     * 修改时间
     */
    @JsonFormat(locale = "zh", timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.UPDATE)
    private Timestamp gmtModified;

}
