package com.itheima.realtime.utils;

import java.util.Collection;
import java.util.Map;

/**
 * 通用工具类
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 */
public class CommonUtil {

    /**
     * 判断对象是否为空
     *
     * @param inputPara 输入对象
     * @return 为空返回true，不为空返回false
     */
    public static boolean isEmpty(Object inputPara) {
        if (inputPara == null) {
            return true;
        }
        if ((inputPara instanceof String)) {
            return ((String) inputPara).trim().isEmpty();
        } else if (inputPara instanceof Map) {
            return ((Map) inputPara).isEmpty();
        } else if (inputPara instanceof Object[]) {
            Object[] object = (Object[]) inputPara;
            if (object.length == 0) {
                return true;
            }
        } else if (inputPara instanceof Collection) {
            return ((Collection) inputPara).isEmpty();
        } else if (inputPara instanceof CharSequence) {
            return ((CharSequence) inputPara).length() == 0;
        }
        return false;
    }

    /**
     * 对Double数组降采样，按比例抽取
     *
     * @param orgData 原始数据
     * @param ratio   采样比率（原始长度/ratio = 采样后长度）
     * @return 降采样后数组
     */
    public static Double[] downsample(Double[] orgData, int ratio) {
        int len = orgData.length / ratio;
        Double[] sampleData = new Double[len];
        int pointer = 0;
        int step = ratio;
        for (int i = 0; i < len; i++) {
            sampleData[i] = orgData[pointer];
            pointer += step;
        }
        return sampleData;
    }

    /**
     * 对double数组降采样，按比例抽取
     *
     * @param orgData 原始数据
     * @param ratio   采样比率
     * @return 降采样后数组
     */
    public static double[] downsample(double[] orgData, int ratio) {
        int len = orgData.length / ratio;
        double[] sampleData = new double[len];
        int pointer = 0;
        int step = ratio;
        for (int i = 0; i < len; i++) {
            sampleData[i] = orgData[pointer];
            pointer += step;
        }
        return sampleData;
    }
}
