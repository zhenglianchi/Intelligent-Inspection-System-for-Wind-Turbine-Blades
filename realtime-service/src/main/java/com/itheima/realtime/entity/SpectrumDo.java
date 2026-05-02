package com.itheima.realtime.entity;

/**
 * 频谱数据实体
 * 用于保存FFT计算后的频谱分析结果
 *
 * @Author MH.Zhang
 * @Description 用于保存频谱计算的结果
 * @Migration migrated from wtb-health-monitor
 */
public class SpectrumDo {

    /**
     * 频率分辨率
     */
    private double frequencyResolution;

    /**
     * 保存振幅信息,长度是参与傅里叶变换的长度,不是采样点的长度
     */
    private double[] amplitude;

    public double getFrequencyResolution() {
        return frequencyResolution;
    }

    public void setFrequencyResolution(double frequencyResolution) {
        this.frequencyResolution = frequencyResolution;
    }

    public double[] getAmplitude() {
        return amplitude;
    }

    public void setAmplitude(double[] amplitude) {
        this.amplitude = amplitude;
    }
}
