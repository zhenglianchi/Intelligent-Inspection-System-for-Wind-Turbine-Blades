package com.itheima.realtime.controller;

import com.itheima.consultant.common.Result;
import com.itheima.consultant.entity.RealtimeDO;
import com.itheima.realtime.service.RealTimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * MQTT 数据接收 + 前端曲线查询控制器
 */
@CrossOrigin
@RestController
@RequestMapping("/realtime")
public class RealTimeMonitorController {

    @Autowired
    RealTimeService realTimeService;

    /** MQTT 板端数据写入 */
    @PostMapping("/insertRealtimeData")
    public Result insertRealtimeData(@RequestBody RealtimeDO realtimeDO) {
        return realTimeService.insertRealtimeData(realtimeDO);
    }

    /** 查询最新特征曲线（前端大屏） */
    @GetMapping("/quaryLatestFeaCurve")
    public Result queryLatestFeaCurve(String windfarm, Integer windturbine) {
        return realTimeService.getFeaCurve(windfarm, windturbine);
    }

    /** 某风场所有风机最新N条数据 */
    @GetMapping("/queryWindFarmLastRecord")
    public Result queryWindFarmLastRecord(String windfarm, Integer N) {
        return realTimeService.queryWindFarmLastRecord(windfarm, N);
    }

    /** 某风场按状态过滤的最新N条数据 */
    @GetMapping("queryWindFarmLastRecordByStatus")
    public Result queryWindFarmLastRecordByStatus(String windfarm, Integer status, Integer N) {
        return realTimeService.queryWindFarmLastRecordByStatus(windfarm, status, N);
    }

    /** 多条件灵活查询（LLM Agent 调用） */
    @GetMapping("/query")
    public Result queryByConditions(
            @RequestParam(required = false) String windfarm,
            @RequestParam(required = false) Integer windturbine,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        com.itheima.consultant.dto.RealtimeQueryDTO query = new com.itheima.consultant.dto.RealtimeQueryDTO();
        query.setWindfarm(windfarm);
        query.setWindturbine(windturbine);
        query.setStatus(status);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setLimit(limit);
        return realTimeService.queryByConditions(query);
    }

    /** MySQL 直查（性能基准对比） */
    @GetMapping("/queryLastNFromDB")
    public Result queryLastNFromDB(
            @RequestParam String windfarm,
            @RequestParam Integer windturbine,
            @RequestParam(defaultValue = "20") Integer N) {
        return realTimeService.queryLastNFromDB(windfarm, windturbine, N);
    }
}
