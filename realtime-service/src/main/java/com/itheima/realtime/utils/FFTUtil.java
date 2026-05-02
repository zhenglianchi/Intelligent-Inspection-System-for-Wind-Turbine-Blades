package com.itheima.realtime.utils;

import com.itheima.realtime.entity.MyComplex;
import com.itheima.realtime.entity.SpectrumDo;
import com.itheima.realtime.entity.TxtFileDo;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

/**
 * 快速傅里叶变换工具类
 * 用于对音频采样数据进行频谱分析
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 */
public class FFTUtil {

    /**
     * 计算频谱
     *
     * @param data         采样数据数组
     * @param Fs           采样率
     * @param needFillZero 是否需要补零到指定长度
     * @param flyLen       参与傅里叶变换的长度
     * @return 频谱计算结果
     */
    public static SpectrumDo caculateSpectrum(double[] data, double Fs, boolean needFillZero, int flyLen) {
        if (data == null || data.length == 0) {
            return null;
        }
        int dataLen = data.length;
        // 参与傅里叶变换的数组
        double[] partakeFLYArr;
        // 需要补零
        if (needFillZero) {
            if (flyLen < dataLen) {
                return null;
            }
            partakeFLYArr = new double[flyLen];
            System.arraycopy(data, 0, partakeFLYArr, 0, dataLen);
            for (int i = dataLen; i < flyLen; i++) {
                partakeFLYArr[i] = 0;
            }
        } else {
            partakeFLYArr = data;
            flyLen = dataLen;
        }
        // 使用Apache Commons Math提供的快速傅里叶变换
        FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);
        Complex[] complexArr = fft.transform(partakeFLYArr, TransformType.FORWARD);

        SpectrumDo spectrumBean = new SpectrumDo();
        double[] amplitude = new double[flyLen / 2];

        // 计算振幅，归一化处理
        Complex complex = complexArr[0];
        amplitude[0] = Math.sqrt(Math.abs(complex.getReal()) * Math.abs(complex.getReal())
                + Math.abs(complex.getImaginary()) * Math.abs(complex.getImaginary())) * 1.0 / dataLen;
        for (int i = 1; i < flyLen / 2; i++) {
            complex = complexArr[i];
            amplitude[i] = Math.sqrt(Math.abs(complex.getReal()) * Math.abs(complex.getReal())
                    + Math.abs(complex.getImaginary()) * Math.abs(complex.getImaginary())) * 2.0 / dataLen;
        }
        // 设置振幅
        spectrumBean.setAmplitude(amplitude);

        // 设置频率分辨率
        spectrumBean.setFrequencyResolution(Fs / flyLen);

        return spectrumBean;
    }

    /**
     * 将double数组转换为Complex数组
     *
     * @param data 输入double数组
     * @return Complex数组
     */
    public static Complex[] double2complex(Double[] data) {
        Complex[] complex = new Complex[data.length];
        // 必须初始化每个元素，否则会空指针
        for (int index = 0; index < complex.length; index++) {
            complex[index] = new Complex(0, 0);
        }
        for (int index = 0; index < complex.length; index++) {
            // 时域数据只有实部，虚部为0
            complex[index] = new Complex(data[index], 0);
        }
        return complex;
    }
}
