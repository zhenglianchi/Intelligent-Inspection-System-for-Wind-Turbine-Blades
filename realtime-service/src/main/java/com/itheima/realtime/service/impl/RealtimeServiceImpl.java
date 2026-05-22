package com.itheima.realtime.service.impl;

import com.itheima.realtime.bo.FeaCurveBO;
import com.itheima.consultant.common.Result;
import com.itheima.consultant.constant.CacheConstant;
import com.itheima.consultant.constant.Constants;
import com.itheima.consultant.dto.RealtimeQueryDTO;
import com.itheima.realtime.entity.FeaPointDO;
import com.itheima.consultant.entity.RealtimeDO;
import com.itheima.realtime.mapper.RealtimeMapper;
import com.itheima.realtime.service.RealTimeService;
import com.itheima.realtime.service.RedisCacheService;
import com.itheima.realtime.service.WindturbineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MQTT 数据接收 + 前端曲线查询服务
 */
@Service
@Slf4j
public class RealtimeServiceImpl implements RealTimeService {

    private final static String MAX_WT_ID = "max_wt_id";
    private final static String FEA_CURVE = "fea_curve";
    private final static String WT_STATUS = "wt_status";

    @Autowired RealtimeMapper realtimeMapper;
    @Autowired WindturbineService windturbineService;
    @Autowired RedisCacheService redisCacheService;
    @Autowired Environment environment;

    /**
     * MQTT 板端数据写入：校验 → 入库 → 更新特征曲线缓存 → 更新风机状态缓存
     */
    @Override
    public Result insertRealtimeData(RealtimeDO realtimeDO) {
        if (null == realtimeDO || Objects.isNull(realtimeDO.getStatus())) {
            log.error("实时数据为空: realtimeDO={}", realtimeDO);
            return Result.buildResult(Result.Status.ERROR);
        }
        String windfarm = realtimeDO.getWindfarm();
        Integer windturbine = realtimeDO.getWindturbine();
        if (Objects.isNull(windturbine) || Objects.isNull(windfarm)) {
            log.error("风场-{} 风机编号为空: {}", windfarm, realtimeDO);
            return Result.buildResult(Result.Status.ERROR, "风机编号为空");
        }
        // 最大风机编号缓存
        String maxIdKey = CacheConstant.getKey(CacheConstant.KEY_REAL_TIME, MAX_WT_ID, windfarm);
        Integer maxId = redisCacheService.getCommon(maxIdKey, Integer.class);
        if (null != maxId && windturbine.compareTo(maxId) > Constants.ZERO) {
            redisCacheService.putCommon(maxIdKey, Math.max(maxId, windturbine));
            log.info("风场-{} 最大风机编号更新为: {}", windfarm, Math.max(maxId, windturbine));
        }
        realtimeMapper.insert(realtimeDO);
        updateFeaCurve(realtimeDO);
        String wtStatusKey = CacheConstant.getWtStatusKey(windfarm, windturbine.toString());
        redisCacheService.putState(wtStatusKey, realtimeDO.getStatus());
        return Result.buildResult(Result.Status.SUCCESS);
    }

    /**
     * 更新特征曲线缓存：追加新点到 Redis 缓存队列
     */
    @Override
    public Integer updateFeaCurve(RealtimeDO realtimeDO) {
        String windfarm = realtimeDO.getWindfarm();
        Integer windturbine = realtimeDO.getWindturbine();
        if (windfarm == null || windturbine == null) return Constants.FAIL_INT;

        String wtSuffix = windfarm.concat(String.valueOf(windturbine));
        String feaCurveKey = CacheConstant.getKey(CacheConstant.KEY_REAL_TIME, FEA_CURVE, wtSuffix);
        FeaCurveBO feaCurve = redisCacheService.getCommon(feaCurveKey, FeaCurveBO.class);

        if (feaCurve == null) {
            Integer cap = Integer.parseInt(environment.getProperty("realtime.feacurve.capacity"));
            LinkedList<FeaPointDO> feaPoints = realtimeMapper.queryLastNRecord(windfarm, windturbine, cap);
            if (Constants.ZERO.equals(feaPoints.size())) return Constants.FAIL_INT;
            feaCurve = FeaCurveBO.builder()
                    .capacity(cap).windfarm(windfarm).windturbine(windturbine)
                    .feePoints(new LinkedList<>()).build();
            for (FeaPointDO point : feaPoints) feaCurve.addFeePoint(point);
        }

        FeaPointDO point = FeaPointDO.builder()
                .feature1(realtimeDO.getFeature1()).feature2(realtimeDO.getFeature2())
                .feature3(realtimeDO.getFeature3()).gmtReceived(realtimeDO.getGmtReceived()).build();
        if (Constants.FAIL_INT.equals(feaCurve.addFeePoint(point))) return Constants.FAIL_INT;
        redisCacheService.putCommon(feaCurveKey, feaCurve);
        return Constants.SUCCESS_INT;
    }

