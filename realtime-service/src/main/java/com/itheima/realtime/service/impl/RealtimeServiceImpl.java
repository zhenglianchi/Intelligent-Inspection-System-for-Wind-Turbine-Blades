package com.itheima.realtime.service.impl;

import com.itheima.realtime.bo.FeaCurveBO;
import com.itheima.consultant.common.Result;
import com.itheima.consultant.constant.CacheConstant;
import com.itheima.consultant.constant.Constants;
import com.itheima.consultant.dto.RealtimeQueryDTO;
import com.itheima.realtime.entity.FeaPointDO;
import com.itheima.consultant.entity.RealtimeDO;
import com.itheima.realtime.entity.SpectrumDo;
import com.itheima.realtime.entity.TxtFileDo;
import com.itheima.realtime.mapper.RealtimeMapper;
import com.itheima.realtime.service.RealTimeService;
import com.itheima.realtime.service.RedisCacheService;
import com.itheima.realtime.service.WindturbineService;
import com.itheima.realtime.utils.FFTUtil;
import com.itheima.realtime.utils.Txt2WavUtil;
import com.itheima.realtime.utils.TxtUtil;
import com.itheima.realtime.utils.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 实时监测数据服务实现类
 * 改造说明：原Caffeine缓存已改为RedisCacheService
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 * @Modified 改造缓存为Redis
 */
@Service
@Slf4j
public class RealtimeServiceImpl implements RealTimeService {

    private final static String MAX_WT_ID = "max_wt_id";
    private final static String FEA_CURVE = "fea_curve";
    private final static String LATEST_FILE = "latest_file";
    private final static String WT_STATUS = "wt_status";

    @Autowired
    RealtimeMapper realtimeMapper;

    @Autowired
    WindturbineService windturbineService;

    @Autowired
    RedisCacheService redisCacheService;

    @Autowired
    Environment environment;

    @Value("${realtime.dataLength}")
    Integer dataLength;

    @Value("${realtime.downsample.ratio}")
    Integer downsampleRatio;

    @Value("${realtime.downsample.open}")
    Boolean downsampleOpenFlag;

    @Value("${realtime.spectrum.segment.num}")
    Integer segmentNum;

    @Value("${realtime.spectrum.downsample.open}")
    Boolean specDownsampleOpenFlag;

    @Value("${realtime.spectrum.downsample.ratio}")
    Integer specDownsampleRatio;

    @Value("${realtime.spectrum.out.downsample.ratio}")
    Integer specOutDownsampleRatio;

    @Value("${realtime.log.open}")
    Boolean logOpen;


    /**
     * 插入新的实时监测数据
     * 同时更新缓存中的最大风机编号、特征曲线、风机状态
     *
     * @param realtimeDO 实时数据对象
     * @return 操作结果
     */
    @Override
    public Result insertRealtimeData(RealtimeDO realtimeDO) {

        // 数据判空校验
        if (null == realtimeDO || Objects.isNull(realtimeDO.getStatus())) {
            log.error("实时数据为空,详细信息为realtimeDO={}", realtimeDO);
            return Result.buildResult(Result.Status.ERROR);
        }

        String windfarm = realtimeDO.getWindfarm();
        Integer windturbine = realtimeDO.getWindturbine();
        String windturbineStr = windturbine.toString();

        // 编号校验：风场和风机编号不能为空
        if (Objects.isNull(windturbine) || Objects.isNull(windfarm)) {
            log.error("风场-{}存在风机编号为空实时数据,详细信息为{}", windfarm, realtimeDO);
            return Result.buildResult(Result.Status.ERROR, "风机编号为空");
        }

        // 最大风机编号缓存处理：如果新风机编号大于缓存中最大值，更新缓存
        String maxWindturbineIdKey = CacheConstant.getKey(CacheConstant.KEY_REAL_TIME, MAX_WT_ID, windfarm);
        Integer maxId = redisCacheService.getCommon(maxWindturbineIdKey, Integer.class);

        if (null != maxId && windturbine.compareTo(maxId) > Constants.ZERO) {
            Integer updateId = Math.max(maxId, windturbine);
            redisCacheService.putCommon(maxWindturbineIdKey, updateId);
            log.info("风场-{}的最大风机编号缓存更新为: {}", windfarm, updateId);
        }

        // 插入数据库
        realtimeMapper.insert(realtimeDO);

        // 更新特征曲线缓存
        updateFeaCurve(realtimeDO);

        // 更新风机状态缓存（20秒过期）
        String wtStatusKey = CacheConstant.getWtStatusKey(windfarm, windturbineStr);
        redisCacheService.putState(wtStatusKey, realtimeDO.getStatus());

        return Result.buildResult(Result.Status.SUCCESS);
    }

