package com.itheima.realtime.service;

import com.itheima.consultant.common.Result;
import com.itheima.consultant.dto.RealtimeQueryDTO;
import com.itheima.consultant.entity.RealtimeDO;

import java.util.List;

/**
 * 实时监测数据服务接口
 * 处理风机上传的实时监测数据，提供数据查询、特征曲线、频谱分析等功能
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 */
public interface RealTimeService {

    /**
     * 插入新的实时数据（MQTT消息触发）
     *
     * @param realtimeDO 实时数据对象
     * @return 操作结果
     */
    Result insertRealtimeData(RealtimeDO realtimeDO);

    /**
     * 更新当前风机的特征曲线数据
     *
     * @param realtimeDO 实时数据
     * @return 成功返回1，失败返回0
     */
    Integer updateFeaCurve(RealtimeDO realtimeDO);

    /**
     * 获取风机的最新特征曲线数据
     *
     * @param windfarm    风场编号
     * @param windturbine 风机编号
     * @return 特征曲线BO
     */
    Result getFeaCurve(String windfarm, Integer windturbine);

    /**
     * 获取指定风场的最大风机编号
     *
     * @param windfarm 风场编号
     * @return 最大风机编号
     */
    Integer getMaxWindturbineId(String windfarm);

    /**
     * 获取当前风场监测的风机总数量
     *
     * @param windfarm 风场编号
     * @return 风机数量
     */
    Integer getWindturbineNum(String windfarm);

    /**
     * 查询风场最新N条所有风机的实时数据记录
     *
     * @param windfarm 风场编号
     * @param N        查询记录条数
     * @return 实时数据列表
     */
    Result queryWindFarmLastRecord(String windfarm, Integer N);

    /**
     * 查询风场指定状态下最新N条实时数据记录
     *
     * @param windfarm 风场编号
     * @param status   风机状态
     * @param N        查询记录条数
     * @return 实时数据列表
     */
    Result queryWindFarmLastRecordByStatus(String windfarm, Integer status, Integer N);

    /**
     * 多条件灵活查询实时数据，所有条件可选
     */
    Result queryByConditions(RealtimeQueryDTO query);

    /** MySQL 直查对比接口，不走Redis */
    Result queryLastNFromDB(String windfarm, Integer windturbine, Integer N);
}
