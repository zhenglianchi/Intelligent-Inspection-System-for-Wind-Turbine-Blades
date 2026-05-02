package com.itheima.realtime.controller;

import com.itheima.consultant.common.Result;
import com.itheima.realtime.service.WindturbineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 风机信息API控制器
 * 提供风机状态查询等接口
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 */
@CrossOrigin
@RestController
@RequestMapping("/windturbine")
public class WindturbineController {

    @Autowired
    WindturbineService windturbineService;

    /**
     * 查询数据库所有风机的健康状态
     * 状态定义：0-正常 1-故障 9-未连接
     *
     * @param windfarm 风场名称
     * @return Map<风机编号, 状态码>
     */
    @GetMapping("/queryAllWindturbineStatus")
    public Result queryAllWindturbineStatus(@RequestParam("windfarm") String windfarm) {
        Map<String, Integer> stringObjectMap = windturbineService.queryAllStatContainsUnconnected(windfarm);
        return Result.buildResult(Result.Status.SUCCESS, stringObjectMap);
    }
}
