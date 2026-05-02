package com.itheima.realtime.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itheima.consultant.dto.RealtimeQueryDTO;
import com.itheima.realtime.entity.FeaPointDO;
import com.itheima.consultant.entity.RealtimeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.LinkedList;
import java.util.List;

/**
 * 实时监测数据 Mapper接口
 * 对应数据库表 hm_realtime
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 */
@Mapper
public interface RealtimeMapper extends BaseMapper<RealtimeDO> {
    /**
     * 查询指定风场的最大风机编号
     *
     * @param windfarmId 风场编号
     * @return 最大风机编号
     */
    Integer searchMaxWindturbineId(@Param("windfarmId") String windfarmId);

    /**
     * 查询指定风机最新N条实时记录，用于构建特征曲线缓存
     *
     * @param windfarm    风场编号
     * @param windturbine 风机编号
     * @param N           返回记录条数
     * @return 特征点列表
     */
    LinkedList<FeaPointDO> queryLastNRecord(@Param("windfarm") String windfarm, @Param("windturbine") Integer windturbine, @Param("N") Integer N);

    /**
     * 查询风场最新N条所有风机的实时数据记录
     *
     * @param windfarm 风场编号
     * @param N        返回记录条数
     * @return 实时数据列表
     */
    List<RealtimeDO> queryWindFarmLastRecord(@Param("windfarm") String windfarm, @Param("N") Integer N);

    /**
     * 查询风场指定状态下最新N条实时数据记录
     *
     * @param windfarm 风场编号
     * @param status   风机状态
     * @param N        返回记录条数
     * @return 实时数据列表
     */
    List<RealtimeDO> queryWindFarmLastRecordByStatus(@Param("windfarm") String windfarm, @Param("status") Integer status, @Param("N") Integer N);

    /**
     * 多条件灵活查询实时数据
     * 所有条件均为可选，MyBatis 动态 SQL 自动组合 WHERE 子句
     */
    List<RealtimeDO> queryByConditions(RealtimeQueryDTO query);
}
