package com.itheima.realtime.service;

import com.itheima.consultant.common.Result;
import com.itheima.consultant.dto.RealtimeQueryDTO;
import com.itheima.consultant.entity.RealtimeDO;
import com.itheima.realtime.entity.SpectrumDo;

import java.io.IOException;
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
     * 获取最新wav文件的绝对路径（已经高通滤波处理）
     *
     * @return 操作结果，包含wav文件路径
     * @throws IOException IO异常
     */
    Result getLatestWavAbsolutePath() throws IOException;

    /**
     * 获取最新txt文件的绝对路径（已经高通滤波处理）
     *
     * @return 操作结果，包含txt文件路径
     * @throws IOException IO异常
     */
    Result getLatestTxtAbsolutePath() throws IOException;

    /**
     * 获取最新txt文件的绝对路径（原始数据，无滤波）
     *
     * @return 操作结果，包含txt文件路径
     */
    Result getLatestOrigTxtAbsolutePath();

    /**
     * 获取最新文件的频谱分析数据（滤波后）
     *
     * @return 频谱数据
     */
    Result getLastFileSpectrum();

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
     * 获取指定风机最新原始txt文件数据（最多20万点，可降采样）
     *
     * @param windturbine 风机编号
     * @return 采样数据数组
     */
    Double[] getLatestOrigTxtData(String windturbine);

    /**
     * 获取指定风机最新滤波后txt文件数据（最多20万点，可降采样）
     *
     * @param windturbine 风机编号
     * @return 采样数据数组
     */
    Double[] getLatestTxtDataAfterFiltering(String windturbine);

    /**
     * 获取指定风机最新txt文件的频谱数据
     *
     * @param windturbine 风机编号
     * @return 频谱数据对象
     */
    SpectrumDo getLatestTxtSpectrumData(String windturbine);

    /**
     * 获取分段频谱集合（分为20段，用于时频分析）
     *
     * @param windturbine 风机编号
     * @return 每段频谱振幅数组的列表
     */
    List<double[]> getLatestTxtSpectrumCollection(String windturbine);

    /**
     * 多条件灵活查询实时数据，所有条件可选
     */
    Result queryByConditions(RealtimeQueryDTO query);
}
