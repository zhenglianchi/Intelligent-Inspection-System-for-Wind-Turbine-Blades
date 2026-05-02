package com.itheima.consultant.constant;

/**
 * 缓存Key常量
 * 统一管理缓存Key的生成规则
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 * @Description 缓存常量定义
 */
public class CacheConstant {

    /**
     * 业务相关Key前缀
     */
    public static final String KEY_REAL_TIME = "real_time";
    public static final String KEY_PLAY_BACK = "play_back";
    public static final String KEY_WIND_FARM = "wind_farm";
    public static final String KEY_WIND_TURBINE = "wind_turbine";

    /**
     * Key连接符
     */
    public static final String CONNECTOR = "_";

    /**
     * 生成风机最新文件缓存Key，按风机ID
     *
     * @param windturbine 风机编号
     * @return 缓存Key
     */
    public static String getLatestFileKeyById(String windturbine){
        return getKey(KEY_REAL_TIME, "wt" + windturbine, "latest_file");
    }

    /**
     * 生成风机状态缓存Key
     *
     * @param windfarm    风场编号
     * @param windturbine 风机编号
     * @return 缓存Key
     */
    public static String getWtStatusKey(String windfarm, String windturbine){
        return getKey(KEY_WIND_TURBINE, "wt_status", windfarm + CONNECTOR + windturbine);
    }

    /**
     * 组合三段缓存Key
     *
     * @param prefix 前缀
     * @param mid    中间部分
     * @param suffix 后缀
     * @return 组合后的Key
     */
    public static String getKey(String prefix, String mid, String suffix) {
        return new StringBuilder()
                .append(prefix).append(CONNECTOR)
                .append(mid).append(CONNECTOR)
                .append(suffix)
                .toString();
    }

    /**
     * 组合两段缓存Key
     *
     * @param prefix 前缀
     * @param suffix 后缀
     * @return 组合后的Key
     */
    public static String getKey(String prefix, String suffix) {
        return new StringBuilder()
                .append(prefix).append(CONNECTOR)
                .append(suffix)
                .toString();
    }
}
