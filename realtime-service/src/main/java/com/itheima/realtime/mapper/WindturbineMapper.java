package com.itheima.realtime.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itheima.consultant.entity.WindturbineInfoDO;
import com.itheima.realtime.mapper.Handler.MapResultHander;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 风机信息 Mapper接口
 * 对应数据库表 hm_windturbine_info
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 */
public interface WindturbineMapper extends BaseMapper<WindturbineInfoDO> {

    /**
     * 查询指定风场的风机总数
     *
     * @param windfarm 风场编号
     * @return 风机数量
     */
    Integer queryWindturbineCount(@Param("windfarm") String windfarm);

    /**
     * 查询指定风场指定状态的风机数量
     *
     * @param windfarm 风场编号
     * @param status   风机状态
     * @return 符合条件的风机数量
     */
    Integer queryStatusCount(@Param("windfarm") String windfarm, @Param("status") Integer status);

    /**
     * 查询指定风场指定状态的风机编号列表
     *
     * @param windfarm 风场编号
     * @param status   风机状态
     * @return 风机编号列表
     */
    List<Integer> queryStatusList(@Param("windfarm") String windfarm, @Param("status") Integer status);

    /**
     * 查询指定风场所有风机编号列表
     *
     * @param windfarm 风场编号
     * @return 风机编号列表
     */
    List<Integer> queryWTList(@Param("windfarm") String windfarm);

    /**
     * 查询风场所有风机的健康状态，结果放入Map
     *
     * @param windfarm        风场编号
     * @param mapResultHander 结果处理器
     */
    void queryAllWindturbineStatus(@Param("windfarm") String windfarm, MapResultHander mapResultHander);
}
