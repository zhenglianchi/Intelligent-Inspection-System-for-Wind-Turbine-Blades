package com.itheima.realtime.controller;

import com.itheima.consultant.common.Result;
import com.itheima.consultant.entity.WindfarmInfoDO;
import com.itheima.realtime.mapper.WindfarmMapper;
import com.itheima.realtime.service.RealTimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@CrossOrigin
@RestController
public class WindfarmController {

    @Autowired
    RealTimeService realTimeService;

    @Autowired
    WindfarmMapper windfarmMapper;

    @GetMapping("/searchMaxWindturbineId")
    public Result<Integer> searchMaxWindturbineId(@RequestParam("windfarm") String windfarm) {
        Integer maxId = realTimeService.getMaxWindturbineId(windfarm);
        if (maxId == null || maxId == 0) {
            return Result.buildResult(Result.Status.ERROR, "未找到风机数据");
        }
        return Result.buildResult(Result.Status.SUCCESS, "ok", maxId);
    }

    @GetMapping("/windfarms")
    public Result<List<Map<String, Object>>> listAllWindfarms() {
        List<WindfarmInfoDO> farms = windfarmMapper.selectList(null);
        List<Map<String, Object>> result = farms.stream().map(f -> {
            Map<String, Object> m = new HashMap<>();
            m.put("windfarm", f.getWindfarm());
            m.put("name", f.getName());
            m.put("turbineCount", f.getWindturbineCount());
            return m;
        }).collect(Collectors.toList());
        return Result.buildResult(Result.Status.SUCCESS, result);
    }
}