    /**
     * 更新特征曲线缓存：添加新的特征点，如果队列满了自动淘汰最早的点
     *
     * @param realtimeDO 实时数据
     * @return 成功返回1，失败返回0
     */
    @Override
    public Integer updateFeaCurve(RealtimeDO realtimeDO) {

        String windfarm = realtimeDO.getWindfarm();
        Integer windturbine = realtimeDO.getWindturbine();

        // 异常处理：风场或风机编号为空
        if (windfarm == null || windturbine == null) {
            return Constants.FAIL_INT;
        }

        // 从Redis缓存获取特征曲线
        String wtSuffix = windfarm.concat(String.valueOf(windturbine));
        String feaCurveKey = CacheConstant.getKey(CacheConstant.KEY_REAL_TIME, FEA_CURVE, wtSuffix);
        FeaCurveBO feaCurve = redisCacheService.getCommon(feaCurveKey, FeaCurveBO.class);

        // 缓存为空，从数据库加载最近N条记录构建缓存
        if (feaCurve == null) {
            Integer cap = Integer.parseInt(environment.getProperty("realtime.feacurve.capacity"));
            LinkedList<FeaPointDO> feaPoints = realtimeMapper.queryLastNRecord(windfarm, windturbine, cap);

            if (Constants.ZERO.equals(feaPoints.size())) {
                return Constants.FAIL_INT;
            }

            // 构造特征曲线对象
            feaCurve = FeaCurveBO.builder()
                    .capacity(cap)
                    .windfarm(windfarm)
                    .windturbine(realtimeDO.getWindturbine())
                    .feePoints(new LinkedList<>())
                    .build();

            for (FeaPointDO point : feaPoints) {
                feaCurve.addFeePoint(point);
            }
        }

        // 添加新特征点到曲线
        FeaPointDO feaPointDO = FeaPointDO.builder()
                .feature1(realtimeDO.getFeature1())
                .feature2(realtimeDO.getFeature2())
                .feature3(realtimeDO.getFeature3())
                .gmtReceived(realtimeDO.getGmtReceived()).build();
        Integer addRes = feaCurve.addFeePoint(feaPointDO);

        if (addRes.equals(Constants.FAIL_INT)) {
            return Constants.FAIL_INT;
        }

        // 更新缓存
        redisCacheService.putCommon(feaCurveKey, feaCurve);

        return Constants.SUCCESS_INT;
    }

    /**
     * 获取风机的特征曲线数据（优先从缓存获取，缓存为空从数据库加载）
     *
     * @param windfarm    风场编号
     * @param windturbine 风机编号
     * @return 特征曲线结果
     */
    @Override
    public Result getFeaCurve(String windfarm, Integer windturbine) {

        // 异常处理
        if (windfarm == null || windturbine == null) {
            return Result.buildResult(Result.Status.ERROR);
        }

        // 从Redis缓存获取
        String wtSuffix = windfarm.concat(String.valueOf(windturbine));
        String feaCurveKey = CacheConstant.getKey(CacheConstant.KEY_REAL_TIME, FEA_CURVE, wtSuffix);
        FeaCurveBO feaCurve = redisCacheService.getCommon(feaCurveKey, FeaCurveBO.class);

        if (feaCurve == null) {
            // 缓存为空，从数据库加载
            Integer cap = Integer.parseInt(environment.getProperty("realtime.feacurve.capacity"));
            LinkedList<FeaPointDO> feaPoints = realtimeMapper.queryLastNRecord(windfarm, windturbine, cap);

            if (Constants.ZERO.equals(feaPoints.size())) {
                return Result.buildResult(Result.Status.NOT_FOUND);
            }

            // 构建特征曲线
            feaCurve = FeaCurveBO.builder()
                    .capacity(cap)
                    .windfarm(windfarm)
                    .windturbine(windturbine)
                    .feePoints(feaPoints)
                    .build();

            // 写入缓存
            redisCacheService.putCommon(feaCurveKey, feaCurve);

            return Result.buildResult(Result.Status.SUCCESS, feaCurve);
        }

        return Result.buildResult(Result.Status.SUCCESS, feaCurve);
    }

