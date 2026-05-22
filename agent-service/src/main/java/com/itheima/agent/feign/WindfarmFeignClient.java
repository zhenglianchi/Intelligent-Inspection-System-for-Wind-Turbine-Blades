package com.itheima.agent.feign;

import com.itheima.consultant.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

/**
 * 风场信息查询 Feign 客户端
 * 对应 realtime-service 的 WindfarmController
 */
@FeignClient(name = "realtime-service", contextId = "windfarmInfo", path = "")
public interface WindfarmFeignClient {

    /** 获取所有风场信息: windfarm(编号), name(中文名), turbineCount(风机数量) */
    @GetMapping("/windfarms")
    Result<List<Map<String, Object>>> listAllWindfarms();
}
