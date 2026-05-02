package com.itheima.realtime.service;

import java.util.Map;

/**
 * 风机信息服务接口
 * 提供风机状态查询和缓存更新
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 */
public interface WindturbineService {

    /**
     * 查询单个风机的当前状态
     *
     * @param windfarm    风场编号
     * @param windturbine 风机编号
     * @return 风机状态码
     */
    Integer queryWindturbineStatus(String windfarm, Integer windturbine);

    /**
     * 从数据库查询所有风机状态（仅正常-故障）
     *
     * @param windfarm 风场编号
     * @return 风机编号 -> 状态码 的Map
     */
    Map<String, Integer> queryDbAllStatus(String windfarm);

    /**
     * 查询所有风机状态，包含未连接状态
     * 数据库中不存在的状态默认为未连接
     *
     * @param windfarm 风场编号
     * @return 风机编号 -> 状态码 的Map
     */
    Map<String, Integer> queryAllStatContainsUnconnected(String windfarm);

    /**
     * 更新缓存中风机的状态
     *
     * @param windfarm        风场编号
     * @param unconnectedWTId 风机编号
     * @param status          新状态码
     * @return 成功返回1，失败返回0
     */
    Integer updateWindturbineCacheStatus(String windfarm, Integer unconnectedWTId, Integer status);
}