    /**
     * 获取指定风场的最大风机编号（优先从缓存获取）
     *
     * @param windfarm 风场编号
     * @return 最大风机编号
     */
    @Override
    public Integer getMaxWindturbineId(String windfarm) {
        // 组合缓存key
        String maxWindturbineIdKey = CacheConstant.getKey(CacheConstant.KEY_REAL_TIME, MAX_WT_ID, windfarm);
        Integer maxId = redisCacheService.getCommon(maxWindturbineIdKey, Integer.class);

        if (null == maxId) {
            // 缓存为空，从数据库查询并写入缓存
            maxId = realtimeMapper.searchMaxWindturbineId(windfarm);
            redisCacheService.putCommon(maxWindturbineIdKey, maxId);
            log.info("风场-{}的最大风机编号缓存更新为: {}", windfarm, maxId);
        }

        return maxId;
    }

    /**
     * 获取当前风场监测风机数量（占位，未实现）
     *
     * @param windfarm 风场编号
     * @return null
     */
    @Override
    public Integer getWindturbineNum(String windfarm) {
        return null;
    }

    /**
     * 获取最新文件绝对路径（内部方法）
     *
     * @return 文件名和路径Pair
     */
    private Pair<String, String> getLatestFileAbsolutePath() {
        String latestFileKey = CacheConstant.getKey(CacheConstant.KEY_REAL_TIME, LATEST_FILE);
        Pair<String, String> fileNameAndPathPair = redisCacheService.getCommon(latestFileKey, Pair.class);
        return fileNameAndPathPair;
    }

    /**
     * 获取最新文件绝对路径（根据风机编号）
     *
     * @param windturbine 风机编号
     * @return 文件名和路径Pair
     */
    private Pair<String, String> getLatestFileAbsolutePath(String windturbine) {
        String latestFileKey = CacheConstant.getLatestFileKeyById(windturbine);
        return redisCacheService.getCommon(latestFileKey, Pair.class);
    }

    /**
     * 获取最新wav文件的绝对路径（已经高通滤波处理）
     *
     * @return 操作结果，包含wav文件路径
     * @throws IOException IO异常
     */
    @Override
    public Result getLatestWavAbsolutePath() throws IOException {

        Pair<String, String> fileNameAndPathPair = getLatestFileAbsolutePath();

        // pair校验
        if (fileNameAndPathPair == null) {
            return Result.buildResult(Result.Status.NOT_FOUND, "数据为空");
        }

        String latestFileAbsolutePath = fileNameAndPathPair.getRight();
        // 路径校验
        if (latestFileAbsolutePath == null || latestFileAbsolutePath.length() == Constants.ZERO) {
            return Result.buildResult(Result.Status.NOT_FOUND, "数据为空");
        }

        TxtFileDo txtFileDo = TxtUtil.readTxtByPath(latestFileAbsolutePath);
        Double[] txtData = txtFileDo.getData();

        // 数据校验
        if (txtData == null || txtData.length == Constants.ZERO) {
            return Result.buildResult(Result.Status.INTERNAL_SERVER_ERROR, "服务器数据处理异常");
        }

        TxtFileDo highpassDo = TxtUtil.highpass(txtData);

        // 文件名校验
        String fullWavName = fileNameAndPathPair.getLeft();
        if (fullWavName == null || fullWavName.length() == 0) {
            return Result.buildResult(Result.Status.INTERNAL_SERVER_ERROR, "服务器数据处理异常");
        }
        String wavName = fullWavName.substring(0, fullWavName.length() - 4);
        Integer resFlag = Txt2WavUtil.doubleDataToWavFile(highpassDo.getData(), wavName, highpassDo.getMax(), highpassDo.getMin());

        // 文件转换校验
        if (Constants.FAIL_INT.equals(resFlag)) {
            return Result.buildResult(Result.Status.INTERNAL_SERVER_ERROR);
        }

        // 路径处理：拼接项目路径，转换为Unix风格路径
        String projectAbsolutePath = System.getProperty("user.dir");
        StringBuilder wavFileAbsolutePathAddr = new StringBuilder();
        String wavFileAbsolutePath = wavFileAbsolutePathAddr.append(projectAbsolutePath)
                .append("/wavFile/")
                .append(wavName)
                .append(".wav")
                .toString();

        return Result.buildResult(Result.Status.SUCCESS, "ok", wavFileAbsolutePath);
    }

