package com.itheima.agent.feign;

import com.itheima.consultant.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "realtime-service", contextId = "windturbine", path = "/windturbine")
public interface WindturbineFeignClient {

    @GetMapping("/queryAllWindturbineStatus")
    Result<Map<String, Integer>> queryAllWindturbineStatus(@RequestParam("windfarm") String windfarm);
}
