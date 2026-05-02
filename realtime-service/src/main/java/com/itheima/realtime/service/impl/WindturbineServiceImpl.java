package com.itheima.realtime.service.impl;

import com.itheima.consultant.constant.CacheConstant;
import com.itheima.consultant.constant.Constants;
import com.itheima.realtime.mapper.Handler.MapResultHander;
import com.itheima.realtime.mapper.WindturbineMapper;
import com.itheima.realtime.service.RedisCacheService;
import com.itheima.realtime.service.WindturbineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 风机信息服务实现类
 * 改造说明：原Caffeine缓存已改为RedisCacheService
 * 分布式读写锁已移除，改用 Redis Lua 脚本保证原子性：
 *   - 状态读取：Redis GET 本身原子，无需额外锁
 *   - 状态写入：通过 Lua 脚本原子执行 SET + EXPIRE
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 * @Modified 移除 Redisson 分布式锁，改用 Redis Lua 脚本
 */
@Slf4j
@Service
public class WindturbineServiceImpl implements WindturbineService {

    private final static String WF_STATUS = "wf_status";

    @Autowired
    WindturbineMapper windturbineMapper;

    @Autowired
    RedisCacheService redisCacheService;

    /**
     * 查询单个风机的当前状态
     */
    @Override
    public Integer queryWindturbineStatus(String windfarm, Integer windturbine) {
        return null;
    }

    /**
     * 从数据库查询所有风机状态（仅正常-故障）
     */
    @Override
    public Map<String, Integer> queryDbAllStatus(String windfarm) {
        MapResultHander mapResultHander = new MapResultHander("windturbine", "status");
        windturbineMapper.queryAllWindturbineStatus(windfarm, mapResultHander);
        return mapResultHander.getMapResults();
    }

    /**
     * 查询所有风机状态，包含未连接状态
     * 数据库中不存在于缓存中的状态默认为未连接
     * 使用Redis缓存加速查询（GET 命令天然原子，无需分布式锁）
     */
    @Override
    public Map<String, Integer> queryAllStatContainsUnconnected(String windfarm) {
        if (windfarm == null || windfarm.length() == 0) {
            return new HashMap<>(0);
        }
        return doQueryAllStatContainsUnconnected(windfarm);
    }

    /**
     * 实际查询逻辑
     * Redis GET 操作本身是原子的，多实例并发读取不会产生数据不一致
     */
    private Map<String, Integer> doQueryAllStatContainsUnconnected(String windfarm) {
        String wfStatusKey = CacheConstant.getKey(CacheConstant.KEY_WIND_TURBINE, WF_STATUS, windfarm);
        Map<String, Integer> wfStatus = redisCacheService.getCommon(wfStatusKey, Map.class);

        if (wfStatus == null || wfStatus.isEmpty()) {
            wfStatus = constructStatusMap(windfarm);
        }

        Set<String> wtIds = wfStatus.keySet();
        List<String> wtStatusKeys = wtIds.stream()
                .map(cur -> CacheConstant.getWtStatusKey(windfarm, cur))
                .collect(Collectors.toList());

        Map<String, Integer> finalWfStatus = new HashMap<>(wfStatus);
        for (String key : wtStatusKeys) {
            Integer cachedStatus = redisCacheService.getState(key, Integer.class);
            if (cachedStatus != null) {
                String wtId = getWtIdFromCacheKey(key);
                finalWfStatus.put(wtId, cachedStatus);
            }
        }

        redisCacheService.putCommon(wfStatusKey, finalWfStatus);

        return finalWfStatus;
    }

    /**
     * 从缓存key中提取风机编号
     */
    private String getWtIdFromCacheKey(String key) {
        int idx = key.length() - 1;
        StringBuilder sb = new StringBuilder();
        while (idx >= 0 && key.charAt(idx) != '_') {
            sb.append(key.charAt(idx));
            idx--;
        }
        return sb.reverse().toString();
    }

    /**
     * 更新缓存中风机的状态（原子操作）
     * 使用 Redis Lua 脚本保证 SET + EXPIRE 原子执行，
     * 替代原有的 Redisson 分布式写锁
     */
    @Override
    public Integer updateWindturbineCacheStatus(String windfarm, Integer windturbine, Integer status) {
        String wtStatusKey = CacheConstant.getWtStatusKey(windfarm, windturbine.toString());
        redisCacheService.putStateAtomic(wtStatusKey, status);
        log.debug("📝 [Lua原子] 更新风机状态 - 风场: {}, 风机: {}, 状态: {}", windfarm, windturbine, status);
        return Constants.SUCCESS_INT;
    }

    /**
     * 从数据库加载所有风机状态，初始设置所有风机为未连接状态
     */
    private Map<String, Integer> constructStatusMap(String windfarm) {
        Map<String, Integer> wtStatusDB = queryDbAllStatus(windfarm);

        Map<String, Integer> wtStatus = wtStatusDB.entrySet().stream()
                .peek(entry -> entry.setValue(Constants.UNCONNECTED))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return wtStatus;
    }
}