    /**
     * 获取最新txt文件的绝对路径（已经高通滤波处理）
     *
     * @return 操作结果，包含txt文件路径
     * @throws IOException IO异常
     */
    @Override
    public Result getLatestTxtAbsolutePath() throws IOException {
        Pair<String, String> fileNameAndPathPair = getLatestFileAbsolutePath();

        // pair校验
        if (fileNameAndPathPair == null) {
            return Result.buildResult(Result.Status.NOT_FOUND, "数据为空");
        }

        String latestFileAbsolutePath = fileNameAndPathPair.getRight();
        // 路径校验
        if (latestFileAbsolutePath == null || latestFileAbsolutePath.length() == Constants.ZERO) {
            return Result.buildResult(Result.Status.NOT_FOUND, "数据为空");
        }

        TxtFileDo txtFileDo = TxtUtil.readTxtByPath(latestFileAbsolutePath);
        Double[] txtData = txtFileDo.getData();

        // 数据校验
        if (txtData == null || txtData.length == Constants.ZERO) {
            return Result.buildResult(Result.Status.INTERNAL_SERVER_ERROR, "服务器数据处理异常");
        }

        TxtFileDo highpassDo = TxtUtil.highpass(txtData);
        Double[] highpassData = highpassDo.getData();

        String orgFileName = fileNameAndPathPair.getLeft();
        String projectAbsolutePath = System.getProperty("user.dir");
        StringBuilder txtFilePathBuilder = new StringBuilder();
        String txtAbsoluteFilePath = txtFilePathBuilder
                .append(projectAbsolutePath)
                .append("/wavFile/")
                .append(orgFileName, 0, orgFileName.length() - 4)
                .append("_after_highpass.txt")
                .toString();

        TxtUtil.writeTxtFile(txtAbsoluteFilePath, highpassData);

        return Result.buildResult(Result.Status.SUCCESS, "ok", txtAbsoluteFilePath);
    }

    /**
     * 获取最新txt文件的绝对路径（原始数据，无滤波）
     *
     * @return 操作结果，包含txt文件路径
     */
    @Override
    public Result getLatestOrigTxtAbsolutePath() {
        Pair<String, String> fileNameAndPathPair = getLatestFileAbsolutePath();

        // pair校验
        if (fileNameAndPathPair == null) {
            return Result.buildResult(Result.Status.NOT_FOUND, "数据为空");
        }

        String latestFileAbsolutePath = fileNameAndPathPair.getRight();
        // 路径校验
        if (latestFileAbsolutePath == null || latestFileAbsolutePath.length() == Constants.ZERO) {
            return Result.buildResult(Result.Status.NOT_FOUND, "数据为空");
        }

        String formatPath = latestFileAbsolutePath.replace("\\", "/");
        return Result.buildResult(Result.Status.SUCCESS, "ok", formatPath);
    }