    /**
     * 获取特征曲线（前端大屏），优先 Redis 缓存
     */
    @Override
    public Result getFeaCurve(String windfarm, Integer windturbine) {
        if (windfarm == null || windturbine == null) return Result.buildResult(Result.Status.ERROR);

        String feaCurveKey = CacheConstant.getKey(CacheConstant.KEY_REAL_TIME, FEA_CURVE,
                windfarm.concat(String.valueOf(windturbine)));
        FeaCurveBO feaCurve = redisCacheService.getCommon(feaCurveKey, FeaCurveBO.class);

        if (feaCurve == null) {
            Integer cap = Integer.parseInt(environment.getProperty("realtime.feacurve.capacity"));
            LinkedList<FeaPointDO> feaPoints = realtimeMapper.queryLastNRecord(windfarm, windturbine, cap);
            if (Constants.ZERO.equals(feaPoints.size())) return Result.buildResult(Result.Status.NOT_FOUND);
            feaCurve = FeaCurveBO.builder()
                    .capacity(cap).windfarm(windfarm).windturbine(windturbine)
                    .feePoints(feaPoints).build();
            redisCacheService.putCommon(feaCurveKey, feaCurve);
        }
        return Result.buildResult(Result.Status.SUCCESS, feaCurve);
    }

    @Override
    public Integer getMaxWindturbineId(String windfarm) {
        String key = CacheConstant.getKey(CacheConstant.KEY_REAL_TIME, MAX_WT_ID, windfarm);
        Integer maxId = redisCacheService.getCommon(key, Integer.class);
        if (null == maxId) {
            maxId = realtimeMapper.searchMaxWindturbineId(windfarm);
            redisCacheService.putCommon(key, maxId);
            log.info("风场-{} 最大风机编号缓存: {}", windfarm, maxId);
        }
        return maxId;
    }

    @Override
    public Integer getWindturbineNum(String windfarm) { return null; }

    @Override
    public Result queryWindFarmLastRecord(String windfarm, Integer N) {
        return Result.buildResult(Result.Status.SUCCESS, "ok",
                realtimeMapper.queryWindFarmLastRecord(windfarm, N));
    }

    @Override
    public Result queryWindFarmLastRecordByStatus(String windfarm, Integer status, Integer N) {
        return Result.buildResult(Result.Status.SUCCESS, "ok",
                realtimeMapper.queryWindFarmLastRecordByStatus(windfarm, status, N));
    }

    @Override
    public Result queryByConditions(RealtimeQueryDTO query) {
        if (query.getLimit() == null || query.getLimit() <= 0) query.setLimit(50);
        return Result.buildResult(Result.Status.SUCCESS, "ok",
                realtimeMapper.queryByConditions(query));
    }

    @Override
    public Result queryLastNFromDB(String windfarm, Integer windturbine, Integer N) {
        if (N == null || N <= 0) N = 20;
        LinkedList<FeaPointDO> list = realtimeMapper.queryLastNRecord(windfarm, windturbine, N);
        FeaCurveBO curve = FeaCurveBO.builder()
                .capacity(N).windfarm(windfarm).windturbine(windturbine)
                .feePoints(new LinkedList<>()).build();
        if (list != null) for (FeaPointDO p : list) curve.addFeePoint(p);
        return Result.buildResult(Result.Status.SUCCESS, "ok", curve);
    }
}
