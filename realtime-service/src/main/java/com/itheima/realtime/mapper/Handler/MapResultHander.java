package com.itheima.realtime.mapper.Handler;

import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义Map结果处理器
 * 用于将查询结果转换为Map<key, value>格式
 *
 * @Author MH.Zhang
 * @Description Map<String, Object>查询结果处理器
 * @Migration migrated from wtb-health-monitor
 */
@SuppressWarnings("all")
public class MapResultHander implements ResultHandler<Map<String, Object>> {

    private final Map<String, Object> mapResults = new HashMap<>();
    private final String key, val;

    @Override
    public void handleResult(ResultContext<? extends Map<String, Object>> resultContext) {
        Map map = (Map) resultContext.getResultObject();
        mapResults.put(map.get(key).toString(), map.get(val));
    }

    public MapResultHander(String key, String val) {
        this.key = key;
        this.val = val;
    }

    /**
     * 获取处理后的Map结果
     *
     * @return 转换后的Map
     */
    public Map getMapResults() {
        return mapResults;
    }
}
