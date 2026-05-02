package com.itheima.realtime.controller;

import com.itheima.consultant.common.Result;
import com.itheima.consultant.entity.RealtimeDO;
import com.itheima.realtime.entity.SpectrumDo;
import com.itheima.realtime.service.RealTimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * 实时监测API控制器
 * 提供风机实时监测数据的查询、特征曲线获取、频谱数据获取等接口
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 */
@CrossOrigin
@RestController
@RequestMapping("/realtime")
public class RealTimeMonitorController {

    @Autowired
    RealTimeService realTimeService;

    /**
     * 插入实时监测数据
     *
     * @param realtimeDO 实时数据对象
     * @return 操作结果
     */
    @PostMapping("/insertRealtimeData")
    public Result insertRealtimeData(@RequestBody RealtimeDO realtimeDO) {
        Result result = realTimeService.insertRealtimeData(realtimeDO);
        return result;
    }

    /**
     * 查询最新特征曲线
     *
     * @param windfarm    风场名称
     * @param windturbine 风机编号
     * @return 特征曲线数据
     */
    @GetMapping("/quaryLatestFeaCurve")
    public Result queryLatestFeaCurve(String windfarm, Integer windturbine) {
        Result result = realTimeService.getFeaCurve(windfarm, windturbine);
        return result;
    }

    /**
     * 获取最新滤波后wav文件的绝对地址
     *
     * @return wav文件路径
     * @throws IOException IO异常
     */
    @GetMapping("/getLatestWavAbsolutePath")
    public Result getLatestFileAbsolutePath() throws IOException {
        Result latestWavAbsolutePath = realTimeService.getLatestWavAbsolutePath();
        return latestWavAbsolutePath;
    }

    /**
     * 获取最新检测文件的[频率分辨率,频谱数据]
     *
     * @return 频谱数据
     */
    @GetMapping("/getLastFileSpectrum")
    public Result getLastFileSpectrum() {
        Result lastFileSpectrum = realTimeService.getLastFileSpectrum();
        return lastFileSpectrum;
    }

    /**
     * 获取最新滤波后txt文件绝对地址
     *
     * @return txt文件路径
     * @throws IOException IO异常
     */
    @GetMapping("/getLastTxtAbsolutePath")
    public Result getLastTxtAbsolutePath() throws IOException {
        Result latestTxtAbsolutePath = realTimeService.getLatestTxtAbsolutePath();
        return latestTxtAbsolutePath;
    }

    /**
     * 获取最新原始数据txt文件绝对地址
     *
     * @return txt文件路径
     */
    @GetMapping("/getLatestOrigTxtAbsolutePath")
    public Result getLatestOrigTxtAbsolutePath(){
        Result latestOrigTxtAbsolutePath = realTimeService.getLatestOrigTxtAbsolutePath();
        return latestOrigTxtAbsolutePath;
    }

    /**
     * 获取某风机最新原始数据（最多20万点）
     *
     * @param windturbine 风机编号
     * @return 原始数据数组
     */
    @GetMapping("/getLatestOrigTxtData")
    public Result getLatestOrigTxtData(String windturbine){
        Double[] latestOrigTxtData = realTimeService.getLatestOrigTxtData(windturbine);

        if (Objects.isNull(latestOrigTxtData) || latestOrigTxtData.length == 0){
            return Result.buildResult(Result.Status.NOT_FOUND);
        }

        return Result.buildResult(Result.Status.SUCCESS,"ok",latestOrigTxtData);
    }

    /**
     * 获取某风机最新滤波后数据（最多20万点）
     *
     * @param windturbine 风机编号
     * @return 滤波后数据数组
     */
    @GetMapping("/getLatestTxtDataAfterFiltering")
    public Result getLatestTxtDataAfterFiltering(String windturbine){
        Double[] latestTxtDataAfterFiltering = realTimeService.getLatestTxtDataAfterFiltering(windturbine);

        if (Objects.isNull(latestTxtDataAfterFiltering) || latestTxtDataAfterFiltering.length == 0){
            return Result.buildResult(Result.Status.NOT_FOUND);
        }

        return Result.buildResult(Result.Status.SUCCESS,"ok",latestTxtDataAfterFiltering);
    }

    /**
     * 获取某风机最新频谱数据
     *
     * @param windturbine 风机编号
     * @return 频谱数据对象（包含幅度数组和频率分辨率）
     */
    @GetMapping("/getLatestTxtSpectrumData")
    public Result getLatestTxtSpectrumData(String windturbine){
        SpectrumDo latestTxtSpectrum = realTimeService.getLatestTxtSpectrumData(windturbine);

        if (Objects.isNull(latestTxtSpectrum) || Objects.isNull(latestTxtSpectrum.getAmplitude()) || latestTxtSpectrum.getAmplitude().length == 0){
            return Result.buildResult(Result.Status.NOT_FOUND);
        }

        return Result.buildResult(Result.Status.SUCCESS, "ok", latestTxtSpectrum);
    }

    /**
     * 获取某风机分段频谱数据
     *
     * @param windturbine 风机编号
     * @return 分段频谱数据列表
     */
    @GetMapping("/getLatestTxtSpectrumCollection")
    public Result getLatestTxtSpectrumCollection(String windturbine){
        List<double[]> latestTxtSpectrumCollection = realTimeService.getLatestTxtSpectrumCollection(windturbine);

        if (Objects.isNull(latestTxtSpectrumCollection) || latestTxtSpectrumCollection.size() == 0){
            return Result.buildResult(Result.Status.NOT_FOUND);
        }

        return Result.buildResult(Result.Status.SUCCESS, "ok", latestTxtSpectrumCollection);
    }

    /**
     * 获取某风场所有风机的最新N条实时数据
     *
     * @param windfarm 风场名称
     * @param N        返回条数
     * @return 实时数据列表
     */
    @GetMapping("/queryWindFarmLastRecord")
    public Result queryWindFarmLastRecord(String windfarm, Integer N){
        Result result = realTimeService.queryWindFarmLastRecord(windfarm, N);
        return result;
    }

    /**
     * 获取某风场（某状态下）所有风机的最新N条实时数据
     *
     * @param windfarm 风场名称
     * @param status   风机状态（0-正常 1-故障 9-未连接）
     * @param N        返回条数
     * @return 实时数据列表
     */
    @GetMapping("queryWindFarmLastRecordByStatus")
    public Result queryWindFarmLastRecordByStatus(String windfarm, Integer status, Integer N){
        Result result = realTimeService.queryWindFarmLastRecordByStatus(windfarm, status, N);
        return result;
    }

    /**
     * 多条件灵活查询实时数据
     * 所有参数可选，LLM 可按用户意图组合任意条件
     */
    @GetMapping("/query")
    public Result queryByConditions(
            @RequestParam(required = false) String windfarm,
            @RequestParam(required = false) Integer windturbine,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false, defaultValue = "50") Integer limit
    ) {
        com.itheima.consultant.dto.RealtimeQueryDTO query = new com.itheima.consultant.dto.RealtimeQueryDTO();
        query.setWindfarm(windfarm);
        query.setWindturbine(windturbine);
        query.setStatus(status);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setLimit(limit);
        return realTimeService.queryByConditions(query);
    }

}
