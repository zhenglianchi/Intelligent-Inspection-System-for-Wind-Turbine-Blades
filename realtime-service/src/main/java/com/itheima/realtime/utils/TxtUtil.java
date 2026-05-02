package com.itheima.realtime.utils;

import com.itheima.realtime.entity.TxtFileDo;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * TXT文件读写和滤波工具类
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 */
@Slf4j
public class TxtUtil {
    // 高通滤波器系数 1kHz 截止频率，采样率44100
    private static final Double[] AUDHA = {1.0, -1.7991, 0.8175};
    private static final Double[] AUDB = {0.9042, -1.8083, 0.9042};

    private static Double[] in;
    private static Double[] out;
    /**
     * 滤波输出数据
     */
    private static Double[] outData;

    /**
     * 高通滤波
     * 使用IIR滤波器，截止频率1kHz
     *
     * @param signal 输入信号
     * @return 滤波结果
     */
    public static TxtFileDo highpass(Double[] signal) {
        return filter(signal, AUDHA, AUDB);
    }

    /**
     * 从文件路径读取TXT文件，每行一个采样值
     *
     * @param filePath 文件路径
     * @return 读取结果，包含数据数组、最大值、最小值
     */
    public static TxtFileDo readTxtByPath(String filePath) {
        if (!filePath.endsWith("txt")) {
            throw new RuntimeException("the file is not end with .txt");
        }
        BufferedReader reader;
        FileReader fileReader;

        List<Double> result = new ArrayList<>();
        Double[] fileReaderResult;

        try {
            fileReader = new FileReader(filePath);
            reader = new BufferedReader(fileReader);
            // 按行读取
            String line = reader.readLine();
            while (line != null) {
                result.add(Double.valueOf(line));
                line = reader.readLine();
            }
            fileReader.close();
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        fileReaderResult = new Double[result.size()];
        Double max = Double.MIN_VALUE;
        Double min = Double.MAX_VALUE;
        for (int i = 0; i < result.size(); i++) {
            Double cur = result.get(i);
            fileReaderResult[i] = cur;
            max = Math.max(cur, max);
            min = Math.min(cur, min);
        }
        int length = result.size();

        return TxtFileDo.builder().data(fileReaderResult)
                .max(max)
                .min(min)
                .build();
    }

    /**
     * 根据绝对路径删除文件
     *
     * @param filePath 文件路径
     * @return 删除成功返回true
     */
    public static Boolean deleteFileByAbsolutePath(String filePath) {
        File file = new File(filePath);
        if (file.isFile() && file.exists()) {
            boolean deleted = file.delete();
            if (deleted) {
                log.debug("🗑️ [TxtUtil] 删除文件: {}", filePath);
            }
            return deleted;
        } else {
            return false;
        }
    }

    /**
     * 将double数组写入TXT文件，每行一个值
     *
     * @param filePath 文件路径
     * @param data     数据数组
     */
    public static void writeTxtFile(String filePath, double[] data) {
        if (!filePath.endsWith("txt")) {
            throw new RuntimeException("the filename is wrong, not end with .txt");
        }
        FileWriter fileWriter;
        BufferedWriter writer;
        try {
            fileWriter = new FileWriter(filePath);
            writer = new BufferedWriter(fileWriter);
            for (double l : data) {
                String line = String.valueOf(l);
                writer.write(line);
                writer.newLine();
            }
            writer.close();
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 将Double数组写入TXT文件
     *
     * @param filePath 文件路径
     * @param data     数据数组
     */
    public static void writeTxtFile(String filePath, Double[] data) {
        if (!filePath.endsWith("txt")) {
            throw new RuntimeException("the filename is wrong, not end with .txt");
        }
        FileWriter fileWriter;
        BufferedWriter writer;
        try {
            fileWriter = new FileWriter(filePath);
            writer = new BufferedWriter(fileWriter);
            for (double l : data) {
                String line = String.valueOf(l);
                writer.write(line);
                writer.newLine();
            }
            writer.close();
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 通用IIR滤波函数
     *
     * @param signal 输入信号
     * @param a      分子系数
     * @param b      分母系数
     * @return 滤波结果
     */
    private static TxtFileDo filter(Double[] signal, Double[] a, Double[] b) {
        in = new Double[b.length];
        out = new Double[a.length - 1];
        outData = new Double[signal.length];
        Double max = Double.MIN_VALUE;
        Double min = Double.MAX_VALUE;
        for (int i = 0; i < signal.length; i++) {
            // 移位输入缓冲区
            System.arraycopy(in, 0, in, 1, in.length - 1);
            in[0] = signal[i];
            // 计算y = sum(b[j] * in[j]) - sum(a[j+1] * out[j])
            Double y = 0.0;
            for (int j = 0; j < b.length; j++) {
                if (in[j] != null) {
                    y += b[j] * in[j];
                }
            }
            for (int j = 0; j < a.length - 1; j++) {
                if (out[j] != null) {
                    y -= a[j + 1] * out[j];
                }
            }
            // 移位输出缓冲区
            System.arraycopy(out, 0, out, 1, out.length - 1);
            out[0] = y;
            outData[i] = y;
            max = Math.max(y, max);
            min = Math.min(y, min);
        }
        return TxtFileDo.builder()
                .data(outData)
                .max(max)
                .min(min)
                .build();
    }
}
