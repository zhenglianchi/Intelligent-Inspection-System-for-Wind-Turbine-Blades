package com.itheima.realtime.listener;

import com.itheima.consultant.constant.CacheConstant;
import com.itheima.consultant.constant.Constants;
import com.itheima.realtime.service.RedisCacheService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 文件变化监听器
 * 当FTP目录下新增txt音频文件时，自动更新Redis缓存中的最新文件路径
 *
 * 文件名格式：HH_WF10001_WT1_CH1_20210410000000.txt
 * 格式说明：HH_风场_风机_通道_时间.txt
 *
 * @Migration migrated from wtb-health-monitor
 * @Modified 缓存改为RedisCacheService
 */
@Slf4j
@Service
public class FileListener extends FileAlterationListenerAdaptor {

    private final RedisCacheService redisCacheService;

    @Autowired
    public FileListener(RedisCacheService redisCacheService) {
        this.redisCacheService = redisCacheService;
    }

    /**
     * 文件创建事件：新文件上传完成后触发
     * 更新缓存中的最新文件路径
     *
     * @param file 新创建的文件
     */
    @Override
    public void onFileCreate(File file) {
        String fileName = file.getName();
        String absolutePath = file.getPath();
        log.info("📄 [FileListener] 新文件上传: {}", fileName);

        // 适配旧版本：保存全局最新文件路径
        String latestFileKey = CacheConstant.getKey(CacheConstant.KEY_REAL_TIME, "latest_file");
        Pair<String, String> fileNameAndPathPair = new ImmutablePair<>(fileName, absolutePath);
        redisCacheService.putCommon(latestFileKey, fileNameAndPathPair);

        // 新版本：按风机ID保存最新文件路径，支持多风机同时上传
        Map<String, String> fileInfo = parseFileName(fileName.substring(0, fileName.length() - 4));
        if (Objects.isNull(fileInfo)) {
            return;
        }

        String windturbine = fileInfo.get("WT");
        if (windturbine != null) {
            String latestFileKeyById = CacheConstant.getLatestFileKeyById(windturbine);
            // 删除旧文件（正常文件，如果配置开启删除）
            Pair<String, String> prevFilePair = redisCacheService.getCommon(latestFileKeyById, Pair.class);
            redisCacheService.putCommon(latestFileKeyById, fileNameAndPathPair);

            // 配置开启删除：删除之前的正常文件节省空间
            boolean oldFileDeleteFlag = Boolean.parseBoolean(
                    System.getProperty("realtime.ftp.normal.delete", "true")
            );
            if (oldFileDeleteFlag && prevFilePair != null) {
                String preFileAbsolutePath = prevFilePair.getRight();
                Map<String, String> prevFileInfo = parseFileName(prevFilePair.getLeft().substring(0, prevFilePair.getLeft().length() - 4));
                if (prevFileInfo != null) {
                    String wtState = prevFileInfo.get("ST");
                    if (wtState == null || "".equals(wtState) || wtState.equals(Constants.FAULT.toString())) {
                        return;
                    }
                    // 删除旧的正常文件
                    File oldFile = new File(preFileAbsolutePath);
                    if (oldFile.exists()) {
                        boolean deleted = oldFile.delete();
                        if (deleted) {
                            log.debug("🗑️ [FileListener] 删除旧正常文件: {}", preFileAbsolutePath);
                        }
                    }
                }
            }
        }
    }

    /**
     * 解析文件名，提取各个字段
     * 文件名格式: HH_WF{风场编号}_WT{风机编号}_CH{通道}_{状态}_{时间}
     * 示例: HH_WF10001_WT1_CH1_ST1_CY1_DT20210410000000
     *
     * @param fileName 文件名（不含扩展名）
     * @return 解析后的字段Map，key为两位前缀，value为值
     */
    private Map<String, String> parseFileName(String fileName) {
        String[] splits = fileName.split("_");
        Map<String, String> result = new HashMap<>();

        try {
            for (String cur : splits) {
                String key = cur.substring(0, 2);
                String val = cur.substring(2);
                result.put(key, val);
            }
        } catch (Exception e) {
            log.warn("⚠️ [FileListener] 文件名解析失败: {}", fileName);
            return null;
        }
        return result;
    }

    @Override
    public void onStart(FileAlterationObserver fileAlterationObserver) {
        // 不需要处理
    }

    @Override
    public void onDirectoryChange(File file) {
        // 不需要处理
    }

    @Override
    public void onDirectoryDelete(File file) {
        // 不需要处理
    }

    @Override
    public void onFileChange(File file) {
        // 不需要处理，文件创建后不会修改
    }

    @Override
    public void onFileDelete(File file) {
        // 不需要处理
    }

    @Override
    public void onStop(FileAlterationObserver fileAlterationObserver) {
        // 不需要处理
    }
}
