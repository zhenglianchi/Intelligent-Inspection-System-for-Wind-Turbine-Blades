package com.itheima.agent.feign;

import com.itheima.consultant.common.Result;
import com.itheima.consultant.entity.RealtimeDO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "realtime-service", contextId = "realtime", path = "/realtime")
public interface RealtimeFeignClient {

    @GetMapping("/queryWindFarmLastRecord")
    Result<List<RealtimeDO>> queryWindFarmLastRecord(@RequestParam("windfarm") String windfarm,
                                                      @RequestParam("N") Integer N);

    @GetMapping("/queryWindFarmLastRecordByStatus")
    Result<List<RealtimeDO>> queryWindFarmLastRecordByStatus(@RequestParam("windfarm") String windfarm,
                                                              @RequestParam("status") Integer status,
                                                              @RequestParam("N") Integer N);

    /**
     * 多条件灵活查询，所有参数可选
     */
    @GetMapping("/query")
    Result<List<RealtimeDO>> queryByConditions(
            @RequestParam(value = "windfarm", required = false) String windfarm,
            @RequestParam(value = "windturbine", required = false) Integer windturbine,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            @RequestParam(value = "limit", required = false, defaultValue = "50") Integer limit
    );
}
