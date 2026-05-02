package com.itheima.realtime.utils;

import com.itheima.consultant.constant.Constants;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * TXT数据转WAV音频文件工具类
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 */
@Slf4j
public class Txt2WavUtil {

    /**
     * 将double数组转换为WAV文件
     *
     * @param txtData 采样数据数组
     * @param wavName 输出WAV文件名（不含后缀）
     * @param max      数据最大值
     * @param min      数据最小值
     * @return 成功返回1，失败返回0
     * @throws IOException IO异常
     */
    public static Integer doubleDataToWavFile(Double[] txtData, String wavName, Double max, Double min) throws IOException {

        // 确保输出目录存在
        File filedir = new File("./wavFile");
        if (!filedir.exists() && !filedir.isDirectory()) {
            boolean created = filedir.mkdir();
            if (created) {
                log.debug("📂 [Txt2Wav] 创建输出目录: ./wavFile");
            }
        }

        int dataLength = txtData.length;
        // 输出文件路径
        String relativePath = "./wavFile/" + wavName + ".wav";
        File wavpath = new File(relativePath);
        FileOutputStream out = new FileOutputStream(wavpath);

        // 写入WAV文件头
        out.write("RIFF".getBytes());
        // 数据长度 + 36 = 数据长度 + 固定头部长度
        out.write(intToByteArraySmall(dataLength + 36));
        out.write("WAVE".getBytes());
        out.write("fmt ".getBytes());
        out.write(0x20);
        out.write(intToByteArraySmall(16));
        out.write(shortToByteArraySmall((short) 1));  // PCM format
        out.write(shortToByteArraySmall((short) 1));  // 1 channel
        out.write(intToByteArraySmall(44100));      // sample rate
        out.write(intToByteArraySmall(44100));      // byte rate
        out.write(shortToByteArraySmall((short) 1));  // block align
        out.write(shortToByteArraySmall((short) 8));  // bits per sample
        out.write("data".getBytes());
        out.write(intToByteArraySmall(dataLength));

        // 归一化转换并写入数据
        Double absmax = Math.max(Math.abs(max), Math.abs(min));
        try {
            for (int i = 0; i < txtData.length; i++) {
                double temp = txtData[i];
                // 归一化到 0-255 8-bit PCM
                int normalized = (int) ((temp / absmax) * 128) + 128;
                out.write(normalized);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return Constants.FAIL_INT;
        }

        log.info("✅ [Txt2Wav] WAV文件创建成功: {}", relativePath);
        return Constants.SUCCESS_INT;
    }

    /**
     * int转4字节小端字节数组
     */
    public static byte[] intToByteArraySmall(int i) {
        byte[] result = new byte[4];
        result[3] = (byte) ((i >> 24) & 0xFF);
        result[2] = (byte) ((i >> 16) & 0xFF);
        result[1] = (byte) ((i >> 8) & 0xFF);
        result[0] = (byte) (i & 0xFF);
        return result;
    }

    /**
     * short转2字节小端字节数组
     */
    public static byte[] shortToByteArraySmall(short i) {
        byte[] result = new byte[2];
        result[1] = (byte) ((i >> 8) & 0xFF);
        result[0] = (byte) (i & 0xFF);
        return result;
    }
}