    /**
     * 获取最新文件的频谱分析数据（滤波后）
     *
     * @return 频谱数据结果
     */
    @Override
    public Result getLastFileSpectrum() {

        Pair<String, String> fileNameAndPathPair = getLatestFileAbsolutePath();

        // pair校验
        if (fileNameAndPathPair == null) {
            return Result.buildResult(Result.Status.NOT_FOUND, "数据为空");
        }

        String latestFileAbsolutePath = fileNameAndPathPair.getRight();
        // 路径校验
        if (latestFileAbsolutePath == null || latestFileAbsolutePath.length() == Constants.ZERO) {
            return Result.buildResult(Result.Status.NOT_FOUND, "数据为空");
        }

        TxtFileDo txtFileDo = TxtUtil.readTxtByPath(latestFileAbsolutePath);
        double fs = 44100.0;
        Double[] orgData = txtFileDo.getData();
        Double[] data = TxtUtil.highpass(orgData).getData();

        int N = 2;
        while (N < data.length) {
            N *= 2;
        }

        double[] doubles = Stream.of(data).mapToDouble(Double::doubleValue).toArray();
        SpectrumDo spectrumDo = FFTUtil.caculateSpectrum(doubles, fs, true, N);

        return Result.buildResult(Result.Status.SUCCESS, "ok", spectrumDo);
    }

    /**
     * 查询风场最新N条风机实时数据记录
     *
     * @param windfarm 风场编号
     * @param N        查询记录条数
     * @return 实时数据列表结果
     */
    @Override
    public Result queryWindFarmLastRecord(String windfarm, Integer N) {
        List<RealtimeDO> list = realtimeMapper.queryWindFarmLastRecord(windfarm, N);
        return Result.buildResult(Result.Status.SUCCESS, "ok", list);
    }

    /**
     * 查询风场指定状态下最新N条风机实时数据记录
     *
     * @param windfarm 风场编号
     * @param status   风机状态
     * @param N        查询记录条数
     * @return 实时数据列表结果
     */
    @Override
    public Result queryWindFarmLastRecordByStatus(String windfarm, Integer status, Integer N) {
        List<RealtimeDO> list = realtimeMapper.queryWindFarmLastRecordByStatus(windfarm, status, N);
        return Result.buildResult(Result.Status.SUCCESS, "ok", list);
    }

    /**
     * 多条件灵活查询实时数据
     * 所有条件均为可选，MyBatis 动态 SQL 自动组合 WHERE 子句
     */
    @Override
    public Result queryByConditions(RealtimeQueryDTO query) {
        if (query.getLimit() == null || query.getLimit() <= 0) {
            query.setLimit(50);
        }
        List<RealtimeDO> list = realtimeMapper.queryByConditions(query);
        return Result.buildResult(Result.Status.SUCCESS, "ok", list);
    }

    @Override
    public Result queryLastNFromDB(String windfarm, Integer windturbine, Integer N) {
        if (N == null || N <= 0) N = 20;
        // 构建与 Redis 版完全相同的 FeaCurveBO，保证对比公平
        LinkedList<FeaPointDO> list = realtimeMapper.queryLastNRecord(windfarm, windturbine, N);
        FeaCurveBO curve = FeaCurveBO.builder()
                .capacity(N)
                .windfarm(windfarm)
                .windturbine(windturbine)
                .feePoints(new java.util.LinkedList<>())
                .build();
        if (list != null) {
            for (FeaPointDO p : list) curve.addFeePoint(p);
        }
        return Result.buildResult(Result.Status.SUCCESS, "ok", curve);
    }

    /**
     * 获取指定风机最新原始txt文件数据（最多20万点，支持降采样）
     *
     * @param windturbine 风机编号
     * @return 采样数据数组
     */
    @Override
    public Double[] getLatestOrigTxtData(String windturbine) {
        Pair<String, String> latestFileAbsolutePath = getLatestFileAbsolutePath(windturbine);
        if (Objects.isNull(latestFileAbsolutePath)) {
            return new Double[0];
        }
        String absolutePath = latestFileAbsolutePath.getRight();
        TxtFileDo txtFileDo = TxtUtil.readTxtByPath(absolutePath);
        Double[] origData = Arrays.copyOfRange(txtFileDo.getData(), 0, dataLength);
        Double[] res = origData;
        if (downsampleOpenFlag) {
            res = CommonUtil.downsample(origData, downsampleRatio);
        }

        return res;
    }

    /**
     * 获取指定风机最新滤波后txt文件数据（最多20万点，支持降采样）
     *
     * @param windturbine 风机编号
     * @return 采样数据数组
     */
    @Override
    public Double[] getLatestTxtDataAfterFiltering(String windturbine) {

        Pair<String, String> latestFileAbsolutePath = getLatestFileAbsolutePath(windturbine);
        if (Objects.isNull(latestFileAbsolutePath)) {
            return new Double[0];
        }
        String absolutePath = latestFileAbsolutePath.getRight();
        TxtFileDo txtFileDo = TxtUtil.readTxtByPath(absolutePath);
        Double[] origData = Arrays.copyOfRange(txtFileDo.getData(), 0, dataLength);
        TxtFileDo highpass = TxtUtil.highpass(origData);

        Double[] resData = highpass.getData();

        // 降采样
        if (downsampleOpenFlag) {
            resData = CommonUtil.downsample(highpass.getData(), downsampleRatio);
        }

        return resData;
    }

    /**
     * 获取指定风机最新txt文件的频谱数据
     *
     * @param windturbine 风机编号
     * @return 频谱数据对象
     */
    @Override
    public SpectrumDo getLatestTxtSpectrumData(String windturbine) {
        Pair<String, String> latestFileAbsolutePath = getLatestFileAbsolutePath(windturbine);
        if (Objects.isNull(latestFileAbsolutePath)) {
            return null;
        }
        String absolutePath = latestFileAbsolutePath.getRight();
        TxtFileDo txtFileDo = TxtUtil.readTxtByPath(absolutePath);
        Double[] origData = Arrays.copyOfRange(txtFileDo.getData(), 0, dataLength);
        TxtFileDo highpass = TxtUtil.highpass(origData);
        Double[] data = highpass.getData();
        double[] doubles = Stream.of(data).mapToDouble(Double::doubleValue).toArray();
        int N = 2;
        while (N < doubles.length) {
            N *= 2;
        }
        SpectrumDo spectrumDo = FFTUtil.caculateSpectrum(doubles, 44100.0, true, N);
        return spectrumDo;
    }

    /**
     * 获取分段频谱集合（分为指定段数，用于时频分析）
     *
     * @param windturbine 风机编号
     * @return 每段频谱振幅数组的列表
     */
    @Override
    public List<double[]> getLatestTxtSpectrumCollection(String windturbine) {
        Pair<String, String> latestFileAbsolutePath = getLatestFileAbsolutePath(windturbine);
        if (Objects.isNull(latestFileAbsolutePath)) {
            return null;
        }
        String absolutePath = latestFileAbsolutePath.getRight();
        TxtFileDo txtFileDo = TxtUtil.readTxtByPath(absolutePath);
        Double[] origData = Arrays.copyOfRange(txtFileDo.getData(), 0, dataLength);
        TxtFileDo highpass = TxtUtil.highpass(origData);
        Double[] data = highpass.getData();

        // 降采样
        if (specDownsampleOpenFlag) {
            data = CommonUtil.downsample(data, specDownsampleRatio);
        }

        // log处理：转换为分贝单位
        if (logOpen) {
            data = Arrays.stream(data).map(org -> 20 * Math.log(Math.abs(org / 10.0) + 10e-2) * (org >= 0 ? 1 : -1)).toArray(Double[]::new);
        }

        double[] initData = Stream.of(data).mapToDouble(Double::doubleValue).toArray();

        // 分段计算频谱
        List<double[]> res = new ArrayList<>();
        int segLen = initData.length / segmentNum;
        int N = 2;
        while (N < segLen) {
            N *= 2;
        }

        Double Fs = 44100.0 / downsampleRatio;
        for (int i = 0; i < initData.length; i = i + segLen) {
            double[] cur = Arrays.copyOfRange(initData, i, i + segLen);
            SpectrumDo spectrumDo = FFTUtil.caculateSpectrum(cur, Fs, true, N);
            double[] amplitude = spectrumDo.getAmplitude();
            if (specOutDownsampleRatio != null && specOutDownsampleRatio > 1) {
                amplitude = CommonUtil.downsample(spectrumDo.getAmplitude(), specOutDownsampleRatio);
            }
            res.add(amplitude);
        }
        return res;
    }
}
